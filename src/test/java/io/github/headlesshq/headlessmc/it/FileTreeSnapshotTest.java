package io.github.headlesshq.headlessmc.it;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.headlesshq.headlessmc.it.HmcAssertions.assertThatFiles;
import static org.assertj.core.api.Assertions.assertThat;

class FileTreeSnapshotTest {
    private static final String SHA256_OF_A = "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb";

    @TempDir
    Path dir;

    @Test
    void diffDetectsAddedModifiedAndDeletedFiles() throws IOException {
        Files.createDirectories(dir.resolve("sub"));
        Files.writeString(dir.resolve("modified.txt"), "old");
        Files.writeString(dir.resolve("deleted.txt"), "gone");
        Files.writeString(dir.resolve("same.txt"), "same");
        FileTreeSnapshot before = FileTreeSnapshot.take(dir);

        Files.writeString(dir.resolve("modified.txt"), "new!");
        Files.delete(dir.resolve("deleted.txt"));
        Files.writeString(dir.resolve("sub/added.txt"), "a");
        FileTreeDiff diff = before.diffTo(FileTreeSnapshot.take(dir));

        assertThat(diff.changes()).containsExactly(
            new FileChange("deleted.txt", FileChange.Kind.DELETED, null, null, 4L,
                before.files().get("deleted.txt").sha256()),
            new FileChange("modified.txt", FileChange.Kind.MODIFIED, 4L,
                FileTreeSnapshot.take(dir).files().get("modified.txt").sha256(), 3L,
                before.files().get("modified.txt").sha256()),
            new FileChange("sub/added.txt", FileChange.Kind.ADDED, 1L, SHA256_OF_A, null, null)
        );
        HmcAssertions.assertThat(diff)
            .hasAdded("sub/added.txt")
            .hasModified("modified.txt")
            .hasDeleted("deleted.txt")
            .doesNotHaveChange("same.txt")
            .containsOnlyPathsMatching("*.txt", "sub/**");
        assertThatFiles(dir)
            .hasFile("sub/added.txt")
            .doesNotHaveFile("deleted.txt")
            .hasFileWithContent("modified.txt", "new!\n")
            .hasFileContaining("same.txt", "am")
            .hasFileWithSha256("sub/added.txt", SHA256_OF_A.toUpperCase())
            .hasFileCount("**/*.txt", 3)
            .hasFileCount("*.txt", 2);
    }

    @Test
    void emptyWhenNothingChanged() throws IOException {
        Files.writeString(dir.resolve("file"), "x");

        HmcAssertions.assertThat(FileTreeSnapshot.take(dir).diffTo(FileTreeSnapshot.take(dir))).isEmpty();
    }

}
