package dev.witherdance;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.registry.EntityRegistry;
@Mod(modid=WitherDance.ID,name="Wither Dance Party",version=WitherDance.VERSION,acceptedMinecraftVersions="[1.8.9]")
public final class WitherDance {
    public static final String ID="witherdance",VERSION="1.0.0";
    public static final DanceParty PARTY=new DanceParty();
    @SidedProxy(clientSide="dev.witherdance.client.ClientProxy",serverSide="dev.witherdance.CommonProxy")
    public static CommonProxy proxy;
    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event) {
        EntityRegistry.registerModEntity(DancingWither.class,"dancing_wither",1,this,80,1,true);
        DanceNetwork.init(); FMLCommonHandler.instance().bus().register(PARTY); proxy.preInit();
    }
    @Mod.EventHandler public void init(FMLInitializationEvent event) { proxy.init(); }
    @Mod.EventHandler public void stopped(FMLServerStoppedEvent event) { PARTY.clear(); }
}
