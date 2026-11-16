package io.github.headlesshq.headlessmc.it.commands;

import io.github.headlesshq.headlessmc.it.CommandResult;
import io.github.headlesshq.headlessmc.it.HmcExecutable;
import io.github.headlesshq.headlessmc.it.HmcRunner;
import io.github.headlesshq.headlessmc.it.HmcTable;
import io.github.headlesshq.headlessmc.it.HmcTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static io.github.headlesshq.headlessmc.it.HmcAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * {@code java ls}. Which Java installations are found depends on the machine,
 * so these tests check the structure of the output instead of exact values.
 */
@HmcTest
class JavaTest {
    private static final List<String> COLUMNS = List.of("name", "version", "home", "current");
    private static final String CURRENT = "<------";
    private static final int MIN_JAVA = 25;

    @Test
    void listsInstallations(HmcRunner hmc) {
        CommandResult result = hmc.run("java", "ls");

        assertThat(result).succeeded().hasEmptyStderr();
        HmcTable table = HmcTable.parse(result);
        assertThat(table.columns()).isEqualTo(COLUMNS);
        assertThat(table.rows()).allSatisfy(row -> {
            assertThat(row.get("name")).isNotBlank();
            assertThat(row.get("version")).matches("\\d+");
            assertThat(row.get("home")).isNotBlank();
            assertThat(row.get("current")).isIn("", CURRENT);
        });
        assertThat(table.column("current").stream().filter(CURRENT::equals)).hasSizeLessThanOrEqualTo(1);
        assertThat(result.files()).containsOnlyPathsMatching("hmc/config.properties", "hmc/logs/**");
    }

    @Test
    void marksJavaRunningTheJarAsCurrent(HmcRunner hmc) {
        assumeTrue(hmc.executable().type() == HmcExecutable.Type.JAR,
            "only a jar runs on a Java installation that can be listed");

        HmcTable table = HmcTable.parse(hmc.run("java", "ls"));

        assertThat(table.row("current", CURRENT)).get().satisfies(row -> {
            assertThat(Integer.parseInt(row.get("version"))).isGreaterThanOrEqualTo(MIN_JAVA);
            assertThat(row.get("home")).isNotBlank();
        });
    }

    @Test
    void listsInstallationFromConfig(HmcRunner hmc) throws IOException {
        Path home = Path.of(System.getProperty("java.home"));
        hmc.property("hmc.java.versions", listElement(home.toString()));

        HmcTable table = HmcTable.parse(hmc.run("java", "ls"));

        List<String> spellings = List.of(normalizedHome(hmc, home), normalizedHome(hmc, home.toRealPath()));
        assertThat(table.rows())
            .filteredOn(row -> spellings.contains(row.get("home")))
            .isNotEmpty()
            .allSatisfy(row -> assertThat(row).containsEntry("version", String.valueOf(Runtime.version().feature())));
    }

    @Test
    void listsProviders(HmcRunner hmc) {
        CommandResult result = hmc.run("java", "ls", "--providers");

        assertThat(result).succeeded();
        HmcTable table = HmcTable.parse(result);
        assertThat(table.columns()).containsExactly("name");
        assertThat(table.column("name")).contains("foojay.io");
    }

    @Test
    @Tag("network")
    void listsRemoteDistributions(HmcRunner hmc) {
        CommandResult result = hmc.run("java", "ls", "--remote");

        assertThat(result).succeeded();
        HmcTable table = HmcTable.parse(result);
        assertThat(table.columns()).containsExactly("name", "id", "provider");
        assertThat(table.row("id", "temurin")).get()
            .satisfies(row -> assertThat(row).containsEntry("name", "Temurin").containsEntry("provider", "foojay.io"));
        assertThat(table.column("id")).allMatch(id -> id.matches("[a-z0-9_]+"), "is a distribution id");
        assertThat(result.files()).containsOnlyPathsMatching("hmc/**");
    }

    /**
     * Escapes a value for a list config property: headlessmc reads lists with SmallRye Config,
     * which splits on {@code ,} and drops an unescaped {@code \}, e.g. in a Windows path.
     */
    private static String listElement(String value) {
        return value.replace("\\", "\\\\").replace(",", "\\,");
    }

    private static String normalizedHome(HmcRunner hmc, Path home) {
        return hmc.normalizer().normalize(home.toAbsolutePath().normalize().toString());
    }

}
