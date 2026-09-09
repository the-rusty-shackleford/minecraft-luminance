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

import com.chunkworks.luminance.domain.Source;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Which items and mobs glow, from resource packs: every
 * {@code assets/<namespace>/luminance/items.json} and
 * {@code entities.json} in every pack, read bottom to top so a later pack
 * overrides an earlier one entry by entry. Each file maps an id to a
 * luminance, either a number or {@code {"luminance": 14, "underwater":
 * false}}; {@code underwater} (default true) says whether the light
 * survives being underwater -- a torch's does not, a sea lantern's does.
 * An unknown id is logged once and skipped, so a pack may name a mod's
 * item without requiring the mod.
 */
public final class LightData extends SimplePreparableReloadListener<LightData.Loaded> {
    private static final Logger LOG = LoggerFactory.getLogger("Luminance");
    public static final LightData INSTANCE = new LightData();

    /** What an item casts. */
    public record ItemLight(int luminance, boolean underwater) {}

    /** What a mob casts. */
    public record EntityLight(int luminance, boolean underwater) {}

    /** Both maps as read; immutable once built. */
    public record Loaded(Map<Item, ItemLight> items, Map<EntityType<?>, EntityLight> entities) {}

    private volatile Loaded loaded = new Loaded(Map.of(), Map.of());

    private LightData() {}

    /** effects: returns the data entry for {@code item}, or null */
    @Nullable
    public ItemLight item(Item item) {
        return loaded.items().get(item);
    }

    /** effects: returns the data entry for {@code type}, or null */
    @Nullable
    public EntityLight entity(EntityType<?> type) {
        return loaded.entities().get(type);
    }

    @Override
    protected Loaded prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<Item, ItemLight> items = new HashMap<>();
        Map<EntityType<?>, EntityLight> entities = new HashMap<>();
        for (String namespace : manager.getNamespaces()) {
            read(manager, ResourceLocation.fromNamespaceAndPath(namespace, "luminance/items.json"), (id, lum, wet) -> {
                Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
                if (item == null) {
                    LOG.info("luminance: no item {} to light; skipped", id);
                } else {
                    items.put(item, new ItemLight(lum, wet));
                }
            });
            read(manager, ResourceLocation.fromNamespaceAndPath(namespace, "luminance/entities.json"), (id, lum, wet) -> {
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
                if (type == null) {
                    LOG.info("luminance: no entity {} to light; skipped", id);
                } else {
                    entities.put(type, new EntityLight(lum, wet));
                }
            });
        }
        return new Loaded(Map.copyOf(items), Map.copyOf(entities));
    }

    @Override
    protected void apply(Loaded prepared, ResourceManager manager, ProfilerFiller profiler) {
        loaded = prepared;
        LOG.info("luminance: {} items and {} entity types glow", prepared.items().size(), prepared.entities().size());
    }

    private interface Entry {
        void accept(ResourceLocation id, int luminance, boolean underwater);
    }

    /** effects: feeds every entry of every copy of {@code file}, lowest pack first, to {@code out}; a bad entry is logged and skipped */
    private static void read(ResourceManager manager, ResourceLocation file, Entry out) {
        for (Resource resource : manager.getResourceStack(file)) {
            try (Reader reader = resource.openAsReader()) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                    ResourceLocation id = ResourceLocation.tryParse(e.getKey());
                    if (id == null) {
                        LOG.warn("luminance: {} in {} ({}) is not an id; skipped", e.getKey(), file, resource.sourcePackId());
                        continue;
                    }
                    int luminance;
                    boolean underwater = true;
                    if (e.getValue().isJsonPrimitive()) {
                        luminance = e.getValue().getAsInt();
                    } else {
                        JsonObject o = e.getValue().getAsJsonObject();
                        luminance = o.get("luminance").getAsInt();
                        underwater = !o.has("underwater") || o.get("underwater").getAsBoolean();
                    }
                    if (luminance < 1 || luminance > Source.MAX_LUMINANCE) {
                        LOG.warn("luminance: {} in {} ({}) has luminance {}, not 1..15; skipped", e.getKey(), file, resource.sourcePackId(), luminance);
                        continue;
                    }
                    out.accept(id, luminance, underwater);
                }
            } catch (IOException | RuntimeException ex) {
                LOG.error("luminance: cannot read {} ({}): {}", file, resource.sourcePackId(), ex.toString());
            }
        }
    }
}
