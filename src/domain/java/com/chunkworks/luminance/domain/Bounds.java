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
 * An inclusive box of block positions.
 *
 * <p>RI: min <= max on every axis.
 * AF: AF(minX..maxZ) = "every block (x, y, z) with minX <= x <= maxX, and so for y and z".
 */
public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public Bounds {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("min must not exceed max: " + minX + ".." + maxX + ", " + minY + ".." + maxY + ", " + minZ + ".." + maxZ);
        }
    }

    /** effects: returns the box of every block within {@code radius} blocks of the block containing the point, on each axis */
    public static Bounds around(double x, double y, double z, int radius) {
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);
        return new Bounds(bx - radius, by - radius, bz - radius, bx + radius, by + radius, bz + radius);
    }

    /**
     * requires: finite coordinates and luminance 1..15
     * effects: returns conservative bounds of block centres where rounded radial light is positive
     */
    public static Bounds litAround(double x, double y, double z, int luminance) {
        return new Bounds((int) Math.ceil(x - luminance), (int) Math.ceil(y - luminance),
                (int) Math.ceil(z - luminance), (int) Math.floor(x + luminance - 1),
                (int) Math.floor(y + luminance - 1), (int) Math.floor(z + luminance - 1));
    }

    /** effects: returns whether the block at {@code (x, y, z)} is in this box */
    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /** effects: returns the smallest box holding both */
    public Bounds union(Bounds other) {
        return new Bounds(Math.min(minX, other.minX), Math.min(minY, other.minY), Math.min(minZ, other.minZ),
                Math.max(maxX, other.maxX), Math.max(maxY, other.maxY), Math.max(maxZ, other.maxZ));
    }

    /** effects: returns the 16-block sections this box touches, as {@code (sx, sy, sz)} triples, each once */
    public java.util.List<int[]> sections() {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        for (int sx = minX >> 4; sx <= maxX >> 4; sx++) {
            for (int sy = minY >> 4; sy <= maxY >> 4; sy++) {
                for (int sz = minZ >> 4; sz <= maxZ >> 4; sz++) {
                    out.add(new int[] {sx, sy, sz});
                }
            }
        }
        return out;
    }
}
