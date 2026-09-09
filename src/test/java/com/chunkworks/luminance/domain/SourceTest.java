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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Partitions. Luminance: 0 and 16 refused, 1 and 15 taken. Point: at the
 * source's own block, one block off on an axis, off on a diagonal (the
 * distance is straight-line), at the edge of reach, beyond it, and below
 * the source. Line: a block beside the middle of the segment, beside an
 * end, beyond an end (falls off from the end, not the extension), on the
 * segment, a zero-length line (a point). Bounds: reach luminance - 1 on
 * each axis for a point, the union of the ends for a line. Settling: two
 * positions in one block settle equal, across a block edge not; settling
 * is idempotent and keeps the light at the block itself.
 */
final class SourceTest {

    @Test
    void luminanceIsOneToFifteen() {
        assertThrows(IllegalArgumentException.class, () -> new Point(0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Point(0, 0, 0, 16));
        assertThrows(IllegalArgumentException.class, () -> new Line(0, 0, 0, 1, 0, 0, 0));
        assertEquals(1, new Point(0, 0, 0, 1).luminance());
        assertEquals(15, new Line(0, 0, 0, 1, 0, 0, 15).luminance());
        assertThrows(IllegalArgumentException.class, () -> new Point(Double.NaN, 0, 0, 5));
        assertThrows(IllegalArgumentException.class, () -> new Line(0, 0, 0, Double.POSITIVE_INFINITY, 0, 0, 5));
    }

    @Test
    void aPointLightsItsOwnBlockFullyAndFallsOffALevelABlock() {
        Point torch = new Point(10.5, 64.5, 10.5, 14);
        assertEquals(14, torch.lightAt(10, 64, 10), "its own block");
        assertEquals(13, torch.lightAt(11, 64, 10), "one east");
        assertEquals(13, torch.lightAt(10, 63, 10), "one down");
        assertEquals(13, torch.lightAt(10, 64, 9), "one north");
        assertEquals(Source.falloff(14, Math.sqrt(2)), torch.lightAt(11, 64, 11), "a diagonal is further than an axis step");
        assertEquals(1, torch.lightAt(23, 64, 10), "thirteen blocks away: the last level");
        assertEquals(0, torch.lightAt(24, 64, 10), "fourteen away: dark");
        assertEquals(0, torch.lightAt(50, 64, 10), "far away");
    }

    @Test
    void aLineLightsItsWholeLengthAndFallsOffFromItsNearestPoint() {
        Line beam = new Line(0.5, 64.5, 0.5, 10.5, 64.5, 0.5, 15);
        assertEquals(15, beam.lightAt(0, 64, 0), "the start");
        assertEquals(15, beam.lightAt(5, 64, 0), "the middle");
        assertEquals(15, beam.lightAt(10, 64, 0), "the end");
        assertEquals(14, beam.lightAt(5, 64, 1), "beside the middle");
        assertEquals(14, beam.lightAt(10, 64, 1), "beside the end");
        assertEquals(14, beam.lightAt(11, 64, 0), "one past the end: from the end, not the extension");
        assertEquals(Source.falloff(15, Math.sqrt(2)), beam.lightAt(11, 64, 1), "past the end and beside");
        assertEquals(0, beam.lightAt(30, 64, 0), "far past the end");
        Line dot = new Line(3.5, 3.5, 3.5, 3.5, 3.5, 3.5, 7);
        assertEquals(new Point(3.5, 3.5, 3.5, 7).lightAt(4, 3, 3), dot.lightAt(4, 3, 3), "a zero-length line is a point");
    }

    @Test
    void boundsReachAsFarAsTheLightDoes() {
        Point p = new Point(10.5, 64.5, 10.5, 14);
        assertEquals(new Bounds(-3, 51, -3, 23, 77, 23), p.bounds());
        assertTrue(p.bounds().contains(23, 64, 10) && !p.bounds().contains(24, 64, 10));
        Point one = new Point(0.5, 0.5, 0.5, 1);
        assertEquals(new Bounds(0, 0, 0, 0, 0, 0), one.bounds(), "a luminance of one lights only its block");
        Line l = new Line(0.5, 64.5, 0.5, 10.5, 70.5, 0.5, 5);
        assertEquals(new Bounds(-4, 60, -4, 14, 74, 4), l.bounds());
        // Every block a source lights is inside its bounds.
        for (int x = -6; x <= 30; x++) {
            for (int y = 50; y <= 80; y++) {
                if (p.lightAt(x, y, 10) > 0) {
                    assertTrue(p.bounds().contains(x, y, 10), x + "," + y);
                }
                if (l.lightAt(x, y, 0) > 0) {
                    assertTrue(l.bounds().contains(x, y, 0), "line " + x + "," + y);
                }
            }
        }
    }

    @Test
    void settlingPutsASourceAtItsBlocksCentre() {
        Point a = new Point(10.2, 64.9, 10.7, 14);
        Point b = new Point(10.8, 64.1, 10.1, 14);
        Point c = new Point(11.0, 64.5, 10.5, 14);
        assertEquals(a.settled(), b.settled(), "one block, one source");
        assertEquals(new Point(10.5, 64.5, 10.5, 14), a.settled());
        assertTrue(!a.settled().equals(c.settled()), "the next block is another source");
        assertEquals(a.settled(), a.settled().settled(), "idempotent");
        assertEquals(14, a.settled().lightAt(10, 64, 10));
        Line beam = new Line(0.1, 64.9, 0.2, 10.9, 64.1, 0.8, 15);
        assertEquals(new Line(0.5, 64.5, 0.5, 10.5, 64.5, 0.5, 15), beam.settled());
    }

    @Test
    void boundsAreABoxAndKnowTheirSections() {
        assertThrows(IllegalArgumentException.class, () -> new Bounds(1, 0, 0, 0, 0, 0));
        Bounds b = new Bounds(-1, 60, 15, 1, 63, 17);
        assertEquals(new Bounds(-1, 0, 0, 5, 63, 17), b.union(new Bounds(0, 0, 0, 5, 1, 1)));
        var sections = b.sections();
        assertEquals(2 * 1 * 2, sections.size(), "x spans sections -1 and 0, y section 3, z sections 0 and 1");
        assertTrue(sections.stream().anyMatch(s -> s[0] == -1 && s[1] == 3 && s[2] == 1));
        assertEquals(2 * 2 * 2, new Bounds(-1, 60, 15, 1, 64, 17).sections().size(), "y 64 is the next section");
    }
}
