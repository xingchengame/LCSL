/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.upgrade;

import org.jackhuang.hmcl.server.LuCunAPI;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Locale;

/// The launcher build the website publishes as the newest one.
///
/// LCSL is updated by downloading a build from the website and installing it, so all the launcher
/// needs from a release is its name and the files this machine can install.
public record RemoteVersion(String version, @Unmodifiable List<Artifact> artifacts) {

    /// One installable file of a release: the short label shown to the player and where it is downloaded from.
    public record Artifact(String label, String url, @Nullable String sizeLabel, @Nullable String note) {
    }

    /// Converts the newest release reported by the website into what the launcher shows,
    /// keeping only the files published for this operating system that have a download address.
    ///
    /// @return `null` when the website has published no launcher build at all
    public static @Nullable RemoteVersion from(@Nullable LuCunAPI.LauncherVersion version) {
        if (version == null || StringUtils.isBlank(version.version())) return null;

        List<Artifact> artifacts = LuCunAPI.installers(version).stream()
                .map(file -> {
                    String url = LuCunAPI.installerUrl(file);
                    return url == null ? null : new Artifact(kindLabel(file.kind()), url, file.size_label(), file.note());
                })
                .filter(artifact -> artifact != null)
                .toList();

        return new RemoteVersion(version.version(), artifacts);
    }

    /// Turns the file extension the website sends (`.exe`) into the label it shows next to it (`EXE`).
    private static String kindLabel(@Nullable String kind) {
        if (StringUtils.isBlank(kind)) return "UPDATE";
        String extension = kind.startsWith(".") ? kind.substring(1) : kind;
        return extension.toUpperCase(Locale.ROOT);
    }
}
