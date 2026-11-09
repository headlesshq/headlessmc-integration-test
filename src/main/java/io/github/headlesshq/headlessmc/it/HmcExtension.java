package io.github.headlesshq.headlessmc.it;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JUnit extension that injects an {@link HmcRunner} working in a fresh temp directory into test methods.
 * After each test the runner's commands are written as a {@link Recording}
 * to {@code <hmc.recordings.dir>/<platform>/<Class>.<method>.json}, and the directory is deleted
 * unless {@code -Dhmc.keep.dirs=true} is set.
 *
 * @see HmcTest
 */
public final class HmcExtension implements ParameterResolver, AfterEachCallback {
    /**
     * System property with the directory recordings are written to.
     */
    public static final String RECORDINGS_DIR_PROPERTY = "hmc.recordings.dir";
    /**
     * System property to keep test directories after the test for debugging.
     */
    public static final String KEEP_DIRS_PROPERTY = "hmc.keep.dirs";

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(HmcExtension.class);

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType() == HmcRunner.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return extensionContext.getStore(NAMESPACE).computeIfAbsent(HmcRunner.class, key -> {
            HmcExecutable executable = HmcExecutable.locate();
            try {
                return new HmcRunner(executable, Files.createTempDirectory("hmc-it-"));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, HmcRunner.class);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        HmcRunner runner = context.getStore(NAMESPACE).remove(HmcRunner.class, HmcRunner.class);
        if (runner == null) {
            return;
        }

        try {
            if (!runner.results().isEmpty()) {
                Recording.of(testName(context), Platform.current(), runner.version(), runner.executable(),
                    runner.results()).write(recordingsDir());
            }
        } finally {
            if (Boolean.getBoolean(KEEP_DIRS_PROPERTY)) {
                context.publishReportEntry("hmcDir", runner.dir().toString());
            } else {
                HmcRunner.deleteRecursively(runner.dir());
            }
        }
    }

    /**
     * @return the directory recordings are written to, {@code build/recordings} by default.
     */
    public static Path recordingsDir() {
        String dir = System.getProperty(RECORDINGS_DIR_PROPERTY);
        return dir == null || dir.isBlank() ? Path.of("build", "recordings") : Path.of(dir);
    }

    private static String testName(ExtensionContext context) {
        String name = context.getRequiredTestClass().getSimpleName() + "." + context.getRequiredTestMethod().getName();
        if (context.getUniqueId().contains("test-template-invocation")) {
            name += "-" + context.getDisplayName();
        }

        return name.replaceAll("[^A-Za-z0-9._-]+", "_");
    }

}
