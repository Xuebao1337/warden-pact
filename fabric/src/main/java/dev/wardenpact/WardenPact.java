package dev.wardenpact;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;

public final class WardenPact implements ModInitializer {
    public static final String MOD_ID = "wardenpact";
    public static final SummonHandler SUMMONS = new SummonHandler();
    public static final PactMounts MOUNTS = new PactMounts();
    public static final Item SCULK_REINS = registerItem("sculk_reins", SculkReinsItem::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant());
    public static final Item SUMMONING_SWORD = registerItem("summoning_sword",
            new Item.Properties().sword(ToolMaterial.NETHERITE, 3.0F, -2.4F)
                    .rarity(Rarity.EPIC).fireResistant());
    public static final EntityType<FriendlyWarden> FRIENDLY_WARDEN = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, id("friendly_warden"), EntityType.Builder
                    .of(FriendlyWarden::new, MobCategory.MISC)
                    .sized(0.9F, 2.9F).eyeHeight(2.55F)
                    .clientTrackingRange(16).fireImmune().noLootTable().noSave()
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("friendly_warden"))));
    public static final EntityType<SculkMount> SCULK_MOUNT = Registry.register(
            BuiltInRegistries.ENTITY_TYPE, id("sculk_mount"), EntityType.Builder
                    .of(SculkMount::new, MobCategory.CREATURE).sized(1.95F, 2.2F).eyeHeight(1.85F)
                    .clientTrackingRange(10).fireImmune().noLootTable()
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id("sculk_mount"))));

    public static Identifier id(String name) {
        return Identifier.fromNamespaceAndPath(MOD_ID, name);
    }

    public static Item registerItem(String name, Item.Properties properties) {
        return registerItem(name, Item::new, properties);
    }

    public static Item registerItem(String name, java.util.function.Function<Item.Properties, Item> factory,
            Item.Properties properties) {
        var key = ResourceKey.create(Registries.ITEM, id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
    }

    @Override
    public void onInitialize() {
        PactEquipment.register();
        FabricDefaultAttributeRegistry.register(FRIENDLY_WARDEN, Warden.createAttributes());
        FabricDefaultAttributeRegistry.register(SCULK_MOUNT, SculkMount.createMountAttributes());
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(SUMMONS::allowDamage);
        ServerLifecycleEvents.SERVER_STARTING.register(server -> PactConfig.load());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SUMMONS.clear();
            MOUNTS.clear();
        });
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(entries -> {
            entries.accept(SUMMONING_SWORD);
            PactEquipment.ARMOR.forEach(entries::accept);
        });
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
                .register(entries -> {
                    PactEquipment.TOOLS.forEach(entries::accept);
                    entries.accept(SCULK_REINS);
                });
    }
}
