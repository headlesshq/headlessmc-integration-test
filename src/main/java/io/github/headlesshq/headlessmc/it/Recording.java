package io.github.headlesshq.headlessmc.it;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Everything one test did, written to {@code build/recordings/<platform>/<test>.json}.
 * The docs generator reads this format, bump {@link #SCHEMA_VERSION} on incompatible changes.
 * Output is normalized with the test's {@link Normalizer}.
 *
 * @param schemaVersion the version of this format.
 * @param test          the name of the test, e.g. {@code HelpTest.help}.
 * @param platform      the platform id, e.g. {@code linux-x64}.
 * @param os            the operating system, e.g. {@code linux}.
 * @param arch          the architecture, e.g. {@code x64}.
 * @param hmcVersion    the version reported by the executable.
 * @param executable    the executable under test.
 * @param steps         the commands run by the test, in order.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Recording(
    int schemaVersion,
    String test,
    String platform,
    String os,
    String arch,
    String hmcVersion,
    Executable executable,
    List<Step> steps
) {
    /**
     * The current version of the recording format.
     */
    public static final int SCHEMA_VERSION = 1;

    private static final JsonMapper MAPPER = JsonMapper.builder()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .build();

    public Recording {
        steps = List.copyOf(steps);
    }

    /**
     * The executable a recording was made with.
     *
     * @param type {@code jar} or {@code native}.
     * @param name the file name, e.g. {@code headlessmc.jar}.
     */
    public record Executable(String type, String name) {
    }

    /**
     * One command of a recording.
     *
     * @param command     the headlessmc arguments.
     * @param commandLine the command as typed by a user, e.g. {@code hmc help}.
     * @param exitCode    the exit code, {@code -1} if timed out.
     * @param timedOut    whether the command was killed after its timeout.
     * @param durationMs  how long the command ran in milliseconds.
     * @param stdout      the normalized standard output.
     * @param stderr      the normalized standard error.
     * @param files       the files changed by the command.
     */
    public record Step(
        List<String> command,
        String commandLine,
        int exitCode,
        boolean timedOut,
        long durationMs,
        String stdout,
        String stderr,
        List<FileChange> files
    ) {
        /**
         * @param result the result of a command.
         * @return the step for the result, with normalized output.
         */
        public static Step of(CommandResult result) {
            return new Step(
                result.args(),
                result.commandLine(),
                result.exitCode(),
                result.timedOut(),
                result.duration().toMillis(),
                result.normalizedStdout(),
                result.normalizedStderr(),
                result.files().changes()
            );
        }
    }

    /**
     * Creates a recording for the given results.
     *
     * @param test       the name of the test.
     * @param platform   the platform the test ran on.
     * @param hmcVersion the version of the executable.
     * @param executable the executable.
     * @param results    the results in order.
     * @return the recording.
     */
    public static Recording of(
        String test,
        Platform platform,
        String hmcVersion,
        HmcExecutable executable,
        List<CommandResult> results
    ) {
        return new Recording(
            SCHEMA_VERSION,
            test,
            platform.id(),
            platform.os(),
            platform.arch(),
            hmcVersion,
            new Executable(executable.type().name().toLowerCase(Locale.ROOT), executable.name()),
            results.stream().map(Step::of).toList()
        );
    }

    /**
     * @param recordingsDir the root recordings directory, e.g. {@code build/recordings}.
     * @return where this recording is stored below the given directory.
     */
    public Path path(Path recordingsDir) {
        return recordingsDir.resolve(platform).resolve(test + ".json");
    }

    /**
     * Writes this recording to {@link #path(Path)}.
     *
     * @param recordingsDir the root recordings directory.
     * @return the written file.
     */
    public Path write(Path recordingsDir) {
        Path file = path(recordingsDir);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, MAPPER.writeValueAsString(this) + "\n");
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write recording " + file, e);
        }

        return file;
    }

    /**
     * Reads a recording.
     *
     * @param file the recording file.
     * @return the recording.
     */
    public static Recording read(Path file) {
        try {
            return MAPPER.readValue(Files.readString(file), Recording.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read recording " + file, e);
        }
    }

}
