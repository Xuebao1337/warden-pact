package dev.witherdance.test;

import dev.witherdance.client.DancingWitherModel;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.ModelWither;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

/** Uses the real vanilla model without starting OpenGL or downloading assets. */
public final class DanceModelCheck {
    private static int checks;
    private static void check(boolean value,String label) { if(!value) throw new AssertionError(label); checks++; }
    public static void main(String[] args) {
        DancingWitherModel model=new DancingWitherModel();
        ModelRenderer[] heads=ReflectionHelper.getPrivateValue(ModelWither.class,model,"field_82904_b");
        ModelRenderer[] bodies=ReflectionHelper.getPrivateValue(ModelWither.class,model,"field_82905_a");
        check(heads.length==3&&bodies.length==3,"six vanilla pieces");
        float[] first=new float[6];
        for(int tick=0;tick<=100;tick++) {
            model.setLivingAnimations(null,0,0,.5F);
            model.setRotationAngles(0,0,tick+.5F,0,0,1/16F,null);
            for(ModelRenderer part:heads) {
                check(Float.isFinite(part.rotateAngleX)&&Float.isFinite(part.rotateAngleY)&&Float.isFinite(part.rotateAngleZ),"head finite");
                check(Math.abs(part.rotateAngleX)<=.241&&Math.abs(part.rotateAngleY)<=.451&&Math.abs(part.rotateAngleZ)<=.201,"head bounded");
            }
            for(int i=0;i<bodies.length;i++) {
                check(Float.isFinite(bodies[i].rotateAngleX)&&Float.isFinite(bodies[i].rotateAngleZ),"body finite");
                check(Math.abs(bodies[i].rotateAngleZ)<=.281,"body roll bounded");
                if(tick==0) { first[i]=heads[i].rotateAngleZ; first[i+3]=bodies[i].rotateAngleZ; }
                if(tick==5) { check(Math.abs(first[i]-heads[i].rotateAngleZ)>.001,"head animated"); check(Math.abs(first[i+3]-bodies[i].rotateAngleZ)>.001,"body animated"); }
            }
        }
        model.setRotationAngles(0,0,.5F,0,0,1/16F,null);
        for(int i=0;i<3;i++) check(Math.abs(first[i+3]-bodies[i].rotateAngleZ)<.00001,"no model roll accumulation");
        System.out.println("WITHER_DANCE_MODEL assertions="+checks+" failed=0");
    }
}
