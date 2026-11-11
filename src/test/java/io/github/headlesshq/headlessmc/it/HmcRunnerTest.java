package io.github.headlesshq.headlessmc.it;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisabledOnOs(OS.WINDOWS)
class HmcRunnerTest {
    @TempDir
    Path dir;

    @Test
    void failingVersionDoesNotNormalizeUnknown() throws IOException {
        HmcExecutable executable = script("fake-hmc-no-version", """
            #!/bin/sh
            for arg in "$@"; do
              if [ "$arg" = "--version" ]; then exit 1; fi
            done
            echo "unknown command"
            """);

        assertThat(HmcRunner.versionOf(executable)).isEmpty();
        HmcRunner runner = new HmcRunner(executable, dir.resolve("run"));
        assertThat(runner.version()).isEqualTo(HmcRunner.UNKNOWN_VERSION);

        CommandResult result = runner.run("foo");
        assertThat(result.exitCode()).isZero();
        assertThat(runner.normalizer().normalize(result.stdout())).isEqualTo("unknown command");
    }

    @Test
    void killsDescendantsThatOutliveTheCommand() throws IOException {
        HmcExecutable executable = script("fake-hmc-orphan", """
            #!/bin/sh
            for arg in "$@"; do
              if [ "$arg" = "--version" ]; then echo 1.0.0; exit 0; fi
            done
            sleep 60 &
            echo $! > child.pid
            sleep 0.5
            echo done
            """);
        HmcRunner runner = new HmcRunner(executable, dir.resolve("run"));

        CommandResult result = runner.run("launch");

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).isEqualTo("done\n");
        assertThat(result.duration()).isLessThan(Duration.ofSeconds(5));
        long pid = Long.parseLong(Files.readString(runner.dir().resolve("child.pid")).strip());
        assertThat(ProcessHandle.of(pid).filter(ProcessHandle::isAlive)).isEqualTo(Optional.empty());
    }

    @Test
    void hasOptionChecksTheHelpOfTheCommand() throws IOException {
        HmcExecutable executable = script("fake-hmc-help", """
            #!/bin/sh
            case " $* " in
              *" --version "*) echo 1.0.0 ;;
              *" server launch --help "*) echo "Usage: headlessmc server launch [-hV] [--offline]" ;;
              *" launch --help "*)
                echo "Usage: headlessmc launch [-hV] [--dry-run] [--offline] [profile...]"
                echo "      --no-backup   Does not back up the profile." ;;
              *) exit 2 ;;
            esac
            """);

        assertThat(HmcRunner.hasOption(executable, List.of("launch"), "--dry-run")).isTrue();
        assertThat(HmcRunner.hasOption(executable, List.of("launch"), "--no-backup")).isTrue();
        assertThat(HmcRunner.hasOption(executable, List.of("launch"), "--dry")).isFalse();
        assertThat(HmcRunner.hasOption(executable, List.of("server", "launch"), "--dry-run")).isFalse();
    }

    private HmcExecutable script(String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-xr-x"));
        return HmcExecutable.of(file);
    }

}
