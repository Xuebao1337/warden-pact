package dev.witherpact.client;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;

/** Vanilla armor UVs, with real raised plates following each animated limb. */
public final class PactArmorModel extends ModelBiped {
    private static final PactArmorModel OUTER=new PactArmorModel(false);
    private static final PactArmorModel LEGS=new PactArmorModel(true);
    private PactArmorModel(boolean leggings) {
        super(leggings?.5F:1F,0,64,32);
        if(leggings) {
            plate(bipedRightLeg,0,16,-2,5,-2.9F,4,3,1);
            plate(bipedLeftLeg,0,16,-2,5,-2.9F,4,3,1);
        } else {
            plate(bipedHead,0,0,-4,-7,-5,8,2,1);
            plate(bipedHead,0,0,-5,-4,-4,2,4,2);
            plate(bipedHead,0,0,3,-4,-4,2,4,2);
            plate(bipedBody,20,20,-4,1,-3.2F,8,7,1);
            plate(bipedRightArm,40,16,-3.5F,-2.7F,-2.7F,5,4,5);
            plate(bipedLeftArm,40,16,-1.5F,-2.7F,-2.7F,5,4,5);
            plate(bipedRightLeg,0,20,-2,9,-4,4,3,2);
            plate(bipedLeftLeg,0,20,-2,9,-4,4,3,2);
        }
    }
    private void plate(ModelRenderer parent,int u,int v,float x,float y,float z,int w,int h,int d) {
        ModelRenderer part=new ModelRenderer(this,u,v);
        part.addBox(x,y,z,w,h,d,.08F); parent.addChild(part);
    }
    public static ModelBiped forSlot(int slot,ModelBiped original) {
        PactArmorModel model=slot==2?LEGS:OUTER;
        model.setModelAttributes(original);
        // Forge 1.8.9 passes equipment slots: 1 boots, 2 legs, 3 chest, 4 head.
        model.bipedHead.showModel=slot==4;
        model.bipedHeadwear.showModel=false;
        model.bipedBody.showModel=slot==3||slot==2;
        model.bipedRightArm.showModel=slot==3;
        model.bipedLeftArm.showModel=slot==3;
        model.bipedRightLeg.showModel=slot==2||slot==1;
        model.bipedLeftLeg.showModel=slot==2||slot==1;
        return model;
    }
}
