package dev.wardendance.test;

import dev.wardendance.client.DancingWardenModel;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.monster.warden.WardenModel;
import net.minecraft.client.renderer.entity.state.WardenRenderState;

/** Exercises real model parts and all emissive-layer variants without starting a GPU client. */
public final class DanceAnimationCheck {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        List<LayerDefinition> layers = List.of(WardenModel.createBodyLayer(), WardenModel.createBioluminescentLayer(),
                WardenModel.createHeartLayer(), WardenModel.createTendrilsLayer(), WardenModel.createPulsatingSpotsLayer());
        for (float time : new float[]{0, 2.5F, 5, 10, 27, 86}) {
            float[] reference = null;
            for (LayerDefinition definition : layers) {
                ModelPart root = definition.bakeRoot();
                DancingWardenModel model = new DancingWardenModel(root);
                WardenRenderState state = new WardenRenderState();
                state.ageInTicks = time;
                model.setupAnim(state);
                ModelPart bone = root.getChild("bone"), body = bone.getChild("body");
                ModelPart left = body.getChild("left_arm"), right = body.getChild("right_arm");
                ModelPart head = body.getChild("head");
                float[] pose = {body.zRot, body.yRot, left.xRot, left.zRot, right.xRot, right.zRot,
                        head.xRot, head.zRot, bone.getChild("left_leg").xRot, bone.getChild("right_leg").xRot};
                for (float value : pose) check(Float.isFinite(value), "Animation must remain finite");
                check(Math.abs(pose[8] + pose[9]) < 0.00001F, "Alternating leg kicks");
                if (reference == null) reference = pose;
                else for (int i = 0; i < pose.length; i++) check(Math.abs(pose[i] - reference[i]) < 0.00001F, "Emissive geometry must match skin");
                model.setupAnim(state);
                check(Math.abs(left.xRot - pose[2]) < 0.00001F, "Repeated frames must not accumulate transforms");
                if (time == 5) check(Math.abs(left.xRot - right.xRot) > 1, "Visible arm swing at quarter beat");
            }
        }
        System.out.println("DANCE_ANIMATION_CHECKS_PASSED=" + checks);
    }
}
