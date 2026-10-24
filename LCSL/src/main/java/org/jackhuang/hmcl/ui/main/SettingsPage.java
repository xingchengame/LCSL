/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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

import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.ui.DownloadRegionManager;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.upgrade.*;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.i18n.SupportedLocale;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class SettingsPage extends ScrollPane {
    @SuppressWarnings("FieldCanBeLocal")
    private final InvalidationListener updateListener;

    public SettingsPage() {
        this.setFitToWidth(true);

        VBox rootPane = new VBox(10);
        rootPane.setPadding(new Insets(10));
        this.setContent(rootPane);
        FXUtils.smoothScrolling(this);

        {
            ComponentList updatePaneList = new ComponentList();
            {
                var updatePane = new LineButton();
                updatePane.setLargeTitle(true);
                updatePane.setTitle(i18n("update"));
                updatePane.setLeading(SVG.UPDATE.createIcon());
                updatePane.setOnAction(e -> onUpdate());

                final StringProperty lblUpdateSubProperty = updatePane.subtitleProperty();

                {
                    updateListener = any -> {
                        if (UpdateChecker.isCheckingUpdate()) {
                            lblUpdateSubProperty.set(i18n("update.checking"));
                        } else if (UpdateChecker.isOutdated()) {
                            lblUpdateSubProperty.set(i18n("update.newest_version", UpdateChecker.getLatestVersion().version()));
                        } else if (UpdateChecker.errorProperty().get() != null) {
                            Throwable t = UpdateChecker.errorProperty().get();
                            String reason = StringUtils.isNotBlank(t.getLocalizedMessage())
                                    ? t.getLocalizedMessage()
                                    : t.getClass().getSimpleName();
                            lblUpdateSubProperty.set(i18n("update.check_failed", reason));
                        } else {
                            lblUpdateSubProperty.set(i18n("update.latest"));
                        }
                    };
                    UpdateChecker.latestVersionProperty().addListener(new WeakInvalidationListener(updateListener));
                    UpdateChecker.outdatedProperty().addListener(new WeakInvalidationListener(updateListener));
                    UpdateChecker.checkingUpdateProperty().addListener(new WeakInvalidationListener(updateListener));
                    UpdateChecker.errorProperty().addListener(new WeakInvalidationListener(updateListener));
                    updateListener.invalidated(null);
                }

                updatePaneList.getContent().add(updatePane);
            }

            {
                LineToggleButton disableAutoShowUpdateDialogPane = new LineToggleButton();
                disableAutoShowUpdateDialogPane.setTitle(i18n("update.disable_auto_show_update_dialog"));
                disableAutoShowUpdateDialogPane.setSubtitle(i18n("update.disable_auto_show_update_dialog.subtitle"));
                disableAutoShowUpdateDialogPane.selectedProperty().bindBidirectional(settings().disableAutoShowUpdateDialogProperty());
                updatePaneList.getContent().add(disableAutoShowUpdateDialogPane);

                var updateTitle = ComponentList.createComponentListTitle(i18n("update"));
                DownloadRegionManager.hideWhenDisabled(updateTitle, updatePaneList);
                rootPane.getChildren().addAll(updateTitle, updatePaneList);
            }

            {
                ComponentList languagePaneList = new ComponentList();

                {
                    var chooseLanguagePane = new LineSelectButton<SupportedLocale>();
                    chooseLanguagePane.setTitle(i18n("settings.launcher.language"));
                    chooseLanguagePane.setSubtitle(i18n("settings.take_effect_after_restart"));

                    SupportedLocale currentLocale = I18n.getLocale();
                    chooseLanguagePane.setNullSafeConverter(locale -> {
                        if (locale.isDefault())
                            return locale.getDisplayName(currentLocale);
                        else if (locale.isSameLanguage(currentLocale))
                            return locale.getDisplayName(locale);
                        else
                            return locale.getDisplayName(currentLocale) + " - " + locale.getDisplayName(locale);
                    });
                    chooseLanguagePane.setItems(SupportedLocale.getSupportedLocales());
                    chooseLanguagePane.valueProperty().bindBidirectional(settings().languageProperty());

                    languagePaneList.getContent().add(chooseLanguagePane);

                }

                rootPane.getChildren().addAll(ComponentList.createComponentListTitle(i18n("settings.launcher.language")), languagePaneList);
            }
        }
    }

    private void onUpdate() {
        RemoteVersion target = UpdateChecker.getLatestVersion();
        if (target == null) {
            return;
        }
        UpdateHandler.updateFrom(target);
    }

}
