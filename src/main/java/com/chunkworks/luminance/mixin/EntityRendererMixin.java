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
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * An entity's block light -- read at its light probe, the eye -- takes the
 * dynamic light into account, so a mob standing in a torch's glow is lit
 * as the ground around it is, and the torch's own holder, first person
 * hand included.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity> {

    @Inject(method = "getBlockLightLevel", at = @At("RETURN"), cancellable = true)
    private void luminance$dynamicEntityLight(T entity, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        int vanilla = cir.getReturnValueI();
        if (vanilla == 15) {
            return;
        }
        int dynamic = Engine.lightAt(pos);
        if (dynamic > vanilla) {
            cir.setReturnValue(dynamic);
        }
    }
}
