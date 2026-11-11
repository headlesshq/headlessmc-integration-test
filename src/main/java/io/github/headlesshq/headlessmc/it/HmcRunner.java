package io.github.headlesshq.headlessmc.it;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs headlessmc commands non-interactively as subprocesses inside one test directory.
 * <p>
 * The test directory ({@code <HMC_DIR>}) is the working directory of every command and contains
 * <ul>
 *     <li>{@code home}: used as user home, {@code APPDATA} and XDG base directories,</li>
 *     <li>{@code hmc}: the headlessmc files location ({@code hmc.files.location}),</li>
 *     <li>{@code mc}: the Minecraft directory ({@code hmc.files.mc}),</li>
 *     <li>{@code game}: the game directory ({@code hmc.files.game}),</li>
 * </ul>
 * so commands never touch the real {@code ~/.minecraft} or headlessmc config.
 * Environment variables that identify a terminal ({@code TERM}, {@code OSTYPE}, {@code COLUMNS}) are not passed on,
 * because output is never a terminal: with them picocli assumes a pseudo terminal on Windows (e.g. Git Bash),
 * colours the output and runs {@code stty} to detect the width, which prints an error to stderr.
 * The directory is snapshotted before and after each command to record the file changes.
 */
public final class HmcRunner {
    /**
     * System property with the default command timeout in seconds.
     */
    public static final String TIMEOUT_PROPERTY = "hmc.timeout";
    /**
     * Token for the real user home of the JVM running the tests.
     */
    public static final String USER_HOME = "<USER_HOME>";

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120);
    /**
     * The version recorded when {@code --version} does not report one.
     */
    public static final String UNKNOWN_VERSION = "unknown";

    private static final Duration READER_GRACE = Duration.ofSeconds(10);
    private static final long POLL_MILLIS = 50;
    private static final Map<Path, Optional<String>> VERSIONS = new ConcurrentHashMap<>();
    private static final Map<List<Object>, Optional<String>> HELP = new ConcurrentHashMap<>();
    private static final List<String> REMOVED_ENV_PREFIXES = List.of("HMC_", "XDG_");
    private static final List<String> REMOVED_ENV = List.of(
        "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "TERM", "OSTYPE", "COLUMNS"
    );

    private final HmcExecutable executable;
    private final Path dir;
    private final Duration timeout;
    private final Optional<String> version;
    private final Normalizer normalizer;
    private final Map<String, String> properties = new LinkedHashMap<>();
    private final Map<String, String> environment = new LinkedHashMap<>();
    private final List<CommandResult> results = new ArrayList<>();

    /**
     * Creates a runner with the timeout from {@value #TIMEOUT_PROPERTY}, 120 seconds by default.
     *
     * @param executable the executable under test.
     * @param dir        the fresh test directory.
     */
    public HmcRunner(HmcExecutable executable, Path dir) {
        this(executable, dir, defaultTimeout());
    }

    /**
     * Creates a runner.
     *
     * @param executable the executable under test.
     * @param dir        the fresh test directory.
     * @param timeout    the default timeout for each command.
     */
    public HmcRunner(HmcExecutable executable, Path dir, Duration timeout) {
        this(executable, dir, timeout, versionOf(executable));
    }

    private HmcRunner(HmcExecutable executable, Path dir, Duration timeout, Optional<String> version) {
        this.executable = executable;
        this.dir = dir.toAbsolutePath().normalize();
        this.timeout = timeout;
        this.version = version;
        try {
            for (Path sub : List.of(homeDir(), hmcDir(), mcDir(), gameDir())) {
                Files.createDirectories(sub);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create test directory " + this.dir, e);
        }

        Normalizer.Builder normalizerBuilder = Normalizer.builder().directory(this.dir, Normalizer.HMC_DIR);
        Path userHome = Path.of(System.getProperty("user.home", "")).toAbsolutePath();
        if (userHome.getNameCount() > 0) {
            normalizerBuilder.directory(userHome, USER_HOME);
        }

        this.normalizer = normalizerBuilder.hmcVersion(version.orElse(null)).build();
        properties.put("hmc.jline.enabled", "false");
        properties.put("hmc.files.location", hmcDir().toString());
        properties.put("hmc.files.mc", mcDir().toString());
        properties.put("hmc.files.game", gameDir().toString());
        properties.put("user.home", homeDir().toString());
        properties.put("stdout.encoding", "UTF-8");
        properties.put("stderr.encoding", "UTF-8");
    }

    /**
     * Runs one command with the default timeout.
     *
     * @param args the headlessmc arguments, e.g. {@code "config", "ls"}.
     * @return the result.
     */
    public CommandResult run(String... args) {
        return run(timeout, Arrays.asList(args));
    }

    /**
     * Runs one command with the default timeout.
     *
     * @param args the headlessmc arguments.
     * @return the result.
     */
    public CommandResult run(List<String> args) {
        return run(timeout, args);
    }

    /**
     * Runs several commands one after another, each with its own snapshot and file diff.
     *
     * @param commands the commands to run.
     * @return the results in order.
     */
    public List<CommandResult> runAll(List<List<String>> commands) {
        return commands.stream().map(this::run).toList();
    }

    /**
     * Runs one command. The process is killed with its descendants if it exceeds the timeout,
     * and descendants still alive after it exited are killed before the output and files are collected.
     *
     * @param timeout how long the command may run.
     * @param args    the headlessmc arguments.
     * @return the result.
     */
    public CommandResult run(Duration timeout, List<String> args) {
        CommandResult result = execute(timeout, args);
        results.add(result);
        return result;
    }

    private CommandResult execute(Duration timeout, List<String> args) {
        FileTreeSnapshot before = FileTreeSnapshot.take(dir);
        List<String> command = executable.command(systemProperties(), args);
        ProcessBuilder builder = new ProcessBuilder(command).directory(dir.toFile());
        configureEnvironment(builder.environment());

        long start = System.nanoTime();
        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to start " + command, e);
        }

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        Thread outReader = pump(process.getInputStream(), stdout);
        Thread errReader = pump(process.getErrorStream(), stderr);
        Set<ProcessHandle> descendants = new HashSet<>();
        boolean timedOut;
        try {
            process.getOutputStream().close();
            timedOut = !waitFor(process, timeout, descendants);
            if (timedOut) {
                kill(process);
            }

            killSurvivors(descendants);
            outReader.join(READER_GRACE);
            errReader.join(READER_GRACE);
        } catch (IOException e) {
            kill(process);
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            kill(process);
            throw new IllegalStateException("Interrupted while running " + command, e);
        }

        Duration duration = Duration.ofNanos(System.nanoTime() - start);
        FileTreeDiff files = before.diffTo(FileTreeSnapshot.take(dir));
        return new CommandResult(
            args,
            timedOut ? -1 : process.exitValue(),
            timedOut,
            duration,
            text(stdout),
            text(stderr),
            files,
            normalizer
        );
    }

    private List<String> systemProperties() {
        List<String> result = new ArrayList<>();
        properties.forEach((key, value) -> result.add("-D" + key + "=" + value));
        return result;
    }

    private void configureEnvironment(Map<String, String> env) {
        env.keySet().removeIf(key -> {
            String upper = key.toUpperCase(Locale.ROOT);
            return REMOVED_ENV.contains(upper) || REMOVED_ENV_PREFIXES.stream().anyMatch(upper::startsWith);
        });

        Path home = homeDir();
        env.put("HOME", home.toString());
        env.put("USERPROFILE", home.toString());
        env.put("APPDATA", home.resolve("AppData").resolve("Roaming").toString());
        env.put("LOCALAPPDATA", home.resolve("AppData").resolve("Local").toString());
        env.put("XDG_CONFIG_HOME", home.resolve(".config").toString());
        env.put("XDG_DATA_HOME", home.resolve(".local").resolve("share").toString());
        env.put("XDG_STATE_HOME", home.resolve(".local").resolve("state").toString());
        env.put("XDG_CACHE_HOME", home.resolve(".cache").toString());
        env.putAll(environment);
    }

    private static Thread pump(InputStream in, ByteArrayOutputStream out) {
        return Thread.ofVirtual().start(() -> {
            byte[] buffer = new byte[8192];
            try (in) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    synchronized (out) {
                        out.write(buffer, 0, read);
                    }
                }
            } catch (IOException ignored) {
                return;
            }
        });
    }

    private static String text(ByteArrayOutputStream out) {
        synchronized (out) {
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    /**
     * Waits for the process to exit while remembering its descendants, which cannot be listed
     * once the process has exited and its orphans were reparented.
     */
    private static boolean waitFor(Process process, Duration timeout, Set<ProcessHandle> descendants)
        throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            process.descendants().forEach(descendants::add);
            long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
            if (remaining <= 0) {
                return !process.isAlive();
            }

            if (process.waitFor(Math.min(remaining, POLL_MILLIS), TimeUnit.MILLISECONDS)) {
                return true;
            }
        }
    }

    private static void killSurvivors(Set<ProcessHandle> descendants) {
        List<ProcessHandle> survivors = new ArrayList<>();
        for (ProcessHandle handle : descendants) {
            if (handle.isAlive()) {
                handle.descendants().forEach(survivors::add);
                survivors.add(handle);
            }
        }

        survivors.forEach(ProcessHandle::destroyForcibly);
        for (ProcessHandle handle : survivors) {
            try {
                handle.onExit().get(READER_GRACE.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (ExecutionException | TimeoutException ignored) {
                continue;
            }
        }
    }

    private static void kill(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        try {
            process.waitFor(READER_GRACE.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Sets an additional system property ({@code -Dkey=value}) for all following commands,
     * e.g. a headlessmc config value. Overrides the isolation properties if the same key is used.
     *
     * @param key   the property name.
     * @param value the property value.
     * @return this runner.
     */
    public HmcRunner property(String key, String value) {
        properties.put(key, value);
        return this;
    }

    /**
     * Sets an additional environment variable for all following commands.
     *
     * @param key   the variable name.
     * @param value the variable value.
     * @return this runner.
     */
    public HmcRunner env(String key, String value) {
        environment.put(key, value);
        return this;
    }

    /**
     * @return the results of all commands run so far, in order.
     */
    public List<CommandResult> results() {
        return Collections.unmodifiableList(results);
    }

    /**
     * @return the executable under test.
     */
    public HmcExecutable executable() {
        return executable;
    }

    /**
     * @return the version reported by {@code --version}, or {@value #UNKNOWN_VERSION}.
     */
    public String version() {
        return version.orElse(UNKNOWN_VERSION);
    }

    /**
     * @return the normalizer replacing this runner's directories and version.
     */
    public Normalizer normalizer() {
        return normalizer;
    }

    /**
     * @return the test directory, {@code <HMC_DIR>}.
     */
    public Path dir() {
        return dir;
    }

    /**
     * @return the isolated user home.
     */
    public Path homeDir() {
        return dir.resolve("home");
    }

    /**
     * @return the headlessmc files location.
     */
    public Path hmcDir() {
        return dir.resolve("hmc");
    }

    /**
     * @return the Minecraft directory.
     */
    public Path mcDir() {
        return dir.resolve("mc");
    }

    /**
     * @return the game directory.
     */
    public Path gameDir() {
        return dir.resolve("game");
    }

    /**
     * @return the timeout from {@value #TIMEOUT_PROPERTY} in seconds, 120 seconds by default.
     */
    public static Duration defaultTimeout() {
        String seconds = System.getProperty(TIMEOUT_PROPERTY);
        return seconds == null || seconds.isBlank() ? DEFAULT_TIMEOUT : Duration.ofSeconds(Long.parseLong(seconds));
    }

    /**
     * Determines the version of an executable by running {@code --version} in a throwaway directory.
     * The result is cached per executable.
     *
     * @param executable the executable.
     * @return the first non-empty line printed by {@code --version}, or empty if it failed or printed nothing.
     */
    public static Optional<String> versionOf(HmcExecutable executable) {
        return VERSIONS.computeIfAbsent(executable.path(), path -> runInTempDir(executable, List.of("--version"))
            .flatMap(stdout -> stdout.lines().map(String::strip).filter(line -> !line.isEmpty()).findFirst()));
    }

    /**
     * Checks whether a command of an executable has an option by running {@code <command> --help}
     * in a throwaway directory. The result is cached per executable and command.
     * Use it to skip tests of options that the executable under test does not have yet.
     *
     * @param executable the executable.
     * @param command    the command, e.g. {@code List.of("launch")}.
     * @param option     the option, e.g. {@code "--dry-run"}.
     * @return whether the help of the command lists the option.
     */
    public static boolean hasOption(HmcExecutable executable, List<String> command, String option) {
        List<String> args = new ArrayList<>(command);
        args.add("--help");
        Optional<String> help = HELP.computeIfAbsent(List.of(executable.path(), List.copyOf(args)),
            key -> runInTempDir(executable, args));
        return help.stream()
            .flatMap(text -> Arrays.stream(text.split("[\\s\\[\\],=|]+")))
            .anyMatch(option::equals);
    }

    private static Optional<String> runInTempDir(HmcExecutable executable, List<String> args) {
        Path tmp = null;
        try {
            tmp = Files.createTempDirectory("hmc-it-probe-");
            CommandResult result = new HmcRunner(executable, tmp, Duration.ofSeconds(60), Optional.empty())
                .execute(Duration.ofSeconds(60), args);
            return result.exitCode() == 0 ? Optional.of(result.stdout()) : Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (tmp != null) {
                deleteRecursively(tmp);
            }
        }
    }

    /**
     * Deletes a directory and everything below it, ignoring files that cannot be deleted.
     *
     * @param root the directory to delete.
     */
    public static void deleteRecursively(Path root) {
        if (!Files.exists(root)) {
            return;
        }

        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directory, IOException exc) {
                    delete(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete " + root, e);
        }
    }

    private static void delete(Path path) {
        try {
            path.toFile().setWritable(true);
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            return;
        }
    }

}
