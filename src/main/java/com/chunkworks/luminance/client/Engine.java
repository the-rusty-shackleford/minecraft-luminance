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
import com.chunkworks.luminance.domain.Bounds;
import com.chunkworks.luminance.domain.Field;
import com.chunkworks.luminance.domain.Source;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Publishes the world's dynamic light before rendering, at most 30 times per second: gathers every
 * source the providers report for the entities in range, settles them into
 * a {@link Field}, and re-meshes the sections whose light changed. The
 * mixins read the published field from whatever thread draws or meshes,
 * which is why it is one immutable object behind a volatile reference and
 * never edited in place.
 *
 * <p>Cost, stated: one pass over the level's rendered entities per sample,
 * one bounded query per block-light lookup (at most {@code maxSources}
 * box tests), and a section rebuild for every 16-block section a source
 * entered or left this sample.
 */
public final class Engine {
    private Engine() {}

    private static volatile Field field = Field.EMPTY;
    private static final boolean METRICS = Boolean.getBoolean("luminance.metrics");
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(Engine.class);
    private static long metricTicks, metricNanos, metricSections;
    private static int peakSections;
    private static long lastSample;
    private static final long SAMPLE_NANOS = 1_000_000_000L / 30;
    private static final LongSet sections = new LongOpenHashSet();

    /** effects: returns the dynamic light at the block {@code (x, y, z)}, 0 when none; safe from any thread */
    public static int lightAt(int x, int y, int z) {
        Field f = field;
        return f.isEmpty() ? 0 : f.lightAt(x, y, z);
    }

    /** effects: returns the dynamic light at {@code pos}; safe from any thread */
    public static int lightAt(BlockPos pos) {
        return lightAt(pos.getX(), pos.getY(), pos.getZ());
    }

    /** effects: returns the field last published */
    public static Field field() {
        return field;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !LuminanceConfig.ENABLED.get()) publish(mc, Field.EMPTY);
    }

    /** effects: samples the position actually being rendered, coalescing multiple simulation ticks into one light update */
    public static void onFrame(net.neoforged.neoforge.client.event.RenderFrameEvent.Pre event) {
        long now = System.nanoTime();
        if (now - lastSample < SAMPLE_NANOS) return;
        lastSample = now;
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || !LuminanceConfig.ENABLED.get()) {
            publish(mc, Field.EMPTY);
            return;
        }
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        int range = LuminanceConfig.RANGE.get();
        List<Source> sources = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (entity.distanceToSqr(eye) > (double) range * range) {
                continue;
            }
            Providers.collect(entity, sources, partialTick);
        }
        publish(mc, Field.of(sources, LuminanceConfig.MAX_SOURCES.get(), eye.x, eye.y, eye.z));
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        field = Field.EMPTY;
        lastSample = 0;
        sections.clear();
    }

    /** effects: makes {@code next} the field and re-meshes every section whose light it changed */
    private static void publish(Minecraft mc, Field next) {
        Field previous = field;
        if (next == previous || (next.isEmpty() && previous.isEmpty())) {
            return;
        }
        field = next;
        if (mc.level == null) {
            return;
        }
        long started = METRICS ? System.nanoTime() : 0;
        sections.clear();
        for (Bounds box : next.dirtyAgainst(previous)) {
            for (int sx = box.minX() >> 4; sx <= box.maxX() >> 4; sx++) {
                for (int sy = box.minY() >> 4; sy <= box.maxY() >> 4; sy++) {
                    if (sy < mc.level.getMinSection() || sy >= mc.level.getMaxSection()) continue;
                    for (int sz = box.minZ() >> 4; sz <= box.maxZ() >> 4; sz++) {
                        if (sections.add(SectionPos.asLong(sx, sy, sz))) {
                            mc.levelRenderer.setSectionDirty(sx, sy, sz);
                        }
                    }
                }
            }
        }
        if (METRICS) {
            metricNanos += System.nanoTime() - started;
            metricSections += sections.size();
            peakSections = Math.max(peakSections, sections.size());
            if (++metricTicks % 100 == 0) {
                LOG.info("luminance-metrics: samples={} sources={} sectionsMean={} sectionsPeak={} schedulingMicros={}",
                        metricTicks, next.sources().size(), metricSections / 100.0, peakSections, metricNanos / 100000.0);
                metricNanos = metricSections = 0;
                peakSections = 0;
            }
        }
    }
}
