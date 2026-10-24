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
package org.jackhuang.hmcl.ui;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.setting.DownloadRegion;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.LineSelectButton;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.util.FXThread;
import org.jetbrains.annotations.NotNullByDefault;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.jackhuang.hmcl.setting.SettingsManager.userState;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Applies the download restriction that comes from the selected download region.
@NotNullByDefault
public final class DownloadRegionManager {
    private DownloadRegionManager() {
    }

    /// Returns whether the selected download region turns off online downloading.
    public static boolean isDownloadDisabled() {
        return settings().isDownloadDisabled();
    }

    /// Returns the observable form of [isDownloadDisabled()].
    public static BooleanBinding downloadDisabledProperty() {
        return Bindings.createBooleanBinding(DownloadRegionManager::isDownloadDisabled,
                settings().downloadRegionProperty());
    }

    /// Keeps [nodes] out of the interface while downloading is disabled.
    @FXThread
    public static void hideWhenDisabled(Node... nodes) {
        BooleanBinding shown = downloadDisabledProperty().not();
        for (Node node : nodes) {
            node.visibleProperty().bind(shown);
            node.managedProperty().bind(shown);
        }
    }

    /// Tells the user that downloading is unavailable in the selected region.
    ///
    /// @return whether the caller may start downloading
    @FXThread
    public static boolean ensureDownloadAllowed() {
        if (!isDownloadDisabled())
            return true;
        Controllers.dialog(i18n("download.china"), i18n("download"), MessageDialogPane.MessageType.WARNING);
        return false;
    }

    /// Asks the user to pick the download region, with Chinese Mainland preselected.
    ///
    /// The prompt is shown only once per user; the picked region is written to the launcher settings.
    @FXThread
    public static void promptForRegion() {
        if (userState().downloadRegionPromptVersionProperty().get() >= 1)
            return;

        var regionButton = new LineSelectButton<DownloadRegion>();
        regionButton.setTitle(i18n("settings.launcher.download_region"));
        regionButton.setNullSafeConverter(region -> switch (region) {
            case CHINA_MAINLAND -> i18n("settings.launcher.download_region.china_mainland");
            case OTHER -> i18n("settings.launcher.download_region.other");
        });
        regionButton.setDescriptionConverter(region -> region == DownloadRegion.CHINA_MAINLAND
                ? i18n("settings.launcher.download_region.china_mainland.desc")
                : null);
        regionButton.setItems(DownloadRegion.values());
        regionButton.setValue(DownloadRegion.CHINA_MAINLAND);

        VBox body = new VBox(10, new Label(i18n("download.region.hint")), regionButton);
        body.setPadding(new Insets(20, 20, 0, 20));

        JFXDialogLayout pane = new JFXDialogLayout();
        pane.setHeading(new Label(i18n("download.region.title")));
        pane.setBody(body);

        JFXButton okButton = new JFXButton(i18n("button.ok"));
        okButton.getStyleClass().add("dialog-accept");
        okButton.setOnAction(e -> {
            settings().downloadRegionProperty().set(regionButton.getValue());
            userState().downloadRegionPromptVersionProperty().set(1);
            pane.fireEvent(new DialogCloseEvent());
        });
        pane.setActions(okButton);

        Controllers.dialog(pane);
    }
}
