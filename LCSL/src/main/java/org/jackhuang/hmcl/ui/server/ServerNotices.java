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
package org.jackhuang.hmcl.ui.server;

import org.jackhuang.hmcl.server.LuCunAPI;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.DownloadRegionManager;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

import static org.jackhuang.hmcl.setting.SettingsManager.userState;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Tells the player about what the server website has published that they have not seen yet.
///
/// Nothing here depends on the player being signed in; everything is read from the public part
/// of the website.
@NotNullByDefault
public final class ServerNotices {
    private ServerNotices() {
    }

    /// Reads the notices the website publishes and shows the ones the player has not seen.
    public static void init() {
        Task.supplyAsync(ServerNotices::load)
                .whenComplete(Schedulers.javafx(), (result, exception) -> {
                    if (result == null) {
                        LOG.warning("Failed to read the notices of the server website", exception);
                        return;
                    }
                    try {
                        showAnnouncement(result);
                        showPackUpdate(result);
                    } catch (Throwable t) {
                        LOG.warning("Failed to show the notices of the server website", t);
                    }
                })
                .start();
    }

    /// Reads every notice the website shows; a notice that fails does not hide the other ones.
    private static Notices load() {
        List<LuCunAPI.Announcement> announcements = List.of();
        @Nullable LuCunAPI.Announcement popup = null;
        @Nullable LuCunAPI.PackVersion pack = null;

        try {
            announcements = LuCunAPI.fetchAnnouncements();
        } catch (IOException e) {
            LOG.warning("Failed to read the announcements", e);
        }
        try {
            popup = LuCunAPI.fetchPopupAnnouncement();
        } catch (IOException e) {
            LOG.warning("Failed to read the popup announcement", e);
        }
        try {
            pack = LuCunAPI.fetchLatestPack();
        } catch (IOException e) {
            LOG.warning("Failed to read the server modpack", e);
        }

        return new Notices(announcements, popup, pack);
    }

    /// Shows the announcement the player has not read yet, once.
    ///
    /// The announcement is remembered in the user state once the player has closed the window of
    /// it, so it is not shown a second time and is shown again when the player did not read it.
    private static void showAnnouncement(Notices notices) {
        int lastShown = userState().lastShownAnnouncementIdProperty().get();
        @Nullable LuCunAPI.Announcement announcement = pickUnread(notices, lastShown);
        if (announcement == null)
            return;

        var dialog = new FeedDialog(announcement.title(), announcement.created_at(), announcement.content());
        dialog.addEventHandler(DialogCloseEvent.CLOSE,
                e -> userState().lastShownAnnouncementIdProperty().set(announcement.id()));
        Controllers.dialogLater(dialog);
    }

    /// Shows the modpack the website published, once per published version.
    ///
    /// The version the player has been told about is remembered in the user state once the window
    /// of it is closed, so a new version is announced exactly once.
    private static void showPackUpdate(Notices notices) {
        if (DownloadRegionManager.isDownloadDisabled())
            return;

        LuCunAPI.PackVersion pack = notices.pack();
        if (pack == null || pack.id() <= userState().lastShownPackIdProperty().get())
            return;

        var dialog = new MessageDialogPane.Builder("",
                        i18n("server.pack.new_version", ServerPage.describePack(pack)),
                        MessageDialogPane.MessageType.INFO)
                .addAction(i18n("button.download"), () -> ServerPage.installPack(pack))
                .addCancel(null)
                .build();
        dialog.addEventHandler(DialogCloseEvent.CLOSE,
                e -> userState().lastShownPackIdProperty().set(pack.id()));
        Controllers.dialogLater(dialog);
    }

    /// Returns the announcement the player has not read yet, or `null` when there is none.
    ///
    /// The announcement the website wants to show comes first; the newest one is shown when the
    /// website has not marked any.
    private static @Nullable LuCunAPI.Announcement pickUnread(Notices notices, int lastShownId) {
        @Nullable LuCunAPI.Announcement popup = notices.popup();
        if (popup != null && popup.id() > lastShownId)
            return popup;

        return notices.announcements().stream()
                .max(Comparator.comparing(LuCunAPI.Announcement::created_at)
                        .thenComparing(LuCunAPI.Announcement::id))
                .filter(it -> it.id() > lastShownId)
                .orElse(null);
    }

    /// Everything the website shows, read in one go.
    private record Notices(List<LuCunAPI.Announcement> announcements,
                            @Nullable LuCunAPI.Announcement popup,
                            @Nullable LuCunAPI.PackVersion pack) {
    }
}
