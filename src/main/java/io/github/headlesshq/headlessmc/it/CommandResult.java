package io.github.headlesshq.headlessmc.it;

import java.time.Duration;
import java.util.List;

/**
 * The outcome of running one headlessmc command.
 *
 * @param args       the headlessmc arguments, without executable and system properties.
 * @param exitCode   the exit code, {@code -1} if the process timed out.
 * @param timedOut   whether the process was killed because it exceeded its timeout.
 * @param duration   how long the process ran.
 * @param stdout     the raw standard output.
 * @param stderr     the raw standard error.
 * @param files      the files changed in the test directory while the command ran.
 * @param normalizer the normalizer of the test directory.
 */
public record CommandResult(
    List<String> args,
    int exitCode,
    boolean timedOut,
    Duration duration,
    String stdout,
    String stderr,
    FileTreeDiff files,
    Normalizer normalizer
) {
    public CommandResult {
        args = List.copyOf(args);
    }

    /**
     * @return the normalized standard output.
     */
    public String normalizedStdout() {
        return normalizer.normalize(stdout);
    }

    /**
     * @return the normalized standard error.
     */
    public String normalizedStderr() {
        return normalizer.normalize(stderr);
    }

    /**
     * @return the arguments with the test directory and other machine specific values normalized.
     */
    public List<String> normalizedArgs() {
        return args.stream().map(normalizer::normalize).toList();
    }

    /**
     * @return the command as a user would type it, e.g. {@code hmc config ls}.
     */
    public String commandLine() {
        return commandLine(args);
    }

    /**
     * @return the command as a user would type it, with {@link #normalizedArgs()}.
     */
    public String normalizedCommandLine() {
        return commandLine(normalizedArgs());
    }

    private static String commandLine(List<String> args) {
        StringBuilder builder = new StringBuilder("hmc");
        for (String arg : args) {
            builder.append(' ');
            if (arg.isEmpty() || arg.chars().anyMatch(c -> Character.isWhitespace(c) || c == '"' || c == '\'')) {
                builder.append('"').append(arg.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
            } else {
                builder.append(arg);
            }
        }

        return builder.toString();
    }

    @Override
    public String toString() {
        return commandLine() + " -> exit code " + exitCode + (timedOut ? " (timed out)" : "")
            + " in " + duration.toMillis() + "ms"
            + "\n--- stdout ---\n" + normalizedStdout()
            + "\n--- stderr ---\n" + normalizedStderr()
            + "\n--- files ---\n" + String.join("\n", files.changes().stream()
                .map(change -> change.change() + " " + change.path()).toList());
    }

}
