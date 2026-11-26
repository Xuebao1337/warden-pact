package dev.wardenpact.client;

import dev.wardenpact.SculkMount;
import dev.wardenpact.WardenPact;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.ravager.RavagerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.RavagerRenderState;
import net.minecraft.resources.Identifier;

/** Vanilla beast geometry and walk animation, with an original sculk skin. */
public final class SculkMountRenderer extends MobRenderer<SculkMount, RavagerRenderState, RavagerModel> {
    private static final Identifier TEXTURE = WardenPact.id("textures/entity/sculk_mount.png");

    public SculkMountRenderer(EntityRendererProvider.Context context) {
        super(context, new RavagerModel(context.bakeLayer(ModelLayers.RAVAGER)), 1.0F);
    }

    @Override
    public RavagerRenderState createRenderState() { return new RavagerRenderState(); }

    @Override
    public Identifier getTextureLocation(RavagerRenderState state) { return TEXTURE; }
}
