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
package org.jackhuang.hmcl.ui.main;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Circle;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.io.HttpRequest;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Loads the Minecraft face of a player for the badges of the guard card.
///
/// Faces come from a public avatar service reachable from China, and every face is cached
/// on disk under the launcher data directory, so a face is downloaded once at most. A face
/// that cannot be loaded never breaks the badge: its view simply stays empty.
@NotNullByDefault
final class PlayerAvatar {

    /// The side of a downloaded face, in pixels. Avatar services render sharp faces at this size.
    private static final int FACE_SIZE = 32;

    /// The folder the downloaded faces are cached in.
    private static final Path CACHE_DIR = Metadata.HMCL_LOCAL_HOME.resolve("cache").resolve("avatars");

    /// The services that render the face of a player, tried in order. One `%s` is the player name.
    private static final @Unmodifiable List<String> SERVICES = List.of(
            "https://cravatar.eu/avatar/%s/" + FACE_SIZE,
            "https://minotar.net/avatar/%s/" + FACE_SIZE);

    /// The players whose face failed to load during this run, so later refreshes stop asking.
    private static final Set<String> failed = ConcurrentHashMap.newKeySet();

    private PlayerAvatar() {
    }

    /// Builds a round face view of [name], [size] pixels wide.
    ///
    /// The view starts empty and fills in later when the face arrives, either from the disk
    /// cache or from the website; if no service answers, it stays empty forever.
    ///
    /// @param name the name of the player whose face is shown
    /// @param size the side of the view, in pixels
    /// @return the view, clipped to a circle
    static ImageView face(String name, int size) {
        var view = new ImageView();
        view.setFitWidth(size);
        view.setFitHeight(size);
        view.setPreserveRatio(true);
        view.setSmooth(false);
        view.setClip(new Circle(size / 2.0, size / 2.0, size / 2.0));

        Path cached = CACHE_DIR.resolve(sanitize(name) + ".png");
        if (Files.isRegularFile(cached)) {
            Image image = load(cached);
            if (image != null) {
                view.setImage(image);
                return view;
            }
        }

        if (failed.contains(name))
            return view;

        // The download runs away from the interface thread and lands on the view when it is done.
        Task.supplyAsync(() -> download(name, cached))
                .whenComplete(Schedulers.javafx(), (image, exception) -> {
                    if (image != null) {
                        view.setImage(image);
                    } else {
                        failed.add(name);
                    }
                })
                .start();
        return view;
    }

    /// Downloads the face of [name] into [target] from the first service that answers.
    ///
    /// @param name the name of the player whose face is downloaded
    /// @param target the file the face is stored in, overwritten when it already exists
    /// @return the downloaded face, or `null` when every service failed
    private static @Nullable Image download(String name, Path target) {
        for (String service : SERVICES) {
            try {
                HttpURLConnection connection = HttpRequest.GET(String.format(service, name)).createConnection();
                int code = connection.getResponseCode();
                if (code / 100 != 2)
                    continue;

                Files.createDirectories(target.getParent());
                try (InputStream stream = connection.getInputStream()) {
                    Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);
                }

                Image image = load(target);
                if (image != null)
                    return image;
            } catch (IOException e) {
                LOG.warning("Failed to download the face of " + name, e);
            }
        }
        return null;
    }

    /// Loads a face from the disk cache.
    ///
    /// @param file the cached face
    /// @return the image, or `null` when the file is not a readable image
    private static @Nullable Image load(Path file) {
        try {
            Image image = new Image(file.toUri().toString(), FACE_SIZE, FACE_SIZE, true, false, false);
            return image.isError() ? null : image;
        } catch (IllegalArgumentException e) {
            // The name the website sent is not a URL the image loader accepts.
            return null;
        }
    }

    /// Turns a player name into a safe file name.
    ///
    /// Official names are already safe, but the server may send more, so everything outside
    /// the letters, the digits, the underline and the hyphen becomes an underline.
    private static String sanitize(String name) {
        return name.replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
