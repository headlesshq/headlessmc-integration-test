package io.github.headlesshq.headlessmc.it.commands;

import io.github.headlesshq.headlessmc.it.CommandResult;
import io.github.headlesshq.headlessmc.it.HmcRunner;
import io.github.headlesshq.headlessmc.it.HmcTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.zip.ZipFile;

import static io.github.headlesshq.headlessmc.it.HmcAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code launch}. headlessmc only downloads the client jar, libraries and assets when it launches a version,
 * so this is where the client jar of a downloaded version is checked. The smallest vanilla version,
 * {@value #VERSION}, is launched with {@code --jvm -version} so that the game JVM exits right away.
 * {@code launch} exits with 0 even if downloads fail, so the files on disk are the real check.
 */
@HmcTest
class LaunchTest {
    private static final String VERSION = "rd-132211";
    private static final String VERSION_DIR = "mc/versions/" + VERSION + "/";
    private static final String VERSION_JSON = VERSION_DIR + VERSION + ".json";
    private static final String CLIENT_JAR = VERSION_DIR + VERSION + ".jar";

    @Test
    @Tag("network")
    void launchDownloadsClientJar(HmcRunner hmc) throws Exception {
        hmc.property("hmc.java.download", "false");
        assertThat(hmc.run("version", "install", "vanilla", VERSION)).succeeded();
        JsonNode json = JsonMapper.shared().readTree(hmc.dir().resolve(VERSION_JSON).toFile());
        createLegacyResourceDirs(hmc, json);

        CommandResult launch = hmc.run("launch", VERSION, "--offline", "-lwjgl", "--jvm", "-version");

        assertThat(launch.files())
            .hasAdded(CLIENT_JAR)
            .hasAdded("mc/assets/indexes/" + json.path("assetIndex").path("id").asString() + ".json");

        JsonNode client = json.path("downloads").path("client");
        Path jar = hmc.dir().resolve(CLIENT_JAR);
        assertThat(Files.size(jar)).isEqualTo(client.path("size").asLong());
        assertThat(sha1(jar)).isEqualTo(client.path("sha1").asString());
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            assertThat(zip.getEntry("com/mojang/rubydung/RubyDung.class")).isNotNull();
        }
    }

    /**
     * Works around a headlessmc bug: for asset indexes with {@code map_to_resources} or {@code virtual},
     * it copies each asset to {@code mc/resources/<name>} without creating the parent directories,
     * so every copy fails and the launch is aborted before the client jar is downloaded.
     * Remove this once headlessmc creates the directories itself.
     */
    private static void createLegacyResourceDirs(HmcRunner hmc, JsonNode version) throws IOException, InterruptedException {
        URI url = URI.create(version.path("assetIndex").path("url").asString());
        JsonNode index;
        try (HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()) {
            HttpRequest request = HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(30)).build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            assertThat(response.statusCode()).as("status of %s", url).isEqualTo(200);
            try (InputStream in = response.body()) {
                index = JsonMapper.shared().readTree(in);
            }
        }

        assertThat(index.path("map_to_resources").asBoolean()).as("map_to_resources of %s", url).isTrue();
        for (String name : index.path("objects").propertyNames()) {
            Path parent = hmc.mcDir().resolve("resources").resolve(name).getParent();
            Files.createDirectories(parent);
        }
    }

    private static String sha1(Path file) throws IOException, NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(file)));
    }

}
