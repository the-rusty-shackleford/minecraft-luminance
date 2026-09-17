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

/**
 * A light at a point: a held torch, a burning mob, a glowing item.
 *
 * <p>RI: coordinates finite; luminance 1..15.
 * AF: AF(x, y, z, luminance) = "a source of {@code luminance} at (x, y, z)".
 */
public record Point(double x, double y, double z, int luminance) implements Source {
    public Point {
        Source.checkLuminance(luminance);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("a point must be finite: " + x + ", " + y + ", " + z);
        }
    }

    @Override
    public Bounds bounds() {
        return Bounds.litAround(x, y, z, luminance);
    }

    @Override
    public int lightAt(int bx, int by, int bz) {
        double dx = bx + 0.5 - x;
        double dy = by + 0.5 - y;
        double dz = bz + 0.5 - z;
        return Source.falloff(luminance, Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    @Override
    public Point settled() {
        return new Point(Source.subBlock(x), Source.subBlock(y), Source.subBlock(z), luminance);
    }
}
