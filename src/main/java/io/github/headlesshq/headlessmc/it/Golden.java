package io.github.headlesshq.headlessmc.it;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Golden files with expected, normalized output, stored in {@code src/test/resources/golden/}.
 * Run with {@code -Dupdate.golden=true} to rewrite them from the actual output.
 */
public final class Golden {
    /**
     * System property with the golden file directory, set by the Gradle build.
     */
    public static final String DIR_PROPERTY = "hmc.golden.dir";
    /**
     * System property that makes golden assertions rewrite the golden files instead of comparing.
     */
    public static final String UPDATE_PROPERTY = "update.golden";

    private Golden() {
    }

    /**
     * @return the golden file directory.
     */
    public static Path dir() {
        String dir = System.getProperty(DIR_PROPERTY);
        return dir == null || dir.isBlank() ? Path.of("src", "test", "resources", "golden") : Path.of(dir);
    }

    /**
     * @return whether golden files are being rewritten.
     */
    public static boolean updating() {
        return Boolean.getBoolean(UPDATE_PROPERTY);
    }

    /**
     * Compares the actual text with a golden file, or rewrites the golden file in update mode.
     * Line endings and trailing newlines of the golden file are ignored.
     *
     * @param name   the golden file name relative to {@link #dir()}, e.g. {@code help.txt}.
     * @param actual the normalized actual text.
     */
    public static void assertMatches(String name, String actual) {
        Path file = dir().resolve(name);
        try {
            if (updating()) {
                Files.createDirectories(file.toAbsolutePath().getParent());
                Files.writeString(file, actual + "\n");
                return;
            }

            if (!Files.isRegularFile(file)) {
                fail("Golden file " + file + " does not exist, run with -D" + UPDATE_PROPERTY + "=true to create it.");
            }

            String expected = Normalizer.builder().timestamps(false).build().normalize(Files.readString(file));
            assertThat(actual)
                .as("output compared to golden file %s (run with -D%s=true to update)", file, UPDATE_PROPERTY)
                .isEqualTo(expected);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to access golden file " + file, e);
        }
    }

}
