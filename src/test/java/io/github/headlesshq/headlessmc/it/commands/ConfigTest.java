package io.github.headlesshq.headlessmc.it.commands;

import io.github.headlesshq.headlessmc.it.CommandResult;
import io.github.headlesshq.headlessmc.it.HmcRunner;
import io.github.headlesshq.headlessmc.it.HmcTable;
import io.github.headlesshq.headlessmc.it.HmcTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.github.headlesshq.headlessmc.it.HmcAssertions.assertThat;
import static io.github.headlesshq.headlessmc.it.HmcAssertions.assertThatFiles;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * {@code config ls}, {@code config get} and {@code config set}, including what ends up in
 * {@code hmc/config.properties}.
 */
@HmcTest
class ConfigTest {
    private static final String CONFIG = "hmc/config.properties";
    private static final List<String> COLUMNS = List.of("name", "value", "description");

    @Test
    void listShowsPropertiesWithValuesAndDescriptions(HmcRunner hmc) {
        CommandResult result = hmc.run("config", "ls");

        assertThat(result).succeeded().hasEmptyStderr();
        HmcTable table = HmcTable.parse(result);
        assertThat(table.columns()).isEqualTo(COLUMNS);
        assertThat(table.column("name"))
            .contains("hmc.jline.enabled", "hmc.java.max-scan-depth", "hmc.files.location")
            .allMatch(name -> name.matches("[a-z0-9]+(\\.[a-z0-9-]+)+"), "is a property name")
            .isSorted();
        assertThat(table.row("name", "hmc.jline.enabled")).get()
            .satisfies(row -> assertThat(row).contains(entry("value", "false")));
        assertThat(table.row("name", "hmc.files.location")).get()
            .satisfies(row -> assertThat(row).contains(entry("value", "<HMC_DIR>/hmc")));
        assertThat(table.row("name", "hmc.files.mc")).get()
            .satisfies(row -> assertThat(row).contains(entry("value", "<HMC_DIR>/mc")));
        assertThat(result.files()).containsOnlyPathsMatching(CONFIG, "hmc/logs/**");
    }

    @Test
    void getShowsDefaultValue(HmcRunner hmc) {
        CommandResult result = hmc.run("config", "get", "hmc.java.max-scan-depth");

        assertThat(result).succeeded().hasEmptyStderr();
        assertThat(HmcTable.parse(result).rows()).containsExactly(Map.of(
            "name", "hmc.java.max-scan-depth",
            "value", "6",
            "description", "How deep to scan Java installation directories."
        ));
    }

    @Test
    void getUnknownPropertyShowsNoValue(HmcRunner hmc) {
        CommandResult result = hmc.run("config", "get", "hmc.it.does-not-exist");

        assertThat(result).succeeded();
        assertThat(HmcTable.parse(result).rows())
            .containsExactly(Map.of("name", "hmc.it.does-not-exist", "value", "-", "description", "-"));
    }

    @Test
    void setWritesConfigFile(HmcRunner hmc) {
        CommandResult set = hmc.run("config", "set", "hmc.java.max-scan-depth", "3");

        assertThat(set)
            .succeeded()
            .hasEmptyStderr()
            .hasOutput("Set hmc.java.max-scan-depth to 3");
        assertThat(set.files()).containsOnlyPathsMatching(CONFIG, "hmc/logs/**");
        assertThat(set.files().paths()).contains(CONFIG);
        assertThatFiles(hmc).hasFileContaining(CONFIG, "\nhmc.java.max-scan-depth=3\n");

        CommandResult get = hmc.run("config", "get", "hmc.java.max-scan-depth");
        assertThat(get).succeeded();
        assertThat(HmcTable.parse(get).row("name", "hmc.java.max-scan-depth")).get()
            .satisfies(row -> assertThat(row).contains(entry("value", "3")));
        assertThat(get.files()).doesNotHaveChange(CONFIG);
    }

    @Test
    void setOverwritesPreviousValue(HmcRunner hmc) {
        hmc.run("config", "set", "hmc.it.example", "first");
        CommandResult result = hmc.run("config", "set", "hmc.it.example", "second");

        assertThat(result).succeeded().hasOutput("Set hmc.it.example to second");
        assertThat(result.files()).hasModified(CONFIG);
        assertThatFiles(hmc).hasFileContaining(CONFIG, "hmc.it.example=second");
        assertThat(hmc.dir().resolve(CONFIG)).content().doesNotContain("hmc.it.example=first");
    }

    @Test
    void setTemporaryDoesNotWriteConfigFile(HmcRunner hmc) {
        hmc.run("config", "ls");
        CommandResult result = hmc.run("config", "set", "--temp", "hmc.java.max-scan-depth", "3");

        assertThat(result)
            .succeeded()
            .hasOutput("Set hmc.java.max-scan-depth to 3 temporarily");
        assertThat(result.files()).doesNotHaveChange(CONFIG);
        assertThat(hmc.dir().resolve(CONFIG)).content().doesNotContain("hmc.java.max-scan-depth");
    }

    @Test
    void setWithoutValueFailsNonInteractively(HmcRunner hmc) {
        hmc.run("config", "ls");
        CommandResult result = hmc.run("config", "set", "hmc.java.max-scan-depth");

        assertThat(result)
            .failed()
            .hasOutputContaining("Please specify the value to set hmc.java.max-scan-depth to");
        assertThat(result.files()).doesNotHaveChange(CONFIG);
    }

    @Test
    void systemPropertyOverridesConfigFile(HmcRunner hmc) {
        hmc.run("config", "set", "hmc.java.max-scan-depth", "3");
        hmc.property("hmc.java.max-scan-depth", "4");

        CommandResult result = hmc.run("config", "get", "hmc.java.max-scan-depth");

        assertThat(result).succeeded();
        assertThat(HmcTable.parse(result).row("name", "hmc.java.max-scan-depth")).get()
            .satisfies(row -> assertThat(row).contains(entry("value", "4")));
        assertThatFiles(hmc).hasFileContaining(CONFIG, "hmc.java.max-scan-depth=3");
    }

}
