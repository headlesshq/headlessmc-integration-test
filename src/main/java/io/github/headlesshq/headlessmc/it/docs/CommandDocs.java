package io.github.headlesshq.headlessmc.it.docs;

import io.github.headlesshq.headlessmc.it.FileChange;
import io.github.headlesshq.headlessmc.it.Recording;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generates Markdown pages for mkdocs-material from the {@link Recording}s of the integration tests:
 * one page per test class, e.g. {@code ConfigTest} becomes {@code config.md}, with a section per test
 * that shows each command, its output and the files it changed, plus an {@code index.md} linking all pages.
 *
 * <p>The output of {@link #CANONICAL} is shown. Other platforms only get their own tab where their output differs,
 * ignoring how many spaces separate table columns and file sizes and hashes, which depend on the machine.
 * The output only depends on the recordings, so regenerating unchanged recordings gives identical files.
 */
public final class CommandDocs {
    /**
     * The run whose output is shown by default, the jar on {@code ubuntu-latest}.
     */
    public static final String CANONICAL = "jar linux-x64";
    /**
     * The name of the generated index page.
     */
    public static final String INDEX = "index.md";

    private static final Pattern COLUMN_GAP = Pattern.compile(" {2,}");
    private static final Pattern BACKTICKS = Pattern.compile("`+");
    private static final Pattern CAMEL_CASE = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])");
    private static final String INDENT = "    ";

    private CommandDocs() {
    }

    /**
     * Generates the docs.
     *
     * @param args the recordings directory and the output directory.
     */
    public static void main(String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: CommandDocs <recordings dir> <output dir>");
        }

        List<Path> pages = generate(Path.of(args[0]), Path.of(args[1]));
        System.out.println("Generated " + pages.size() + " pages in " + Path.of(args[1]));
    }

    /**
     * Reads all {@code *.json} recordings below {@code recordingsDir} and writes the pages to {@code outputDir},
     * replacing all Markdown files already in it.
     *
     * @param recordingsDir the directory containing the recordings, searched recursively.
     * @param outputDir     the directory to write the pages to.
     * @return the written files, the index last.
     */
    public static List<Path> generate(Path recordingsDir, Path outputDir) {
        List<Run> runs = read(recordingsDir);
        if (runs.isEmpty()) {
            throw new IllegalStateException("No recordings found in " + recordingsDir);
        }

        Map<String, Map<String, List<Run>>> pages = runs.stream().collect(Collectors.groupingBy(
            run -> page(run.recording().test()),
            TreeMap::new,
            Collectors.groupingBy(run -> run.recording().test(), TreeMap::new, Collectors.toList())
        ));
        String versions = runs.stream()
            .map(run -> run.recording().hmcVersion())
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(TreeSet::new))
            .stream()
            .collect(Collectors.joining(", "));
        List<String> labels = runs.stream().map(Run::label).distinct().sorted(LABEL_ORDER).toList();

        try {
            Files.createDirectories(outputDir);
            try (Stream<Path> existing = Files.list(outputDir)) {
                for (Path file : existing.filter(file -> file.toString().endsWith(".md")).toList()) {
                    Files.delete(file);
                }
            }

            List<Path> written = new ArrayList<>();
            for (Map.Entry<String, Map<String, List<Run>>> page : pages.entrySet()) {
                written.add(write(outputDir.resolve(page.getKey() + ".md"), page(page.getKey(), page.getValue(), versions, labels)));
            }

            written.add(write(outputDir.resolve(INDEX), index(pages, versions, labels)));
            return written;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write docs to " + outputDir, e);
        }
    }

    private static final Comparator<String> LABEL_ORDER = Comparator
        .comparing((String label) -> !label.equals(CANONICAL))
        .thenComparing(Function.identity());

    private record Run(Recording recording, String label) {
        private Run(Recording recording) {
            this(recording, recording.executable().type() + " " + recording.platform());
        }

        private Recording.Step step(int index) {
            return recording.steps().get(index);
        }
    }

    private static List<Run> read(Path recordingsDir) {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(recordingsDir)) {
            files = walk.filter(Files::isRegularFile).filter(file -> file.toString().endsWith(".json")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list recordings in " + recordingsDir, e);
        }

        Map<String, Run> runs = new LinkedHashMap<>();
        for (Path file : files) {
            Recording recording = Recording.read(file);
            if (recording.schemaVersion() != Recording.SCHEMA_VERSION) {
                throw new IllegalStateException("Unsupported schema version " + recording.schemaVersion() + " of " + file);
            }

            Run run = new Run(recording);
            runs.putIfAbsent(run.label() + " " + recording.test(), run);
        }

        return runs.values().stream()
            .sorted(Comparator.comparing((Run run) -> run.recording().test()).thenComparing(Run::label, LABEL_ORDER))
            .toList();
    }

    private static String page(String test) {
        String testClass = test.substring(0, test.indexOf('.'));
        if (testClass.endsWith("Test") && testClass.length() > "Test".length()) {
            testClass = testClass.substring(0, testClass.length() - "Test".length());
        }

        return CAMEL_CASE.splitAsStream(testClass).map(word -> word.toLowerCase(Locale.ROOT)).collect(Collectors.joining("-"));
    }

    private static String title(String page) {
        String words = page.replace('-', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private static String testTitle(String test) {
        String method = test.substring(test.indexOf('.') + 1);
        String parameter = null;
        int dash = method.indexOf('-');
        if (dash >= 0) {
            parameter = method.substring(dash + 1).replaceAll("^_+|_+$", "").replace('_', ' ');
            method = method.substring(0, dash);
        }

        String words = CAMEL_CASE.splitAsStream(method).map(word -> word.toLowerCase(Locale.ROOT)).collect(Collectors.joining(" "));
        String title = Character.toUpperCase(words.charAt(0)) + words.substring(1);
        return parameter == null || parameter.isEmpty() ? title : title + ": " + code(parameter);
    }

    private static String header(String versions) {
        return "!!! info \"Auto-generated from integration tests for hmc " + versions + "\"";
    }

    private static String page(String page, Map<String, List<Run>> tests, String versions, List<String> labels) {
        Out out = new Out();
        out.line("# " + title(page));
        out.line();
        out.line(header(versions));
        out.line(INDENT + "Generated by `./gradlew generateDocs` from the recordings of " + code(tests.keySet().iterator().next().replaceAll("\\..*", "")) + ".");
        out.line(INDENT + "Output is shown for " + code(CANONICAL) + ", other platforms get their own tab where their output differs.");
        for (Map.Entry<String, List<Run>> test : tests.entrySet()) {
            List<Run> runs = test.getValue();
            out.line();
            out.line("## " + testTitle(test.getKey()));
            out.line();
            out.line(code(test.getKey()));
            if (runs.size() < labels.size()) {
                out.line();
                out.line("!!! note \"" + recordedOn(runs.stream().map(Run::label).toList(), labels) + "\"");
            }

            Map<List<String>, List<Run>> structures = groupBy(runs, run -> run.recording().steps().stream().map(Recording.Step::commandLine).toList());
            if (structures.size() == 1) {
                steps(runs, out);
            } else {
                for (List<Run> group : structures.values()) {
                    out.line();
                    out.line("=== \"" + tab(group, runs) + "\"");
                    steps(group, out.indented());
                }
            }
        }

        return out.toString();
    }

    private static void steps(List<Run> runs, Out out) {
        int count = runs.getFirst().recording().steps().size();
        for (int i = 0; i < count; i++) {
            int index = i;
            Map<StepKey, List<Run>> variants = groupBy(runs, run -> StepKey.of(run.step(index)));
            if (variants.size() == 1) {
                step(runs.getFirst().step(i), out);
            } else {
                for (List<Run> variant : variants.values()) {
                    out.line();
                    out.line("=== \"" + tab(variant, runs) + "\"");
                    step(variant.getFirst().step(i), out.indented());
                }
            }
        }
    }

    private static void step(Recording.Step step, Out out) {
        List<String> console = new ArrayList<>();
        console.add("$ " + step.commandLine());
        if (!step.stdout().isEmpty()) {
            console.addAll(step.stdout().lines().toList());
        }

        out.line();
        out.block("console", null, console);
        if (!step.stderr().isEmpty()) {
            out.line();
            out.block("text", "stderr", step.stderr().lines().toList());
        }

        if (step.timedOut()) {
            out.line();
            out.line("Timed out.");
        } else if (step.exitCode() != 0) {
            out.line();
            out.line("Exit code " + code(String.valueOf(step.exitCode())) + ".");
        }

        out.line();
        if (step.files().isEmpty()) {
            out.line("No files changed.");
        } else {
            out.block("text", "Files changed", FileTree.render(step.files()));
        }
    }

    private static String index(Map<String, Map<String, List<Run>>> pages, String versions, List<String> labels) {
        Out out = new Out();
        out.line("# Commands");
        out.line();
        out.line(header(versions));
        out.line(INDENT + "Generated by `./gradlew generateDocs` from the recordings of the integration tests on "
            + labels.stream().map(CommandDocs::code).collect(Collectors.joining(", ")) + ".");
        out.line();
        out.line("| Page | Examples |");
        out.line("|------|----------|");
        pages.forEach((page, tests) -> out.line("| [" + title(page) + "](" + page + ".md) | " + tests.size() + " |"));
        return out.toString();
    }

    private record StepKey(int exitCode, boolean timedOut, String stdout, String stderr, List<String> files) {
        private static StepKey of(Recording.Step step) {
            return new StepKey(
                step.exitCode(),
                step.timedOut(),
                COLUMN_GAP.matcher(step.stdout()).replaceAll("  "),
                COLUMN_GAP.matcher(step.stderr()).replaceAll("  "),
                step.files().stream().map(CommandDocs::fileKey).sorted().toList()
            );
        }
    }

    private static String fileKey(FileChange change) {
        return change.change() + " " + change.path();
    }

    private static <K> Map<K, List<Run>> groupBy(List<Run> runs, Function<Run, K> key) {
        return runs.stream().collect(Collectors.groupingBy(key, LinkedHashMap::new, Collectors.toList()));
    }

    private static String tab(List<Run> group, List<Run> all) {
        return tabTitle(group.stream().map(Run::label).toList(), all.stream().map(Run::label).toList());
    }

    /**
     * Describes the platforms of a tab, listing every one of them unless it can be shortened without losing any:
     * all platforms of a type become e.g. {@code jar (all)}, and a group missing only a few of {@code all}
     * becomes e.g. {@code all except native windows-x64}.
     *
     * @param group the labels of the platforms in the tab.
     * @param all   the labels of all platforms the tab is chosen from.
     * @return the title of the tab.
     */
    static String tabTitle(List<String> group, List<String> all) {
        List<String> parts = collapse(group, all, " (all)");
        List<String> missing = collapse(complement(group, all), all, " (all)");
        if (!missing.isEmpty() && missing.size() < parts.size()) {
            return "all except " + String.join(", ", missing);
        }

        return String.join(", ", parts);
    }

    /**
     * Describes on which of {@code all} platforms a test was recorded, e.g. {@code Only recorded on jar}
     * or {@code Not recorded on native windows-x64}, without leaving out any platform.
     *
     * @param recorded the labels of the platforms the test was recorded on.
     * @param all      the labels of all platforms that have recordings.
     * @return the note.
     */
    static String recordedOn(List<String> recorded, List<String> all) {
        List<String> parts = collapse(recorded, all, "");
        List<String> missing = collapse(complement(recorded, all), all, "");
        if (!missing.isEmpty() && missing.size() < parts.size()) {
            return "Not recorded on " + String.join(", ", missing);
        }

        return "Only recorded on " + String.join(", ", parts);
    }

    private static List<String> complement(List<String> group, List<String> all) {
        return all.stream().filter(label -> !group.contains(label)).toList();
    }

    private static List<String> collapse(List<String> group, List<String> all, String suffix) {
        Map<String, List<String>> types = all.stream().distinct().collect(Collectors.groupingBy(CommandDocs::type, TreeMap::new, Collectors.toList()));
        List<String> parts = new ArrayList<>();
        types.forEach((type, labels) -> {
            if (labels.size() > 1 && group.containsAll(labels)) {
                parts.add(type + suffix);
            } else {
                labels.stream().filter(group::contains).sorted(LABEL_ORDER).forEach(parts::add);
            }
        });
        return parts;
    }

    private static String type(String label) {
        int space = label.indexOf(' ');
        return space < 0 ? label : label.substring(0, space);
    }

    private static String code(String text) {
        String fence = "`".repeat(longestBackticks(List.of(text)) + 1);
        String padding = text.startsWith("`") || text.endsWith("`") ? " " : "";
        return fence + padding + text + padding + fence;
    }

    private static int longestBackticks(List<String> lines) {
        int longest = 0;
        for (String line : lines) {
            Matcher matcher = BACKTICKS.matcher(line);
            while (matcher.find()) {
                longest = Math.max(longest, matcher.group().length());
            }
        }

        return longest;
    }

    private static Path write(Path file, String content) throws IOException {
        return Files.writeString(file, content);
    }

    private static final class Out {
        private final StringBuilder builder;
        private final String indent;

        private Out() {
            this(new StringBuilder(), "");
        }

        private Out(StringBuilder builder, String indent) {
            this.builder = builder;
            this.indent = indent;
        }

        private Out indented() {
            return new Out(builder, indent + INDENT);
        }

        private void line() {
            builder.append('\n');
        }

        private void line(String line) {
            builder.append(indent).append(line).append('\n');
        }

        private void block(String language, String title, List<String> lines) {
            String fence = "`".repeat(Math.max(3, longestBackticks(lines) + 1));
            line(fence + language + (title == null ? "" : " title=\"" + title + "\""));
            lines.forEach(line -> {
                if (line.isEmpty()) {
                    line();
                } else {
                    line(line);
                }
            });
            line(fence);
        }

        @Override
        public String toString() {
            return builder.toString();
        }
    }

}
