/*
 * Copyright (C) 2020 Nathan P. Bombana, IterationFunk
 *
 * This file is part of Deep Mob Learning: Refabricated.
 *
 * Deep Mob Learning: Refabricated is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Deep Mob Learning: Refabricated is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Deep Mob Learning: Refabricated.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.nathanpb.dml.trial

import dev.nathanpb.dml.config
import dev.nathanpb.dml.utils.runningTrials
import dev.nathanpb.dml.utils.squared
import dev.nathanpb.dml.utils.toBlockPos
import dev.nathanpb.dml.utils.toVec3i
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.block.Blocks
import net.minecraft.entity.Entity
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.mob.EndermanEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import net.minecraft.util.registry.Registry
import net.minecraft.world.World
import net.minecraft.world.explosion.Explosion
import net.minecraft.world.explosion.ExplosionBehavior

class TrialGriefPrevention : AttackBlockCallback, UseBlockCallback {

    companion object {
        fun isInArea(trialPos: BlockPos, pos: BlockPos): Boolean {
            return trialPos.getSquaredDistance(pos.toVec3i()) <= config.trial.arenaRadius.squared()
        }

        fun isBlockProtected(world: World, pos: BlockPos): Boolean {
            return world.runningTrials.any {
                isBlockProtected(pos, it)
            }
        }

        fun isBlockProtected(pos: BlockPos, trial: Trial) : Boolean {
            return isInArea(trial.pos, pos) && pos.y >= trial.pos.y - 1
        }
    }

    override fun interact(player: PlayerEntity, world: World, hand: Hand, pos: BlockPos, direction: Direction): ActionResult {
        return if (
            !world.isClient
            && config.trial.interactGriefPrevention
            && isBlockProtected(world, pos)
            && world.getBlockState(pos)?.run {
                block !== Blocks.CHEST
                && block !== Blocks.TRAPPED_CHEST
                && "grave" !in Registry.BLOCK.getId(block).path
            } != false
        ) {
            ActionResult.FAIL
        } else ActionResult.PASS
    }

    override fun interact(player: PlayerEntity, world: World, hand: Hand, hitResult: BlockHitResult): ActionResult {
        if (config.trial.buildGriefPrevention) {
            val posOfPlacedBlock = hitResult.blockPos.offset(hitResult.side)
            if (!world.isClient && isBlockProtected(world, posOfPlacedBlock)) {
                return ActionResult.FAIL
            }
        }
        return ActionResult.PASS
    }

    fun explode(
        world: World,
        entity: Entity?,
        damageSource: DamageSource?,
        behavior: ExplosionBehavior?,
        pos: BlockPos,
        power: Float,
        createFire: Boolean,
        destructionType: Explosion.DestructionType?
    ): ActionResult {
        if (
            !world.isClient
            && config.trial.explosionGriefPrevention
            && destructionType != Explosion.DestructionType.NONE
            && isBlockProtected(world, pos)
        ) {
            world.createExplosion(entity, pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(), power, createFire, Explosion.DestructionType.NONE)
            return ActionResult.FAIL
        }
        return ActionResult.PASS
    }

    fun onEndermanTeleport(entity: EndermanEntity, pos: Vec3d): ActionResult {
        if (!config.trial.allowMobsLeavingArena) {
            val isInProtectedArea = isBlockProtected(entity.world, entity.pos.toBlockPos())
            val toProtectedArea = isBlockProtected(entity.world, pos.toBlockPos())

            // The first real use of xor in my entire life
            // 22/07/2020, 5:19 AM - Passo Fundo, Brazil
            if (isInProtectedArea xor toProtectedArea) {
                return ActionResult.FAIL
            }
        }
        return ActionResult.SUCCESS
    }
}
