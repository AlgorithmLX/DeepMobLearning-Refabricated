/*
 *
 *  Copyright (C) 2021 Nathan P. Bombana, IterationFunk
 *
 *  This file is part of Deep Mob Learning: Refabricated.
 *
 *  Deep Mob Learning: Refabricated is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Deep Mob Learning: Refabricated is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with Deep Mob Learning: Refabricated.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.nathanpb.dml.modular_armor

import com.google.common.collect.Multimap
import com.google.common.collect.MultimapBuilder
import dev.nathanpb.dml.MOD_ID
import dev.nathanpb.dml.data.DataModelData
import dev.nathanpb.dml.enums.DataModelTier
import dev.nathanpb.dml.identifier
import dev.nathanpb.dml.item.settings
import dev.nathanpb.dml.modular_armor.core.ModularEffectRegistry
import dev.nathanpb.dml.modular_armor.data.ModularArmorData
import dev.nathanpb.dml.modular_armor.mixin.IArmorItemMixin
import dev.nathanpb.dml.modular_armor.screen.ModularArmorScreenHandlerFactory
import dev.nathanpb.dml.utils.RenderUtils
import net.minecraft.client.MinecraftClient
import net.minecraft.client.item.TooltipContext
import net.minecraft.entity.Entity
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.attribute.EntityAttribute
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ArmorItem
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.text.Text
import net.minecraft.text.TranslatableText
import net.minecraft.util.Formatting
import net.minecraft.util.Hand
import net.minecraft.util.Rarity
import net.minecraft.util.TypedActionResult
import net.minecraft.util.registry.Registry
import net.minecraft.world.World
import kotlin.math.roundToInt

class ItemModularGlitchArmor(slot: EquipmentSlot, settings: Settings) : ArmorItem(
        GlitchArmorMaterial.INSTANCE,
        slot,
        settings.fireproof()
) {

    companion object {
        val HELMET = ItemModularGlitchArmor(EquipmentSlot.HEAD, settings().fireproof())
        val CHESTPLATE = ItemModularGlitchArmor(EquipmentSlot.CHEST, settings().fireproof())
        val LEGGINGS = ItemModularGlitchArmor(EquipmentSlot.LEGS, settings().fireproof())
        val BOOTS = ItemModularGlitchArmor(EquipmentSlot.FEET, settings().fireproof())

        fun register() {
            Registry.register(Registry.ITEM, identifier("glitch_helmet"), HELMET)
            Registry.register(Registry.ITEM, identifier("glitch_chestplate"), CHESTPLATE)
            Registry.register(Registry.ITEM, identifier("glitch_leggings"), LEGGINGS)
            Registry.register(Registry.ITEM, identifier("glitch_boots"), BOOTS)
        }

    }

    override fun getRarity(stack: ItemStack?) = Rarity.EPIC

    override fun getItemBarColor(stack: ItemStack?) = 0x00FFC0
    override fun isItemBarVisible(stack: ItemStack) = true

    override fun getItemBarStep(stack: ItemStack): Int {
        val data = ModularArmorData(stack)
        val max = data.tier().dataAmount
        val current = data.dataModel?.dataAmount ?: 0

        return if (max == 0) 0 else if (current > max) 13 else ((13f * current) / max).roundToInt()
    }

    override fun appendTooltip(stack: ItemStack?, world: World?, tooltip: MutableList<Text>?, context: TooltipContext?) {
        if (world?.isClient == true && stack != null && tooltip != null) {
            val data = ModularArmorData(stack)
            if (!data.tier().isMaxTier()) {
                RenderUtils.getTextWithDefaultTextColor(TranslatableText("tooltip.${MOD_ID}.data_amount.1"), world)
                    ?.append(TranslatableText("tooltip.${MOD_ID}.data_amount.2", data.dataAmount, ModularArmorData.amountRequiredTo(data.tier().nextTierOrCurrent()))
                        .formatted(Formatting.WHITE))?.let { tooltip.add(it) }
            }
            RenderUtils.getTextWithDefaultTextColor(TranslatableText("tooltip.${MOD_ID}.tier.1"), world)
                ?.append(TranslatableText("tooltip.${MOD_ID}.tier.2", data.tier().text))?.let { tooltip.add(it) }

            MinecraftClient.getInstance().player?.let { player ->
                if (player.isCreative) {
                    tooltip.add(TranslatableText("tooltip.${MOD_ID}.cheat").formatted(Formatting.GRAY, Formatting.ITALIC))
                }
            }
        }
        super.appendTooltip(stack, world, tooltip, context)
    }

    fun getAttributeModifiers(stack: ItemStack, slot: EquipmentSlot?): Multimap<EntityAttribute, EntityAttributeModifier> {
        return super.getAttributeModifiers(slot).let {
            val builder = MultimapBuilder.ListMultimapBuilder
                .hashKeys()
                .arrayListValues()
                .build<EntityAttribute, EntityAttributeModifier>()

            if (slot != null && slot == this.slot) {
                (material as? GlitchArmorMaterial)?.let { material ->
                    val data = ModularArmorData(stack)
                    val uuid = IArmorItemMixin.dmlRefGetModifierUUIDs()[slot.entitySlotId]

                    val protection = material.getProtectionAmount(slot, data.tier()).toDouble()
                    val toughness = material.getToughness(data.tier()).toDouble()
                    val knockback = material.getKnockbackResistance(data.tier()).toDouble()

                    builder.put(EntityAttributes.GENERIC_ARMOR, EntityAttributeModifier(uuid, "Armor modifier", protection, EntityAttributeModifier.Operation.ADDITION))
                    builder.put(EntityAttributes.GENERIC_ARMOR_TOUGHNESS, EntityAttributeModifier(uuid, "Armor toughness", toughness, EntityAttributeModifier.Operation.ADDITION))
                    builder.put(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, EntityAttributeModifier(uuid, "Armor knockback resistance", knockback, EntityAttributeModifier.Operation.ADDITION))

                    data.dataModel?.let { dataModel ->
                        if (dataModel.category != null) {
                            builder.putAll(appendModularEffectModifiers(data, dataModel))
                        }
                    }
                }
            }

            builder
        }
    }

    override fun use(world: World?, user: PlayerEntity?, hand: Hand): TypedActionResult<ItemStack> {
        if (user != null) {
            val stack = user.getStackInHand(hand)
            if (user.isSneaking && user.isCreative) {
                if (world?.isClient == false) {
                    val data = ModularArmorData(stack)
                    val tier = data.tier()

                    data.dataAmount = ModularArmorData.amountRequiredTo(
                        if (tier.isMaxTier()) DataModelTier.FAULTY else tier.nextTierOrCurrent()
                    )
                }
                return TypedActionResult.success(stack)
            }

            if (world?.isClient == false) {
                user.openHandledScreen(ModularArmorScreenHandlerFactory(hand, this))
            }
            return TypedActionResult.success(stack)
        }
        return super.use(world, user, hand)
    }

    override fun isDamageable() = false

    private fun appendModularEffectModifiers(armor: ModularArmorData, dataModel: DataModelData): Multimap<EntityAttribute, EntityAttributeModifier> {
        val multimap = MultimapBuilder.ListMultimapBuilder
            .hashKeys()
            .arrayListValues()
            .build<EntityAttribute, EntityAttributeModifier>()

        dataModel.category?.let { category ->
            if (dataModel.category != null) {
                ModularEffectRegistry.INSTANCE.allMatching(category, armor.tier())
                    .filter { it.id !in armor.disabledEffects }
                    .forEach {
                        multimap.put(it.entityAttribute, it.createEntityAttributeModifier(armor))
                    }
            }
        }

        return multimap
    }

    override fun inventoryTick(stack: ItemStack, world: World?, entity: Entity?, slot: Int, selected: Boolean) {
        super.inventoryTick(stack, world, entity, slot, selected)
        if (stack.hasEnchantments()) {
            stack.enchantments.firstOrNull {
                (it as? NbtCompound)?.getString("id") == "minecraft:mending"
            }?.let(stack.enchantments::remove)
        }
    }

}
