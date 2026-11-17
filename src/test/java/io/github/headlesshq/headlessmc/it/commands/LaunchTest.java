package io.github.headlesshq.headlessmc.it.commands;

import io.github.headlesshq.headlessmc.it.CommandResult;
import io.github.headlesshq.headlessmc.it.HmcRunner;
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
import java.util.zip.ZipFile;

import static io.github.headlesshq.headlessmc.it.HmcAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code launch} without {@code --dry-run}. Like the dry run in {@link VersionTest}, it downloads the client jar,
 * libraries and assets of the launched version, and then starts the game. The smallest vanilla version,
 * {@value #PARENT}, is launched with {@code --jvm -version} so that the game JVM exits right away.
 * <p>
 * {@code launch} exits with 1 if the client jar cannot be downloaded or if no Java runtime matching the version
 * is installed. {@value #PARENT} asks for Java 8, which is not available on every platform, so, as in
 * {@link VersionTest#dryRunDownloadsClientJar}, the test launches a version that inherits everything from
 * {@value #PARENT} but asks for the Java the tests run on. headlessmc writes the client jar of the parent
 * into the directory of the launched version.
 */
@HmcTest
class LaunchTest {
    private static final String PARENT = "rd-132211";
    private static final String PARENT_JSON = "mc/versions/" + PARENT + "/" + PARENT + ".json";

    @Test
    @Tag("network")
    void launchDownloadsClientJar(HmcRunner hmc) throws Exception {
        int java = Runtime.version().feature();
        String version = PARENT + "-java" + java;
        String versionDir = "mc/versions/" + version + "/";
        String clientJar = versionDir + version + ".jar";
        hmc.property("hmc.java.download", "false")
            .listProperty("hmc.java.versions", System.getProperty("java.home"));
        assertThat(hmc.run("version", "install", "vanilla", PARENT)).succeeded();
        JsonNode json = JsonMapper.shared().readTree(hmc.dir().resolve(PARENT_JSON).toFile());
        Files.createDirectories(hmc.dir().resolve(versionDir));
        Files.writeString(hmc.dir().resolve(versionDir + version + ".json"), """
            {"id": "%s", "inheritsFrom": "%s", "type": "old_alpha", "javaVersion": {"majorVersion": %d}}
            """.formatted(version, PARENT, java));

        CommandResult launch = hmc.run("launch", version, "--offline", "-lwjgl", "--jvm", "-version");

        assertThat(launch).succeeded();
        assertThat(launch.files())
            .hasAdded(clientJar)
            .hasAdded("mc/assets/indexes/" + json.path("assetIndex").path("id").asString() + ".json");

        JsonNode client = json.path("downloads").path("client");
        Path jar = hmc.dir().resolve(clientJar);
        assertThat(Files.size(jar)).isEqualTo(client.path("size").asLong());
        assertThat(sha1(jar)).isEqualTo(client.path("sha1").asString());
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            assertThat(zip.getEntry("com/mojang/rubydung/RubyDung.class")).isNotNull();
        }
    }

    private static String sha1(Path file) throws IOException, NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(file)));
    }

}
