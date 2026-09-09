# Luminance

Dynamic light from the things that carry it. A torch in a hand lights the ground
around its holder, a burning mob lights its surroundings, a dropped glowstone glows
on the floor, a blaze glows, and any mod can add its own sources: a point, or a line
for a beam. Client-only; a server without it is no mismatch.

## How it works

Every block-light lookup the renderer makes goes through one vanilla function,
`LevelRenderer.getLightColor`, and Sodium's chunk meshing calls the same one; an
entity's light goes through `EntityRenderer.getBlockLightLevel`. Two mixins make both
answer with the dynamic light where it beats the block light. Once a client tick the
engine gathers the sources in range, settles each to the centre of its block, keeps the
nearest `maxSources`, publishes the result as one immutable field, and asks the level
renderer to re-mesh exactly the sections a source entered or left. With Sodium present,
vanilla's re-mesh request is Sodium's own, so nothing here names Sodium. Shader packs
read the lightmap coordinates the mixins changed, so Iris needs nothing either.

A source casts `luminance − distance` at a block, straight-line distance to the
block's centre, rounded, never below zero: the game's own falloff of a level a block,
without the grid. Two sources give the stronger, never the sum. Settling to block
centres means a torch carried across a block casts the same light until it leaves the
block, so nothing is redrawn in between.

## What glows

- **Held items**: a mod's registration, else `luminance/items.json`, else a block
  item's own block light -- so torches, lanterns, glowstone, sea lanterns, shroomlights
  and every other lit block light their holder with no data at all. An entry may say
  the light does not survive being underwater (a torch's does not; a sea lantern's does).
- **Dropped items**: the same rule for the item on the ground.
- **Anything on fire**: luminance 10.
- **Entities** in `luminance/entities.json`: blaze, magma cube, glow squid, allay,
  fireballs, spectral arrows, primed TNT, end crystals, glow item frames.
- **Registered sources**: `Luminance.forEntity(type, entity -> sources)` from a mod's
  client setup; `Luminance.forItem(item, luminance)` for an item.

The data files are read from every resource pack, bottom to top, at
`assets/<namespace>/luminance/items.json` and `entities.json`; a later pack overrides an
earlier one entry by entry. An entry is a luminance, or `{"luminance": 14, "underwater":
false}`. An id the game does not know is logged and skipped, so a pack may light a mod's
things without requiring the mod.

## Settings

`config/luminance-client.toml`: `enabled`, `range` (blocks from the camera a source
still counts, 64), `maxSources` (the nearest win, 64), and switches for held items,
dropped items, burning things and entities.

With Complementary's own "Dynamic Handheld Lighting" left on, a held torch is lit twice:
once by the shader at the viewer's eye, once here in the world. Turning the shader's off
is the better choice, since Luminance lights every player's torch, not only the viewer's.

## Layout

`src/domain` (JDK-only, plain JUnit): `Source` (`Point`, `Line`), `Bounds`, `Field` --
what light a set of sources casts at a block and which blocks a change touches.
`src/main`: `client/Engine` (the tick, the published field, the re-mesh), `Providers`
(what casts light), `LightData` (the JSON), `LuminanceConfig`, the two mixins, and
`api/Luminance`. `src/gametest`: the photo booth, a mod of its own, never shipped.

## Building and looking at it

```
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 PATH="$JAVA_HOME/bin:$PATH"
./gradlew test                  # the pure layer
./gradlew check                 # plus the photo booth (needs a display; -PskipBooth to omit)
```

The booth makes a flat world at night and photographs the ground with empty hands, with
a torch in hand, with the engine switched off, with a dropped glowstone and with a
burning cow, reading each frame's brightness back; its `booth: PASS/FAIL` lines are the
assertion. Headless: `Xephyr :7 -screen 1280x720 -ac -br -noreset` on another display,
then `DISPLAY=:7 __GLX_VENDOR_LIBRARY_NAME=mesa LIBGL_ALWAYS_SOFTWARE=1
GALLIUM_DRIVER=llvmpipe ./gradlew check`. To look at it under the pack's renderer, drop
Sodium and Iris into `run/booth/mods`, a shader pack into `run/booth/shaderpacks` with
`run/booth/config/iris.properties` naming it, and add `MESA_GL_VERSION_OVERRIDE=4.6
MESA_GLSL_VERSION_OVERRIDE=460` to the environment.

## Licence

AGPL-3.0-or-later. Copyright 2026 Rusty Shackleford and nfx.
