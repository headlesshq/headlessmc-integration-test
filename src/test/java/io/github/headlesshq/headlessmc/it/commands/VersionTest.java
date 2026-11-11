package io.github.headlesshq.headlessmc.it.commands;

import io.github.headlesshq.headlessmc.it.CommandResult;
import io.github.headlesshq.headlessmc.it.HmcRunner;
import io.github.headlesshq.headlessmc.it.HmcTable;
import io.github.headlesshq.headlessmc.it.HmcTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static io.github.headlesshq.headlessmc.it.HmcAssertions.assertThat;
import static io.github.headlesshq.headlessmc.it.HmcAssertions.assertThatFiles;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * {@code version ls}, {@code version install} and downloading a version with {@code launch --dry-run}.
 * Remote listings change whenever Minecraft or a mod loader releases, so they are checked structurally.
 * <p>
 * {@code version install vanilla} only writes the version json, {@code launch --dry-run} downloads the client jar,
 * libraries, natives, assets and the Java runtime without starting the game. The dry run tests are skipped
 * if the executable under test has no {@code --dry-run} yet, which headlessmc#554 added after 3.0.0-RC8.
 */
@HmcTest
class VersionTest {
    private static final String VERSION = "1.21.1";
    private static final String VERSION_JSON = "mc/versions/" + VERSION + "/" + VERSION + ".json";
    private static final String DRY_RUN_PARENT = "rd-132211";

    @Test
    void listInstalledIsEmptyInitially(HmcRunner hmc) {
        CommandResult result = hmc.run("version", "ls");

        assertThat(result).succeeded().hasEmptyStderr();
        HmcTable table = HmcTable.parse(result);
        assertThat(table.columns()).containsExactly("name", "parent", "type");
        assertThat(table.rows()).isEmpty();
        HelpTest.assertNoFilesBesidesConfigAndLogs(hmc, result);
    }

    @Test
    @Tag("network")
    void listRemoteVanillaVersions(HmcRunner hmc) {
        CommandResult result = hmc.run("version", "ls", "--remote", "vanilla");

        assertThat(result).succeeded().hasEmptyStderr();
        HmcTable table = HmcTable.parse(result);
        assertThat(table.columns()).containsExactly("platform", "version", "build");
        assertThat(table.rows()).hasSizeGreaterThan(500).allSatisfy(row -> {
            assertThat(row.get("platform")).isEqualTo("vanilla");
            assertThat(row.get("version")).isNotBlank().isEqualTo(row.get("build"));
        });
        assertThat(table.column("version")).contains(VERSION, "1.8.9", "rd-132211");
        assertThat(result.files()).containsOnlyPathsMatching("hmc/**").hasAdded("hmc/cache/vanilla-versions.json");
    }

    @Test
    @Tag("network")
    void listRemoteFiltersByVersion(HmcRunner hmc) {
        CommandResult result = hmc.run("version", "ls", "--remote", "vanilla", VERSION);

        assertThat(result).succeeded();
        assertThat(HmcTable.parse(result).rows())
            .containsExactly(Map.of("platform", "vanilla", "version", VERSION, "build", VERSION));
    }

    @Test
    @Tag("network")
    void installVanillaVersion(HmcRunner hmc) {
        CommandResult install = hmc.run("version", "install", "vanilla", VERSION);

        assertThat(install)
            .succeeded()
            .hasEmptyStderr()
            .hasOutput("Installed version " + VERSION + " successfully");
        assertThat(install.files())
            .containsOnlyPathsMatching("hmc/**", "mc/versions/" + VERSION + "/**")
            .hasAdded(VERSION_JSON);
        assertThatFiles(hmc).hasFileCount("{home,game}/**", 0);

        JsonNode json = JsonMapper.shared().readTree(hmc.dir().resolve(VERSION_JSON).toFile());
        assertThat(json.path("id").asString()).isEqualTo(VERSION);
        assertThat(json.path("type").asString()).isEqualTo("release");
        assertThat(json.path("mainClass").asString()).isNotBlank();
        assertThat(json.path("downloads").path("client").path("url").asString())
            .startsWith("https://").endsWith("/client.jar");
        assertThat(json.path("downloads").path("client").path("sha1").asString()).matches("[0-9a-f]{40}");

        CommandResult list = hmc.run("version", "ls");
        assertThat(list).succeeded();
        assertThat(HmcTable.parse(list).rows())
            .containsExactly(Map.of("name", VERSION, "parent", "-", "type", "release"));
    }

    @Test
    @Tag("network")
    void installTwiceKeepsVersion(HmcRunner hmc) {
        List<CommandResult> results = hmc.runAll(List.of(
            List.of("version", "install", "vanilla", VERSION),
            List.of("version", "install", "vanilla", VERSION)
        ));

        assertThat(results.get(1)).succeeded().hasOutputContaining(VERSION);
        assertThatFiles(hmc).hasFile(VERSION_JSON).hasFileCount("mc/versions/*/*.json", 1);
    }

    /**
     * Downloads the smallest vanilla version, {@value #DRY_RUN_PARENT}, with {@code launch --dry-run}.
     * A dry run fails if no Java runtime matching the version is installed, and Temurin publishes no Java 8,
     * which {@value #DRY_RUN_PARENT} asks for, for macOS and Windows on arm64. So the dry run launches a version
     * that inherits everything from {@value #DRY_RUN_PARENT} but asks for the Java the tests run on, which is
     * passed via {@code hmc.java.versions}. headlessmc writes the client jar of the parent into the directory
     * of the launched version, like for Fabric. Assets cannot be disabled; {@code hmc.assets.dummy} replaces
     * the sounds headlessmc ships dummies for, the rest of the about 50MB are downloaded.
     */
    @Test
    @Tag("network")
    void dryRunDownloadsClientJar(HmcRunner hmc) throws IOException, NoSuchAlgorithmException {
        assumeDryRun(hmc);
        int java = Runtime.version().feature();
        String version = DRY_RUN_PARENT + "-java" + java;
        String clientJar = "mc/versions/" + version + "/" + version + ".jar";
        hmc.property("hmc.java.download", "false")
            .property("hmc.java.versions", System.getProperty("java.home"))
            .property("hmc.assets.dummy", "true");
        assertThat(hmc.run("version", "install", "vanilla", DRY_RUN_PARENT)).succeeded();
        writeVersion(hmc, version, """
            {"id": "%s", "inheritsFrom": "%s", "type": "old_alpha", "javaVersion": {"majorVersion": %d}}
            """.formatted(version, DRY_RUN_PARENT, java));

        CommandResult dryRun = hmc.run("launch", version, "--dry-run");

        assertThat(dryRun).succeeded();
        assertThat(dryRun.files())
            .hasAdded(clientJar)
            .hasAdded("mc/assets/indexes/pre-1.6.json");
        JsonNode client = JsonMapper.shared()
            .readTree(hmc.dir().resolve("mc/versions/" + DRY_RUN_PARENT + "/" + DRY_RUN_PARENT + ".json").toFile())
            .path("downloads").path("client");
        Path jar = hmc.dir().resolve(clientJar);
        assertThat(Files.size(jar)).isEqualTo(client.path("size").asLong());
        assertThat(sha1(jar)).isEqualTo(client.path("sha1").asString());
    }

    /**
     * A dry run of a local version whose client jar points at a closed port on localhost.
     * The HTTP proxies point there too and the updater is disabled, so the test does not need the internet.
     */
    @Test
    void dryRunFailsIfADownloadFails(HmcRunner hmc) throws IOException {
        assumeDryRun(hmc);
        int java = Runtime.version().feature();
        String deadUrl = "http://127.0.0.1:1/";
        hmc.property("hmc.java.download", "false")
            .property("hmc.java.versions", System.getProperty("java.home"))
            .property("hmc.updater.enabled", "false")
            .property("http.proxyHost", "127.0.0.1")
            .property("http.proxyPort", "1")
            .property("https.proxyHost", "127.0.0.1")
            .property("https.proxyPort", "1");
        Files.createDirectories(hmc.mcDir().resolve("assets/indexes"));
        Files.writeString(hmc.mcDir().resolve("assets/indexes/broken.json"), "{\"objects\": {}}");
        writeVersion(hmc, "broken", """
            {
              "id": "broken",
              "type": "release",
              "mainClass": "net.minecraft.client.Minecraft",
              "javaVersion": {"majorVersion": %d},
              "assets": "broken",
              "assetIndex": {"id": "broken", "sha1": "%s", "size": 14, "totalSize": 0, "url": "%sbroken.json"},
              "libraries": [],
              "downloads": {"client": {"sha1": "%s", "size": 1, "url": "%sclient.jar"}}
            }
            """.formatted(java, "0".repeat(40), deadUrl, "0".repeat(40), deadUrl));

        CommandResult dryRun = hmc.run("launch", "broken", "--dry-run");

        assertThat(dryRun).failed().hasOutputContaining("ConnectException");
        assertThat(hmc.mcDir().resolve("versions/broken/broken.jar")).doesNotExist();
    }

    private static void assumeDryRun(HmcRunner hmc) {
        assumeTrue(HmcRunner.hasOption(hmc.executable(), List.of("launch"), "--dry-run"),
            "launch --dry-run is not available in headlessmc " + hmc.version());
    }

    private static void writeVersion(HmcRunner hmc, String id, String json) throws IOException {
        Path dir = hmc.mcDir().resolve("versions").resolve(id);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(id + ".json"), json);
    }

    private static String sha1(Path file) throws IOException, NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(file)));
    }

}
