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
package com.chunkworks.luminance.gametest;

import com.chunkworks.luminance.LuminanceConfig;
import com.chunkworks.luminance.client.Engine;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The mod on film: a flat world at night, the player standing on the grass
 * looking down at the ground ahead. Each act changes one source and the
 * ground's brightness in the frame is read back: empty hands are dark; a
 * torch in hand lights the ground; the same torch with the engine switched
 * off lights nothing (so it is the engine, not the game); a dropped
 * glowstone lights the ground around it; a burning cow lights its
 * surroundings. One {@code booth: PASS} or {@code booth: FAIL} line per
 * check; the Gradle task reads them. Client only, active only under
 * {@code luminance.photobooth}.
 *
 * <p>Ticks between acts are generous: the ground is re-meshed by chunk
 * workers, slow under a software renderer, and the photo must come after.
 */
@EventBusSubscriber(modid = BoothMod.MOD_ID, value = Dist.CLIENT)
public final class LuminanceBooth {
    private LuminanceBooth() {}

    private static final Logger LOG = LoggerFactory.getLogger("Luminance booth");
    private static final boolean ACTIVE = Boolean.getBoolean("luminance.photobooth");

    private enum Phase { TITLE, LOADING, RUNNING, DONE }

    private record Step(int at, Runnable action) {}

    /** Ticks for the world to settle after loading, and between a change and its photo. */
    private static final int HOLD = 100;
    private static final int SETTLE = 60;
    /** Standing spot and where the props go, ahead of the player. */
    private static final double X = 0.5;
    private static final double Z = 0.5;
    private static final double AHEAD = 4.0;

    private static Phase phase = Phase.TITLE;
    private static int tick = 0;
    private static List<Step> steps;
    private static double dark = -1.0;
    private static double lit = -1.0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ACTIVE) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0);
        switch (phase) {
            case TITLE -> {
                if (mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                    phase = Phase.LOADING;
                    createWorld(mc);
                }
            }
            case LOADING -> {
                MinecraftServer server = mc.getSingleplayerServer();
                if (mc.level != null && mc.player != null && mc.screen == null && server != null
                        && mc.level.hasChunkAt(mc.player.blockPosition())) {
                    phase = Phase.RUNNING;
                    tick = 0;
                    steps = plan(mc);
                    onServer(mc, LuminanceBooth::setUp);
                }
            }
            case RUNNING -> {
                for (Step step : steps) {
                    if (step.at() == tick) {
                        step.action().run();
                    }
                }
                tick++;
            }
            case DONE -> { }
        }
    }

    private static void createWorld(Minecraft mc) {
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        LevelSettings settings = new LevelSettings("Luminance booth", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                true, rules, WorldDataConfiguration.DEFAULT);
        WorldOptions options = new WorldOptions(1L, false, false);
        mc.createWorldOpenFlows().createFreshLevel("luminance-booth", settings, options,
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                        .value().createWorldDimensions(),
                mc.screen);
    }

    /** Midnight; the player on the grass, facing south, looking down at the ground ahead. */
    private static void setUp(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        level.setDayTime(18000L);
        sp.getAbilities().flying = false;
        sp.onUpdateAbilities();
        sp.teleportTo(level, X, level.getMinBuildHeight() + 5, Z, 0.0f, 55.0f);
        sp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        sp.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
    }

    private static List<Step> plan(Minecraft mc) {
        List<Step> s = new ArrayList<>();
        int t = HOLD;
        s.add(new Step(t, () -> {
            dark = brightness(mc);
            shoot(mc, "booth-night-empty");
            verdict("the ground at night with empty hands is dark", () -> dark < 60 ? null : "brightness " + dark);
        }));
        s.add(new Step(t += 2, () -> onServer(mc, sp -> sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TORCH)))));
        s.add(new Step(t += SETTLE, () -> {
            lit = brightness(mc);
            shoot(mc, "booth-torch-in-hand");
            verdict("a torch in hand lights the ground ahead", () -> lit > dark + 25 ? null : "dark " + dark + ", torch " + lit);
            verdict("the engine holds one source for the torch", () -> Engine.field().sources().size() == 1 ? null : "sources " + Engine.field().sources());
        }));
        s.add(new Step(t += 2, () -> LuminanceConfig.ENABLED.set(false)));
        s.add(new Step(t += SETTLE, () -> {
            double off = brightness(mc);
            shoot(mc, "booth-torch-engine-off");
            // Under a shader pack with its own handheld light (Complementary's
            // "Dynamic Handheld Lighting") the ground stays lit by the shader
            // here; that is the shader's doing, and the check is for vanilla.
            verdict("with the engine off the same torch lights nothing", () -> Math.abs(off - dark) < 12 ? null : "dark " + dark + ", off " + off);
            verdict("the engine publishes no field when off", () -> Engine.field().isEmpty() ? null : "field " + Engine.field().sources());
        }));
        s.add(new Step(t += 2, () -> {
            LuminanceConfig.ENABLED.set(true);
            onServer(mc, sp -> {
                sp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                ItemEntity glow = new ItemEntity(sp.serverLevel(), X, sp.getY() + 0.5, Z + AHEAD, new ItemStack(Items.GLOWSTONE), 0.0, 0.0, 0.0);
                glow.setPickUpDelay(Integer.MAX_VALUE);
                sp.serverLevel().addFreshEntity(glow);
            });
        }));
        s.add(new Step(t += SETTLE, () -> {
            double glow = brightness(mc);
            shoot(mc, "booth-glowstone-dropped");
            verdict("a dropped glowstone lights the ground around it", () -> glow > dark + 25 ? null : "dark " + dark + ", glowstone " + glow);
        }));
        s.add(new Step(t += 2, () -> onServer(mc, sp -> {
            sp.serverLevel().getEntitiesOfClass(ItemEntity.class, sp.getBoundingBox().inflate(16.0)).forEach(ItemEntity::discard);
            // A cow, not a zombie: the booth's world is peaceful, and a
            // hostile mob is gone the tick it is added.
            Cow cow = EntityType.COW.create(sp.serverLevel());
            if (cow != null) {
                cow.setPos(X, sp.getY(), Z + AHEAD);
                cow.setNoAi(true);
                cow.setInvulnerable(true);
                cow.setRemainingFireTicks(20 * 60);
                sp.serverLevel().addFreshEntity(cow);
            }
        })));
        s.add(new Step(t += SETTLE, () -> {
            double burning = brightness(mc);
            shoot(mc, "booth-burning-cow");
            verdict("a burning cow lights its surroundings", () -> burning > dark + 8 ? null : "dark " + dark + ", burning " + burning);
        }));
        s.add(new Step(t += 20, () -> {
            LOG.info("booth: PASS all checks ran");
            phase = Phase.DONE;
            mc.stop();
        }));
        return s;
    }

    /**
     * The mean brightness (0..255) of the middle band of the frame: the
     * ground a few blocks ahead, where every prop is put.
     */
    private static double brightness(Minecraft mc) {
        var target = mc.getMainRenderTarget();
        try (NativeImage image = Screenshot.takeScreenshot(target)) {
            long sum = 0;
            int n = 0;
            int w = image.getWidth();
            int h = image.getHeight();
            for (int y = (int) (h * 0.35); y < (int) (h * 0.75); y += 4) {
                for (int x = (int) (w * 0.3); x < (int) (w * 0.7); x += 4) {
                    int argb = image.getPixelRGBA(x, y);
                    sum += (argb & 0xFF) + ((argb >> 8) & 0xFF) + ((argb >> 16) & 0xFF);
                    n += 3;
                }
            }
            return n == 0 ? 0.0 : (double) sum / n;
        }
    }

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            return;
        }
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (sp != null) {
                action.accept(sp);
            }
        });
    }

    private static void shoot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(),
                message -> LOG.info("booth: {}", message.getString()));
    }

    /** Runs {@code check}; null is a pass, anything else the failure's detail. */
    private static void verdict(String what, java.util.function.Supplier<String> check) {
        String detail;
        try {
            detail = check.get();
        } catch (RuntimeException e) {
            detail = e.toString();
        }
        if (detail == null) {
            LOG.info("booth: PASS {}", what);
        } else {
            LOG.error("booth: FAIL {} -- {}", what, detail);
        }
    }
}
