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

import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.modpack.ModpackConfiguration;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Locale;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Tells which instances are the LuCUNserver modpack and where its server lives.
///
/// The launcher joins the server on its own when the player starts the LuCUNserver modpack,
/// so that nobody has to type the address by hand.
@NotNullByDefault
public final class LuCunServer {

    /// The address the survival server is reachable at.
    public static final String DEFAULT_ADDRESS = "lucunserver.xyz";

    /// The name the website publishes the modpack under.
    public static final String MODPACK_NAME = "LuCUNserver";

    /// The address the website published, or `null` when the launcher has not heard of one yet.
    private static volatile @Nullable String address;

    private LuCunServer() {
    }

    /// Remembers the address the website published, so the launcher joins the right one.
    ///
    /// @param address the published address, or `null` to keep the one already known
    public static void rememberAddress(@Nullable String address) {
        if (address != null && !address.isBlank())
            LuCunServer.address = address.trim();
    }

    /// Returns the address of the survival server.
    ///
    /// @return the published address, or [DEFAULT_ADDRESS] when the website has not told us one
    public static String serverAddress() {
        String current = address;
        return current != null ? current : DEFAULT_ADDRESS;
    }

    /// Returns whether the instance is the LuCUNserver modpack.
    ///
    /// An instance counts as the LuCUNserver modpack when it was installed under that name,
    /// or when the modpack it came from still carries that name.
    ///
    /// @param instance       the instance about to be started
    /// @param configuration  the modpack configuration already read for the instance, or `null`
    /// @return whether the instance should join the LuCUNserver server on its own
    public static boolean isLuCunInstance(HMCLGameInstance instance, @Nullable ModpackConfiguration<?> configuration) {
        if (instance.getId().toString().toLowerCase(Locale.ROOT).contains(MODPACK_NAME.toLowerCase(Locale.ROOT)))
            return true;

        if (configuration == null) {
            try {
                configuration = instance.readModpackConfiguration();
            } catch (IOException e) {
                LOG.warning("Failed to read the modpack configuration of " + instance.getId(), e);
                return false;
            }
        }

        return configuration != null && MODPACK_NAME.equalsIgnoreCase(configuration.getName().trim());
    }
}
