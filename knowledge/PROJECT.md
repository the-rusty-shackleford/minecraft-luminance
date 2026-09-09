---
title: Luminance — project
type: overview
layer: store
tags: [overview]
---

# Luminance

## What this is

A client-only NeoForge 1.21.1 mod: dynamic light from held items, burning things,
glowing mobs and any source a mod registers. Written to replace LambDynamicLights in
the pack, whose licence ruled it out, and to light Sodium's terrain, which the pack's
copy of LambDynamicLights did not.

## Shape

`domain`: `Source` (a `Point` or a `Line`), its `Bounds`, and `Field` -- the strongest
light at a block over settled sources, and the boxes a change touches. `main`: the
`Engine` publishes one immutable `Field` per client tick behind a volatile reference and
re-meshes the sections a source entered or left; `Providers` turn entities into sources
(held and dropped items, fire, data entries, registered providers); `LightData` reads
`luminance/items.json` and `entities.json` from every pack; two mixins make
`LevelRenderer.getLightColor` (vanilla and Sodium meshing, block entities, particles) and
`EntityRenderer.getBlockLightLevel` answer with the field.

## How it is verified

`./gradlew check`: twelve JUnit tests on the pure layer (falloff, lines, bounds,
settling, the field's max, the cap, what dirties); the photo booth on a real client
(dark at night, a torch lights the ground, the engine off leaves it dark, a dropped
glowstone, a burning cow), read off the frame. Run once by hand under Sodium + Iris +
Complementary before 1.0.0: the terrain took the light there too.

## Decisions

D-0001: one static mixin on vanilla's light lookup rather than a Sodium hook, because
Sodium calls it; sources settle to block centres so motion within a block redraws nothing.

## Next

1.0.0 (2026-09-09). Vanilla Wheels registers headlamp beams through the api. A per-pack
suggestion: turn Complementary's own handheld light off now that every player's torch
lights the world.
