package dev.wardenpact.client;

import dev.wardenpact.WardenPact;
import net.minecraft.client.renderer.entity.WardenRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@Mod(value = WardenPact.MOD_ID, dist = Dist.CLIENT)
public final class WardenPactClient {
    public WardenPactClient(IEventBus modBus) {
        modBus.addListener(WardenPactClient::renderers);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(WardenPact.FRIENDLY_WARDEN.get(), WardenRenderer::new);
    }
}
