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

/// Debian packaging metadata for one launcher release type.
///
/// The package name doubles as the installed command, the desktop entry name
/// and the icon name, so two release types can be installed side by side
/// without conflicting files.
public enum ReleaseType {
    STABLE("lcsl", "LuCUNserver Launcher"),
    DEVELOPMENT("lcsl-beta", "LuCUNserver Launcher (Beta)"),
    NIGHTLY("lcsl-nightly", "LuCUNserver Launcher (Nightly)");

    private final String packageName;
    private final String displayName;

    ReleaseType(String packageName, String displayName) {
        this.packageName = packageName;
        this.displayName = displayName;
    }

    /// Debian package name written into `control`, also used for the command, desktop entry and icon.
    public String getPackageName() {
        return packageName;
    }

    /// Human readable name shown in the desktop entry.
    public String getDisplayName() {
        return displayName;
    }
}
