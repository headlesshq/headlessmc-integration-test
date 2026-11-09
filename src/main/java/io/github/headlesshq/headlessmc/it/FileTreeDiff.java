package io.github.headlesshq.headlessmc.it;

import java.util.List;

/**
 * The file changes caused by one command, sorted by path.
 *
 * @param changes the changes.
 */
public record FileTreeDiff(List<FileChange> changes) {
    public FileTreeDiff {
        changes = List.copyOf(changes);
    }

    /**
     * @return {@code true} if no file was changed.
     */
    public boolean isEmpty() {
        return changes.isEmpty();
    }

    /**
     * @return the paths of all changed files.
     */
    public List<String> paths() {
        return changes.stream().map(FileChange::path).toList();
    }

    /**
     * @param kind the kind of change.
     * @return the paths of all files changed in the given way.
     */
    public List<String> paths(FileChange.Kind kind) {
        return changes.stream().filter(c -> c.change() == kind).map(FileChange::path).toList();
    }

}
