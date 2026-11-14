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

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.jackhuang.hmcl.server.LuCunAPI;
import org.jackhuang.hmcl.server.LuCunServer;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.LineButton;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.download.ModpackInstallWizardProvider;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// The server panel: live status, announcements and the changelog of LuCunServer.
///
/// Everything here is read from the website; the panel never sends anything unless the
/// player taps a row.
@NotNullByDefault
public final class ServerPage extends SpinnerPane implements DecoratorPage {

    /// How long the panel waits between two automatic refreshes.
    private static final Duration AUTO_REFRESH_PERIOD = Duration.seconds(120);

    /// Whether the popup announcement has been shown since the launcher started.
    private static boolean popupShown;

    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(
            new State(i18n("server"), null, true, true, true));

    private final LineButton serverItem = statusRow(i18n("server.name"));
    private final LineButton playersItem = statusRow(i18n("server.players"));
    private final LineButton latencyItem = statusRow(i18n("server.latency"));
    private final LineButton motdItem = statusRow(i18n("server.motd"));

    private final LineButton packItem = new LineButton();

    private final ComponentList statusPane = new ComponentList();
    private final ComponentList packPane = new ComponentList();
    private final ComponentList communityPane = new ComponentList();
    private final ComponentList notifyPane = new ComponentList();
    private final ComponentList statsPane = new ComponentList();
    private final ComponentList toolsPane = new ComponentList();
    private final ComponentList announcementPane = new ComponentList();
    private final ComponentList changelogPane = new ComponentList();

    /// The row that tells how much this launcher has been used on this machine.
    private final LineButton statsItem = new LineButton();

    /// The title above the community section, hidden while the website publishes no links.
    private final Node communityTitle = ComponentList.createComponentListTitle(i18n("server.community"));

    /// Whether the website published at least one community link.
    private final BooleanProperty hasCommunityLinks = new SimpleBooleanProperty(this, "hasCommunityLinks");

    private boolean loading;

    public ServerPage() {
        VBox content = new VBox();
        content.getStyleClass().add("spinner-pane-content");
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        FXUtils.smoothScrolling(scrollPane);
        setContent(scrollPane);

        statusPane.getContent().setAll(serverItem, playersItem, latencyItem, motdItem);

        packItem.setLargeTitle(true);
        packItem.setTitle(i18n("server.pack"));
        packItem.setSubtitle("--");
        packItem.setLeading(SVG.PACKAGE2.createIcon());
        packPane.getContent().setAll(packItem);

        communityTitle.visibleProperty().bind(hasCommunityLinks);
        communityTitle.managedProperty().bind(hasCommunityLinks);
        communityPane.visibleProperty().bind(hasCommunityLinks);
        communityPane.managedProperty().bind(hasCommunityLinks);

        var refreshItem = new LineButton();
        refreshItem.setLeading(SVG.REFRESH.createIcon());
        refreshItem.setTitle(i18n("server.refresh"));
        refreshItem.setOnAction(e -> refresh());

        var siteItem = LineButton.createExternalLinkButton(LuCunAPI.BASE_URL);
        siteItem.setLeading(SVG.PUBLIC.createIcon());
        siteItem.setTitle(i18n("server.website"));

        var linkPane = new ComponentList();
        linkPane.getContent().setAll(refreshItem, siteItem);

        var notifyStatusItem = toggleRow(SettingsManager.settings().serverNotifyStatusProperty(),
                i18n("server.notify.status.title"));
        var notifyPlayersItem = toggleRow(SettingsManager.settings().serverNotifyPlayerOnlineProperty(),
                i18n("server.notify.players.title"));
        var notifyAnnouncementsItem = toggleRow(SettingsManager.settings().serverNotifyAnnouncementsProperty(),
                i18n("server.notify.announcements.title"));
        var notifyPackItem = toggleRow(SettingsManager.settings().serverNotifyPackProperty(),
                i18n("server.notify.pack.title"));
        var notifyDigestItem = toggleRow(SettingsManager.settings().serverNotifyDigestProperty(),
                i18n("server.notify.digest.title"));
        notifyPane.getContent().setAll(notifyStatusItem, notifyPlayersItem, notifyAnnouncementsItem,
                notifyPackItem, notifyDigestItem);

        statsItem.setLeading(SVG.GAMEPAD.createIcon());
        statsItem.setTitle(i18n("server.stats.title"));
        statsPane.getContent().setAll(statsItem);
        refreshStats();

        toolsPane.getContent().setAll(
                toolRow(SVG.FOLDER, i18n("server.tools.directory"), ""),
                toolRow(SVG.FOLDER, i18n("server.tools.mods"), "mods"),
                toolRow(SVG.FOLDER, i18n("server.tools.saves"), "saves"));

        content.getChildren().setAll(
                ComponentList.createComponentListTitle(i18n("server.status")),
                statusPane,
                linkPane,
                ComponentList.createComponentListTitle(i18n("server.notify.section")),
                notifyPane,
                packPane,
                communityTitle,
                communityPane,
                ComponentList.createComponentListTitle(i18n("server.stats.section")),
                statsPane,
                ComponentList.createComponentListTitle(i18n("server.tools.section")),
                toolsPane,
                ComponentList.createComponentListTitle(i18n("server.announcements")),
                announcementPane,
                ComponentList.createComponentListTitle(i18n("server.changelog")),
                changelogPane);

        refresh();

        // Refresh whenever the player comes back to the panel, so the numbers are never stale.
        visibleProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue)
                refresh();
        });

        startAutoRefresh();
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    @Override
    public void refresh() {
        refresh(true);
    }

    /// Reads every feed again.
    ///
    /// @param notify whether the player should see the progress and failures; the automatic
    /// refreshes stay silent, so they never interrupt the player with a dialog
    private void refresh(boolean notify) {
        if (loading) return;
        loading = true;
        refreshStats();
        if (notify) showSpinner();

        Task.supplyAsync(ServerPage::load)
                .whenComplete(Schedulers.javafx(), (result, exception) -> {
                    loading = false;
                    if (notify) hideSpinner();
                    if (result != null) {
                        showResult(result);
                    } else if (notify) {
                        showFailure(exception);
                    } else {
                        LOG.warning("Failed to refresh the server feeds", exception);
                    }
                })
                .start();
    }

    /// Refreshes the feeds automatically while the launcher shows the panel.
    private void startAutoRefresh() {
        Timeline timeline = new Timeline(new KeyFrame(AUTO_REFRESH_PERIOD, event -> {
            if (isDisplayed())
                refresh(false);
        }));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
    }

    /// Whether the panel is in a window the player can see right now.
    private boolean isDisplayed() {
        @Nullable Scene scene = getScene();
        return isVisible() && scene != null && scene.getWindow() != null && scene.getWindow().isShowing();
    }

    /// Builds one row that switches a server reminder on and off.
    ///
    /// @param property the switch the row mirrors and toggles
    /// @param title the name of the reminder
    /// @return the row
    private static LineButton toggleRow(BooleanProperty property, String title) {
        var row = new LineButton();
        row.setTitle(title);
        Runnable subtitleUpdate = () -> row.setSubtitle(i18n(property.get()
                ? "server.notify.on"
                : "server.notify.off"));
        subtitleUpdate.run();
        property.addListener((observable, oldValue, newValue) -> subtitleUpdate.run());
        row.setOnAction(e -> property.set(!property.get()));
        return row;
    }

    /// Rewrites the statistics row with how much this launcher has been used so far.
    private void refreshStats() {
        var settings = SettingsManager.settings();
        long seconds = settings.playSecondsProperty().get();
        long minutes = Math.max(1, seconds / 60);
        String duration = minutes < 60
                ? i18n("server.stats.minutes", minutes)
                : i18n("server.stats.hours", String.format(Locale.ROOT, "%.1f", minutes / 60.0));
        statsItem.setSubtitle(i18n("server.stats.value", settings.launchCountProperty().get(), duration));
    }

    /// Reads every feed the panel shows in one background step.
    private static Feed load() throws IOException {
        return new Feed(LuCunAPI.fetchSiteConfig(), LuCunAPI.fetchServerStatus(),
                LuCunAPI.fetchAnnouncements(), LuCunAPI.fetchChangelog(), LuCunAPI.fetchPopupAnnouncement(),
                fetchLatestPackQuietly());
    }

    /// A modpack the website does not publish is not a reason to hide the rest of the panel.
    private static @Nullable LuCunAPI.PackVersion fetchLatestPackQuietly() {
        try {
            return LuCunAPI.fetchLatestPack();
        } catch (IOException e) {
            LOG.warning("Failed to read the server modpack", e);
            return null;
        }
    }

    private void showResult(Feed feed) {
        var config = feed.config();
        var status = feed.status();

        LuCunServer.rememberAddress(config.server_ip());

        serverItem.setSubtitle(status.online()
                ? join(config.server_version(), config.server_ip())
                : i18n("server.offline"));
        // The row copies the address, so nobody has to type it by hand.
        String address = LuCunServer.serverAddress();
        serverItem.setOnAction(e -> FXUtils.copyText(address));
        playersItem.setSubtitle(status.online()
                ? i18n("server.players.value", status.players_online(), status.players_max(), status.players_total())
                : "--");
        latencyItem.setSubtitle(status.latency_ms() == null ? "--" : status.latency_ms() + " ms");
        motdItem.setSubtitle(shorten(status.motd()));
        String motd = status.motd();
        motdItem.setOnAction(e -> {
            if (motd != null && !motd.isBlank()) {
                showContent(i18n("server.motd"), "", motd);
            }
        });

        List<LineButton> community = communityLinks(config);
        hasCommunityLinks.set(!community.isEmpty());
        communityPane.getContent().setAll(community);

        announcementPane.getContent().setAll(sortedAnnouncements(feed.announcements()).stream()
                .map(ServerPage::announcementItem)
                .toList());

        var pack = feed.pack();
        if (pack == null) {
            packItem.setSubtitle(i18n("server.pack.none"));
            packItem.setOnAction(null);
        } else {
            packItem.setSubtitle(join(pack.name(), join(pack.mc_version(), pack.size_label())));
            packItem.setOnAction(e -> installPack(pack));
        }

        changelogPane.getContent().setAll(feed.changelog().entries().stream()
                .map(it -> listItem(join(it.version(), it.title()), it.released_at(), it.content(), it.summary()))
                .toList());

        var popup = feed.popup();
        if (popup != null && !popupShown) {
            popupShown = true;
            showContent(popup.title(), popup.created_at(), popup.content());
        }
    }

    private void showFailure(@Nullable Exception exception) {
        serverItem.setSubtitle("--");
        playersItem.setSubtitle("--");
        latencyItem.setSubtitle("--");
        motdItem.setSubtitle("--");
        packItem.setSubtitle("--");
        packItem.setOnAction(null);
        Controllers.dialog(describe(exception), i18n("server.load_failed"), MessageDialogPane.MessageType.WARNING);
    }

    /// Describes the published modpack in one line.
    ///
    /// @param pack the published version to describe
    /// @return the name, the game version and the size of the version
    public static String describePack(LuCunAPI.PackVersion pack) {
        StringBuilder result = new StringBuilder(pack.name());
        if (pack.mc_version() != null && !pack.mc_version().isEmpty())
            result.append(" · ").append(pack.mc_version());
        if (pack.size_label() != null && !pack.size_label().isEmpty())
            result.append(" · ").append(pack.size_label());
        return result.toString();
    }

    /// Downloads the newest modpack, then hands it to the ordinary install wizard.
    public static void installPack(LuCunAPI.PackVersion pack) {
        try {
            Path file = Files.createTempFile("lucun-pack", ".zip");
            Controllers.taskDialog(
                    new FileDownloadTask(LuCunAPI.downloadUrl(pack), file)
                            .whenComplete(Schedulers.javafx(), (ignored, exception) -> {
                                if (exception != null) {
                                    Controllers.dialog(describe(exception), i18n("server.pack"), MessageDialogPane.MessageType.WARNING);
                                    return;
                                }
                                Controllers.getDecorator().startWizard(
                                        new ModpackInstallWizardProvider(GameDirectoryManager.getSelectedRepository(), file),
                                        i18n("install.modpack"));
                            }),
                    i18n("message.downloading"), TaskCancellationAction.NORMAL);
        } catch (IOException e) {
            LOG.warning("Failed to start downloading the server modpack", e);
            Controllers.dialog(describe(e), i18n("server.pack"), MessageDialogPane.MessageType.WARNING);
        }
    }

    /// Builds one tappable row that opens a feed item.
    private static LineButton listItem(String title, String subtitle, String content, @Nullable String summary) {
        var item = new LineButton();
        item.setLargeTitle(true);
        item.setTitle(shorten(title));
        item.setSubtitle(subtitle.isEmpty() && summary != null ? shorten(summary) : subtitle);
        item.setOnAction(e -> showContent(title, subtitle, content));
        return item;
    }

    /// Puts the pinned announcements on top of the list.
    ///
    /// The sort is stable, so inside each group the website order, the newest first, is kept.
    ///
    /// @param announcements the announcements the website answered with
    /// @return the same announcements, pinned ones first
    private static @Unmodifiable List<LuCunAPI.Announcement> sortedAnnouncements(List<LuCunAPI.Announcement> announcements) {
        List<LuCunAPI.Announcement> result = new ArrayList<>(announcements);
        result.sort((a, b) -> Boolean.compare(b.is_pinned(), a.is_pinned()));
        return List.copyOf(result);
    }

    /// Builds one row of the announcement list, marked when the website pinned or flagged it.
    ///
    /// An urgent announcement carries the flag of the website before its title, and a pinned
    /// one carries its flag on the line under the title.
    ///
    /// @param announcement the announcement the row shows
    /// @return the row
    private static LineButton announcementItem(LuCunAPI.Announcement announcement) {
        String title = announcement.is_urgent()
                ? i18n("server.announce.urgent") + " " + announcement.title()
                : announcement.title();
        String subtitle = join(announcement.created_at(), announcement.type());
        if (announcement.is_pinned())
            subtitle = i18n("server.announce.pinned") + " · " + subtitle;
        return listItem(title, subtitle, announcement.content(), announcement.summary());
    }

    /// Builds one row of the tools section that opens a folder of the selected instance.
    ///
    /// The row follows the instance the player has picked at the moment of the tap, and a
    /// folder that does not exist yet is created before it is opened, so the row also works
    /// on a fresh install.
    ///
    /// @param icon the icon drawn before the title
    /// @param title the name of the folder to open
    /// @param child the folder inside the instance, empty for the instance itself
    /// @return the row
    private static LineButton toolRow(SVG icon, String title, String child) {
        var item = new LineButton();
        item.setLargeTitle(true);
        item.setLeading(icon.createIcon());
        item.setTitle(title);
        item.setSubtitle(i18n("server.tools.follows_instance"));
        item.setOnAction(e -> {
            var instance = GameDirectoryManager.getSelectedInstance();
            if (instance == null) {
                Controllers.dialog(i18n("server.tools.no_instance"), null, MessageDialogPane.MessageType.INFO);
                return;
            }
            Path folder = child.isEmpty()
                    ? instance.getRunDirectory()
                    : instance.getRunDirectory().resolve(child);
            try {
                Files.createDirectories(folder);
            } catch (IOException ex) {
                LOG.warning("Failed to create the folder " + folder, ex);
            }
            FXUtils.openFolder(folder);
        });
        return item;
    }

    /// Builds the rows of the community section from what the website publishes.
    ///
    /// @param config the site configuration the website answered with
    /// @return one row per link the website published, empty when it published none
    private static @Unmodifiable List<LineButton> communityLinks(LuCunAPI.SiteConfig config) {
        List<LineButton> result = new ArrayList<>();
        communityLink(result, SVG.CHAT, i18n("server.community.qq"), config.qq_group());
        communityLink(result, SVG.PUBLIC, i18n("server.community.bilibili"), config.bilibili_url());
        communityLink(result, SVG.PUBLIC, i18n("server.community.douyin"), config.douyin_url());
        communityLink(result, SVG.PUBLIC, i18n("server.community.aifadian"), config.aifadian_url());
        return List.copyOf(result);
    }

    /// Adds one community row for the value the website published, if any.
    ///
    /// A row for an address opens it in the browser; a group number without an address
    /// is copied to the clipboard instead, so the player can paste it into QQ.
    ///
    /// @param result the list the row is added to
    /// @param icon the icon drawn before the title
    /// @param title the name of the community
    /// @param value the address or number the website published, or `null`
    private static void communityLink(List<LineButton> result, SVG icon, String title, @Nullable String value) {
        if (value == null || value.isBlank()) return;
        String target = value.trim();

        var item = target.startsWith("http://") || target.startsWith("https://")
                ? LineButton.createExternalLinkButton(target)
                : new LineButton();
        if (item.getOnAction() == null)
            item.setOnAction(e -> FXUtils.copyText(target));
        item.setLargeTitle(true);
        item.setTitle(title);
        item.setSubtitle(shorten(target));
        item.setLeading(icon.createIcon());
        result.add(item);
    }

    /// Shows one feed item, rendered the same way the website renders its Markdown.
    private static void showContent(String title, String subtitle, String content) {
        Controllers.dialog(new FeedDialog(title, subtitle, content));
    }

    private static LineButton statusRow(String title) {
        var item = new LineButton();
        item.setLargeTitle(true);
        item.setTitle(title);
        item.setSubtitle("--");
        return item;
    }

    /// Joins the parts of a subtitle, leaving out the ones the website did not send.
    private static String join(@Nullable String first, @Nullable String second) {
        if (first == null || first.isEmpty()) return second == null ? "" : second;
        if (second == null || second.isEmpty()) return first;
        return first + " · " + second;
    }

    /// Fits a piece of website text onto one row: the site sends line breaks and whole paragraphs here.
    private static String shorten(@Nullable String text) {
        if (text == null || text.isBlank()) return "--";
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() <= 60 ? flat : flat.substring(0, 59) + "…";
    }

    /// Turns a failure into the sentence the website wrote for it.
    private static String describe(@Nullable Exception exception) {
        if (exception == null) return i18n("server.load_failed");
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    /// Everything the panel shows, read from the website in one go.
    private record Feed(LuCunAPI.SiteConfig config, LuCunAPI.ServerStatus status,
                        List<LuCunAPI.Announcement> announcements, LuCunAPI.Changelog changelog,
                        @Nullable LuCunAPI.Announcement popup, @Nullable LuCunAPI.PackVersion pack) {
    }
}
