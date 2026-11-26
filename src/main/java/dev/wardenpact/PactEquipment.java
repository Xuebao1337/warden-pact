package dev.wardenpact;

import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Vanilla equipment components retain mining, right-click, repair and enchantment behavior. */
public final class PactEquipment {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(WardenPact.MOD_ID);
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

    public static final DeferredItem<Item> ECHO_HELMET = armor("echo_helmet", ArmorType.HELMET);
    public static final DeferredItem<Item> ECHO_CHESTPLATE = armor("echo_chestplate", ArmorType.CHESTPLATE);
    public static final DeferredItem<Item> ECHO_LEGGINGS = armor("echo_leggings", ArmorType.LEGGINGS);
    public static final DeferredItem<Item> ECHO_BOOTS = armor("echo_boots", ArmorType.BOOTS);
    public static final DeferredItem<Item> ECHO_PICKAXE = ITEMS.registerItem("echo_pickaxe", Item::new,
            p -> finish(p.pickaxe(TOOL_MATERIAL, 1.0F, -2.8F)));
    public static final DeferredItem<AxeItem> ECHO_AXE = ITEMS.registerItem("echo_axe",
            p -> new AxeItem(TOOL_MATERIAL, 5.0F, -3.0F, finish(p)));
    public static final DeferredItem<ShovelItem> ECHO_SHOVEL = ITEMS.registerItem("echo_shovel",
            p -> new ShovelItem(TOOL_MATERIAL, 1.5F, -3.0F, finish(p)));
    public static final DeferredItem<HoeItem> ECHO_HOE = ITEMS.registerItem("echo_hoe",
            p -> new HoeItem(TOOL_MATERIAL, -4.0F, 0.0F, finish(p)));

    public static final List<DeferredItem<Item>> ARMOR =
            List.of(ECHO_HELMET, ECHO_CHESTPLATE, ECHO_LEGGINGS, ECHO_BOOTS);
    public static final List<DeferredItem<? extends Item>> TOOLS =
            List.of(ECHO_PICKAXE, ECHO_AXE, ECHO_SHOVEL, ECHO_HOE);

    private static DeferredItem<Item> armor(String name, ArmorType type) {
        return ITEMS.registerItem(name, Item::new, p -> finish(p.humanoidArmor(ARMOR_MATERIAL, type)));
    }

    private static Item.Properties finish(Item.Properties properties) {
        return properties.rarity(Rarity.RARE).fireResistant();
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    private PactEquipment() {}
}
