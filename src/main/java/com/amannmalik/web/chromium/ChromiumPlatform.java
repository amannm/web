package com.amannmalik.web.chromium;

import java.util.Locale;

/**
 * Supported host platforms for Chromium snapshot downloads.
 */
public enum ChromiumPlatform {
    MAC_ARM("Mac_Arm", "chrome-mac/Chromium.app/Contents/MacOS/Chromium", "chrome-mac.zip"),
    MAC_INTEL("Mac", "chrome-mac/Chromium.app/Contents/MacOS/Chromium", "chrome-mac.zip"),
    LINUX_X64("Linux_x64", "chrome-linux/chrome", "chrome-linux.zip"),
    WINDOWS_X64("Win", "chrome-win/chrome.exe", "chrome-win.zip");

    private final String snapshotLabel;
    private final String executableRelativePath;
    private final String archiveName;

    ChromiumPlatform(String snapshotLabel, String executableRelativePath, String archiveName) {
        this.snapshotLabel = snapshotLabel;
        this.executableRelativePath = executableRelativePath;
        this.archiveName = archiveName;
    }

    public String snapshotLabel() {
        return snapshotLabel;
    }

    public String executableRelativePath() {
        return executableRelativePath;
    }

    public String archiveName() {
        return archiveName;
    }

    public static ChromiumPlatform detect() {
        var os = System.getProperty("os.name");
        var arch = System.getProperty("os.arch");
        return from(os, arch);
    }

    public static ChromiumPlatform from(String osName, String archName) {
        var normalizedOs = osName.toLowerCase(Locale.ROOT);
        var normalizedArch = archName.toLowerCase(Locale.ROOT);

        return switch (normalizedOs) {
            case String s when s.contains("mac") || s.contains("darwin") -> switch (normalizedArch) {
                case "aarch64", "arm64" -> MAC_ARM;
                default -> MAC_INTEL;
            };
            case String s when s.contains("win") -> WINDOWS_X64;
            case String s when s.contains("nux") || s.contains("linux") -> LINUX_X64;
            default -> throw new IllegalArgumentException("Unsupported platform: " + osName + " / " + archName);
        };
    }
}
