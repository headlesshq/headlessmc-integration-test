package io.github.headlesshq.headlessmc.it;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.AbstractStringAssert;
import org.assertj.core.api.Assertions;

import java.util.regex.Pattern;

/**
 * AssertJ assertions for a {@link CommandResult}. Output assertions use the normalized output.
 * Failure messages include the full result to make failures easy to diagnose.
 */
public final class CommandResultAssert extends AbstractAssert<CommandResultAssert, CommandResult> {
    /**
     * @param actual the result to check.
     */
    public CommandResultAssert(CommandResult actual) {
        super(actual, CommandResultAssert.class);
    }

    /**
     * Checks the exit code.
     *
     * @param expected the expected exit code.
     * @return this assertion.
     */
    public CommandResultAssert hasExitCode(int expected) {
        isNotNull();
        if (actual.timedOut()) {
            failWithMessage("Expected exit code %d but the command timed out:%n%s", expected, actual);
        }

        if (actual.exitCode() != expected) {
            failWithMessage("Expected exit code %d but was %d:%n%s", expected, actual.exitCode(), actual);
        }

        return this;
    }

    /**
     * Checks that the command exited with code 0 within its timeout.
     *
     * @return this assertion.
     */
    public CommandResultAssert succeeded() {
        return hasExitCode(0);
    }

    /**
     * Checks that the command exited with a code other than 0 within its timeout.
     *
     * @return this assertion.
     */
    public CommandResultAssert failed() {
        isNotNull();
        if (actual.timedOut() || actual.exitCode() == 0) {
            failWithMessage("Expected the command to fail:%n%s", actual);
        }

        return this;
    }

    /**
     * @return this assertion.
     */
    public CommandResultAssert hasNonEmptyOutput() {
        isNotNull();
        if (actual.normalizedStdout().isBlank()) {
            failWithMessage("Expected non-empty stdout:%n%s", actual);
        }

        return this;
    }

    /**
     * @param expected the expected normalized stdout.
     * @return this assertion.
     */
    public CommandResultAssert hasOutput(String expected) {
        stdout().isEqualTo(expected);
        return this;
    }

    /**
     * @param values strings the normalized stdout must contain.
     * @return this assertion.
     */
    public CommandResultAssert hasOutputContaining(String... values) {
        stdout().contains(values);
        return this;
    }

    /**
     * Checks that the regex is found somewhere in the normalized stdout, in multiline mode.
     *
     * @param regex the regex.
     * @return this assertion.
     */
    public CommandResultAssert hasOutputMatching(String regex) {
        stdout().containsPattern(Pattern.compile(regex, Pattern.MULTILINE));
        return this;
    }

    /**
     * Compares the normalized stdout with a golden file.
     *
     * @param name the golden file name, e.g. {@code help.txt}.
     * @return this assertion.
     * @see Golden
     */
    public CommandResultAssert hasOutputMatchingGolden(String name) {
        isNotNull();
        Golden.assertMatches(name, actual.normalizedStdout());
        return this;
    }

    /**
     * @return this assertion.
     */
    public CommandResultAssert hasEmptyStderr() {
        stderr().isEmpty();
        return this;
    }

    /**
     * @return assertions on the normalized stdout.
     */
    public AbstractStringAssert<?> stdout() {
        isNotNull();
        return Assertions.assertThat(actual.normalizedStdout()).as("stdout of %s", actual.commandLine());
    }

    /**
     * @return assertions on the normalized stderr.
     */
    public AbstractStringAssert<?> stderr() {
        isNotNull();
        return Assertions.assertThat(actual.normalizedStderr()).as("stderr of %s", actual.commandLine());
    }

    /**
     * @return assertions on the file changes of the command.
     */
    public FileTreeDiffAssert files() {
        isNotNull();
        return new FileTreeDiffAssert(actual.files()).as("files changed by %s", actual.commandLine());
    }

}
