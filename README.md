# headlessmc-integration-test

Black-box integration tests for [headlessmc](https://github.com/headlesshq/headlessmc).
The tests run a headlessmc jar or native binary as a subprocess, record its output and the files it changes,
and write a JSON recording per test that the docs generator uses.

## Running locally

You need Java 25. Point `HMC_EXECUTABLE` (or the `hmc.executable` system property) at the executable under test.

Against a jar, which is run with `java -jar`:

```shell
gh release download 3.0.0-RC8 --repo headlesshq/headlessmc --pattern headlessmc.jar --dir hmc
HMC_EXECUTABLE=hmc/headlessmc.jar ./gradlew test
```

The jar is run with the Java the tests run on. Use `HMC_JAVA` or `-Dhmc.java=/path/to/java` to pick another one.

Against a native binary, which is run directly:

```shell
gh release download 3.0.0-RC8 --repo headlesshq/headlessmc --pattern headlessmc-launcher-linux-x64 --dir hmc
chmod +x hmc/headlessmc-launcher-linux-x64
./gradlew test -Dhmc.executable=hmc/headlessmc-launcher-linux-x64
```

On Windows use `headlessmc-launcher-windows-x64.exe`, the `.exe` suffix may be omitted.
Relative paths are resolved against the project directory.

`./gradlew test` runs all tests. Tests that need the internet (Mojang, foojay.io, ...) are tagged `network`;
`./gradlew offlineTest` runs everything except them.

Other options, all passed as `-D` to Gradle:

| Property             | Default | Description                                                             |
|----------------------|---------|-------------------------------------------------------------------------|
| `hmc.timeout`        | `120`   | Default timeout per command in seconds.                                 |
| `hmc.keep.dirs`      | `false` | Keep each test's temp directory instead of deleting it after the test. |
| `update.golden`      | `false` | Rewrite the golden files in `src/test/resources/golden/` from the actual output. |

## headlessmc version under test

The tests, and in particular the golden files, are written against a pinned headlessmc release,
currently **3.0.0-RC8**, set as `HMC_VERSION` in `.github/workflows/test.yml`. To move to a new release,
bump `HMC_VERSION`, download that release, run `./gradlew test -Dupdate.golden=true` and review the golden diff.

## CI

`.github/workflows/test.yml` runs the suite against `headlessmc.jar` (JDK 25, Temurin, or the Microsoft Build of OpenJDK on `windows-11-arm`, for which Temurin publishes no JDK 25) on
`ubuntu-latest`, `ubuntu-24.04-arm`, `windows-latest`, `windows-11-arm`, `macos-latest` and `macos-15-intel`,
and against the native images on the same runners, except `windows-11-arm`, for which no native image exists.
A first job looks up which executables exist and drops the matrix entries whose executable is missing,
e.g. the natives other than linux-x64 for a headlessmc run that is not a release.

Pushes and pull requests test `HMC_VERSION`. The weekly schedule tests the latest release to catch upstream changes early.
`workflow_dispatch` and `workflow_call` take these inputs:

| Input                    | Default                     | Description                                                                 |
|--------------------------|-----------------------------|-----------------------------------------------------------------------------|
| `tag`                    | latest release              | headlessmc release to test.                                                 |
| `run-id`                 |                             | headlessmc workflow run to take the executables from instead of a release. |
| `repository`             | `headlesshq/headlessmc`     | Repository of the release or run.                                           |
| `jar-artifact`           | `HeadlessMc Launcher files` | Run artifact containing `headlessmc.jar`.                                   |
| `native-artifact-prefix` | `HeadlessMc Native Image `  | Run artifacts of the natives are named this prefix plus the image name.     |
| `ref`                    | `main` (`workflow_call`)    | Ref of this repository to run the tests from.                               |

headlessmc can test unreleased builds with a job like this. The job must grant `contents: read` and `actions: read`:
a called workflow cannot get more permissions than its caller, and GitHub refuses to start the whole calling workflow
if it asks for more. `repository` makes forks of headlessmc look up the run in their own repository.

```yaml
integration-test:
  needs: [ build, build-natives ]
  permissions:
    contents: read
    actions: read
  uses: headlesshq/headlessmc-integration-test/.github/workflows/test.yml@main
  with:
    run-id: ${{ github.run_id }}
    repository: ${{ github.repository }}

merge:
  needs: [ release-please, build, build-natives, packages, docker-release, integration-test ]
```

On release runs, headlessmc's `merge` job replaces the run artifacts with `merged-artifacts` (`delete-merged: true`).
It must therefore list `integration-test` in `needs`, as above, or the test jobs may fail to download
`HeadlessMc Launcher files` and the native images after `merge` has deleted them.

Each job uploads its recordings as `recordings-<variant>-<os>-<arch>`, e.g. `recordings-native-linux-x64`,
and, if it fails, its JUnit reports as `test-reports-<variant>-<os>-<arch>`.

Known headlessmc bugs the tests currently encode or work around:

- `golden/help/headlessmc.txt` contains a stray `test` line below the usage line. Remove it once headlessmc fixes it.
- `LaunchTest` creates the `mc/resources/...` directories for legacy asset indexes before launching,
  because headlessmc copies those assets without creating the parent directories.
- `JavaTest.listsRemoteDistributions` expects `java ls --remote` to fail on the native image (exit code 1,
  `InvalidDefinitionException` for `foojay.Result`), because the native image has no reflection config for that class.
  The test fails once headlessmc fixes it; then drop the native branch.

## Test cases

The command tests live in `src/test/java/.../it/commands`, one class per command group:

| Class                | Covers                                                                          |
|----------------------|---------------------------------------------------------------------------------|
| `HelpTest`           | `help`, `--help`, `help <command>` for every top-level command and alias        |
| `VersionInfoTest`    | `--version`, `-V`                                                               |
| `UnknownCommandTest` | unknown commands and subcommands, missing parameters (exit code 2)              |
| `ConfigTest`         | `config ls`, `config get`, `config set` and the written `hmc/config.properties` |
| `JavaTest`           | `java ls`; remote distributions (`network`)                                     |
| `VersionTest`        | `version ls`; remote versions and `version install vanilla 1.21.1` (`network`) |
| `ServerTest`         | `server ls`; `server add vanilla 1.21.1` (`network`)                            |
| `LaunchTest`         | `launch rd-132211`, checks the downloaded client jar and its sha1 (`network`)   |

Golden files are only used for stable output such as help text. Output that depends on the machine,
installed Javas or remote data is checked structurally, e.g. by parsing tables with `HmcTable`.

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
Tag tests that need the internet with `@Tag("network")`.

List commands print tables. `HmcTable.parse(result)` splits them by the header's column positions
before normalizing each cell, so cells can contain spaces and long directories do not shift the columns:

```java
HmcTable table = HmcTable.parse(hmc.run("server", "ls"));
assertThat(table.columns()).containsExactly("name", "version", "directory");
assertThat(table.row("name", "test-server")).isPresent();
```

Each test gets a fresh temp directory, `<HMC_DIR>`, which is the working directory of every command:

| Directory | Used as                                                                    |
|-----------|----------------------------------------------------------------------------|
| `home`    | user home, `HOME`, `USERPROFILE`, `APPDATA`, `LOCALAPPDATA`, `XDG_*_HOME` |
| `hmc`     | headlessmc files (`hmc.files.location`), e.g. `hmc/config.properties`     |
| `mc`      | Minecraft directory (`hmc.files.mc`)                                       |
| `game`    | game directory (`hmc.files.game`)                                          |

so tests never share state or touch your real `~/.minecraft` or headlessmc config.
Commands run non-interactively with `-Dhmc.jline.enabled=false` and stdin closed.
`HMC_*`, `XDG_*`, `JAVA_TOOL_OPTIONS`-style and terminal (`TERM`, `OSTYPE`, `COLUMNS`) environment variables are not passed on.
Otherwise picocli treats Git Bash on Windows as a pseudo terminal, colours the output and prints `stty` errors to stderr.
Use `hmc.property(key, value)` or `hmc.env(key, value)` to configure further.

`hmc.run(...)` runs one command, `hmc.runAll(...)` a sequence. Each command is killed with its child processes
when it exceeds its timeout, and the directory is snapshotted before and after it to compute the added,
modified and deleted files with their size and sha256.

Output assertions compare normalized output: the temp dir becomes `<HMC_DIR>`, the real user home `<USER_HOME>`,
path separators after these become `/`, line endings `\n` (a lone `\r` overwrites the line like in a terminal,
so only the final state of a progress bar remains), timestamps `<TIMESTAMP>`/`<TIME>`,
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

Output and command arguments in recordings are normalized. Modified files also carry `previousSize` and `previousSha256`,
deleted files only those. The format is modelled by `Recording`, which can read recordings back.
