package io.github.headlesshq.headlessmc.it;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * AssertJ assertions for the files below a directory, usually {@link HmcRunner#dir()}.
 * Paths are relative to that directory and use {@code /}.
 */
public final class HmcFilesAssert extends AbstractAssert<HmcFilesAssert, Path> {
    /**
     * @param actual the root directory.
     */
    public HmcFilesAssert(Path actual) {
        super(actual, HmcFilesAssert.class);
    }

    /**
     * @param path the relative path of a file that must exist.
     * @return this assertion.
     */
    public HmcFilesAssert hasFile(String path) {
        isNotNull();
        if (!Files.isRegularFile(resolve(path))) {
            failWithMessage("Expected file %s to exist in %s", path, actual);
        }

        return this;
    }

    /**
     * @param path the relative path of a file or directory that must not exist.
     * @return this assertion.
     */
    public HmcFilesAssert doesNotHaveFile(String path) {
        isNotNull();
        if (Files.exists(resolve(path))) {
            failWithMessage("Expected %s to be absent in %s", path, actual);
        }

        return this;
    }

    /**
     * Compares the content of a text file, ignoring line endings and trailing newlines.
     *
     * @param path     the relative path of the file.
     * @param expected the expected content.
     * @return this assertion.
     */
    public HmcFilesAssert hasFileWithContent(String path, String expected) {
        Normalizer lines = Normalizer.builder().timestamps(false).build();
        Assertions.assertThat(lines.normalize(read(path)))
            .as("content of %s", path)
            .isEqualTo(lines.normalize(expected));
        return this;
    }

    /**
     * @param path   the relative path of the file.
     * @param values strings the file must contain.
     * @return this assertion.
     */
    public HmcFilesAssert hasFileContaining(String path, String... values) {
        Assertions.assertThat(read(path)).as("content of %s", path).contains(values);
        return this;
    }

    /**
     * @param path   the relative path of the file.
     * @param sha256 the expected hex sha256, case-insensitive.
     * @return this assertion.
     */
    public HmcFilesAssert hasFileWithSha256(String path, String sha256) {
        hasFile(path);
        try {
            Assertions.assertThat(FileTreeSnapshot.sha256(resolve(path)))
                .as("sha256 of %s", path)
                .isEqualToIgnoringCase(sha256);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return this;
    }

    /**
     * Counts the regular files matching a glob.
     *
     * @param glob     the glob, see {@link Glob}.
     * @param expected the expected number of matching files.
     * @return this assertion.
     */
    public HmcFilesAssert hasFileCount(String glob, int expected) {
        isNotNull();
        Glob compiled = Glob.of(glob);
        long count = FileTreeSnapshot.take(actual).files().keySet().stream().filter(compiled::matches).count();
        if (count != expected) {
            failWithMessage("Expected %d files matching %s in %s but found %d", expected, glob, actual, count);
        }

        return this;
    }

    private String read(String path) {
        hasFile(path);
        try {
            return Files.readString(resolve(path));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path resolve(String path) {
        return actual.resolve(path);
    }

}
