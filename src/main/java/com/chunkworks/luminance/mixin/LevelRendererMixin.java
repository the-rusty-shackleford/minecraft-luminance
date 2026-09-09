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
package com.chunkworks.luminance.mixin;

import com.chunkworks.luminance.client.Engine;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The one lookup every block face, block entity, particle and chunk mesher
 * goes through -- vanilla's builder and Sodium's alike call this static --
 * answers with the dynamic light where it beats the block light. The packed
 * value is {@code sky << 20 | block << 4}; only the block half is raised.
 * Called from chunk-meshing workers, so it reads the engine's published
 * field and touches nothing else.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Inject(method = "getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I",
            at = @At("RETURN"), cancellable = true)
    private static void luminance$dynamicBlockLight(BlockAndTintGetter level, BlockState state, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        int packed = cir.getReturnValueI();
        int block = (packed >> 4) & 0xF;
        if (block == 15) {
            return;
        }
        int dynamic = Engine.lightAt(pos.getX(), pos.getY(), pos.getZ());
        if (dynamic > block) {
            cir.setReturnValue((packed & ~(0xF << 4)) | (dynamic << 4));
        }
    }
}
