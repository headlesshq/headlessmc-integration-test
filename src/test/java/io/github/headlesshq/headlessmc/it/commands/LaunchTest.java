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
 * {@code launch}. headlessmc only downloads the client jar, libraries and assets when it launches a version,
 * so this is where the client jar of a downloaded version is checked. The smallest vanilla version,
 * {@value #VERSION}, is launched with {@code --jvm -version} so that the game JVM exits right away.
 * The launch itself exits with 1 where no Java 8 is installed, so the files on disk are the real check.
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

    private static String sha1(Path file) throws IOException, NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(file)));
    }

}
