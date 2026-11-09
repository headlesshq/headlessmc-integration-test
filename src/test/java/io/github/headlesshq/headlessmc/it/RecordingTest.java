package io.github.headlesshq.headlessmc.it;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecordingTest {
    @TempDir
    Path dir;

    @Test
    void writesNormalizedJsonThatCanBeReadBack() throws IOException {
        Path jar = Files.writeString(dir.resolve("headlessmc.jar"), "");
        Normalizer normalizer = Normalizer.builder().directory(dir, Normalizer.HMC_DIR).hmcVersion("1.2.3").build();
        CommandResult result = new CommandResult(
            List.of("config", "set", "a b"),
            0,
            false,
            Duration.ofMillis(42),
            "HeadlessMc 1.2.3\r\nConfig: " + dir.resolve("hmc") + "\r\n",
            "",
            new FileTreeDiff(List.of(new FileChange("hmc/config.properties", FileChange.Kind.ADDED, 3L, "abc", null, null))),
            normalizer
        );

        Recording recording = Recording.of(
            "RecordingTest.example", new Platform("linux", "x64"), "1.2.3", HmcExecutable.of(jar), List.of(result)
        );
        Path file = recording.write(dir.resolve("recordings"));

        assertThat(file).isEqualTo(dir.resolve("recordings/linux-x64/RecordingTest.example.json"));
        String json = Files.readString(file);
        assertThat(json)
            .contains("\"commandLine\" : \"hmc config set \\\"a b\\\"\"")
            .contains("\"stdout\" : \"HeadlessMc <HMC_VERSION>\\nConfig: <HMC_DIR>/hmc\"")
            .contains("\"change\" : \"added\"")
            .contains("\"type\" : \"jar\"")
            .doesNotContain("previousSha256");
        assertThat(Recording.read(file)).isEqualTo(recording);
    }

}
