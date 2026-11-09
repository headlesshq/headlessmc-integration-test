package io.github.headlesshq.headlessmc.it.commands;

import io.github.headlesshq.headlessmc.it.CommandResult;
import io.github.headlesshq.headlessmc.it.HmcRunner;
import io.github.headlesshq.headlessmc.it.HmcTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.github.headlesshq.headlessmc.it.HmcAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code --version} and {@code -V} print the headlessmc version.
 */
@HmcTest
class VersionInfoTest {
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"--version", "-V"})
    void printsVersion(String option, HmcRunner hmc) {
        CommandResult result = hmc.run(option);

        assertThat(result)
            .succeeded()
            .hasEmptyStderr()
            .hasOutputMatchingGolden("version.txt");
        assertThat(hmc.version()).matches("\\d+\\.\\d+\\.\\d+(-[\\w.]+)?");
        HelpTest.assertNoFilesBesidesConfigAndLogs(hmc, result);
    }

}
