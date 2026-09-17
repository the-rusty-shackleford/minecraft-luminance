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
package com.chunkworks.luminance.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Every dynamic light in the world at one instant, settled to sixteenths of a block,
 * and the light they cast together: the strongest at each block, never a
 * sum, as the game's own lights combine.
 *
 * <p>The engine publishes these at a bounded cadence before rendered frames and the renderer
 * reads it from any thread -- chunk meshing runs on workers -- so a field
 * is immutable and its queries allocate nothing.
 *
 * <p>RI: {@code sources} holds settled sources, no two equal, at most
 *     {@code cap} of them; {@code bounds[i] == sources.get(i).bounds()}.
 * AF: AF(sources) = "the dynamic light at any block is the greatest
 *     {@code lightAt} over the sources, 0 with none".
 */
public final class Field {
    /** No light anywhere. */
    public static final Field EMPTY = new Field(List.of());

    private final List<Source> sources;
    private final Bounds[] bounds;

    private Field(List<Source> sources) {
        this.sources = List.copyOf(sources);
        this.bounds = new Bounds[this.sources.size()];
        for (int i = 0; i < this.bounds.length; i++) {
            this.bounds[i] = this.sources.get(i).bounds();
        }
        assert this.sources.size() == new HashSet<>(this.sources).size() : "duplicate sources";
    }

    /**
     * effects: returns the field of {@code sources}, each settled to its
     * sub-block position, duplicates merged, keeping at most {@code cap} of them -- the
     * ones nearest {@code (nearX, nearY, nearZ)}, the viewer, since a light
     * far away matters least<br>
     * throws: {@link IllegalArgumentException} if {@code cap} < 0
     */
    public static Field of(Collection<? extends Source> sources, int cap, double nearX, double nearY, double nearZ) {
        if (cap < 0) {
            throw new IllegalArgumentException("cap must not be negative: " + cap);
        }
        Set<Source> settled = new HashSet<>();
        for (Source s : sources) {
            settled.add(s.settled());
        }
        List<Source> kept = new ArrayList<>(settled);
        if (kept.size() > cap) {
            kept.sort(Comparator.comparingDouble(s -> distance2(s, nearX, nearY, nearZ)));
            kept = new ArrayList<>(kept.subList(0, cap));
        }
        return kept.isEmpty() ? EMPTY : new Field(kept);
    }

    private static double distance2(Source s, double x, double y, double z) {
        Bounds b = s.bounds();
        double cx = (b.minX() + b.maxX() + 1) / 2.0;
        double cy = (b.minY() + b.maxY() + 1) / 2.0;
        double cz = (b.minZ() + b.maxZ() + 1) / 2.0;
        return (cx - x) * (cx - x) + (cy - y) * (cy - y) + (cz - z) * (cz - z);
    }

    /** effects: returns the settled sources, in no particular order */
    public List<Source> sources() {
        return sources;
    }

    /** effects: returns whether no source is in the field */
    public boolean isEmpty() {
        return sources.isEmpty();
    }

    /** effects: returns the dynamic light at the block with minimum corner {@code (x, y, z)}: the strongest source's, 0..15 */
    public int lightAt(int x, int y, int z) {
        int best = 0;
        for (int i = 0; i < bounds.length; i++) {
            if (bounds[i].contains(x, y, z)) {
                int light = sources.get(i).lightAt(x, y, z);
                if (light > best) {
                    best = light;
                    if (best == Source.MAX_LUMINANCE) {
                        break;
                    }
                }
            }
        }
        return best;
    }

    /**
     * effects: returns the boxes of blocks whose light may differ between
     * {@code previous} and this field: the bounds of every source in one
     * and not the other. A source that stayed at its quantized position changes nothing.
     */
    public List<Bounds> dirtyAgainst(Field previous) {
        List<Bounds> dirty = new ArrayList<>();
        Set<Source> mine = new HashSet<>(sources);
        Set<Source> theirs = new HashSet<>(previous.sources);
        for (Source s : sources) {
            if (!theirs.contains(s)) {
                dirty.add(s.bounds());
            }
        }
        for (Source s : previous.sources) {
            if (!mine.contains(s)) {
                dirty.add(s.bounds());
            }
        }
        return dirty;
    }
}
