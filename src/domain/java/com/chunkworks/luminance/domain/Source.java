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
 * A dynamic light: something in the world that casts light around itself
 * without being a block -- a torch in a hand, a burning mob, a headlamp's
 * beam. A source has a luminance, 1 to 15 like a block's, and casts
 * {@code luminance - distance} at a block, floored at zero, with the
 * distance measured from the block's centre to the source (a point) or to
 * the nearest point of it (a line). That is the game's own falloff of one
 * level per block, along a straight line instead of the block grid.
 *
 * <p>Sources are compared by value: the engine tells a source that moved
 * from one that stayed by equality. {@link #settled()} quantizes positions
 * to a sixteenth of a block, preserving small movements without rebuilding
 * for floating-point noise. The client publishes fields before rendering at a bounded cadence.
 */
public sealed interface Source permits Point, Line {
    /** The brightest a source can be: a block's full light. */
    int MAX_LUMINANCE = 15;

    /** effects: returns the source's luminance, 1..15 */
    int luminance();

    /** effects: returns the blocks this source can light at all, inclusive; a block outside gets 0 from it */
    Bounds bounds();

    /**
     * effects: returns the light this source casts at the block whose
     * minimum corner is {@code (x, y, z)}: the luminance less the distance
     * from the block's centre, rounded to the nearest level, never below 0
     */
    int lightAt(int x, int y, int z);

    /** effects: returns this source quantized to a sixteenth of a block; idempotent */
    Source settled();

    /** effects: returns {@code luminance} if it is a legal luminance<br>throws: {@link IllegalArgumentException} if it is not 1..15 */
    static int checkLuminance(int luminance) {
        if (luminance < 1 || luminance > MAX_LUMINANCE) {
            throw new IllegalArgumentException("luminance must be 1.." + MAX_LUMINANCE + ", was " + luminance);
        }
        return luminance;
    }

    /** effects: returns the light {@code luminance} casts at a point {@code distance} away: rounded, floored at 0 */
    static int falloff(int luminance, double distance) {
        return Math.max(0, (int) Math.round(luminance - distance));
    }

    /** effects: returns the nearest sixteenth-block coordinate */
    static double subBlock(double coordinate) {
        return Math.rint(coordinate * 16.0) / 16.0;
    }

    /** effects: returns the centre of the block containing {@code coordinate} */
    static double centreOf(double coordinate) {
        return Math.floor(coordinate) + 0.5;
    }
}
