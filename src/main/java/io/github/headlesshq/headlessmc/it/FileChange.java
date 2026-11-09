package io.github.headlesshq.headlessmc.it;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single file that was added, modified or deleted by a command.
 *
 * @param path           path relative to {@code <HMC_DIR>}, with {@code /} separators.
 * @param change         what happened to the file.
 * @param size           size after the command, {@code null} if deleted.
 * @param sha256         sha256 after the command, {@code null} if deleted.
 * @param previousSize   size before the command, {@code null} if added.
 * @param previousSha256 sha256 before the command, {@code null} if added.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FileChange(
    String path,
    Kind change,
    Long size,
    String sha256,
    Long previousSize,
    String previousSha256
) {
    /**
     * The type of change.
     */
    public enum Kind {
        @JsonProperty("added")
        ADDED,
        @JsonProperty("modified")
        MODIFIED,
        @JsonProperty("deleted")
        DELETED
    }

}
