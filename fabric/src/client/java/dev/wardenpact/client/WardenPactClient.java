package dev.wardenpact.client;

import dev.wardenpact.WardenPact;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.WardenRenderer;

public final class WardenPactClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(WardenPact.FRIENDLY_WARDEN, WardenRenderer::new);
    }
}
