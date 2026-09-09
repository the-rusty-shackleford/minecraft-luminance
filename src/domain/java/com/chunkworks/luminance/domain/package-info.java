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
/**
 * The pure layer of Luminance: what light a set of sources casts at a
 * block, and which blocks a change touches. Compiled against the JDK only;
 * a {@code net.minecraft} import here is a compile error, which is the
 * layer rule enforced by the build. Everything here is immutable, specified
 * with {@code requires/effects/throws}, carries its rep invariant, and is
 * tested with partitions written at the top of each test class.
 */
package com.chunkworks.luminance.domain;
