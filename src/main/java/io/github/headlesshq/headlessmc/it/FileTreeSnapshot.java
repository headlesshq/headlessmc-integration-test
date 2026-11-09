package io.github.headlesshq.headlessmc.it;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The regular files below a directory with their size and sha256.
 * Paths are relative to the root and always use {@code /} as separator.
 */
public final class FileTreeSnapshot {
    private final Path root;
    private final SortedMap<String, Entry> files;

    private FileTreeSnapshot(Path root, SortedMap<String, Entry> files) {
        this.root = root;
        this.files = Collections.unmodifiableSortedMap(files);
    }

    /**
     * A file in a snapshot.
     *
     * @param size   the size in bytes.
     * @param sha256 the lowercase hex sha256 of the content.
     */
    public record Entry(long size, String sha256) {
    }

    /**
     * Snapshots all regular files below the given root. Symbolic links are not followed.
     *
     * @param root the directory to snapshot.
     * @return the snapshot, empty if the root does not exist.
     */
    public static FileTreeSnapshot take(Path root) {
        SortedMap<String, Entry> files = new TreeMap<>();
        if (!Files.isDirectory(root)) {
            return new FileTreeSnapshot(root, files);
        }

        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (attrs.isRegularFile()) {
                        try {
                            files.put(relativize(root, file), new Entry(attrs.size(), sha256(file)));
                        } catch (NoSuchFileException ignored) {
                            return FileVisitResult.CONTINUE;
                        }
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    if (exc instanceof NoSuchFileException) {
                        return FileVisitResult.CONTINUE;
                    }

                    throw exc;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to snapshot " + root, e);
        }

        return new FileTreeSnapshot(root, files);
    }

    /**
     * Computes the changes from {@code before} to {@code after}.
     *
     * @param before the earlier snapshot.
     * @param after  the later snapshot.
     * @return the added, modified and deleted files, sorted by path.
     */
    public static FileTreeDiff diff(FileTreeSnapshot before, FileTreeSnapshot after) {
        TreeSet<String> paths = new TreeSet<>(before.files.keySet());
        paths.addAll(after.files.keySet());
        List<FileChange> changes = new ArrayList<>();
        for (String path : paths) {
            Entry old = before.files.get(path);
            Entry now = after.files.get(path);
            if (old == null) {
                changes.add(new FileChange(path, FileChange.Kind.ADDED, now.size(), now.sha256(), null, null));
            } else if (now == null) {
                changes.add(new FileChange(path, FileChange.Kind.DELETED, null, null, old.size(), old.sha256()));
            } else if (!old.equals(now)) {
                changes.add(new FileChange(
                    path, FileChange.Kind.MODIFIED, now.size(), now.sha256(), old.size(), old.sha256()
                ));
            }
        }

        return new FileTreeDiff(changes);
    }

    /**
     * @param after the later snapshot.
     * @return the changes from this snapshot to {@code after}.
     */
    public FileTreeDiff diffTo(FileTreeSnapshot after) {
        return diff(this, after);
    }

    /**
     * @return the snapshotted root directory.
     */
    public Path root() {
        return root;
    }

    /**
     * @return the files by their relative path.
     */
    public Map<String, Entry> files() {
        return files;
    }

    /**
     * Computes the sha256 of a file.
     *
     * @param file the file to hash.
     * @return the lowercase hex sha256.
     * @throws IOException if the file cannot be read.
     */
    public static String sha256(Path file) throws IOException {
        MessageDigest digest = newSha256();
        try (InputStream is = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }

        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Converts a path below root to a relative path with {@code /} separators.
     *
     * @param root the root directory.
     * @param file a path below root.
     * @return the relative path.
     */
    public static String relativize(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

}
