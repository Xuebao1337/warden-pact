package dev.witherpact;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;

public final class PactNetwork {
    public static final SimpleNetworkWrapper CHANNEL=NetworkRegistry.INSTANCE.newSimpleChannel(WitherPact.ID);
    public static void init() {
        CHANNEL.registerMessage(ControlHandler.class,Control.class,0,Side.SERVER);
        CHANNEL.registerMessage(SenseHandler.class,Sense.class,1,Side.CLIENT);
    }
    public static final class Control implements IMessage {
        private int vertical;
        public Control() {} public Control(int vertical) { this.vertical=vertical; }
        @Override public void fromBytes(ByteBuf buffer) { vertical=Math.max(-1,Math.min(1,buffer.readByte())); }
        @Override public void toBytes(ByteBuf buffer) { buffer.writeByte(vertical); }
    }
    public static final class ControlHandler implements IMessageHandler<Control,IMessage> {
        @Override public IMessage onMessage(final Control message,final MessageContext context) {
            FMLCommonHandler.instance().getWorldThread(context.netHandler).addScheduledTask(new Runnable() {
                @Override public void run() {
                    EntityPlayerMP player=context.getServerHandler().playerEntity;
                    if(player.ridingEntity instanceof PactWither) ((PactWither)player.ridingEntity).controls(player,message.vertical);
                }
            }); return null;
        }
    }
    public static final class Sense implements IMessage {
        public int[] ids=new int[0]; public int ticks;
        public Sense() {} public Sense(int[] ids,int ticks) { this.ids=ids; this.ticks=ticks; }
        @Override public void fromBytes(ByteBuf buffer) {
            int length=buffer.readUnsignedShort();
            if(length>128||buffer.readableBytes()<length*4+2) throw new IllegalArgumentException("Invalid sense packet");
            ids=new int[length]; for(int i=0;i<length;i++) ids[i]=buffer.readInt();
            ticks=Math.min(100,buffer.readUnsignedShort());
        }
        @Override public void toBytes(ByteBuf buffer) {
            buffer.writeShort(ids.length); for(int id:ids) buffer.writeInt(id); buffer.writeShort(ticks);
        }
    }
    public static final class SenseHandler implements IMessageHandler<Sense,IMessage> {
        @Override public IMessage onMessage(final Sense message,MessageContext context) {
            WitherPact.proxy.sense(message.ids,message.ticks); return null;
        }
    }
}
