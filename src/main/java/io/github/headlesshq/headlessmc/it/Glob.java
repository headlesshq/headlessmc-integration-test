package io.github.headlesshq.headlessmc.it;

import java.util.regex.Pattern;

/**
 * Platform independent glob matching for relative paths with {@code /} separators.
 * {@code **} matches across directories, {@code *} and {@code ?} within one path segment
 * and {@code {a,b}} matches alternatives. A leading {@code **}{@code /} also matches no directory.
 */
public final class Glob {
    private final String glob;
    private final Pattern pattern;

    private Glob(String glob, Pattern pattern) {
        this.glob = glob;
        this.pattern = pattern;
    }

    /**
     * Compiles a glob.
     *
     * @param glob the glob, e.g. {@code hmc/logs/**}.
     * @return the compiled glob.
     */
    public static Glob of(String glob) {
        StringBuilder regex = new StringBuilder();
        int braces = 0;
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> {
                    if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                        boolean directory = i + 2 < glob.length() && glob.charAt(i + 2) == '/';
                        regex.append(directory ? "(?:.*/)?" : ".*");
                        i += directory ? 2 : 1;
                    } else {
                        regex.append("[^/]*");
                    }
                }
                case '?' -> regex.append("[^/]");
                case '{' -> {
                    braces++;
                    regex.append("(?:");
                }
                case '}' -> {
                    if (braces > 0) {
                        braces--;
                        regex.append(')');
                    } else {
                        regex.append("\\}");
                    }
                }
                case ',' -> regex.append(braces > 0 ? "|" : ",");
                default -> regex.append(Pattern.quote(String.valueOf(c)));
            }
        }

        return new Glob(glob, Pattern.compile(regex.toString()));
    }

    /**
     * @param path a relative path with {@code /} separators.
     * @return {@code true} if the whole path matches.
     */
    public boolean matches(String path) {
        return pattern.matcher(path).matches();
    }

    @Override
    public String toString() {
        return glob;
    }

}
