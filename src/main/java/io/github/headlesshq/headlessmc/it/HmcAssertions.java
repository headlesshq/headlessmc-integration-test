package io.github.headlesshq.headlessmc.it;

import java.nio.file.Path;

/**
 * Entry point for the harness assertions, use alongside {@link org.assertj.core.api.Assertions}.
 */
public final class HmcAssertions {
    private HmcAssertions() {
    }

    /**
     * @param actual the result of a command.
     * @return assertions for the result.
     */
    public static CommandResultAssert assertThat(CommandResult actual) {
        return new CommandResultAssert(actual);
    }

    /**
     * @param actual the file changes of a command.
     * @return assertions for the changes.
     */
    public static FileTreeDiffAssert assertThat(FileTreeDiff actual) {
        return new FileTreeDiffAssert(actual);
    }

    /**
     * @param root the directory, usually {@link HmcRunner#dir()}.
     * @return assertions for the files below the directory.
     */
    public static HmcFilesAssert assertThatFiles(Path root) {
        return new HmcFilesAssert(root);
    }

    /**
     * @param runner the runner whose test directory to check.
     * @return assertions for the files in the test directory.
     */
    public static HmcFilesAssert assertThatFiles(HmcRunner runner) {
        return new HmcFilesAssert(runner.dir());
    }

}
