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
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static io.github.headlesshq.headlessmc.it.HmcAssertions.assertThat;
import static io.github.headlesshq.headlessmc.it.HmcAssertions.assertThatFiles;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code server ls} and {@code server add}.
 */
@HmcTest
class ServerTest {
    private static final String NAME = "test-server";
    private static final String VERSION = "1.21.1";
    private static final String DIR = "servers/" + NAME;

    @Test
    void listIsEmptyInitially(HmcRunner hmc) {
        CommandResult result = hmc.run("server", "ls");

        assertThat(result).succeeded().hasEmptyStderr();
        HmcTable table = HmcTable.parse(result);
        assertThat(table.columns()).containsExactly("name", "version", "directory");
        assertThat(table.rows()).isEmpty();
        HelpTest.assertNoFilesBesidesConfigAndLogs(hmc, result);
    }

    @Test
    @Tag("network")
    void addVanillaServer(HmcRunner hmc) throws IOException {
        CommandResult add = addServer(hmc);

        assertThat(add)
            .succeeded()
            .hasOutputMatching("^Installed server " + NAME + " for version server/" + VERSION + "$");
        assertThat(add.files())
            .containsOnlyPathsMatching(DIR + "/**", "hmc/**", "mc/versions/**")
            .hasAdded(DIR + "/server.jar")
            .hasAdded("hmc/profiles/" + NAME + ".json");
        assertThatFiles(hmc)
            .hasFileCount(DIR + "/**", 1)
            .hasFileCount("{home,game}/**", 0);

        JsonNode profile = JsonMapper.shared().readTree(hmc.dir().resolve("hmc/profiles/" + NAME + ".json").toFile());
        assertThat(profile.path("name").asString()).isEqualTo(NAME);
        assertThat(profile.path("version").path("side").asString()).isEqualTo("SERVER");
        assertThat(profile.path("version").path("platform").asString()).isEqualTo("vanilla");
        assertThat(profile.path("version").path("version").asString()).isEqualTo(VERSION);
        assertThat(Path.of(URI.create(profile.path("path").asString()))).isEqualTo(hmc.dir().resolve(DIR));

        Path jar = hmc.dir().resolve(DIR).resolve("server.jar");
        assertThat(Files.size(jar)).isGreaterThan(10_000_000L);
        try (InputStream in = Files.newInputStream(jar)) {
            assertThat(in.readNBytes(4)).as("zip header of %s", jar).containsExactly('P', 'K', 3, 4);
        }

        CommandResult list = hmc.run("server", "ls");
        assertThat(list).succeeded();
        assertThat(HmcTable.parse(list).rows())
            .containsExactly(Map.of("name", NAME, "version", "server/" + VERSION, "directory", "<HMC_DIR>/" + DIR));
    }

    @Test
    @Tag("network")
    void addExistingServerFails(HmcRunner hmc) {
        assertThat(addServer(hmc)).succeeded();

        CommandResult again = addServer(hmc);

        assertThat(again).failed().hasOutputContaining(NAME);
        assertThat(again.files()).containsOnlyPathsMatching("hmc/config.properties", "hmc/logs/**", "hmc/cache/**");
    }

    private static CommandResult addServer(HmcRunner hmc) {
        return hmc.run("server", "add", "vanilla", VERSION, "--name", NAME,
            "--dir", hmc.dir().resolve(DIR).toString());
    }

}
