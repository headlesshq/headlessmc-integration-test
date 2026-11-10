package io.github.headlesshq.headlessmc.it.docs;

import io.github.headlesshq.headlessmc.it.FileChange;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class FileTreeTest {
    @Test
    void rendersSortedTreeWithChanges() {
        List<String> lines = FileTree.render(List.of(
            change("hmc/logs/headlessmc.log", FileChange.Kind.MODIFIED),
            change("mc/versions/1.21.1/1.21.1.json", FileChange.Kind.ADDED),
            change("hmc/config.properties", FileChange.Kind.ADDED),
            change("hmc/old.txt", FileChange.Kind.DELETED),
            change("top.txt", FileChange.Kind.ADDED)
        ));

        assertThat(lines).containsExactly(
            "hmc/",
            "├── config.properties  (added)",
            "├── logs/",
            "│   └── headlessmc.log  (modified)",
            "└── old.txt  (deleted)",
            "mc/",
            "└── versions/",
            "    └── 1.21.1/",
            "        └── 1.21.1.json  (added)",
            "top.txt  (added)"
        );
    }

    @Test
    void collapsesLargeDirectoriesBelowTopLevel() {
        List<FileChange> changes = new ArrayList<>();
        IntStream.range(0, FileTree.COLLAPSE_THRESHOLD).forEach(i ->
            changes.add(change("mc/assets/objects/" + i + "/file", FileChange.Kind.ADDED)));
        changes.add(change("mc/assets/objects/x/file", FileChange.Kind.MODIFIED));
        changes.add(change("mc/assets/indexes/1.json", FileChange.Kind.ADDED));

        assertThat(FileTree.render(changes)).containsExactly(
            "mc/",
            "└── assets/  (21 files added, 1 file modified)"
        );
    }

    @Test
    void rendersNothingWithoutChanges() {
        assertThat(FileTree.render(List.of())).isEmpty();
    }

    private static FileChange change(String path, FileChange.Kind kind) {
        return new FileChange(path, kind, kind == FileChange.Kind.DELETED ? null : 1L, null, null, null);
    }

}
