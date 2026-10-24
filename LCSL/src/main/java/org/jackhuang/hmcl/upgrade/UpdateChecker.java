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

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.*;
import javafx.beans.value.ObservableBooleanValue;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.server.LuCunAPI;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.jackhuang.hmcl.util.Lang.thread;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Asks the server website whether a newer launcher build has been published.
public final class UpdateChecker {
    private UpdateChecker() {
    }

    /// The newest build the website has published, `null` when none is published or the check failed.
    private static final ObjectProperty<@Nullable RemoteVersion> latestVersion = new SimpleObjectProperty<>();

    /// Whether the website says its newest build is newer than the one that is running.
    private static final BooleanProperty hasUpdate = new SimpleBooleanProperty();

    /// Whether the running launcher should offer to be updated.
    private static final BooleanBinding outdated = Bindings.createBooleanBinding(
            () -> latestVersion.get() != null && hasUpdate.get(),
            latestVersion, hasUpdate);

    /// Whether an update check is running right now.
    private static final ReadOnlyBooleanWrapper checkingUpdate = new ReadOnlyBooleanWrapper(false);

    /// The failure of the last update check, `null` when it answered.
    private static final ReadOnlyObjectWrapper<@Nullable Throwable> error = new ReadOnlyObjectWrapper<>();

    /// Checks for an update once at startup.
    public static void init() {
        requestCheckUpdate();
    }

    /// Returns the newest published build, or `null` when there is none the launcher knows about.
    public static @Nullable RemoteVersion getLatestVersion() {
        return latestVersion.get();
    }

    /// The newest published build, updated after every check.
    public static ReadOnlyObjectProperty<@Nullable RemoteVersion> latestVersionProperty() {
        return latestVersion;
    }

    /// Whether a newer build is published.
    public static boolean isOutdated() {
        return outdated.get();
    }

    /// Whether a newer build is published.
    public static ObservableBooleanValue outdatedProperty() {
        return outdated;
    }

    /// Whether an update check is running.
    public static boolean isCheckingUpdate() {
        return checkingUpdate.get();
    }

    /// Whether an update check is running.
    public static ReadOnlyBooleanProperty checkingUpdateProperty() {
        return checkingUpdate.getReadOnlyProperty();
    }

    /// The failure of the last update check, `null` when it answered.
    public static ReadOnlyObjectProperty<@Nullable Throwable> errorProperty() {
        return error.getReadOnlyProperty();
    }

    /// Asks the website which build is newest, compared with the release number of this one.
    private static LuCunAPI.LauncherUpdate checkUpdate() throws IOException {
        return LuCunAPI.fetchLauncherUpdate(Metadata.RELEASE_SEQ);
    }

    /// Checks for an update in the background, ignoring the request while a check is already running.
    public static void requestCheckUpdate() {
        if (settings().isDownloadDisabled())
            return;

        Platform.runLater(() -> {
            if (isCheckingUpdate())
                return;
            checkingUpdate.set(true);

            thread(() -> {
                LuCunAPI.LauncherUpdate result = null;
                Throwable t = null;
                try {
                    result = checkUpdate();
                    LOG.info("Latest launcher version is " + result.latest() + ", update available: " + result.has_update());
                } catch (Throwable e) {
                    t = e;
                    LOG.warning("Failed to check for update", e);
                }
                Throwable throwable = t;

                LuCunAPI.LauncherUpdate update = result;
                Platform.runLater(() -> {
                    if (throwable != null) {
                        latestVersion.set(null);
                        hasUpdate.set(false);
                        error.set(throwable);
                    } else {
                        error.set(null);
                        latestVersion.set(RemoteVersion.from(update.latest()));
                        hasUpdate.set(update.has_update());
                    }
                    checkingUpdate.set(false);
                });
            }, "Update Checker", true);
        });
    }
}
