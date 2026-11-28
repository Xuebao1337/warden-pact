package dev.wardendance.client;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WardenRenderer;
import net.minecraft.client.renderer.entity.layers.LivingEntityEmissiveLayer;
import net.minecraft.resources.Identifier;

public final class DancingWardenRenderer extends WardenRenderer {
    public DancingWardenRenderer(EntityRendererProvider.Context context) {
        super(context);
        model = new DancingWardenModel(context.bakeLayer(ModelLayers.WARDEN));
        layers.clear();
        addLayer(new LivingEntityEmissiveLayer<>(this,
                state -> Identifier.withDefaultNamespace("textures/entity/warden/warden_bioluminescent_layer.png"),
                (state, age) -> 1.0F,
                new DancingWardenModel(context.bakeLayer(ModelLayers.WARDEN_BIOLUMINESCENT)),
                texture -> net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucentEmissive(texture), false));
        addLayer(new LivingEntityEmissiveLayer<>(this,
                state -> Identifier.withDefaultNamespace("textures/entity/warden/warden_heart.png"),
                (state, age) -> 0.65F + 0.35F * (float) Math.sin(age * Math.PI / 10),
                new DancingWardenModel(context.bakeLayer(ModelLayers.WARDEN_HEART)),
                texture -> net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucentEmissive(texture), false));
    }
}
