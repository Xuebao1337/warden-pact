package dev.wardenpact;

import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/** Vanilla equipment components retain mining, right-click, repair and enchantment behavior. */
public final class PactEquipment {
    private static final TagKey<Item> REPAIR_ITEMS = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(WardenPact.MOD_ID, "repairs_echo_equipment"));
    private static final ToolMaterial BASE_TOOL = ToolMaterial.NETHERITE;
    public static final ToolMaterial TOOL_MATERIAL = new ToolMaterial(
            BASE_TOOL.incorrectBlocksForDrops(), BASE_TOOL.durability(), BASE_TOOL.speed(),
            BASE_TOOL.attackDamageBonus(), BASE_TOOL.enchantmentValue(), REPAIR_ITEMS);
    private static final ArmorMaterial BASE_ARMOR = ArmorMaterials.NETHERITE;
    public static final ArmorMaterial ARMOR_MATERIAL = new ArmorMaterial(
            BASE_ARMOR.durability(), BASE_ARMOR.defense(), BASE_ARMOR.enchantmentValue(),
            BASE_ARMOR.equipSound(), BASE_ARMOR.toughness(), BASE_ARMOR.knockbackResistance(),
            REPAIR_ITEMS, ResourceKey.create(EquipmentAssets.ROOT_ID,
                    Identifier.fromNamespaceAndPath(WardenPact.MOD_ID, "echo")));

    public static final Item ECHO_HELMET = armor("echo_helmet", ArmorType.HELMET);
    public static final Item ECHO_CHESTPLATE = armor("echo_chestplate", ArmorType.CHESTPLATE);
    public static final Item ECHO_LEGGINGS = armor("echo_leggings", ArmorType.LEGGINGS);
    public static final Item ECHO_BOOTS = armor("echo_boots", ArmorType.BOOTS);
    public static final Item ECHO_PICKAXE = WardenPact.registerItem("echo_pickaxe",
            properties -> new EchoToolItem(properties, EchoToolItem.Skill.ORE),
            finish(new Item.Properties().pickaxe(TOOL_MATERIAL, 1.0F, -2.8F)));
    public static final Item ECHO_AXE = WardenPact.registerItem("echo_axe",
            properties -> new EchoToolItem(properties, EchoToolItem.Skill.LOGS),
            finish(new Item.Properties().axe(TOOL_MATERIAL, 5.0F, -3.0F)));
    public static final Item ECHO_SHOVEL = WardenPact.registerItem("echo_shovel",
            properties -> new EchoToolItem(properties, EchoToolItem.Skill.PATH),
            finish(new Item.Properties().shovel(TOOL_MATERIAL, 1.5F, -3.0F)));
    public static final Item ECHO_HOE = WardenPact.registerItem("echo_hoe",
            properties -> new EchoToolItem(properties, EchoToolItem.Skill.FARM),
            finish(new Item.Properties().hoe(TOOL_MATERIAL, -4.0F, 0.0F)));
    public static final List<Item> ARMOR = List.of(ECHO_HELMET, ECHO_CHESTPLATE, ECHO_LEGGINGS, ECHO_BOOTS);
    public static final List<Item> TOOLS = List.of(ECHO_PICKAXE, ECHO_AXE, ECHO_SHOVEL, ECHO_HOE);

    private static Item armor(String name, ArmorType type) {
        var attributes = ARMOR_MATERIAL.createAttributes(type);
        if (type == ArmorType.LEGGINGS) {
            attributes = attributes.withModifierAdded(Attributes.MOVEMENT_SPEED,
                    new AttributeModifier(WardenPact.id("echo_stride"), 0.15,
                            AttributeModifier.Operation.ADD_MULTIPLIED_BASE), EquipmentSlotGroup.LEGS);
        } else if (type == ArmorType.BOOTS) {
            attributes = attributes.withModifierAdded(Attributes.FALL_DAMAGE_MULTIPLIER,
                    new AttributeModifier(WardenPact.id("echo_cushion"), -0.5,
                            AttributeModifier.Operation.ADD_MULTIPLIED_BASE), EquipmentSlotGroup.FEET);
        }
        return WardenPact.registerItem(name, EchoArmorItem::new,
                finish(new Item.Properties().humanoidArmor(ARMOR_MATERIAL, type).attributes(attributes)));
    }

    private static Item.Properties finish(Item.Properties properties) {
        return properties.rarity(Rarity.RARE).fireResistant();
    }

    public static void register() {}

    private PactEquipment() {}
}
