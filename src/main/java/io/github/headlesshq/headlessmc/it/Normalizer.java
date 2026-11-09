package io.github.headlesshq.headlessmc.it;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Makes command output comparable across machines, platforms and runs, for assertions and docs.
 * Rules are applied in this order:
 * <ol>
 *     <li>{@code \r\n} and {@code \r} line endings become {@code \n}.</li>
 *     <li>ANSI escape sequences (colour codes) are removed.</li>
 *     <li>Registered directories are replaced by their token, e.g. {@code <HMC_DIR>},
 *     and path separators following a token become {@code /}.</li>
 *     <li>ISO-8601 timestamps become {@code <TIMESTAMP>}, times of day {@code <TIME>}.</li>
 *     <li>Registered versions and other literals or patterns are replaced.</li>
 *     <li>Trailing whitespace on each line and trailing empty lines are removed.</li>
 * </ol>
 */
public final class Normalizer {
    /**
     * Token for the per-test temp directory.
     */
    public static final String HMC_DIR = "<HMC_DIR>";
    /**
     * Token for the version of the executable under test.
     */
    public static final String HMC_VERSION = "<HMC_VERSION>";

    private static final Pattern ANSI = Pattern.compile("\u001B\\[[0-?]*[ -/]*[@-~]|\u001B[@-Z\\\\-_]");
    private static final Pattern TIMESTAMP = Pattern.compile(
        "\\b\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}(?::\\d{2}(?:[.,]\\d+)?)?(?:Z|[+-]\\d{2}:?\\d{2})?(?:\\[[\\w/+-]+])?"
    );
    private static final Pattern TIME = Pattern.compile("\\b\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d+)?\\b");
    private static final Pattern TRAILING_WHITESPACE = Pattern.compile("[ \\t]+$", Pattern.MULTILINE);

    private final List<UnaryOperator<String>> rules;

    private Normalizer(List<UnaryOperator<String>> rules) {
        this.rules = List.copyOf(rules);
    }

    /**
     * @return a builder for a normalizer.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Normalizes the given text.
     *
     * @param text the raw text.
     * @return the normalized text.
     */
    public String normalize(String text) {
        String result = text;
        for (UnaryOperator<String> rule : rules) {
            result = rule.apply(result);
        }

        return result;
    }

    /**
     * Builds a {@link Normalizer}.
     */
    public static final class Builder {
        private final Map<String, String> directories = new LinkedHashMap<>();
        private final List<UnaryOperator<String>> replacements = new ArrayList<>();
        private boolean timestamps = true;

        private Builder() {
        }

        /**
         * Replaces a directory, in all its spellings, with a token.
         *
         * @param directory the directory.
         * @param token     the replacement, e.g. {@link #HMC_DIR}.
         * @return this builder.
         */
        public Builder directory(Path directory, String token) {
            for (String spelling : spellings(directory)) {
                directories.putIfAbsent(spelling, token);
            }

            return this;
        }

        /**
         * Replaces the given version with {@link #HMC_VERSION}.
         *
         * @param version the version of the executable, ignored if {@code null} or blank.
         * @return this builder.
         */
        public Builder hmcVersion(String version) {
            if (version != null && !version.isBlank()) {
                replace(version, HMC_VERSION);
            }

            return this;
        }

        /**
         * Replaces a literal string.
         *
         * @param literal     the text to replace.
         * @param replacement the replacement.
         * @return this builder.
         */
        public Builder replace(String literal, String replacement) {
            replacements.add(text -> text.replace(literal, replacement));
            return this;
        }

        /**
         * Replaces all matches of a regex.
         *
         * @param regex       the pattern to replace.
         * @param replacement the replacement, may reference groups.
         * @return this builder.
         */
        public Builder replacePattern(String regex, String replacement) {
            Pattern pattern = Pattern.compile(regex);
            replacements.add(text -> pattern.matcher(text).replaceAll(replacement));
            return this;
        }

        /**
         * @param timestamps whether to replace timestamps and times of day, enabled by default.
         * @return this builder.
         */
        public Builder timestamps(boolean timestamps) {
            this.timestamps = timestamps;
            return this;
        }

        /**
         * @return the normalizer.
         */
        public Normalizer build() {
            List<UnaryOperator<String>> rules = new ArrayList<>();
            rules.add(text -> text.replace("\r\n", "\n").replace('\r', '\n'));
            rules.add(text -> ANSI.matcher(text).replaceAll(""));
            directoryRules(rules);
            if (timestamps) {
                rules.add(text -> TIMESTAMP.matcher(text).replaceAll("<TIMESTAMP>"));
                rules.add(text -> TIME.matcher(text).replaceAll("<TIME>"));
            }

            rules.addAll(replacements);
            rules.add(text -> TRAILING_WHITESPACE.matcher(text).replaceAll(""));
            rules.add(Builder::stripTrailingNewlines);
            return new Normalizer(rules);
        }

        private void directoryRules(List<UnaryOperator<String>> rules) {
            boolean ignoreCase = Platform.current().isWindows();
            List<Map.Entry<String, String>> sorted = new ArrayList<>(directories.entrySet());
            sorted.sort(Comparator.comparingInt((Map.Entry<String, String> e) -> e.getKey().length()).reversed());
            Set<String> tokens = new LinkedHashSet<>();
            for (Map.Entry<String, String> entry : sorted) {
                Pattern pattern = Pattern.compile(
                    Pattern.quote(entry.getKey()) + "(?![\\w.-])",
                    ignoreCase ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0
                );
                String replacement = Matcher.quoteReplacement(entry.getValue());
                rules.add(text -> pattern.matcher(text).replaceAll(replacement));
                tokens.add(entry.getValue());
            }

            for (String token : tokens) {
                Pattern path = Pattern.compile(Pattern.quote(token) + "[^\\s\"'<>|,;]*");
                rules.add(text -> path.matcher(text).replaceAll(
                    match -> Matcher.quoteReplacement(match.group().replaceAll("\\\\+", "/"))
                ));
            }
        }

        private static Set<String> spellings(Path directory) {
            Set<String> result = new LinkedHashSet<>();
            Path absolute = directory.toAbsolutePath().normalize();
            addSpellings(result, absolute.toString());
            try {
                addSpellings(result, absolute.toRealPath().toString());
            } catch (IOException ignored) {
                return result;
            }

            return result;
        }

        private static void addSpellings(Set<String> result, String path) {
            String trimmed = path.length() > 1 && (path.endsWith("/") || path.endsWith("\\"))
                ? path.substring(0, path.length() - 1)
                : path;
            result.add(trimmed);
            result.add(trimmed.replace('\\', '/'));
            result.add(trimmed.replace('/', '\\'));
            result.add(trimmed.replace("\\", "\\\\"));
        }

        private static String stripTrailingNewlines(String text) {
            int end = text.length();
            while (end > 0 && text.charAt(end - 1) == '\n') {
                end--;
            }

            return text.substring(0, end);
        }
    }

}
