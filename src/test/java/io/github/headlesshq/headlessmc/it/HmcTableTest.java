package io.github.headlesshq.headlessmc.it;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HmcTableTest {
    @Test
    void parsesCellsByHeaderPositionBeforeNormalizing() {
        Path dir = Path.of("/tmp/some/very/long/directory/name").toAbsolutePath();
        Normalizer normalizer = Normalizer.builder().directory(dir, Normalizer.HMC_DIR).build();
        String home = dir.resolve("Program Files").toString();
        String raw = String.format("%-8s%-" + (home.length() + 3) + "s%s%n%-8s%-" + (home.length() + 3) + "s%s%n",
            "name", "home", "current version", "jdk", home, "<------");

        HmcTable table = HmcTable.parse(raw, normalizer);

        assertThat(table.columns()).containsExactly("name", "home", "current version");
        assertThat(table.rows()).containsExactly(
            Map.of("name", "jdk", "home", "<HMC_DIR>/Program Files", "current version", "<------")
        );
    }

    @Test
    void placeholderRowMeansEmpty() {
        HmcTable table = HmcTable.parse("name   parent   type\r\n-      -        -\r\n", Normalizer.builder().build());

        assertThat(table.columns()).isEqualTo(List.of("name", "parent", "type"));
        assertThat(table.rows()).isEmpty();
    }

    @Test
    void rejectsEmptyOutput() {
        assertThatThrownBy(() -> HmcTable.parse("\n", Normalizer.builder().build()))
            .isInstanceOf(IllegalArgumentException.class);
    }

}
