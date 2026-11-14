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
package org.jackhuang.hmcl.ui.instances;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// The standalone screenshots panel, following the selected game instance.
///
/// The screenshots themselves are listed by [ScreenshotsPage]; this frame only turns the
/// panel into a page the navigator can show on its own, and retargets it whenever the
/// player picks another instance.
@NotNullByDefault
public final class ScreenshotsFrame extends StackPane implements DecoratorPage {

    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(
            State.fromTitle(i18n("screenshots.manage")));

    public ScreenshotsFrame() {
        // Rebuild the optional whenever the selected instance changes; the screenshots
        // page listens to this and reloads its folder.
        var instanceContext = Bindings.createObjectBinding(
                () -> {
                    @Nullable HMCLGameInstance instance = GameDirectoryManager.getSelectedInstance();
                    return instance != null
                            ? HMCLGameInstance.Optional.of(instance)
                            : HMCLGameInstance.Optional.empty(GameDirectoryManager.getSelectedRepository());
                },
                GameDirectoryManager.selectedInstanceProperty());

        Node page = new ScreenshotsPage(instanceContext);
        page.getStyleClass().add("gray-background");
        getChildren().add(page);
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }
}
