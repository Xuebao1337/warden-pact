package dev.wardendance.client;

import dev.wardendance.DanceRequest;
import dev.wardendance.WardenDance;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.lwjgl.glfw.GLFW;

@Mod(value = WardenDance.ID, dist = Dist.CLIENT)
public final class WardenDanceClient {
    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath(WardenDance.ID, "party"));
    private static final KeyMapping DANCE = new KeyMapping("key.wardendance.dance", GLFW.GLFW_KEY_G, CATEGORY);
    public WardenDanceClient(IEventBus bus) {
        bus.addListener(WardenDanceClient::keys);
        bus.addListener(WardenDanceClient::renderers);
        NeoForge.EVENT_BUS.addListener(WardenDanceClient::tick);
    }
    private static void keys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(DANCE);
    }
    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(WardenDance.DANCER.get(), DancingWardenRenderer::new);
    }
    private static void tick(ClientTickEvent.Post event) {
        boolean clicked = false;
        while (DANCE.consumeClick()) clicked = true;
        Minecraft mc = Minecraft.getInstance();
        if (clicked && mc.player != null && mc.gui.screen() == null && !mc.isPaused() && !mc.player.isSpectator()) {
            ClientPacketDistributor.sendToServer(DanceRequest.INSTANCE);
        }
    }
}
