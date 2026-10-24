/*
 * LuCUNserver Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
 * Copyright (C) 2025-2026 LuCunServer
 * Not affiliated with Mojang Studios or Microsoft.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl;

import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.JarUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;

/**
 * Stores metadata about this application.
 */
public final class Metadata {
    private Metadata() {
    }

    public static final String NAME = "LCSL";
    public static final String FULL_NAME = "LuCUNserver Launcher";
    public static final String VERSION = System.getProperty("hmcl.version.override", "0.0.1");

    /// Number of the release this build was uploaded as on the website, used by the update check.
    ///
    /// The website compares release numbers, not version labels, so this has to be set to the number
    /// of the release this very build is. `0` means the build was never published, which makes the
    /// website report an update for it.
    public static final int RELEASE_SEQ = 0;

    /// Explicit Application User Model ID used for Windows taskbar grouping and pinning.
    public static final String WINDOWS_APP_USER_MODEL_ID = "org.jackhuang.hmcl";

    public static final String TITLE = NAME + " " + VERSION;

    /// Whether the build metadata identifies this launcher as an official build.
    public static final boolean OFFICIAL_BUILD = Boolean.parseBoolean(JarUtils.getAttribute("hmcl.official", "false"));

    public static final int MINIMUM_REQUIRED_JAVA_VERSION = 17;
    public static final int MINIMUM_SUPPORTED_JAVA_VERSION = 17;
    public static final int RECOMMENDED_JAVA_VERSION = 21;

    /// Homepage of the server this launcher is made for.
    public static final String PUBLISH_URL = "http://lucunserver.xyz/";
    public static final String DOWNLOAD_URL = PUBLISH_URL + "download";

    /// The page on the website that lists the published launcher builds.
    public static final String LAUNCHER_DOWNLOADS_URL = PUBLISH_URL + "launcher.html";

    /// Repository of this fork, where issues are collected and new builds are published.
    public static final String REPOSITORY_URL = "https://github.com/xingchenggame/LCSL";
    public static final String MANUAL_UPDATE_URL = REPOSITORY_URL + "/releases";

    public static final String DOCS_URL = "https://docs.hmcl.net";
    /// Link shown when the user needs help, including the crash windows.
    public static final String CONTACT_URL = PUBLISH_URL;
    public static final String CHANGELOG_URL = DOCS_URL + "/changelog/";
    public static final String EULA_URL = DOCS_URL + "/eula/hmcl.html";
    /// The server's Bilibili space, where news about the server is posted.
    public static final String GROUPS_URL = "https://space.bilibili.com/3546699488299896";

    public static final String BUILD_CHANNEL = JarUtils.getAttribute("hmcl.version.type", "nightly");
    public static final String GITHUB_SHA = JarUtils.getAttribute("hmcl.version.hash", null);

    public static final Path CURRENT_DIRECTORY = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    public static final Path MINECRAFT_DIRECTORY = OperatingSystem.getWorkingDirectory("minecraft");
    public static final Path HMCL_USER_HOME;
    public static final Path HMCL_LOCAL_HOME;
    public static final Path DEPENDENCIES_DIRECTORY;

    static {
        String hmclHome = System.getProperty("hmcl.home", System.getenv("HMCL_USER_HOME"));
        if (StringUtils.isBlank(hmclHome)) {
            if (OperatingSystem.CURRENT_OS.isLinuxOrBSD()) {
                String xdgData = System.getenv("XDG_DATA_HOME");
                if (StringUtils.isNotBlank(xdgData)) {
                    HMCL_USER_HOME = Path.of(xdgData, "hmcl").toAbsolutePath().normalize();
                } else {
                    HMCL_USER_HOME = Path.of(System.getProperty("user.home"), ".local", "share", "hmcl").toAbsolutePath().normalize();
                }
            } else {
                HMCL_USER_HOME = OperatingSystem.getWorkingDirectory("hmcl");
            }
        } else {
            HMCL_USER_HOME = Path.of(hmclHome).toAbsolutePath().normalize();
        }

        String hmclCurrentDir = System.getProperty("hmcl.dir", System.getenv("HMCL_LOCAL_HOME"));
        HMCL_LOCAL_HOME = StringUtils.isNotBlank(hmclCurrentDir)
                ? Path.of(hmclCurrentDir).toAbsolutePath().normalize()
                : CURRENT_DIRECTORY.resolve(".hmcl");

        String hmclDependencies = System.getProperty("hmcl.dependencies.dir", System.getenv("HMCL_DEPENDENCIES_DIR"));
        DEPENDENCIES_DIRECTORY = StringUtils.isNotBlank(hmclDependencies)
                ? Path.of(hmclDependencies).toAbsolutePath().normalize()
                : HMCL_LOCAL_HOME.resolve("dependencies");
    }

    public static @Nullable String getSuggestedJavaDownloadLink() {
        if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX && Architecture.SYSTEM_ARCH == Architecture.LOONGARCH64_OW)
            return "https://www.loongnix.cn/zh/api/java/downloads-jdk21/index.html";
        else {
            EnumSet<Architecture> supportedArchitectures;
            if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS)
                supportedArchitectures = EnumSet.of(Architecture.X86_64, Architecture.X86, Architecture.ARM64);
            else if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX)
                supportedArchitectures = EnumSet.of(
                        Architecture.X86_64, Architecture.X86,
                        Architecture.ARM64, Architecture.ARM32,
                        Architecture.RISCV64, Architecture.LOONGARCH64
                );
            else if (OperatingSystem.CURRENT_OS == OperatingSystem.MACOS)
                supportedArchitectures = EnumSet.of(Architecture.X86_64, Architecture.ARM64);
            else
                supportedArchitectures = EnumSet.noneOf(Architecture.class);
            if (supportedArchitectures.contains(Architecture.SYSTEM_ARCH))
                return String.format("https://docs.hmcl.net/downloads/%s/%s.html",
                        OperatingSystem.CURRENT_OS.getCheckedName(),
                        Architecture.SYSTEM_ARCH.getCheckedName()
                );
            else
                return null;
        }
    }

    /// Directory under [HMCL_LOCAL_HOME] that holds a launcher-bundled modpack for automatic install.
    public static final String BUNDLED_MODPACK_DIRECTORY_NAME = "modpack";

    /// Returns the directory for a launcher-bundled modpack (`[HMCL_LOCAL_HOME]/modpack`).
    public static Path getBundledModpackDirectory() {
        return HMCL_LOCAL_HOME.resolve(BUNDLED_MODPACK_DIRECTORY_NAME);
    }

    /// Returns the bundled modpack package under [getBundledModpackDirectory], if present.
    ///
    /// Prefers `modpack.zip` over `modpack.mrpack` when both exist. Presence of the package is the
    /// signal to offer automatic install; the file is removed after a successful install.
    ///
    /// @return the modpack path, or `null` when no package is present
    public static @Nullable Path findBundledModpackFile() {
        Path directory = getBundledModpackDirectory();
        Path zipModpack = directory.resolve("modpack.zip");
        if (Files.isRegularFile(zipModpack)) {
            return zipModpack;
        }
        Path mrpackModpack = directory.resolve("modpack.mrpack");
        if (Files.isRegularFile(mrpackModpack)) {
            return mrpackModpack;
        }
        return null;
    }

}
