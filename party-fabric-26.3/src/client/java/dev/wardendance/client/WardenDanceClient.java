package dev.wardendance.client;
import dev.wardendance.DanceRequest;
import dev.wardendance.WardenDance;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class WardenDanceClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(WardenDance.ID, "party"));
        KeyMapping dance = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.wardendance.dance", InputConstants.KEY_G, category));
        EntityRendererRegistry.register(WardenDance.DANCER, DancingWardenRenderer::new);
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            boolean clicked = false;
            while (dance.consumeClick()) clicked = true;
            if (clicked && mc.player != null && mc.gui.screen() == null && !mc.isPaused() && !mc.player.isSpectator()
                    && ClientPlayNetworking.canSend(DanceRequest.TYPE)) ClientPlayNetworking.send(DanceRequest.INSTANCE);
        });
    }
}
