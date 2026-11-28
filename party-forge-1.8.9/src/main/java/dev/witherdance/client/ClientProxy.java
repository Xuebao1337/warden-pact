package dev.witherdance.client;
import dev.witherdance.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.registry.*;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
public final class ClientProxy extends CommonProxy {
    private final KeyBinding dance=new KeyBinding("key.witherdance.toggle",Keyboard.KEY_G,"key.categories.witherdance");
    @Override public void preInit() {
        RenderingRegistry.registerEntityRenderingHandler(DancingWither.class,new IRenderFactory<DancingWither>() {
            @Override public Render<? super DancingWither> createRenderFor(RenderManager manager) { return new DanceRenderer(manager); }
        });
    }
    @Override public void init() {
        ClientRegistry.registerKeyBinding(dance); FMLCommonHandler.instance().bus().register(this);
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END) return;
        Minecraft mc=Minecraft.getMinecraft(); boolean clicked=false;
        while(dance.isPressed()) clicked=true;
        if(clicked&&mc.thePlayer!=null&&mc.theWorld!=null&&mc.currentScreen==null&&!mc.thePlayer.isSpectator())
            DanceNetwork.CHANNEL.sendToServer(new DanceNetwork.Toggle());
    }
    private static final class DanceRenderer extends RenderLiving<DancingWither> {
        private static final ResourceLocation TEXTURE=new ResourceLocation("minecraft:textures/entity/wither/wither.png");
        DanceRenderer(RenderManager manager) { super(manager,new DancingWitherModel(),.6F); }
        @Override protected ResourceLocation getEntityTexture(DancingWither entity) { return TEXTURE; }
    }
}
