/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.JFXHyperlink;
import org.jackhuang.hmcl.upgrade.RemoteVersion;

import java.util.StringJoiner;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Offers the launcher build the website publishes as the newest one.
///
/// The website keeps a download per platform, so this dialog lists the ones that fit the machine
/// LCSL runs on and lets the player open them; installing is up to the player.
public final class UpgradeDialog extends JFXDialogLayout {

    /// Shows [version] together with the installers published for this operating system.
    public UpgradeDialog(RemoteVersion version) {
        maxWidthProperty().bind(Controllers.getDecorator().contentWidthProperty().multiply(0.7));
        maxHeightProperty().bind(Controllers.getDecorator().contentHeightProperty().multiply(0.7));

        Label heading = new Label(i18n("update.found"));
        heading.setPadding(new Insets(0, 0, 8, 0));
        setHeading(heading);

        VBox body = new VBox(8);
        body.setPadding(new Insets(8));
        {
            var newest = new Label(i18n("update.newest_version", version.version()));
            newest.setWrapText(true);
            body.getChildren().add(newest);

            if (version.artifacts().isEmpty()) {
                var none = new Label(i18n("update.files.none"));
                none.setWrapText(true);
                body.getChildren().add(none);
            } else {
                body.getChildren().add(new Label(i18n("update.files")));
                for (RemoteVersion.Artifact artifact : version.artifacts()) {
                    JFXHyperlink link = new JFXHyperlink(describe(artifact));
                    link.setExternalLink(artifact.url());
                    body.getChildren().add(link);
                }
            }
        }
        setBody(body);

        JFXHyperlink downloadsPage = new JFXHyperlink(i18n("update.downloads_page"));
        downloadsPage.setExternalLink(Metadata.LAUNCHER_DOWNLOADS_URL);

        JFXButton okButton = new JFXButton(i18n("button.ok"));
        okButton.getStyleClass().add("dialog-accept");
        okButton.setOnAction(e -> fireEvent(new DialogCloseEvent()));

        setActions(downloadsPage, okButton);
        onEscPressed(this, okButton::fire);
    }

    /// Writes one installer row: what the file is, how big it is, and what the website says about it.
    private static String describe(RemoteVersion.Artifact artifact) {
        StringJoiner text = new StringJoiner(" · ");
        text.add(artifact.label());
        if (artifact.sizeLabel() != null && !artifact.sizeLabel().isBlank()) text.add(artifact.sizeLabel());
        if (artifact.note() != null && !artifact.note().isBlank()) text.add(artifact.note());
        return text.toString();
    }
}
