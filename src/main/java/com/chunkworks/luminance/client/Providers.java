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
package com.chunkworks.luminance.client;

import com.chunkworks.luminance.LuminanceConfig;
import com.chunkworks.luminance.domain.Point;
import com.chunkworks.luminance.domain.Source;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.BiFunction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * What casts light: the built-in rules -- a glowing item in a hand or on
 * the ground, anything on fire, the mobs the data files name -- and the
 * providers other mods register per entity type. Each is a pure function
 * of the entity, asked at the engine's bounded frame cadence.
 */
public final class Providers {
    private Providers() {}

    /** What a burning thing casts: the fire block's light, less a little, since it is a small fire. */
    private static final int BURNING = 10;

    private static final Map<EntityType<?>, List<BiFunction<Entity, Float, Collection<? extends Source>>>> BY_TYPE = new HashMap<>();
    private static final Map<Item, Integer> ITEM_OVERRIDES = new HashMap<>();

    /** effects: registers {@code provider} for {@code type} (see the api) */
    @SuppressWarnings("unchecked")
    public static <T extends Entity> void forEntity(EntityType<T> type, Function<? super T, ? extends Collection<? extends Source>> provider) {
        forEntityInterpolated(type, (e, partial) -> provider.apply(e));
    }

    /** effects: registers a provider accepting the rendered partial tick */
    @SuppressWarnings("unchecked")
    public static <T extends Entity> void forEntityInterpolated(EntityType<T> type,
            BiFunction<? super T, Float, ? extends Collection<? extends Source>> provider) {
        BY_TYPE.computeIfAbsent(type, t -> new ArrayList<>()).add((e, partial) -> provider.apply((T) e, partial));
    }

    /** effects: registers a luminance for {@code item} that beats the data files' */
    public static void forItem(Item item, int luminance) {
        ITEM_OVERRIDES.put(item, luminance);
    }

    /** effects: registers nothing yet; the built-in rules are code in {@link #collect}, kept here so the entry point reads as one list */
    public static void registerBuiltIns() {}

    /**
     * effects: returns the light {@code stack} casts, 0 for none: a mod's
     * registration, else the data files' entry (0 when {@code underwater}
     * and the entry says the light does not survive it), else a block item's
     * own block light
     */
    public static int luminanceOf(ItemStack stack, boolean underwater) {
        if (stack.isEmpty()) {
            return 0;
        }
        Item item = stack.getItem();
        Integer override = ITEM_OVERRIDES.get(item);
        if (override != null) {
            return override;
        }
        LightData.ItemLight data = LightData.INSTANCE.item(item);
        if (data != null) {
            return underwater && !data.underwater() ? 0 : data.luminance();
        }
        if (item instanceof BlockItem blockItem) {
            return blockItem.getBlock().defaultBlockState().getLightEmission();
        }
        return 0;
    }

    /** effects: appends every source {@code entity} casts this tick to {@code out} */
    public static void collect(Entity entity, List<Source> out) {
        collect(entity, out, 1.0f);
    }

    /** effects: appends sources at the rendered position between the entity's last and current tick */
    public static void collect(Entity entity, List<Source> out, float partialTick) {
        if (entity.isSpectator()) {
            return;
        }
        double x = Mth.lerp(partialTick, entity.xOld, entity.getX());
        double y = Mth.lerp(partialTick, entity.yOld, entity.getY());
        double z = Mth.lerp(partialTick, entity.zOld, entity.getZ());
        if (LuminanceConfig.BURNING.get() && entity.isOnFire()) {
            out.add(new Point(x, y + entity.getBbHeight() * 0.5, z, BURNING));
        }
        if (LuminanceConfig.HELD_ITEMS.get() && entity instanceof LivingEntity living) {
            boolean underwater = living.isUnderWater();
            int held = Math.max(luminanceOf(living.getMainHandItem(), underwater), luminanceOf(living.getOffhandItem(), underwater));
            if (held > 0) {
                out.add(new Point(x, y + living.getEyeHeight() - 0.2, z, held));
            }
        }
        if (LuminanceConfig.DROPPED_ITEMS.get() && entity instanceof ItemEntity dropped) {
            int light = luminanceOf(dropped.getItem(), dropped.isUnderWater());
            if (light > 0) {
                out.add(new Point(x, y + 0.25, z, light));
            }
        }
        if (LuminanceConfig.ENTITIES.get()) {
            LightData.EntityLight data = LightData.INSTANCE.entity(entity.getType());
            if (data != null && !(entity.isUnderWater() && !data.underwater())) {
                out.add(new Point(x, y + entity.getBbHeight() * 0.5, z, data.luminance()));
            }
            List<BiFunction<Entity, Float, Collection<? extends Source>>> providers = BY_TYPE.get(entity.getType());
            if (providers != null) {
                for (BiFunction<Entity, Float, Collection<? extends Source>> provider : providers) {
                    out.addAll(provider.apply(entity, partialTick));
                }
            }
        }
    }
}
