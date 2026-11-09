package io.github.headlesshq.headlessmc.it.commands;

import io.github.headlesshq.headlessmc.it.CommandResult;
import io.github.headlesshq.headlessmc.it.HmcRunner;
import io.github.headlesshq.headlessmc.it.HmcTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.github.headlesshq.headlessmc.it.HmcAssertions.assertThat;
import static io.github.headlesshq.headlessmc.it.HmcAssertions.assertThatFiles;

/**
 * {@code help} and {@code help <command>} for every top-level command, compared with golden files.
 */
@HmcTest
class HelpTest {
    @Test
    void help(HmcRunner hmc) {
        CommandResult result = hmc.run("help");

        assertThat(result)
            .succeeded()
            .hasEmptyStderr()
            .hasOutputMatchingGolden("help/headlessmc.txt");
        assertNoFilesBesidesConfigAndLogs(hmc, result);
    }

    @Test
    void helpOption(HmcRunner hmc) {
        assertThat(hmc.run("--help"))
            .succeeded()
            .hasOutputMatchingGolden("help/headlessmc.txt");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"config", "account", "java", "launch", "version", "profile", "server", "mod", "help"})
    void helpForCommand(String command, HmcRunner hmc) {
        CommandResult result = hmc.run("help", command);

        assertThat(result)
            .succeeded()
            .hasEmptyStderr()
            .hasOutputMatchingGolden("help/" + command + ".txt");
        assertNoFilesBesidesConfigAndLogs(hmc, result);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"auth, account", "login, account", "download, version", "install, version"})
    void helpForAliasIsHelpForCommand(String alias, String command, HmcRunner hmc) {
        assertThat(hmc.run("help", alias))
            .succeeded()
            .hasOutputMatchingGolden("help/" + command + ".txt");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"config", "java", "server"})
    void helpOptionOfCommandIsHelpForCommand(String command, HmcRunner hmc) {
        assertThat(hmc.run(command, "--help"))
            .succeeded()
            .hasOutputMatchingGolden("help/" + command + ".txt");
    }

    static void assertNoFilesBesidesConfigAndLogs(HmcRunner hmc, CommandResult result) {
        assertThat(result.files()).containsOnlyPathsMatching("hmc/config.properties", "hmc/logs/**");
        assertThatFiles(hmc).hasFileCount("{home,mc,game}/**", 0);
    }

}
