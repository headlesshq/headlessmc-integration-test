package io.github.headlesshq.headlessmc.it;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The headlessmc executable under test, either a jar run with {@code java -jar} or a native binary.
 *
 * @param path the jar or native binary.
 * @param type whether {@link #path()} is a jar or a native binary.
 * @param java the java executable used to run a jar, unused for native binaries.
 */
public record HmcExecutable(Path path, Type type, Path java) {
    /**
     * System property pointing at the executable under test.
     */
    public static final String PROPERTY = "hmc.executable";
    /**
     * Environment variable pointing at the executable under test, used if {@link #PROPERTY} is not set.
     */
    public static final String ENV = "HMC_EXECUTABLE";
    /**
     * System property pointing at the java executable used to run a jar.
     */
    public static final String JAVA_PROPERTY = "hmc.java";
    /**
     * Environment variable pointing at the java executable used to run a jar.
     */
    public static final String JAVA_ENV = "HMC_JAVA";

    /**
     * The kind of executable.
     */
    public enum Type {
        JAR,
        NATIVE
    }

    /**
     * Locates the executable from the {@value #PROPERTY} system property or the {@value #ENV} environment variable.
     *
     * @return the located executable.
     * @throws IllegalStateException if neither is set or the file does not exist.
     */
    public static HmcExecutable locate() {
        String location = System.getProperty(PROPERTY);
        if (location == null || location.isBlank()) {
            location = System.getenv(ENV);
        }

        if (location == null || location.isBlank()) {
            throw new IllegalStateException(
                "No headlessmc executable configured, set the " + ENV + " environment variable or the "
                    + PROPERTY + " system property to a headlessmc jar or native binary."
            );
        }

        return of(Path.of(location));
    }

    /**
     * Creates an executable for the given path, detecting jars by their {@code .jar} extension.
     *
     * @param path the jar or native binary.
     * @return the executable.
     * @throws IllegalStateException if the file does not exist.
     */
    public static HmcExecutable of(Path path) {
        Path resolved = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(resolved) && Platform.current().isWindows()) {
            Path exe = resolved.resolveSibling(resolved.getFileName() + ".exe");
            if (Files.isRegularFile(exe)) {
                resolved = exe;
            }
        }

        if (!Files.isRegularFile(resolved)) {
            throw new IllegalStateException("headlessmc executable " + resolved + " does not exist");
        }

        boolean jar = resolved.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar");
        return new HmcExecutable(resolved, jar ? Type.JAR : Type.NATIVE, jar ? locateJava() : null);
    }

    /**
     * Builds the full command line to run this executable.
     *
     * @param systemProperties properties passed as {@code -Dkey=value}.
     * @param args             the headlessmc arguments.
     * @return the command line for a {@link ProcessBuilder}.
     */
    public List<String> command(List<String> systemProperties, List<String> args) {
        List<String> command = new ArrayList<>();
        if (type == Type.JAR) {
            command.add(java.toString());
            command.addAll(systemProperties);
            command.add("-jar");
            command.add(path.toString());
        } else {
            command.add(path.toString());
            command.addAll(systemProperties);
        }

        command.addAll(args);
        return command;
    }

    /**
     * @return the file name of the executable, e.g. {@code headlessmc.jar}.
     */
    public String name() {
        return path.getFileName().toString();
    }

    private static Path locateJava() {
        String java = System.getProperty(JAVA_PROPERTY);
        if (java == null || java.isBlank()) {
            java = System.getenv(JAVA_ENV);
        }

        if (java != null && !java.isBlank()) {
            return Path.of(java).toAbsolutePath();
        }

        String executable = Platform.current().isWindows() ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable);
    }

}
