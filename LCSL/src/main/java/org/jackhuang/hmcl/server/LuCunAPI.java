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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.HttpRequest;
import org.jackhuang.hmcl.util.io.ResponseCodeException;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/// Reads the information the LuCunServer website publishes for players.
///
/// Every method in this class blocks until the website answers, so they are meant to be
/// called from a background thread rather than from the JavaFX thread.
@NotNullByDefault
public final class LuCunAPI {

    /// Address all endpoints below are resolved against, taken from the launcher homepage address.
    public static final String BASE_URL = Metadata.PUBLISH_URL.endsWith("/")
            ? Metadata.PUBLISH_URL.substring(0, Metadata.PUBLISH_URL.length() - 1)
            : Metadata.PUBLISH_URL;

    private LuCunAPI() {
    }

    /// The information the website shows about the server itself.
    @JsonSerializable
    public record SiteConfig(
            String server_name,
            String server_ip,
            String server_version,
            @Nullable String site_version,
            @Nullable String qq_group,
            @Nullable String bilibili_url,
            @Nullable String douyin_url,
            @Nullable String aifadian_url) {
    }

    /// The live state of the survival server.
    @JsonSerializable
    public record ServerStatus(
            boolean online,
            int players_online,
            int players_max,
            int players_total,
            @Nullable String version,
            @Nullable String motd,
            @Nullable Integer latency_ms,
            @Nullable List<String> player_list) {
    }

    /// The state of the survival server together with the load measured for it.
    ///
    /// The website measures the load from the server itself, so the ticks per second are the real
    /// ones and not an estimate.
    @JsonSerializable
    public record ServerLoad(
            boolean online,
            int players_online,
            int players_max,
            @Nullable Double tps,
            @Nullable Integer latency_ms,
            @Nullable String version,
            @Nullable List<String> player_list) {
    }

    /// The state of the survival server as the website measures it for its own status card.
    ///
    /// This is what the website shows on its home page: the load of the server, the guard that
    /// watches it and the measurements of the last hour.
    @JsonSerializable
    public record GuardStatus(
            boolean online,
            int players_online,
            int players_max,
            @Unmodifiable @Nullable List<String> player_list,
            @Nullable Double tps,
            @Nullable String tps_source,
            @Nullable Integer latency_ms,
            @Nullable String version,
            double inbound_mbps,
            @Nullable Posture posture,
            @Nullable Guard guard,
            @Nullable Thresholds thresholds,
            @Unmodifiable @Nullable List<Sample> samples,
            long updated_at) {

        /// Makes the lists the website sent unmodifiable.
        public GuardStatus {
            player_list = unmodifiable(player_list);
            samples = unmodifiable(samples);
        }

        /// How the server is doing, in the words of the server itself.
        @JsonSerializable
        public record Posture(String label, String level, boolean sim) {
        }

        /// What the guard turned away in the last day.
        @JsonSerializable
        public record Guard(int bans_active, int intercepts_24h) {
        }

        /// The limits the website judges the load by.
        @JsonSerializable
        public record Thresholds(double inbound_spike_kbps, double latency_warn_ms, double tps_danger, double tps_warn) {
        }

        /// One measurement of the server taken a moment ago.
        @JsonSerializable
        public record Sample(double inbound_kbps, @Nullable Double latency_ms, int players, @Nullable Double tps) {
        }
    }

    /// An announcement. Its content is the Markdown source stored on the website.
    @JsonSerializable
    public record Announcement(
            int id,
            String title,
            @Nullable String summary,
            String content,
            @Nullable String type,
            String created_at,
            boolean is_pinned,
            boolean is_urgent) {
    }

    /// One entry of the server changelog. Its content is the Markdown source.
    @JsonSerializable
    public record ChangelogEntry(
            int id,
            String title,
            @Nullable String summary,
            String content,
            @Nullable String version,
            @Nullable String kind_label,
            @Nullable String released_at) {
    }

    /// The changelog feed together with the content version of the website.
    @JsonSerializable
    public record Changelog(@Nullable String version, List<ChangelogEntry> entries) {
    }

    /// One published version of the server modpack. The website keeps a permanent link per version.
    @JsonSerializable
    public record PackVersion(
            int id,
            String name,
            @Nullable String mc_version,
            @Nullable String note,
            long bytes,
            @Nullable String size_label,
            @Nullable String created_at,
            @Nullable String url,
            @Nullable String path) {
    }

    /// One launcher build the website offers for download, such as a Windows installer or a shell script.
    @JsonSerializable
    public record LauncherFile(
            @Nullable String kind,
            @Nullable String url,
            @Nullable String path,
            @Nullable String size_label,
            @Nullable String note,
            @Nullable String platform_label) {
    }

    /// The launcher files published for one operating system.
    @JsonSerializable
    public record LauncherFileList(@Nullable List<LauncherFile> files) {
    }

    /// The launcher files published per operating system, absent when the website has none for that system.
    @JsonSerializable
    public record LauncherPlatforms(
            @Nullable LauncherFileList windows,
            @Nullable LauncherFileList linux) {
    }

    /// A launcher version as published on the website. [version] is the label players read.
    @JsonSerializable
    public record LauncherVersion(
            String version,
            @Nullable String released_at,
            @Nullable LauncherPlatforms platforms) {
    }

    /// The answer to an update check: whether the website has something newer than the version
    /// number the launcher sent as `current`.
    @JsonSerializable
    public record LauncherUpdate(boolean has_update, int current, @Nullable LauncherVersion latest) {
    }

    /// Returns the information the website shows about the server.
    public static SiteConfig fetchSiteConfig() throws IOException {
        return member("api/config", "config", SiteConfig.class);
    }

    /// Returns the live state of the survival server.
    public static ServerStatus fetchServerStatus() throws IOException {
        return member("api/server-status", "status", ServerStatus.class);
    }

    /// Returns the live state of the survival server together with the load measured for it.
    ///
    /// The load is what the website shows on its own home page, so the ticks per second and the
    /// latency it reports come from the same measurement.
    public static ServerLoad fetchServerLoad() throws IOException {
        return convert(request("api/lcsg/status"), ServerLoad.class);
    }

    /// Returns the state of the survival server as the website measures it for its own status card.
    ///
    /// The measurements of the last hour come with it, so the latency curve can be drawn without
    /// asking the website a second time.
    public static GuardStatus fetchGuardStatus() throws IOException {
        return convert(request("api/lcsg/status"), GuardStatus.class);
    }

    /// Returns the published announcements, the newest first.
    public static @Unmodifiable List<Announcement> fetchAnnouncements() throws IOException {
        return listMember("api/announcements", "announcements", Announcement.class);
    }

    /// Returns the announcement the website wants visitors to read right now,
    /// or `null` when no announcement is marked as a popup.
    public static @Nullable Announcement fetchPopupAnnouncement() throws IOException {
        JsonElement element = request("api/announcements/popup").get("announcement");
        if (element == null || element.isJsonNull()) return null;
        return convert(element, Announcement.class);
    }

    /// Returns the server changelog feed.
    public static Changelog fetchChangelog() throws IOException {
        return convert(request("api/changelog"), Changelog.class);
    }

    /// Returns the newest published version of the server modpack.
    public static PackVersion fetchLatestPack() throws IOException {
        return convert(request("api/pack.serve.new"), PackVersion.class);
    }

    /// Asks the website whether a newer launcher build has been published.
    ///
    /// [currentSeq] is the number this build was published as; the website compares it with its
    /// newest release and answers with `has_update`. A site that has no launcher build yet reports
    /// that as an error, whose text is meant to be shown to the player as it is.
    public static LauncherUpdate fetchLauncherUpdate(int currentSeq) throws IOException {
        LauncherUpdate update = convert(request("api/launcher/update?current=" + currentSeq), LauncherUpdate.class);
        LauncherVersion latest = update.latest();
        if (latest != null && StringUtils.isBlank(latest.version()))
            throw new IOException("The website sent a launcher version without a name");
        return update;
    }

    /// Returns the launcher builds the website offers for the operating system LCSL runs on.
    ///
    /// Empty when the website has nothing for this system, which is the case for operating systems
    /// the server does not ship a launcher for.
    public static @Unmodifiable List<LauncherFile> installers(LauncherVersion version) {
        LauncherPlatforms platforms = version.platforms();
        if (platforms == null) return List.of();

        LauncherFileList list = switch (OperatingSystem.CURRENT_OS) {
            case WINDOWS -> platforms.windows();
            case LINUX -> platforms.linux();
            default -> null;
        };
        if (list == null || list.files() == null) return List.of();

        List<@Nullable LauncherFile> files = list.files();
        files.removeIf(Objects::isNull);
        return Collections.unmodifiableList(files);
    }

    /// Returns the address the pack file is downloaded from.
    public static String downloadUrl(PackVersion pack) throws IOException {
        String location = absoluteDownloadUrl(pack.url(), pack.path());
        if (location == null) throw new IOException("The website sent no download address for the modpack");
        return location;
    }

    /// Returns the address [file] is downloaded from, or `null` when the website published none for it.
    public static @Nullable String installerUrl(LauncherFile file) {
        return absoluteDownloadUrl(file.url(), file.path());
    }

    /// Returns the full download address of a file the website published.
    ///
    /// The website only fills in a full URL when it knows its own public address, so a bare
    /// site path is resolved against [BASE_URL].
    private static @Nullable String absoluteDownloadUrl(@Nullable String url, @Nullable String path) {
        String location = StringUtils.isNotBlank(url) ? url : path;
        if (StringUtils.isBlank(location)) return null;
        if (location.startsWith("http://") || location.startsWith("https://")) return location;
        return BASE_URL + (location.startsWith("/") ? "" : "/") + location;
    }

    /// GETs [path] and returns the response as a JSON object.
    ///
    /// The website reports failures as an HTTP error status together with
    /// `{"error": "a message for the player"}`; that message is rethrown so the
    /// interface can show it as it is.
    private static JsonObject request(String path) throws IOException {
        String body;
        try {
            body = HttpRequest.GET(BASE_URL + "/" + path).getString();
        } catch (ResponseCodeException e) {
            throw new IOException(serverMessage(e.getData(), e.getResponseCode()), e);
        }
        return JsonUtils.fromNonNullJson(body, JsonObject.class);
    }

    /// Reads one member of a response and converts it to [type].
    private static <T> T member(String path, String name, Class<T> type) throws IOException {
        JsonElement element = require(request(path), name, path);
        return convert(element, type);
    }

    /// Reads one array member of a response and converts every item to [type].
    private static <T> @Unmodifiable List<T> listMember(String path, String name, Class<T> type) throws IOException {
        JsonElement element = require(request(path), name, path);
        List<@Nullable T> result = JsonUtils.GSON.fromJson(element, TypeToken.getParameterized(List.class, type).getType());
        if (result == null) return List.of();
        result.removeIf(Objects::isNull);
        return Collections.unmodifiableList(result);
    }

    private static JsonElement require(JsonObject root, String name, String path) throws IOException {
        JsonElement element = root.get(name);
        if (element == null) throw new IOException("Unexpected response from " + path);
        return element;
    }

    private static <T> T convert(JsonElement element, Class<T> type) throws IOException {
        T result = JsonUtils.GSON.fromJson(element, type);
        if (result == null) throw new IOException("Unexpected response type for " + type.getSimpleName());
        return result;
    }

    /// Copies a list the website sent into one that cannot be changed afterwards.
    ///
    /// @param list the list to copy, or `null` when the website sent none
    /// @return the copy, or `null` when there was no list
    private static <T> @Unmodifiable @Nullable List<T> unmodifiable(@Nullable List<@Nullable T> list) {
        if (list == null) return null;
        return list.stream().filter(Objects::nonNull).toList();
    }

    /// Turns the error text sent by the website into a message that can be shown to the player.
    private static String serverMessage(@Nullable String data, int responseCode) {
        if (data != null && !data.isBlank()) {
            try {
                JsonElement error = JsonUtils.fromNonNullJson(data, JsonObject.class).get("error");
                if (error != null && error.isJsonPrimitive()) return error.getAsString();
            } catch (RuntimeException e) {
                // Not a JSON object, or an error we cannot read; fall back to the status code.
            }
            return data;
        }
        return "HTTP " + responseCode;
    }
}
