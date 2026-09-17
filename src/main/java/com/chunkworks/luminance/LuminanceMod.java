/*
 * Luminance - dynamic light from the things that carry it.
 * Copyright (C) 2026 Rusty Shackleford and nfx
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.chunkworks.luminance;

import com.chunkworks.luminance.client.Engine;
import com.chunkworks.luminance.client.LightData;
import com.chunkworks.luminance.client.Providers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Dynamic light from the things that carry it. Client-only: the server has
 * no light to draw. The mod wires four things together and decides nothing
 * itself: the {@link Engine} that publishes a light field each tick, the
 * {@link Providers} that say what casts light, the {@link LightData} that
 * reads which items and mobs glow from resource packs, and two mixins that
 * make every block-light lookup see the field.
 */
@Mod(value = LuminanceMod.MOD_ID, dist = Dist.CLIENT)
public final class LuminanceMod {
    public static final String MOD_ID = "luminance";

    public LuminanceMod(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, LuminanceConfig.SPEC);
        modBus.addListener((RegisterClientReloadListenersEvent event) -> event.registerReloadListener(LightData.INSTANCE));
        Providers.registerBuiltIns();
        NeoForge.EVENT_BUS.addListener(Engine::onClientTick);
        NeoForge.EVENT_BUS.addListener(Engine::onFrame);
        NeoForge.EVENT_BUS.addListener(Engine::onLoggingOut);
    }
}
