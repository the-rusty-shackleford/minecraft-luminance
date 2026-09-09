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
package com.chunkworks.luminance.api;

import com.chunkworks.luminance.client.Providers;
import com.chunkworks.luminance.domain.Line;
import com.chunkworks.luminance.domain.Point;
import com.chunkworks.luminance.domain.Source;
import java.util.Collection;
import java.util.function.Function;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

/**
 * What another mod calls to make its things cast light. Client-side only:
 * call it from client setup, guarded by whether this mod is loaded if it is
 * optional to you. A source is a {@link Point} or a {@link Line} (a beam),
 * both with a luminance of 1..15; the engine asks the provider once per
 * client tick for every entity of the type within range and draws the
 * strongest light at each block.
 */
public final class Luminance {
    private Luminance() {}

    /**
     * effects: from now on, every entity of {@code type} in view casts the
     * sources {@code provider} returns for it (none for an empty
     * collection); several providers for one type add up
     */
    public static <T extends Entity> void forEntity(EntityType<T> type, Function<? super T, ? extends Collection<? extends Source>> provider) {
        Providers.forEntity(type, provider);
    }

    /**
     * effects: from now on, {@code item} casts {@code luminance} held in a
     * hand or lying on the ground, whatever the data files say; a block
     * item needs no call, it casts its block's light already<br>
     * throws: {@link IllegalArgumentException} if luminance is not 1..15
     */
    public static void forItem(Item item, int luminance) {
        Source.checkLuminance(luminance);
        Providers.forItem(item, luminance);
    }
}
