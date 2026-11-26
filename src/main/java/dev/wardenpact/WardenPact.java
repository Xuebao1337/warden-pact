package dev.wardenpact;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(WardenPact.MOD_ID)
public final class WardenPact {
    public static final String MOD_ID = "wardenpact";
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);

    public static final DeferredItem<Item> SUMMONING_SWORD = ITEMS.registerItem(
            "summoning_sword", Item::new, p -> p.sword(ToolMaterial.NETHERITE, 3.0F, -2.4F)
                    .rarity(Rarity.EPIC).fireResistant());

    public static final DeferredHolder<EntityType<?>, EntityType<FriendlyWarden>> FRIENDLY_WARDEN =
            ENTITIES.register("friendly_warden", id -> EntityType.Builder
                    .of(FriendlyWarden::new, MobCategory.MISC)
                    .sized(0.9F, 2.9F).eyeHeight(2.55F)
                    .clientTrackingRange(16).fireImmune().noLootTable().noSave()
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));

    public WardenPact(IEventBus modBus, ModContainer container) {
        ITEMS.register(modBus);
        PactEquipment.register(modBus);
        ENTITIES.register(modBus);
        modBus.addListener(WardenPact::attributes);
        modBus.addListener(WardenPact::creativeTab);
        container.registerConfig(ModConfig.Type.SERVER, PactConfig.SPEC);
        NeoForge.EVENT_BUS.register(new SummonHandler());
        NeoForge.EVENT_BUS.register(new PactEnchantments());
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(FRIENDLY_WARDEN.get(), Warden.createAttributes().build());
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(SUMMONING_SWORD);
            PactEquipment.ARMOR.forEach(event::accept);
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            PactEquipment.TOOLS.forEach(event::accept);
        }
    }
}
