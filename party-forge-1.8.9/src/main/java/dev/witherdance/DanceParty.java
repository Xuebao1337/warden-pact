package dev.witherdance;
import java.util.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.*;
import net.minecraft.world.WorldServer;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
/** All state and mutations are confined to the main server thread. */
public final class DanceParty {
    public static final int COUNT=5,LIFETIME=600,COOLDOWN=100;
    private static final int[] MELODY={0,4,7,12,7,4,2,7,11,14,11,7,0,7,12,7};
    private final Map<UUID,Party> parties=new HashMap<UUID,Party>();
    private final Map<UUID,Long> nextStart=new HashMap<UUID,Long>();
    private static final class Party {
        final EntityPlayerMP owner; final WorldServer world; final long started; final List<DancingWither> dancers;
        Party(EntityPlayerMP owner,List<DancingWither> dancers) {
            this.owner=owner; this.world=(WorldServer)owner.worldObj; this.started=world.getTotalWorldTime(); this.dancers=dancers;
        }
    }
    public enum Result { STARTED,DISMISSED,COOLDOWN,NO_ROOM,UNAVAILABLE }
    public Result toggle(EntityPlayerMP owner) {
        if(owner==null||!owner.isEntityAlive()||owner.isSpectator()||!(owner.worldObj instanceof WorldServer)) return Result.UNAVAILABLE;
        UUID id=owner.getUniqueID(); Party previous=parties.get(id);
        if(previous!=null) {
            boolean alive=false; for(DancingWither dancer:previous.dancers) alive|=!dancer.isDead;
            dismiss(id); if(alive) { message(owner,"dismissed"); return Result.DISMISSED; }
        }
        WorldServer world=(WorldServer)owner.worldObj;
        long now=world.getMinecraftServer().worldServerForDimension(0).getTotalWorldTime(); Long next=nextStart.get(id);
        if(next!=null&&now<next) { message(owner,"cooldown"); return Result.COOLDOWN; }
        List<DancingWither> dancers=new ArrayList<DancingWither>();
        for(int index=0;index<COUNT;index++) {
            DancingWither dancer=new DancingWither(world);
            if(!place(world,owner,dancer,dancers,index)) {
                dancer.setDead(); for(DancingWither chosen:dancers) chosen.setDead(); message(owner,"no_room"); return Result.NO_ROOM;
            }
            dancer.bind(owner,index,LIFETIME); dancers.add(dancer);
        }
        for(DancingWither dancer:dancers) if(!world.spawnEntityInWorld(dancer)) {
            for(DancingWither chosen:dancers) chosen.setDead(); return Result.UNAVAILABLE;
        }
        parties.put(id,new Party(owner,dancers)); nextStart.put(id,now+COOLDOWN);
        owner.addChatMessage(new ChatComponentTranslation("message.witherdance.started",COUNT,LIFETIME/20)); return Result.STARTED;
    }
    private static void message(EntityPlayerMP owner,String key) { owner.addChatMessage(new ChatComponentTranslation("message.witherdance."+key)); }
    private static boolean place(WorldServer world,EntityPlayerMP owner,DancingWither dancer,List<DancingWither> chosen,int index) {
        for(int turn=0;turn<4;turn++) {
            double angle=Math.toRadians(owner.rotationYaw+turn*90-72+index*36);
            double x=owner.posX-Math.sin(angle)*4.5,z=owner.posZ+Math.cos(angle)*4.5;
            for(int dy:new int[]{0,1,-1,2,-2}) {
                BlockPos feet=new BlockPos(x,owner.posY+dy,z); dancer.setPosition(x,feet.getY(),z);
                AxisAlignedBB room=dancer.getEntityBoundingBox().addCoord(0,.35,0);
                if(!loadedAndInside(world,room)) continue;
                if(!world.doesBlockHaveSolidTopSurface(world,feet.down())||world.isAnyLiquid(room)
                        ||!world.getCollidingBoundingBoxes(dancer,room).isEmpty()) continue;
                boolean overlap=false;
                for(DancingWither other:chosen) overlap|=other.getEntityBoundingBox().expand(.2,.2,.2).intersectsWith(room);
                if(!overlap) return true;
            }
        }
        return false;
    }
    static boolean loadedAndInside(World world,AxisAlignedBB room) {
        WorldBorder border=world.getWorldBorder();
        if(room.minY<1||room.maxY>=256||room.minX<border.minX()||room.maxX>border.maxX()
                ||room.minZ<border.minZ()||room.maxZ>border.maxZ()) return false;
        // Legacy collision queries inspect entities up to two blocks beyond the room.
        for(int cx=((int)Math.floor(room.minX-2))>>4;cx<=((int)Math.floor(room.maxX+2))>>4;cx++)
            for(int cz=((int)Math.floor(room.minZ-2))>>4;cz<=((int)Math.floor(room.maxZ+2))>>4;cz++)
                if(!world.isBlockLoaded(new BlockPos(cx*16,(int)room.minY,cz*16),false)) return false;
        return true;
    }
    public List<DancingWither> dancers(EntityPlayerMP player) {
        Party party=parties.get(player.getUniqueID());
        return party==null?Collections.<DancingWither>emptyList():Collections.unmodifiableList(party.dancers);
    }
    public void dismiss(UUID id) {
        Party party=parties.remove(id); if(party!=null) for(DancingWither dancer:party.dancers) dancer.setDead();
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        dismiss(event.player.getUniqueID()); nextStart.remove(event.player.getUniqueID());
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END) return;
        Iterator<Map.Entry<UUID,Party>> iterator=parties.entrySet().iterator();
        while(iterator.hasNext()) {
            Party party=iterator.next().getValue(); long elapsed=party.world.getTotalWorldTime()-party.started;
            boolean alive=false; for(DancingWither dancer:party.dancers) alive|=!dancer.isDead;
            if(!party.owner.isEntityAlive()||party.owner.isSpectator()||party.owner.worldObj!=party.world
                    ||party.owner.dimension!=party.world.provider.getDimensionId()||elapsed>=LIFETIME||!alive) {
                for(DancingWither dancer:party.dancers) dancer.setDead(); iterator.remove(); continue;
            }
            if(elapsed%10==0) {
                DancingWither leader=null; for(DancingWither dancer:party.dancers) if(!dancer.isDead) { leader=dancer; break; }
                int note=MELODY[(int)(elapsed/10)%MELODY.length];
                party.world.playSoundEffect(leader.posX,leader.posY,leader.posZ,"note.pling",.45F,(float)Math.pow(2,(note-12)/12.0));
            }
        }
        Iterator<Map.Entry<UUID,Long>> cooldown=nextStart.entrySet().iterator();
        long now=net.minecraft.server.MinecraftServer.getServer().worldServerForDimension(0).getTotalWorldTime();
        while(cooldown.hasNext()) if(cooldown.next().getValue()<=now) cooldown.remove();
    }
    public void clear() {
        for(Party party:parties.values()) for(DancingWither dancer:party.dancers) dancer.setDead(); parties.clear(); nextStart.clear();
    }
}
