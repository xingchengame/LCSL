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
package org.jackhuang.hmcl.server;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Watches the LuCunServer website in the background and toasts the player about changes.
///
/// The monitor runs on the JavaFX thread on a fixed period, a little apart from the panels
/// that refresh while they are shown: it keeps checking while the launcher is open, so the
/// player learns that the server went up, that a friend joined or that a new announcement
/// was published without opening the server panel. Every check fails quietly; the monitor
/// never shows a dialog, it only toasts.
@NotNullByDefault
public final class LuCunMonitor {

    /// How long the monitor waits between two checks.
    private static final Duration CHECK_PERIOD = Duration.seconds(120);

    /// When the first check happens after the launcher starts.
    private static final Duration FIRST_CHECK_DELAY = Duration.seconds(15);

    /// The started timeline, or `null` before [start] is called.
    private static @Nullable Timeline timeline;

    /// Whether a check is on its way, so overlapping checks never fire.
    private static boolean checking;

    /// Whether the server state is known, so the first check never toasts a transition.
    private static boolean hasStatus;

    /// Whether the server was online at the last check.
    private static boolean serverOnline;

    /// The players who were in the server at the last check.
    private static @Unmodifiable List<String> knownPlayers = List.of();

    private LuCunMonitor() {
    }

    /// Starts the background monitor; later calls do nothing.
    public static void start() {
        if (timeline != null)
            return;

        var checker = new Timeline(
                new KeyFrame(FIRST_CHECK_DELAY, event -> check()),
                new KeyFrame(FIRST_CHECK_DELAY.add(CHECK_PERIOD), event -> check()));
        checker.setCycleCount(Animation.INDEFINITE);
        checker.play();
        timeline = checker;
    }

    /// Reads the website once and compares what changed with the last check.
    private static void check() {
        if (checking)
            return;
        checking = true;

        Task.supplyAsync(LuCunMonitor::fetch)
                .whenComplete(Schedulers.javafx(), (snapshot, exception) -> {
                    checking = false;
                    if (snapshot != null) {
                        apply(snapshot);
                    } else {
                        LOG.warning("Failed to check the server state");
                    }
                })
                .start();
    }

    /// Reads the server state, the newest announcement and the newest modpack in one background step.
    ///
    /// @return the snapshot, or `null` when the website did not answer
    private static @Nullable Snapshot fetch() {
        try {
            LuCunAPI.GuardStatus status = LuCunAPI.fetchGuardStatus();
            return new Snapshot(status, fetchNewestAnnouncementQuietly(), fetchNewestPackQuietly());
        } catch (IOException e) {
            LOG.warning("Failed to read the server state", e);
            return null;
        }
    }

    /// Reads the newest announcement, or returns `null` when there is none or it cannot be read.
    private static @Nullable LuCunAPI.Announcement fetchNewestAnnouncementQuietly() {
        try {
            List<LuCunAPI.Announcement> announcements = LuCunAPI.fetchAnnouncements();
            return announcements.isEmpty() ? null : announcements.get(0);
        } catch (IOException e) {
            LOG.warning("Failed to read the announcements", e);
            return null;
        }
    }

    /// Reads the newest modpack version, or returns `null` when there is none or it cannot be read.
    private static @Nullable LuCunAPI.PackVersion fetchNewestPackQuietly() {
        try {
            return LuCunAPI.fetchLatestPack();
        } catch (IOException e) {
            LOG.warning("Failed to read the server modpack", e);
            return null;
        }
    }

    /// Compares the snapshot with the last check and toasts the player about every change.
    private static void apply(Snapshot snapshot) {
        var settings = SettingsManager.settings();
        LuCunAPI.GuardStatus status = snapshot.status();

        // The server going up or down is worth a word only after the state is known.
        if (hasStatus && status.online() != serverOnline
                && settings.serverNotifyStatusProperty().get())
            Controllers.showToast(i18n(status.online()
                    ? "server.notify.toast.online"
                    : "server.notify.toast.offline"));
        serverOnline = status.online();
        hasStatus = true;

        // The players who are in now but were not in at the last check just joined.
        @Unmodifiable List<String> players = status.player_list() != null ? status.player_list() : List.of();
        if (hasStatus && settings.serverNotifyPlayerOnlineProperty().get()) {
            var joined = new TreeSet<>(players);
            joined.removeAll(knownPlayers);
            if (!joined.isEmpty())
                Controllers.showToast(i18n("server.notify.toast.players", String.join(", ", joined)));
        }
        knownPlayers = players;

        // The id also moves forward while the reminder is off, so turning it on later
        // never toasts the announcements the player has already missed.
        LuCunAPI.Announcement announcement = snapshot.announcement();
        if (announcement != null) {
            int seen = settings.serverLastAnnouncementIdProperty().get();
            if (announcement.id() > seen) {
                if (seen > 0 && settings.serverNotifyAnnouncementsProperty().get())
                    Controllers.showToast(i18n("server.notify.toast.announcement", announcement.title()));
                settings.serverLastAnnouncementIdProperty().set(announcement.id());
            }
        }

        // The modpack id works the same way: the first version seen is only recorded, so a
        // launcher installed on an old version never toasts the version it never had.
        LuCunAPI.PackVersion pack = snapshot.pack();
        if (pack != null) {
            int seen = settings.serverLastPackIdProperty().get();
            if (pack.id() != seen) {
                if (seen > 0 && settings.serverNotifyPackProperty().get())
                    Controllers.showToast(i18n("server.notify.toast.pack", pack.name()));
                settings.serverLastPackIdProperty().set(pack.id());
            }
        }

        // The first good check of every day says how the server is doing, once, so the
        // player who just opened the launcher learns the server is up without looking.
        if (status.online() && settings.serverNotifyDigestProperty().get()) {
            String today = LocalDate.now().toString();
            if (!today.equals(settings.serverDigestDateProperty().get())) {
                settings.serverDigestDateProperty().set(today);
                Controllers.showToast(i18n("server.notify.toast.digest", status.players_online()));
            }
        }
    }

    /// What one check read from the website.
    private record Snapshot(LuCunAPI.GuardStatus status, @Nullable LuCunAPI.Announcement announcement,
                            @Nullable LuCunAPI.PackVersion pack) {
        Snapshot {
            Objects.requireNonNull(status);
        }
    }
}
