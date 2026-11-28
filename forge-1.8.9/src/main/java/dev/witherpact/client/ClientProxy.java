package dev.witherpact.client;

import java.util.*;
import dev.witherpact.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelWither;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.tileentity.RenderWitherSkull;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityWitherSkull;
import net.minecraft.item.Item;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.*;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

public final class ClientProxy extends CommonProxy {
    private final KeyBinding descend=new KeyBinding("key.witherpact.descend",Keyboard.KEY_X,"key.categories.witherpact");
    private final Map<Integer,Long> sensed=new HashMap<Integer,Long>();
    private World lastWorld;
    private int ticks;
    @Override public void init() {
        Item[] items={WitherPact.shard,WitherPact.sword,WitherPact.reins,EchoGear.helmet,EchoGear.chest,EchoGear.legs,EchoGear.boots,
                EchoGear.pickaxe,EchoGear.axe,EchoGear.shovel,EchoGear.hoe,Item.getItemFromBlock(EchoGear.path)};
        String[] names={"echo_shard","summoning_sword","wither_reins","echo_helmet","echo_chestplate","echo_leggings","echo_boots",
                "echo_pickaxe","echo_axe","echo_shovel","echo_hoe","echo_path"};
        for(int i=0;i<items.length;i++) Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(items[i],0,
                new ModelResourceLocation(WitherPact.ID+":"+names[i],"inventory"));
        RenderingRegistry.registerEntityRenderingHandler(PactWither.class,new IRenderFactory<PactWither>() {
            @Override public Render<? super PactWither> createRenderFor(RenderManager manager) { return new WitherRenderer(manager); }
        });
        RenderingRegistry.registerEntityRenderingHandler(PactSkull.class,new IRenderFactory<PactSkull>() {
            @Override public Render<? super PactSkull> createRenderFor(RenderManager manager) { return new SkullRenderer(manager); }
        });
        ClientRegistry.registerKeyBinding(descend);
        MinecraftForge.EVENT_BUS.register(this); FMLCommonHandler.instance().bus().register(this);
    }
    @Override public EntityPlayer localPlayer() { return Minecraft.getMinecraft().thePlayer; }
    @Override public int verticalInput() {
        Minecraft mc=Minecraft.getMinecraft(); if(mc.currentScreen!=null) return 0;
        return (mc.gameSettings.keyBindJump.isKeyDown()?1:0)-(descend.isKeyDown()?1:0);
    }
    @Override public void sense(final int[] ids,final int duration) {
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override public void run() {
                World world=Minecraft.getMinecraft().theWorld; if(world==null) return;
                if(world!=lastWorld) { sensed.clear(); lastWorld=world; }
                for(int id:ids) sensed.put(id,world.getTotalWorldTime()+duration);
            }
        });
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END) return;
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.theWorld!=lastWorld) { sensed.clear(); lastWorld=mc.theWorld; }
        if(mc.thePlayer!=null&&mc.thePlayer.ridingEntity instanceof PactWither&&++ticks%5==0)
            PactNetwork.CHANNEL.sendToServer(new PactNetwork.Control(verticalInput()));
    }
    @SubscribeEvent public void render(RenderWorldLastEvent event) {
        Minecraft mc=Minecraft.getMinecraft(); World world=mc.theWorld;
        Entity camera=mc.getRenderViewEntity(); if(world==null||camera==null||sensed.isEmpty()) return;
        double x=camera.lastTickPosX+(camera.posX-camera.lastTickPosX)*event.partialTicks;
        double y=camera.lastTickPosY+(camera.posY-camera.lastTickPosY)*event.partialTicks;
        double z=camera.lastTickPosZ+(camera.posZ-camera.lastTickPosZ)*event.partialTicks;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS); GL11.glPushMatrix();
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false); GL11.glEnable(GL11.GL_BLEND); GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glLineWidth(2); GL11.glColor4f(.73F,.45F,1F,.9F);
            Iterator<Map.Entry<Integer,Long>> it=sensed.entrySet().iterator();
            while(it.hasNext()) {
                Map.Entry<Integer,Long> entry=it.next(); Entity entity=world.getEntityByID(entry.getKey());
                if(entry.getValue()<=world.getTotalWorldTime()||entity==null||entity.isDead) { it.remove(); continue; }
                double dx=(entity.lastTickPosX-entity.posX)*(1-event.partialTicks)-x;
                double dy=(entity.lastTickPosY-entity.posY)*(1-event.partialTicks)-y;
                double dz=(entity.lastTickPosZ-entity.posZ)*(1-event.partialTicks)-z;
                RenderGlobal.drawSelectionBoundingBox(entity.getEntityBoundingBox().expand(.04,.04,.04).offset(dx,dy,dz));
            }
        } finally { GL11.glPopMatrix(); GL11.glPopAttrib(); }
    }
    private static final class WitherRenderer extends RenderLiving<PactWither> {
        private static final ResourceLocation ALLY=new ResourceLocation(WitherPact.ID,"textures/entity/pact_wither.png"),
                MOUNT=new ResourceLocation(WitherPact.ID,"textures/entity/wither_mount.png");
        WitherRenderer(RenderManager manager) { super(manager,new ModelWither(0),.75F); }
        @Override protected ResourceLocation getEntityTexture(PactWither entity) { return entity.mount()?MOUNT:ALLY; }
        @Override protected void preRenderCallback(PactWither entity,float partialTicks) {
            float size=entity.mount()?1.2F:1F; GlStateManager.scale(size,size,size);
        }
    }
    private static final class SkullRenderer extends RenderWitherSkull {
        private static final ResourceLocation TEXTURE=new ResourceLocation(WitherPact.ID,"textures/entity/pact_wither.png");
        SkullRenderer(RenderManager manager) { super(manager); }
        @Override protected ResourceLocation getEntityTexture(EntityWitherSkull entity) { return TEXTURE; }
    }
}
