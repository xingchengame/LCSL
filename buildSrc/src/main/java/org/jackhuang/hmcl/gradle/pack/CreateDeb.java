/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.gradle.pack;

import kala.compress.archivers.ar.ArArchiveEntry;
import kala.compress.archivers.ar.ArArchiveOutputStream;
import kala.compress.archivers.tar.TarArchiveEntry;
import kala.compress.archivers.tar.TarArchiveOutputStream;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

/// Creates a Debian package for the current LCSL channel.
///
/// ## Package layout
///
/// The generated `data.tar.gz` contains four installed artifacts:
///
/// - the bundled HMCL shell launcher under `/usr/share/java/<package>/`
/// - the launcher command under `/usr/bin/`
/// - a desktop entry under `/usr/share/applications/`
/// - the LCSL icon under `/usr/share/icons/hicolor/256x256/apps/`
///
/// ## Channel commands
///
/// Every package installs its own command, such as `lcsl` or `lcsl-beta`.
/// The names never collide, so several channels can be installed side by side.
///
/// @author Glavo
public abstract class CreateDeb extends DefaultTask {
    public static final Logger LOGGER = Logging.getLogger(CreateDeb.class);

    private static final int DIRECTORY_MODE = 0755;
    private static final int EXECUTABLE_MODE = 0755;
    private static final int REGULAR_FILE_MODE = 0644;

    /// Debian version written into the `control` file and output filename.
    @Input
    public abstract Property<String> getVersion();

    /// Release type metadata that controls package name, launcher name, and alias priority.
    @Input
    public abstract Property<ReleaseType> getReleaseType();

    /// Launcher class name for Linux StartupWMClass property in the .desktop file.
    @Input
    public abstract Property<String> getLauncherClassName();

    /// Executable `.sh` artifact produced by `makeExecutables`.
    @InputFile
    public abstract RegularFileProperty getAppShFile();

    /// Desktop icon installed into the hicolor icon theme.
    @InputFile
    public abstract RegularFileProperty getIconFile();

    /// Final `.deb` archive written by this task.
    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    private ReleaseType getCurrentType() {
        return getReleaseType().get();
    }

    private String getLauncherPath() {
        return "/usr/bin/" + getCurrentType().getPackageName();
    }

    private String getTargetPath() {
        return "/usr/share/java/" + getCurrentType().getPackageName() + "/" + getAppShFile().getAsFile().get().getName();
    }

    private String getDesktopFilePath() {
        return "/usr/share/applications/%s.desktop".formatted(getCurrentType().getPackageName());
    }

    private String getIconTargetPath() {
        return "/usr/share/icons/hicolor/256x256/apps/%s.png".formatted(getCurrentType().getPackageName());
    }

    /// Ensures parent directories exist in the tar stream before child entries are written.
    private static void makeDirectories(Set<String> directories, TarArchiveOutputStream output, String dirName) throws IOException {
        if (dirName.isEmpty() || ".".equals(dirName) || directories.contains(dirName))
            return;

        int idx = dirName.lastIndexOf('/');
        if (idx > 0) {
            makeDirectories(directories, output, dirName.substring(0, idx));
        }

        TarArchiveEntry entry = new TarArchiveEntry(dirName + "/", true);
        entry.setMode(DIRECTORY_MODE);
        output.putArchiveEntry(entry);
        output.closeArchiveEntry();
        directories.add(dirName);
    }

    /// Writes UTF-8 text content as a tar entry with the requested file mode.
    private static void putEntry(Set<String> directories, TarArchiveOutputStream output, String name, String content, int mode) throws IOException {
        putEntry(directories, output, name, content.getBytes(StandardCharsets.UTF_8), mode);
    }

    /// Writes binary content as a tar entry and creates parent directories on demand.
    private static void putEntry(Set<String> directories, TarArchiveOutputStream output, String name, byte[] content, int mode) throws IOException {
        int idx = name.lastIndexOf('/');
        if (idx > 0) {
            makeDirectories(directories, output, name.substring(0, idx));
        }

        TarArchiveEntry entry = new TarArchiveEntry(name);
        entry.setMode(mode);
        entry.setSize(content.length);
        output.putArchiveEntry(entry);
        output.write(content);
        output.closeArchiveEntry();
    }

    /// Builds a valid `.deb` archive with `debian-binary`, `control.tar.gz`, and `data.tar.gz`.
    @TaskAction
    public void run() throws IOException {
        Path appShFile = getAppShFile().getAsFile().get().toPath();
        if (!Files.isRegularFile(appShFile))
            throw new IOException("Invalid app script file: " + appShFile);

        Path iconFile = getIconFile().getAsFile().get().toPath();
        if (!Files.isRegularFile(iconFile))
            throw new IOException("Invalid icon file: " + iconFile);

        byte[] appShBytes = Files.readAllBytes(appShFile);
        if (appShBytes.length == 0)
            throw new IOException("Empty app script file: " + appShFile);

        byte[] iconBytes = Files.readAllBytes(iconFile);
        if (iconBytes.length == 0)
            throw new IOException("Empty icon file: " + iconFile);

        byte[] launcherScriptBytes = getLauncherScript().getBytes(StandardCharsets.UTF_8);
        byte[] desktopInfoBytes = getDesktopInfo().getBytes(StandardCharsets.UTF_8);

        LOGGER.lifecycle("Creating control.tar.gz");
        var controlData = new ByteArrayOutputStream();
        try (var output = new TarArchiveOutputStream(new GZIPOutputStream(controlData))) {
            output.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);

            Set<String> directories = new HashSet<>();
            putEntry(directories, output, "./control", getControl(appShBytes.length, launcherScriptBytes.length, desktopInfoBytes.length, iconBytes.length), REGULAR_FILE_MODE);
        }

        Path outputFile = getOutputFile().get().getAsFile().toPath();
        Files.createDirectories(outputFile.getParent());

        LOGGER.lifecycle("Creating data.tar.gz");
        var dataTarBuffer = new ByteArrayOutputStream(12 * 1024 * 1024);
        try (var output = new TarArchiveOutputStream(new GZIPOutputStream(dataTarBuffer))) {
            output.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);

            Set<String> directories = new HashSet<>();
            putEntry(directories, output, "." + getTargetPath(), appShBytes, EXECUTABLE_MODE);
            putEntry(directories, output, "." + getLauncherPath(), launcherScriptBytes, EXECUTABLE_MODE);
            putEntry(directories, output, "." + getDesktopFilePath(), desktopInfoBytes, REGULAR_FILE_MODE);
            putEntry(directories, output, "." + getIconTargetPath(), iconBytes, REGULAR_FILE_MODE);
        }

        LOGGER.lifecycle("Creating deb file");
        try (var output = new ArArchiveOutputStream(Files.newOutputStream(outputFile,
                StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE))) {
            putArEntry(output, "debian-binary", "2.0\n".getBytes(StandardCharsets.UTF_8));
            putArEntry(output, "control.tar.gz", controlData.toByteArray());
            putArEntry(output, "data.tar.gz", dataTarBuffer.toByteArray());
        }
    }

    /// Adds one member to the outer ar container used by Debian packages.
    private static void putArEntry(ArArchiveOutputStream output, String name, byte[] content) throws IOException {
        ArArchiveEntry entry = new ArArchiveEntry(name, content.length);
        output.putArchiveEntry(entry);
        output.write(content);
        output.closeArchiveEntry();
    }

    /// Generates the package metadata file.
    ///
    /// The upstream project is credited in the description, because the package
    /// ships a modified copy of its code.
    ///
    /// `Depends` is deliberately omitted so the package stays policy-light and
    /// leaves runtime dependency resolution to the target environment.
    private String getControl(long appSize, long launcherScriptSize, long desktopInfoSize, long iconSize) {
        long installedSize = (appSize + launcherScriptSize + desktopInfoSize + iconSize + 1023) / 1024;

        return """
                Package: %s
                Version: %s
                Section: games
                Priority: optional
                Architecture: all
                Installed-Size: %d
                Maintainer: LuCunServer <http://lucunserver.xyz/>
                Description: LuCUNserver Launcher
                 A build of Hello Minecraft! Launcher (https://github.com/HMCL-dev/HMCL)
                 by huangyuhui and contributors, distributed under the GNU General Public
                 License version 3 or later. Not affiliated with Mojang or Microsoft.
                Homepage: http://lucunserver.xyz/
                """.formatted(getCurrentType().getPackageName(), getVersion().get(), Math.max(installedSize, 1)) + "\n";
    }

    /// Creates a tiny wrapper that launches the bundled shell script from the user's home directory.
    private String getLauncherScript() {
        return """
                #!/usr/bin/env bash
                cd "$HOME"
                if [ -z "${HMCL_USER_HOME:-}" ]; then
                    if [ -z "${XDG_DATA_HOME:-}" ]; then
                        export HMCL_USER_HOME="$HOME/.local/share/hmcl"
                    else
                        export HMCL_USER_HOME="$XDG_DATA_HOME/hmcl"
                    fi
                fi
                if [ -z "${HMCL_LOCAL_HOME:-}" ]; then
                    export HMCL_LOCAL_HOME="$HMCL_USER_HOME/local-lcsl"
                fi
                if [ -z "${HMCL_DEPENDENCIES_DIR:-}" ]; then
                    export HMCL_DEPENDENCIES_DIR="$HMCL_USER_HOME/dependencies"
                fi
                exec %s "$@"
                """.formatted(getTargetPath());
    }

    /// Generates the desktop entry that points to the channel-specific launcher command.
    private String getDesktopInfo() {
        return """
                [Desktop Entry]
                Type=Application
                Name=%s
                Comment=Hello Minecraft! Launcher
                Exec=%s
                Icon=%s
                Terminal=false
                StartupNotify=false
                Categories=Game;
                Keywords=mc;minecraft;
                StartupWMClass=%s
                """.formatted(getCurrentType().getDisplayName(), getLauncherPath(), getIconTargetPath(), getLauncherClassName().get());
    }
}
