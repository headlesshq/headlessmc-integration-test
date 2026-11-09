package io.github.headlesshq.headlessmc.it.commands;

import io.github.headlesshq.headlessmc.it.CommandResult;
import io.github.headlesshq.headlessmc.it.HmcRunner;
import io.github.headlesshq.headlessmc.it.HmcTable;
import io.github.headlesshq.headlessmc.it.HmcTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static io.github.headlesshq.headlessmc.it.HmcAssertions.assertThat;
import static io.github.headlesshq.headlessmc.it.HmcAssertions.assertThatFiles;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code version ls} and {@code version install}. Remote listings change whenever Minecraft or a mod loader
 * releases, so they are checked structurally.
 */
@HmcTest
class VersionTest {
    private static final String VERSION = "1.21.1";
    private static final String VERSION_JSON = "mc/versions/" + VERSION + "/" + VERSION + ".json";

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

}
