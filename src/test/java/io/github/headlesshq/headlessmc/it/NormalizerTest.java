package io.github.headlesshq.headlessmc.it;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizerTest {
    @TempDir
    Path dir;

    @Test
    void replacesDirectoriesAndSeparators() {
        Normalizer normalizer = Normalizer.builder().directory(dir, Normalizer.HMC_DIR).build();
        String windowsStyle = dir.toString().replace('/', '\\') + "\\hmc\\config.properties";

        assertThat(normalizer.normalize("Config: " + dir.resolve("hmc") + "\n"))
            .isEqualTo("Config: <HMC_DIR>/hmc");
        assertThat(normalizer.normalize(windowsStyle)).isEqualTo("<HMC_DIR>/hmc/config.properties");
        assertThat(normalizer.normalize(dir + "-other")).isEqualTo(dir + "-other");
    }

    @Test
    void normalizesLineEndingsColoursAndWhitespace() {
        Normalizer normalizer = Normalizer.builder().build();

        assertThat(normalizer.normalize("\u001B[90mname\u001B[0m   \r\n\u001B[1m-\u001B[0m\r\n\r\n"))
            .isEqualTo("name\n-");
    }

    @Test
    void replacesTimestampsAndVersions() {
        Normalizer normalizer = Normalizer.builder()
            .hmcVersion("3.0.0-RC8")
            .replacePattern("Java \\d+", "Java <JAVA>")
            .build();

        assertThat(normalizer.normalize("HeadlessMc 3.0.0-RC8 at 2026-11-09T13:08:24.123Z, 13:08:24, Java 25"))
            .isEqualTo("HeadlessMc <HMC_VERSION> at <TIMESTAMP>, <TIME>, Java <JAVA>");
    }

    @Test
    void timestampsCanBeDisabled() {
        assertThat(Normalizer.builder().timestamps(false).build().normalize("12:00:00")).isEqualTo("12:00:00");
    }

}
