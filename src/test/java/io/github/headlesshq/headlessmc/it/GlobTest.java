package io.github.headlesshq.headlessmc.it;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GlobTest {
    @Test
    void matchesSegmentsAndDirectories() {
        assertThat(Glob.of("*.txt").matches("a.txt")).isTrue();
        assertThat(Glob.of("*.txt").matches("dir/a.txt")).isFalse();
        assertThat(Glob.of("**/*.txt").matches("a.txt")).isTrue();
        assertThat(Glob.of("**/*.txt").matches("dir/sub/a.txt")).isTrue();
        assertThat(Glob.of("hmc/logs/**").matches("hmc/logs/headlessmc.log")).isTrue();
        assertThat(Glob.of("hmc/logs/**").matches("hmc/config.properties")).isFalse();
        assertThat(Glob.of("{home,mc}/**").matches("mc/versions/1.21/1.21.json")).isTrue();
        assertThat(Glob.of("{home,mc}/**").matches("game/options.txt")).isFalse();
        assertThat(Glob.of("file?.json").matches("file1.json")).isTrue();
        assertThat(Glob.of("a.b").matches("axb")).isFalse();
    }

}
