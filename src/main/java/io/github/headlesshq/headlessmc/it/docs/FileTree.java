package io.github.headlesshq.headlessmc.it.docs;

import io.github.headlesshq.headlessmc.it.FileChange;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Renders the files changed by a command as a text tree, e.g.
 * <pre>
 * hmc/
 * ├── config.properties  (added)
 * └── logs/
 *     └── headlessmc.log  (added)
 * </pre>
 * Directories below the top level with more than {@link #COLLAPSE_THRESHOLD} changed files,
 * e.g. downloaded assets, are collapsed into a single line with the number of files per change.
 */
public final class FileTree {
    /**
     * Directories with more changed files than this are collapsed, unless they are top level.
     */
    public static final int COLLAPSE_THRESHOLD = 20;

    private final Node root = new Node();

    private FileTree(List<FileChange> changes) {
        for (FileChange change : changes) {
            Node node = root;
            for (String segment : change.path().split("/")) {
                node = node.children.computeIfAbsent(segment, key -> new Node());
            }

            node.change = change.change();
        }
    }

    /**
     * @param changes the changed files.
     * @return the lines of the tree, empty if nothing changed.
     */
    public static List<String> render(List<FileChange> changes) {
        List<String> lines = new ArrayList<>();
        FileTree tree = new FileTree(changes);
        tree.root.children.forEach((name, node) -> {
            if (node.isFile()) {
                lines.add(name + "  " + node.label());
            } else {
                lines.add(name + "/");
                renderChildren(node, "", 1, lines);
            }
        });

        return lines;
    }

    private static void renderChildren(Node dir, String prefix, int depth, List<String> lines) {
        int remaining = dir.children.size();
        for (Map.Entry<String, Node> entry : dir.children.entrySet()) {
            boolean last = --remaining == 0;
            String name = entry.getKey();
            Node node = entry.getValue();
            String line = prefix + (last ? "└── " : "├── ");
            if (node.isFile()) {
                lines.add(line + name + "  " + node.label());
            } else if (depth >= 1 && node.fileCount() > COLLAPSE_THRESHOLD) {
                lines.add(line + name + "/  " + node.summary());
            } else {
                lines.add(line + name + "/");
                renderChildren(node, prefix + (last ? "    " : "│   "), depth + 1, lines);
            }
        }
    }

    private static final class Node {
        private final Map<String, Node> children = new TreeMap<>();
        private FileChange.Kind change;

        private boolean isFile() {
            return children.isEmpty() && change != null;
        }

        private String label() {
            return "(" + kind(change) + ")";
        }

        private int fileCount() {
            return isFile() ? 1 : children.values().stream().mapToInt(Node::fileCount).sum();
        }

        private void count(Map<FileChange.Kind, Integer> counts) {
            if (isFile()) {
                counts.merge(change, 1, Integer::sum);
            }

            children.values().forEach(child -> child.count(counts));
        }

        private String summary() {
            Map<FileChange.Kind, Integer> counts = new EnumMap<>(FileChange.Kind.class);
            count(counts);
            return counts.entrySet().stream()
                .map(e -> e.getValue() + (e.getValue() == 1 ? " file " : " files ") + kind(e.getKey()))
                .collect(Collectors.joining(", ", "(", ")"));
        }

        private static String kind(FileChange.Kind kind) {
            return kind.name().toLowerCase(Locale.ROOT);
        }
    }

}
