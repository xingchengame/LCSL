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

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.server.LuCunAPI;
import org.jackhuang.hmcl.server.LuCunServer;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.DownloadRegionManager;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// The server panel: live status, announcements and the changelog of LuCunServer.
///
/// Everything here is read from the website; the panel never sends anything unless the
/// player taps a row.
@NotNullByDefault
public final class ServerPage extends SpinnerPane implements DecoratorPage {

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
    private final ComponentList announcementPane = new ComponentList();
    private final ComponentList changelogPane = new ComponentList();

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

        var refreshItem = new LineButton();
        refreshItem.setLeading(SVG.REFRESH.createIcon());
        refreshItem.setTitle(i18n("server.refresh"));
        refreshItem.setOnAction(e -> refresh());

        var siteItem = LineButton.createExternalLinkButton(LuCunAPI.BASE_URL);
        siteItem.setLeading(SVG.PUBLIC.createIcon());
        siteItem.setTitle(i18n("server.website"));

        var linkPane = new ComponentList();
        linkPane.getContent().setAll(refreshItem, siteItem);

        DownloadRegionManager.hideWhenDisabled(packPane);

        content.getChildren().setAll(
                ComponentList.createComponentListTitle(i18n("server.status")),
                statusPane,
                linkPane,
                packPane,
                ComponentList.createComponentListTitle(i18n("server.announcements")),
                announcementPane,
                ComponentList.createComponentListTitle(i18n("server.changelog")),
                changelogPane);

        refresh();
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    @Override
    public void refresh() {
        if (loading) return;
        loading = true;
        showSpinner();

        Task.supplyAsync(ServerPage::load)
                .whenComplete(Schedulers.javafx(), (result, exception) -> {
                    loading = false;
                    hideSpinner();
                    if (result != null) {
                        showResult(result);
                    } else {
                        showFailure(exception);
                    }
                })
                .start();
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

        announcementPane.getContent().setAll(feed.announcements().stream()
                .map(it -> listItem(it.title(), join(it.created_at(), it.type()), it.content(), it.summary()))
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
        if (!DownloadRegionManager.ensureDownloadAllowed())
            return;

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
