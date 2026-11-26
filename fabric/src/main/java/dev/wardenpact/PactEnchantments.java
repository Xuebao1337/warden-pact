package dev.wardenpact;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/** Definitions and availability are data-driven; only summon and hostile-only guard need hooks. */
public final class PactEnchantments {
    public static final ResourceKey<Enchantment> CONTRACT_RESONANCE = key("contract_resonance");
    public static final ResourceKey<Enchantment> SCULK_GUARD = key("sculk_guard");
    public static final ResourceKey<Enchantment> ECHO_MINING = key("echo_mining");
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private static ResourceKey<Enchantment> key(String name) {
        return ResourceKey.create(Registries.ENCHANTMENT,
                Identifier.fromNamespaceAndPath(WardenPact.MOD_ID, name));
    }

    public static int level(LivingEntity wearer, ItemStack stack, ResourceKey<Enchantment> key) {
        var enchantment = wearer.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
        return Math.clamp(EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack),
                0, enchantment.value().getMaxLevel());
    }

    public static int summonLifetimeSeconds(LivingEntity owner, ItemStack sword) {
        return PactConfig.LIFETIME_SECONDS.get() + 15 * level(owner, sword, CONTRACT_RESONANCE);
    }

    public static int summonCooldownSeconds(LivingEntity owner, ItemStack sword) {
        return Math.max(1, PactConfig.COOLDOWN_SECONDS.get() - 3 * level(owner, sword, CONTRACT_RESONANCE));
    }

    public static float guardDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof Player player)
                || !(source.getEntity() instanceof Enemy)
                || source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)) {
            return amount;
        }
        int total = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            total += level(player, player.getItemBySlot(slot), SCULK_GUARD);
        }
        return amount * (1.0F - Math.min(0.40F, total * 0.02F));
    }
}
