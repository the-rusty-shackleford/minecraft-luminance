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
 * A light along a segment: a headlamp's beam, lit evenly from one end to
 * the other and falling off with the distance to the nearest point of it,
 * so the lit region is a rounded corridor.
 *
 * <p>RI: coordinates finite; luminance 1..15.
 * AF: AF(x0..z1, luminance) = "a source of {@code luminance} along the
 *     segment from (x0, y0, z0) to (x1, y1, z1)"; the two ends may coincide,
 *     in which case it is a point.
 */
public record Line(double x0, double y0, double z0, double x1, double y1, double z1, int luminance) implements Source {
    public Line {
        Source.checkLuminance(luminance);
        for (double c : new double[] {x0, y0, z0, x1, y1, z1}) {
            if (!Double.isFinite(c)) {
                throw new IllegalArgumentException("a line must be finite");
            }
        }
    }

    @Override
    public Bounds bounds() {
        return Bounds.around(x0, y0, z0, luminance - 1).union(Bounds.around(x1, y1, z1, luminance - 1));
    }

    @Override
    public int lightAt(int bx, int by, int bz) {
        double px = bx + 0.5;
        double py = by + 0.5;
        double pz = bz + 0.5;
        double dx = x1 - x0;
        double dy = y1 - y0;
        double dz = z1 - z0;
        double length2 = dx * dx + dy * dy + dz * dz;
        double t = 0.0;
        if (length2 > 0.0) {
            t = ((px - x0) * dx + (py - y0) * dy + (pz - z0) * dz) / length2;
            t = Math.max(0.0, Math.min(1.0, t));
        }
        double nx = x0 + t * dx;
        double ny = y0 + t * dy;
        double nz = z0 + t * dz;
        double ex = px - nx;
        double ey = py - ny;
        double ez = pz - nz;
        return Source.falloff(luminance, Math.sqrt(ex * ex + ey * ey + ez * ez));
    }

    @Override
    public Line settled() {
        return new Line(Source.centreOf(x0), Source.centreOf(y0), Source.centreOf(z0),
                Source.centreOf(x1), Source.centreOf(y1), Source.centreOf(z1), luminance);
    }
}
