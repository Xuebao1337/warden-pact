package dev.wardendance;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.warden.Warden;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(WardenDance.ID)
public final class WardenDance {
    public static final String ID = "wardendance";
    public static final DanceParty PARTIES = new DanceParty();
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, ID);
    public static final DeferredHolder<EntityType<?>, EntityType<DancingWarden>> DANCER =
            ENTITIES.register("dancing_warden", id -> EntityType.Builder
                    .of(DancingWarden::new, MobCategory.MISC).sized(0.9F, 2.9F).eyeHeight(2.55F)
                    .clientTrackingRange(10).updateInterval(2).fireImmune().noLootTable().noSave().noSummon()
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));

    public WardenDance(IEventBus bus) {
        ENTITIES.register(bus);
        bus.addListener(WardenDance::attributes);
        bus.addListener(WardenDance::network);
        NeoForge.EVENT_BUS.register(PARTIES);
    }
    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(DANCER.get(), Warden.createAttributes().build());
    }
    private static void network(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(DanceRequest.TYPE, DanceRequest.CODEC, DanceRequest::handle);
    }
}
