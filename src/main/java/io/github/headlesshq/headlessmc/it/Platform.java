package io.github.headlesshq.headlessmc.it;

import java.util.Locale;

/**
 * The operating system and architecture the tests run on,
 * named like the native headlessmc release artifacts, e.g. {@code linux-x64}.
 *
 * @param os   {@code linux}, {@code windows}, {@code macos} or the raw {@code os.name}.
 * @param arch {@code x64}, {@code arm64} or the raw {@code os.arch}.
 */
public record Platform(String os, String arch) {
    /**
     * @return the platform of the current JVM.
     */
    public static Platform current() {
        String osName = System.getProperty("os.name", "unknown").toLowerCase(Locale.ROOT);
        String os;
        if (osName.startsWith("windows")) {
            os = "windows";
        } else if (osName.startsWith("mac") || osName.startsWith("darwin")) {
            os = "macos";
        } else if (osName.startsWith("linux")) {
            os = "linux";
        } else {
            os = osName.replaceAll("[^a-z0-9]+", "-");
        }

        String archName = System.getProperty("os.arch", "unknown").toLowerCase(Locale.ROOT);
        String arch = switch (archName) {
            case "amd64", "x86_64", "x64" -> "x64";
            case "aarch64", "arm64" -> "arm64";
            default -> archName.replaceAll("[^a-z0-9]+", "-");
        };

        return new Platform(os, arch);
    }

    /**
     * @return {@code true} if this is a Windows platform.
     */
    public boolean isWindows() {
        return "windows".equals(os);
    }

    /**
     * @return the identifier used for recording directories, e.g. {@code linux-x64}.
     */
    public String id() {
        return os + "-" + arch;
    }

    @Override
    public String toString() {
        return id();
    }

}
