package dev.wardendance;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.warden.Warden;

public final class WardenDance implements ModInitializer {
    public static final String ID = "wardendance";
    public static final DanceParty PARTIES = new DanceParty();
    public static final EntityType<DancingWarden> DANCER = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(ID, "dancing_warden"), EntityType.Builder
            .of(DancingWarden::new, MobCategory.MISC).sized(0.9F, 2.9F).eyeHeight(2.55F)
            .clientTrackingRange(10).updateInterval(2).fireImmune().noLootTable().noSave().noSummon()
            .build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(ID, "dancing_warden"))));
    @Override public void onInitialize() {
        FabricDefaultAttributeRegistry.register(DANCER, Warden.createAttributes());
        PayloadTypeRegistry.serverboundPlay().register(DanceRequest.TYPE, DanceRequest.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(DanceRequest.TYPE,
                (request, context) -> DanceRequest.handle(context.player()));
        ServerTickEvents.END_SERVER_TICK.register(PARTIES::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PARTIES.logout(handler.player));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PARTIES.clear());
    }
}
