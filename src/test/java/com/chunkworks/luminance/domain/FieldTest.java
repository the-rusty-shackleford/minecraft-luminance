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

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Partitions. Sources: none, one, two apart, two overlapping (the stronger
 * wins, never a sum), two in one sub-block cell (one source). Cap: under, at, over
 * (the nearest to the viewer survive), zero, negative (refused). Dirty
 * against the previous field: identical (nothing), a source moved within
 * its block (changes), sub-block noise (nothing), moved to the next block (the old and the new box),
 * appeared, vanished, changed luminance (both boxes), the empty field on
 * either side.
 */
final class FieldTest {

    @Test
    void noSourcesIsNoLight() {
        assertTrue(Field.EMPTY.isEmpty());
        assertEquals(0, Field.EMPTY.lightAt(0, 0, 0));
        assertEquals(Field.EMPTY, Field.of(List.of(), 64, 0, 0, 0), "the empty field is one object");
        assertEquals(List.of(), Field.EMPTY.dirtyAgainst(Field.EMPTY));
    }

    @Test
    void theStrongestSourceLightsABlockNeverTheirSum() {
        Point a = new Point(0.5, 0.5, 0.5, 10);
        Point b = new Point(4.5, 0.5, 0.5, 10);
        Field f = Field.of(List.of(a, b), 64, 0, 0, 0);
        assertEquals(10, f.lightAt(0, 0, 0));
        assertEquals(10, f.lightAt(4, 0, 0));
        assertEquals(8, f.lightAt(2, 0, 0), "two away from each: 8, not 16");
        assertEquals(0, f.lightAt(40, 0, 0));
        assertEquals(2, f.sources().size());
    }

    @Test
    void twoSourcesInOneQuantizedCellAreOne() {
        Field f = Field.of(List.of(new Point(0.2, 0.2, 0.2, 10), new Point(0.201, 0.201, 0.201, 10)), 64, 0, 0, 0);
        assertEquals(1, f.sources().size());
    }

    @Test
    void theCapKeepsTheSourcesNearestTheViewer() {
        List<Source> far = List.of(new Point(0.5, 0.5, 0.5, 10), new Point(20.5, 0.5, 0.5, 10), new Point(40.5, 0.5, 0.5, 10));
        Field f = Field.of(far, 2, 40, 0, 0);
        assertEquals(2, f.sources().size());
        assertEquals(0, f.lightAt(0, 0, 0), "the far one was dropped");
        assertEquals(10, f.lightAt(40, 0, 0));
        assertEquals(10, f.lightAt(20, 0, 0));
        assertEquals(3, Field.of(far, 3, 0, 0, 0).sources().size(), "at the cap, all kept");
        assertTrue(Field.of(far, 0, 0, 0, 0).isEmpty(), "a cap of zero is no light");
        assertThrows(IllegalArgumentException.class, () -> Field.of(far, -1, 0, 0, 0));
    }

    @Test
    void SubBlockMotionDirtiesBothOldAndNewBounds() {
        Point torch = new Point(10.2, 64.5, 10.5, 14);
        Field before = Field.of(List.of(torch), 64, 0, 0, 0);
        assertEquals(List.of(), before.dirtyAgainst(before), "same field");
        Field within = Field.of(List.of(new Point(10.9, 64.1, 10.9, 14)), 64, 0, 0, 0);
        assertEquals(2, within.dirtyAgainst(before).size(), "moved within the block");
        Field noise = Field.of(List.of(new Point(10.201, 64.501, 10.501, 14)), 64, 0, 0, 0);
        assertEquals(List.of(), noise.dirtyAgainst(before));
        Field next = Field.of(List.of(new Point(11.2, 64.5, 10.5, 14)), 64, 0, 0, 0);
        List<Bounds> dirty = next.dirtyAgainst(before);
        assertEquals(2, dirty.size(), "the new box and the old");
        assertTrue(dirty.contains(new Point(11.2, 64.5, 10.5, 14).settled().bounds()));
        assertTrue(dirty.contains(torch.settled().bounds()));
    }

    @Test
    void appearingVanishingAndChangingLuminanceDirtyTheirBoxes() {
        Point torch = new Point(10.5, 64.5, 10.5, 14);
        Field lit = Field.of(List.of(torch), 64, 0, 0, 0);
        assertEquals(List.of(torch.bounds()), lit.dirtyAgainst(Field.EMPTY), "appeared");
        assertEquals(List.of(torch.bounds()), Field.EMPTY.dirtyAgainst(lit), "vanished");
        Field dimmer = Field.of(List.of(new Point(10.5, 64.5, 10.5, 7)), 64, 0, 0, 0);
        List<Bounds> dirty = dimmer.dirtyAgainst(lit);
        assertEquals(2, dirty.size(), "the smaller new box and the larger old one");
        assertTrue(dirty.contains(torch.bounds()) && dirty.contains(new Point(10.5, 64.5, 10.5, 7).bounds()));
    }
}
