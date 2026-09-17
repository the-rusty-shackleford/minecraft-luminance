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
`Engine` publishes one immutable `Field` at most 30 times per second before rendering behind a volatile reference and
re-meshes the sections a source entered or left; `Providers` turn entities into sources
(held and dropped items, fire, data entries, registered providers); `LightData` reads
`luminance/items.json` and `entities.json` from every pack; two mixins make
`LevelRenderer.getLightColor` (vanilla and Sodium meshing, block entities, particles) and
`EntityRenderer.getBlockLightLevel` answer with the field.

## How it is verified

`./gradlew check`: JUnit tests on the pure layer (falloff, lines, bounds,
settling, the field's max, the cap, what dirties); the photo booth on a real client
(dark at night, a torch lights the ground, the engine off leaves it dark, a dropped
glowstone, a burning cow), read off the frame. Run once by hand under Sodium + Iris +
Complementary before 1.0.0: the terrain took the light there too.

## Decisions

D-0001: one static mixin on vanilla's light lookup rather than a Sodium hook, because
Sodium calls it; the original whole-block settling trade-off. D-0002 supersedes that settling with
1/16-block precision and interpolated frame samples, retaining point/line appearance.

## Next

1.0.0 (2026-09-09). Vanilla Wheels registers headlamp beams through the api. A per-pack
suggestion: turn Complementary's own handheld light off now that every player's torch
lights the world.

## Release authorization — 2026-09-17

Rusty approved the final vehicle cosmetics, then explicitly requested the release.
Version 1.1.0 is the coordinated release version, superseding the prior hold.
The release set is Luminance 1.1.0, Vanilla Wheels 1.7.0 (network protocol 4),
Trailblazer 1.7.0, Farmer's Pickup 1.3.0 and Trailer 2.3.0, targeting pack 1.36.0.
All peers must update together. Vehicle artwork changes leave the existing gameplay
profiles, recipes, seats and interaction anchors unchanged; the separately approved
collision and moving-light changes ship in the shared libraries.

Independent driver/observer multiplayer, the historical live movement-warning route,
and representative 4–8-player tracking/DH capacity remain open follow-ups. Local tests
do not establish those results. Release authorization does not claim those checks passed.

## Published release — 2026-09-17

[Version 1.1.0](https://github.com/the-rusty-shackleford/minecraft-luminance/releases/tag/v1.1.0) is published and deployed in pack 1.36.0.
The coordinated set passed 96 JUnit tests, 59 real-server GameTests and all five
Iris/Complementary booths on clean release builds. Downloaded release assets match
the validated jars; nested dependencies are the exact newly built artifacts.
The three cosmetic vehicle profiles remain identical to their preserved references.

Both pack archives were verified against the source. Deployment occurred with zero
players online; installed server hashes match, and Mod Hub reports pack parity.
The initial empty-server sample was 20 TPS. Startup retained the same 36 pre-existing
third-party error messages, with none added. This does not close the multiplayer,
historical movement-warning or representative capacity follow-ups above.
