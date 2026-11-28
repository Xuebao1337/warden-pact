package dev.wardendance.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.warden.WardenModel;
import net.minecraft.client.renderer.entity.state.WardenRenderState;

/** Every emissive layer uses this animation too, keeping the glowing ribs aligned. */
public final class DancingWardenModel extends WardenModel {
    public DancingWardenModel(ModelPart root) { super(root); }
    @Override public void setupAnim(WardenRenderState state) {
        super.setupAnim(state);
        float beat = state.ageInTicks * (float) Math.PI / 10;
        float sway = (float) Math.sin(beat);
        body.zRot = 0.13F * sway;
        body.yRot = 0.18F * (float) Math.sin(beat / 2);
        head.xRot = 0.16F * (float) Math.sin(beat * 2);
        head.zRot = -0.12F * sway;
        leftArm.xRot = -0.9F + 0.65F * sway;
        rightArm.xRot = -0.9F - 0.65F * sway;
        leftArm.zRot = -0.65F - 0.35F * sway;
        rightArm.zRot = 0.65F - 0.35F * sway;
        leftLeg.xRot = 0.32F * sway;
        rightLeg.xRot = -0.32F * sway;
        leftTendril.zRot += 0.2F * sway;
        rightTendril.zRot -= 0.2F * sway;
    }
}
