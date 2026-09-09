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

import net.neoforged.neoforge.common.ModConfigSpec;

/** The player's own switches: {@code config/luminance-client.toml}. */
public final class LuminanceConfig {
    private LuminanceConfig() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("Dynamic light at all. Off leaves the world lit as vanilla lights it.")
            .define("enabled", true);
    public static final ModConfigSpec.IntValue RANGE = BUILDER
            .comment("How far from the camera, in blocks, a source is still considered.")
            .defineInRange("range", 64, 8, 256);
    public static final ModConfigSpec.IntValue MAX_SOURCES = BUILDER
            .comment("The most sources lit at once; the nearest win. Each costs a little in every chunk it touches.")
            .defineInRange("maxSources", 64, 1, 512);
    public static final ModConfigSpec.BooleanValue HELD_ITEMS = BUILDER
            .comment("A torch (or any glowing item) in a hand lights its holder's surroundings.")
            .define("heldItems", true);
    public static final ModConfigSpec.BooleanValue DROPPED_ITEMS = BUILDER
            .comment("A glowing item on the ground glows.")
            .define("droppedItems", true);
    public static final ModConfigSpec.BooleanValue BURNING = BUILDER
            .comment("Anything on fire lights its surroundings.")
            .define("burning", true);
    public static final ModConfigSpec.BooleanValue ENTITIES = BUILDER
            .comment("Mobs and projectiles listed in luminance/entities.json glow, and anything a mod registers.")
            .define("entities", true);

    public static final ModConfigSpec SPEC = BUILDER.build();
}
