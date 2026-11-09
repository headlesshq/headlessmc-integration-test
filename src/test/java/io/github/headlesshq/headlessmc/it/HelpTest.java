package io.github.headlesshq.headlessmc.it;

import org.junit.jupiter.api.Test;

import static io.github.headlesshq.headlessmc.it.HmcAssertions.assertThat;
import static io.github.headlesshq.headlessmc.it.HmcAssertions.assertThatFiles;

/**
 * Self-test of the harness against the executable under test.
 */
@HmcTest
class HelpTest {
    @Test
    void help(HmcRunner hmc) {
        CommandResult result = hmc.run("help");

        assertThat(result)
            .succeeded()
            .hasNonEmptyOutput()
            .hasOutputContaining("Usage: headlessmc");
        assertThat(result.files()).containsOnlyPathsMatching("hmc/config.properties", "hmc/logs/**");
        assertThatFiles(hmc).hasFileCount("{home,mc,game}/**", 0);
    }

    @Test
    void version(HmcRunner hmc) {
        assertThat(hmc.run("--version"))
            .succeeded()
            .hasOutputMatchingGolden("version.txt");
    }

    @Test
    void unknownCommandFails(HmcRunner hmc) {
        assertThat(hmc.run("this-command-does-not-exist"))
            .failed()
            .stderr().contains("this-command-does-not-exist");
    }

}
