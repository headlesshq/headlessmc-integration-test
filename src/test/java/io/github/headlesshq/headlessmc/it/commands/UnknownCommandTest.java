package io.github.headlesshq.headlessmc.it.commands;

import io.github.headlesshq.headlessmc.it.CommandResult;
import io.github.headlesshq.headlessmc.it.HmcRunner;
import io.github.headlesshq.headlessmc.it.HmcTest;
import org.junit.jupiter.api.Test;

import static io.github.headlesshq.headlessmc.it.HmcAssertions.assertThat;

/**
 * Unknown commands, unknown subcommands and missing parameters fail with picocli's usage error exit code
 * and print the error with the usage help to stderr.
 */
@HmcTest
class UnknownCommandTest {
    private static final int USAGE_ERROR = 2;

    @Test
    void unknownCommand(HmcRunner hmc) {
        CommandResult result = hmc.run("this-command-does-not-exist");

        assertThat(result)
            .hasExitCode(USAGE_ERROR)
            .hasOutput("")
            .hasStderrMatchingGolden("errors/unknown-command.txt");
        HelpTest.assertNoFilesBesidesConfigAndLogs(hmc, result);
    }

    @Test
    void unknownSubcommand(HmcRunner hmc) {
        CommandResult result = hmc.run("config", "this-subcommand-does-not-exist");

        assertThat(result)
            .hasExitCode(USAGE_ERROR)
            .hasOutput("")
            .hasStderrMatchingGolden("errors/unknown-subcommand.txt");
    }

    @Test
    void misspelledCommandSuggestsAlternatives(HmcRunner hmc) {
        assertThat(hmc.run("confg", "ls"))
            .hasExitCode(USAGE_ERROR)
            .hasStderrMatching("^Unmatched arguments? from index 0: 'confg'")
            .hasStderrMatching("^Did you mean: headlessmc config\\b");
    }

    @Test
    void missingRequiredParameter(HmcRunner hmc) {
        CommandResult result = hmc.run("config", "set");

        assertThat(result)
            .hasExitCode(USAGE_ERROR)
            .hasOutput("")
            .hasStderrMatchingGolden("errors/missing-parameter.txt");
        assertThat(result.files()).containsOnlyPathsMatching("hmc/config.properties", "hmc/logs/**");
    }

}
