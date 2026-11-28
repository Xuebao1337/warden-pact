package dev.witherdance.client;
import dev.witherdance.DancingWither;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.ModelWither;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
/** Retains original wither geometry/UVs, with three-head dance choreography. */
public final class DancingWitherModel extends ModelWither {
    private final ModelRenderer[] bodies,heads;
    public DancingWitherModel() {
        super(0);
        bodies=ReflectionHelper.getPrivateValue(ModelWither.class,this,"field_82905_a");
        heads=ReflectionHelper.getPrivateValue(ModelWither.class,this,"field_82904_b");
    }
    @Override public void setLivingAnimations(EntityLivingBase entity,float strafe,float forward,float partialTick) {
        // Vanilla casts to EntityWither. Our harmless performer is an EntityCreature.
    }
    @Override public void setRotationAngles(float limbSwing,float amount,float age,float yaw,float pitch,float scale,Entity entity) {
        super.setRotationAngles(limbSwing,amount,age,yaw,pitch,scale,entity);
        int index=entity instanceof DancingWither?((DancingWither)entity).danceIndex():0;
        double beat=(age+index*2)*Math.PI/10;
        for(int i=0;i<heads.length;i++) {
            heads[i].rotateAngleX=(float)Math.sin(beat+i*.65)*.24F;
            heads[i].rotateAngleY=(float)Math.sin(beat*.5+i*.8)*.45F;
            heads[i].rotateAngleZ=(float)Math.sin(beat+i*.7)*.20F;
        }
        bodies[0].rotateAngleZ=(float)Math.sin(beat)*.12F;
        bodies[1].rotateAngleZ=(float)Math.sin(beat+.5)*.2F;
        bodies[2].rotateAngleZ=(float)Math.sin(beat+1)*.28F;
        bodies[1].rotateAngleX+=(float)Math.cos(beat)*.12F;
        bodies[2].rotateAngleX+=(float)Math.cos(beat+.6)*.18F;
    }
}
