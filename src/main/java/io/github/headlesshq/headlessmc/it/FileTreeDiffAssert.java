package io.github.headlesshq.headlessmc.it;

import org.assertj.core.api.AbstractAssert;

import java.util.Arrays;
import java.util.List;

/**
 * AssertJ assertions for a {@link FileTreeDiff}. Paths are relative to {@code <HMC_DIR>} and use {@code /}.
 */
public final class FileTreeDiffAssert extends AbstractAssert<FileTreeDiffAssert, FileTreeDiff> {
    /**
     * @param actual the diff to check.
     */
    public FileTreeDiffAssert(FileTreeDiff actual) {
        super(actual, FileTreeDiffAssert.class);
    }

    /**
     * Checks that no file was changed.
     *
     * @return this assertion.
     */
    public FileTreeDiffAssert isEmpty() {
        isNotNull();
        if (!actual.isEmpty()) {
            failWithMessage("Expected no file changes but got:%n%s", describe());
        }

        return this;
    }

    /**
     * Checks that every changed file matches at least one of the globs, e.g. to allow only config and log files.
     *
     * @param globs the allowed paths, see {@link Glob}.
     * @return this assertion.
     */
    public FileTreeDiffAssert containsOnlyPathsMatching(String... globs) {
        isNotNull();
        List<Glob> compiled = Arrays.stream(globs).map(Glob::of).toList();
        List<String> unexpected = actual.paths().stream()
            .filter(path -> compiled.stream().noneMatch(glob -> glob.matches(path)))
            .toList();
        if (!unexpected.isEmpty()) {
            failWithMessage("Expected only changes matching %s but also got %s in:%n%s",
                Arrays.toString(globs), unexpected, describe());
        }

        return this;
    }

    /**
     * @param path the expected added file.
     * @return this assertion.
     */
    public FileTreeDiffAssert hasAdded(String path) {
        return hasChange(path, FileChange.Kind.ADDED);
    }

    /**
     * @param path the expected modified file.
     * @return this assertion.
     */
    public FileTreeDiffAssert hasModified(String path) {
        return hasChange(path, FileChange.Kind.MODIFIED);
    }

    /**
     * @param path the expected deleted file.
     * @return this assertion.
     */
    public FileTreeDiffAssert hasDeleted(String path) {
        return hasChange(path, FileChange.Kind.DELETED);
    }

    /**
     * @param path a file that must not have been changed.
     * @return this assertion.
     */
    public FileTreeDiffAssert doesNotHaveChange(String path) {
        isNotNull();
        if (actual.paths().contains(path)) {
            failWithMessage("Expected %s to be unchanged but got:%n%s", path, describe());
        }

        return this;
    }

    private FileTreeDiffAssert hasChange(String path, FileChange.Kind kind) {
        isNotNull();
        if (!actual.paths(kind).contains(path)) {
            failWithMessage("Expected %s to be %s but got:%n%s", path, kind, describe());
        }

        return this;
    }

    private String describe() {
        if (actual.isEmpty()) {
            return "  (no changes)";
        }

        StringBuilder builder = new StringBuilder();
        for (FileChange change : actual.changes()) {
            builder.append("  ").append(change.change()).append(' ').append(change.path()).append('\n');
        }

        return builder.toString();
    }

}
