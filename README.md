# headlessmc-integration-test

Black-box integration tests for [headlessmc](https://github.com/headlesshq/headlessmc).
The tests run a headlessmc jar or native binary as a subprocess, record its output and the files it changes,
and write a JSON recording per test that the docs generator uses.

## Running locally

You need Java 25. Point `HMC_EXECUTABLE` (or the `hmc.executable` system property) at the executable under test.

Against a jar, which is run with `java -jar`:

```shell
gh release download --repo headlesshq/headlessmc --pattern headlessmc.jar --dir hmc
HMC_EXECUTABLE=hmc/headlessmc.jar ./gradlew test
```

The jar is run with the Java the tests run on. Use `HMC_JAVA` or `-Dhmc.java=/path/to/java` to pick another one.

Against a native binary, which is run directly:

```shell
gh release download --repo headlesshq/headlessmc --pattern headlessmc-launcher-linux-x64 --dir hmc
chmod +x hmc/headlessmc-launcher-linux-x64
./gradlew test -Dhmc.executable=hmc/headlessmc-launcher-linux-x64
```

On Windows use `headlessmc-launcher-windows-x64.exe`, the `.exe` suffix may be omitted.
Relative paths are resolved against the project directory.

Other options, all passed as `-D` to Gradle:

| Property             | Default | Description                                                             |
|----------------------|---------|-------------------------------------------------------------------------|
| `hmc.timeout`        | `120`   | Default timeout per command in seconds.                                 |
| `hmc.keep.dirs`      | `false` | Keep each test's temp directory instead of deleting it after the test. |
| `update.golden`      | `false` | Rewrite the golden files in `src/test/resources/golden/` from the actual output. |

## Writing tests

```java
@HmcTest
class ConfigTest {
    @Test
    void listsConfig(HmcRunner hmc) {
        CommandResult result = hmc.run("config", "ls");

        assertThat(result).succeeded().hasOutputContaining("hmc.jline.enabled");
        assertThat(result.files()).containsOnlyPathsMatching("hmc/config.properties", "hmc/logs/**");
        assertThatFiles(hmc).hasFile("hmc/config.properties");
    }
}
```

`assertThat` and `assertThatFiles` come from `HmcAssertions`.

Each test gets a fresh temp directory, `<HMC_DIR>`, which is the working directory of every command:

| Directory | Used as                                                                    |
|-----------|----------------------------------------------------------------------------|
| `home`    | user home, `HOME`, `USERPROFILE`, `APPDATA`, `LOCALAPPDATA`, `XDG_*_HOME` |
| `hmc`     | headlessmc files (`hmc.files.location`), e.g. `hmc/config.properties`     |
| `mc`      | Minecraft directory (`hmc.files.mc`)                                       |
| `game`    | game directory (`hmc.files.game`)                                          |

so tests never share state or touch your real `~/.minecraft` or headlessmc config.
Commands run non-interactively with `-Dhmc.jline.enabled=false` and stdin closed.
`HMC_*`, `XDG_*` and `JAVA_TOOL_OPTIONS`-style environment variables are not passed on.
Use `hmc.property(key, value)` or `hmc.env(key, value)` to configure further.

`hmc.run(...)` runs one command, `hmc.runAll(...)` a sequence. Each command is killed with its child processes
when it exceeds its timeout, and the directory is snapshotted before and after it to compute the added,
modified and deleted files with their size and sha256.

Output assertions compare normalized output: the temp dir becomes `<HMC_DIR>`, the real user home `<USER_HOME>`,
path separators after these become `/`, line endings `\n`, timestamps `<TIMESTAMP>`/`<TIME>`,
the headlessmc version `<HMC_VERSION>`, and colour codes and trailing whitespace are removed.

## Recordings

After each test, its commands are written to `build/recordings/<platform>/<Class>.<method>.json`,
e.g. `build/recordings/linux-x64/HelpTest.help.json`:

```json
{
  "schemaVersion" : 1,
  "test" : "HelpTest.help",
  "platform" : "linux-x64",
  "os" : "linux",
  "arch" : "x64",
  "hmcVersion" : "3.0.0-RC8",
  "executable" : { "type" : "jar", "name" : "headlessmc.jar" },
  "steps" : [ {
    "command" : [ "help" ],
    "commandLine" : "hmc help",
    "exitCode" : 0,
    "timedOut" : false,
    "durationMs" : 842,
    "stdout" : "Usage: headlessmc [-hV] [COMMAND]\n...",
    "stderr" : "",
    "files" : [ {
      "path" : "hmc/config.properties",
      "change" : "added",
      "size" : 52,
      "sha256" : "0007884d..."
    } ]
  } ]
}
```

Output in recordings is normalized. Modified files also carry `previousSize` and `previousSha256`,
deleted files only those. The format is modelled by `Recording`, which can read recordings back.
