# Luminance

Dynamic light from the things that carry it. A torch in a hand lights the ground
around its holder, a burning mob lights its surroundings, a dropped glowstone glows
on the floor, a blaze glows, and any mod can add its own sources: a point, or a line
for a beam. Client-only; a server without it is no mismatch.

## How it works

Every block-light lookup the renderer makes goes through one vanilla function,
`LevelRenderer.getLightColor`, and Sodium's chunk meshing calls the same one; an
entity's light goes through `EntityRenderer.getBlockLightLevel`. Two mixins make both
answer with the dynamic light where it beats the block light. At most thirty times a second, just before a rendered frame, the
engine gathers interpolated sources in range, settles each to a 1/16-block grid, keeps the
nearest `maxSources`, publishes the result as one immutable field, and asks the level
renderer to re-mesh exactly the sections a source entered or left. With Sodium present,
vanilla's re-mesh request is Sodium's own, so nothing here names Sodium. Shader packs
read the lightmap coordinates the mixins changed, so Iris needs nothing either.

A source casts `luminance − distance` at a block, straight-line distance to the
block's centre, rounded, never below zero: the game's own falloff of a level a block,
without the grid. Two sources give the stronger, never the sum. Sub-block settling avoids whole-block jumps while bounding tiny position changes.
Old and new bounds include every positive rounded sample; section rebuild requests are
deduplicated and restricted to the world height. Light queries allocate nothing.
The point and line falloff and brightness are unchanged.

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
  client setup; `Luminance.forEntityInterpolated(type, (entity, partialTick) -> sources)`
  when light follows a rendered moving entity; `Luminance.forItem(item, luminance)` for an item.
  Providers run on the render thread and should return bounded, inexpensive source lists.

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
`src/main`: `client/Engine` (the frame sample, the published field, the re-mesh), `Providers`
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
assertion. Use one rendering client at a time and a verified free display; the booth mutes itself
and exits. To test shader compatibility, put Sodium and Iris in `run/booth/mods`,
the shader pack in `run/booth/shaderpacks`, and name it in
`run/booth/config/iris.properties`. For the engine-off assertion, set `HELD_LIGHTING_MODE=0` in the fixture’s
`shaderpacks/ComplementaryUnbound_r5.8.1.zip.txt`; otherwise the shader independently
lights the torch even with Luminance disabled. This fixture setting does not change
players’ shader preferences. Software rendering can verify the images but does
not measure GPU performance.

`-Dluminance.metrics=true` logs mean/peak dirty sections and scheduling time every
100 samples. This measures rebuild scheduling, not the asynchronous mesh work.
The moving-light change is also gated by the moving Trailblazer night course under
Sodium, Iris and Complementary, with frame times recorded separately. See D-0002.

## Release 1.1.0

Sub-block interpolated light movement with the original point/line brightness and falloff. The additive provider API accepts the render partial tick; terrain rebuild requests remain bounded.

## Licence

AGPL-3.0-or-later. Copyright 2026 Rusty Shackleford and nfx.
