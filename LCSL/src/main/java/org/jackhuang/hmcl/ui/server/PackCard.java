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
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.LineButton;
import org.jetbrains.annotations.NotNullByDefault;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// The card that offers the server modpack the website publishes.
///
/// Tapping the card downloads the newest published version and hands it to the ordinary install
/// wizard.
@NotNullByDefault
public final class PackCard extends ComponentList {

    private final LineButton item = new LineButton();

    private boolean loading;

    public PackCard() {
        item.setLeading(SVG.PACKAGE2.createIcon());
        item.setTitle(i18n("server.pack"));
        item.setSubtitle("--");
        getContent().setAll(item);

        refresh();
    }

    /// Reloads the modpack the website publishes.
    public void refresh() {
        if (loading)
            return;
        loading = true;

        Task.supplyAsync(LuCunAPI::fetchLatestPack)
                .whenComplete(Schedulers.javafx(), (pack, exception) -> {
                    loading = false;
                    if (pack == null) {
                        item.setSubtitle(i18n("server.pack.none"));
                        item.setOnAction(null);
                        return;
                    }

                    item.setTitle(pack.name());
                    item.setSubtitle(describe(pack));
                    item.setOnAction(e -> ServerPage.installPack(pack));
                })
                .start();
    }

    /// Makes the line under the name of the pack.
    ///
    /// The name is already the title of the card, so the line only tells which game the pack is
    /// for and how big it is.
    ///
    /// @param pack the published version of the pack
    /// @return the line under the name
    private static String describe(LuCunAPI.PackVersion pack) {
        StringBuilder result = new StringBuilder();
        if (pack.mc_version() != null && !pack.mc_version().isEmpty())
            result.append(pack.mc_version());
        if (pack.size_label() != null && !pack.size_label().isEmpty())
            result.append(result.length() > 0 ? " · " : "").append(pack.size_label());
        return result.length() > 0 ? result.toString() : pack.name();
    }
}
