package dev.witherdance;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;
/** Coordinates/counts are server controlled; the client sends only a toggle. */
public final class DanceNetwork {
    public static final SimpleNetworkWrapper CHANNEL=NetworkRegistry.INSTANCE.newSimpleChannel(WitherDance.ID);
    public static void init() { CHANNEL.registerMessage(Handler.class,Toggle.class,0,Side.SERVER); }
    public static final class Toggle implements IMessage {
        @Override public void toBytes(ByteBuf buffer) {}
        @Override public void fromBytes(ByteBuf buffer) {
            if(buffer.readableBytes()!=0) throw new IllegalArgumentException("Toggle has no fields");
        }
    }
    public static final class Handler implements IMessageHandler<Toggle,IMessage> {
        @Override public IMessage onMessage(Toggle message,final MessageContext context) {
            FMLCommonHandler.instance().getWorldThread(context.netHandler).addScheduledTask(new Runnable() {
                @Override public void run() {
                    EntityPlayerMP player=context.getServerHandler().playerEntity;
                    WitherDance.PARTY.toggle(player);
                }
            }); return null;
        }
    }
}
