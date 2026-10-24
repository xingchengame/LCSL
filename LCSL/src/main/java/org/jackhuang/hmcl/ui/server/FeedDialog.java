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

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.server.LuCunAPI;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.HTMLRenderer;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.decorator.Decorator;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Jsoup;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Shows one item of the website's feed: the Markdown body the website stores,
/// under its title and one-line subtitle.
@NotNullByDefault
public final class FeedDialog extends JFXDialogLayout {

    /// Creates a dialog for one feed item.
    ///
    /// @param title    the title of the item
    /// @param subtitle the one-line subtitle of the item, may be empty
    /// @param content  the Markdown body of the item
    public FeedDialog(String title, String subtitle, String content) {
        setHeading(new HBox(new Label(title)));

        VBox box = new VBox(8);
        box.setPadding(new Insets(8));

        if (!subtitle.isEmpty()) {
            var subtitleLabel = new Label(subtitle);
            subtitleLabel.setWrapText(true);
            box.getChildren().add(subtitleLabel);
        }

        var body = new HTMLRenderer(Controllers::openUriOrCopy)
                .appendNode(Jsoup.parse(StringUtils.convertToHtml(content, false), LuCunAPI.BASE_URL))
                .mergeLineBreaks()
                .render();
        body.getStyleClass().add("addon-changelog");

        var scrollPane = new ScrollPane(body);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        FXUtils.setOverflowHidden(scrollPane, 8);
        box.getChildren().add(scrollPane);
        VBox.setVgrow(scrollPane, Priority.SOMETIMES);

        setBody(box);

        var closeButton = new JFXButton(i18n("button.ok"));
        closeButton.getStyleClass().add("dialog-accept");
        closeButton.setOnAction(e -> fireEvent(new DialogCloseEvent()));
        setActions(closeButton);
        FXUtils.onEscPressed(this, closeButton::fire);

        @Nullable Decorator decorator = Controllers.findDecorator();
        if (decorator != null) {
            prefWidthProperty().bind(decorator.contentWidthProperty().multiply(0.7));
            prefHeightProperty().bind(decorator.contentHeightProperty().multiply(0.8));
        }
    }
}
