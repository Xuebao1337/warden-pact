package dev.witherdance;

import java.io.*;
import java.util.*;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.*;
import net.minecraft.entity.*;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.*;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.*;
import net.minecraft.world.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** An optional real-server integration test mod. Never shipped in the release. */
@Mod(modid="witherdance_smoke",name="Wither Dance integration checks",version="1",dependencies="required-after:witherdance")
public final class SmokeTests {
    private final List<String> passed=new ArrayList<String>(),failed=new ArrayList<String>();
    private WorldServer world;
    private SmokePlayer owner,other;
    private boolean rejectThird;
    private int joins;
    private void check(String label,boolean value) {
        if(!value) throw new AssertionError(label);
        passed.add(label); System.out.println("WITHER_DANCE_TEST PASS "+label);
    }
    private void near(String label,double expected,double actual) { check(label,Math.abs(expected-actual)<.0001); }
    private void run(String name,Runnable work) {
        try { reset(); work.run(); }
        catch(Throwable error) { failed.add(name+": "+error); error.printStackTrace(); }
        finally { rejectThird=false; WitherDance.PARTY.clear(); }
    }
    @Mod.EventHandler public void start(FMLServerStartedEvent event) {
        MinecraftServer server=MinecraftServer.getServer(); world=server.worldServerForDimension(0);
        try {
            for(int x=9;x<=11;x++) for(int z=9;z<=11;z++) world.getChunkFromChunkCoords(x,z);
            world.getGameRules().setOrCreateGameRule("doMobSpawning","false");
            owner=new SmokePlayer(world,"DanceOwner"); other=new SmokePlayer(world,"DanceOther");
            world.spawnEntityInWorld(owner); world.spawnEntityInWorld(other); MinecraftForge.EVENT_BUS.register(this);
            run("toggle and cooldown",new Runnable(){ public void run(){ toggle(); }});
            run("harmlessness and stage",new Runnable(){ public void run(){ harmless(); }});
            run("peaceful difficulty",new Runnable(){ public void run(){ peaceful(); }});
            run("lifetime and cleanup",new Runnable(){ public void run(){ cleanup(); }});
            run("safe spawn and rollback",new Runnable(){ public void run(){ safeSpawn(); }});
            run("payload boundary",new Runnable(){ public void run(){ packet(); }});
        } catch(Throwable error) { failed.add("setup: "+error); error.printStackTrace(); }
        finally {
            WitherDance.PARTY.clear(); MinecraftForge.EVENT_BUS.unregister(this);
            Map<String,Object> report=new LinkedHashMap<String,Object>();
            report.put("minecraft","1.8.9"); report.put("forge","11.15.1.2318");
            report.put("passed",passed.size()); report.put("failed",failed.size()); report.put("checks",passed); report.put("failures",failed);
            try(Writer out=new OutputStreamWriter(new FileOutputStream("witherdance-smoke-results.json"),"UTF-8")) {
                new GsonBuilder().setPrettyPrinting().create().toJson(report,out);
            } catch(IOException error) { error.printStackTrace(); }
            System.out.println("WITHER_DANCE_TEST RESULTS passed="+passed.size()+" failed="+failed.size());
            server.initiateShutdown();
        }
    }
    private void reset() {
        WitherDance.PARTY.clear();
        world.getWorldInfo().setDifficulty(EnumDifficulty.NORMAL);
        owner.worldObj=world; owner.dimension=0; owner.isDead=false; owner.setHealth(20);
        owner.theItemInWorldManager.setGameType(WorldSettings.GameType.SURVIVAL);
        owner.setPosition(160.5,64,160.5); owner.rotationYaw=0;
        other.setPosition(171.5,64,160.5); other.isDead=false; other.setHealth(20);
        for(Entity entity:new ArrayList<Entity>(world.loadedEntityList)) if(!(entity instanceof EntityPlayer)) entity.setDead();
        for(int x=150;x<=172;x++) for(int z=150;z<=172;z++) {
            world.setBlockState(new BlockPos(x,63,z),Blocks.stone.getDefaultState(),2);
            for(int y=61;y<=71;y++) if(y!=63) world.setBlockToAir(new BlockPos(x,y,z));
        }
    }
    private List<DancingWither> crew() { return new ArrayList<DancingWither>(WitherDance.PARTY.dancers(owner)); }
    private int alive() {
        int result=0; for(Entity entity:world.loadedEntityList) if(entity instanceof DancingWither&&!entity.isDead) result++;
        return result;
    }
    private void advance(int ticks) {
        for(int i=0;i<ticks;i++) {
            world.getWorldInfo().setWorldTotalTime(world.getTotalWorldTime()+1);
            for(Entity entity:new ArrayList<Entity>(world.loadedEntityList)) if(entity instanceof DancingWither&&!entity.isDead) entity.onUpdate();
            WitherDance.PARTY.tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
        }
    }
    private void toggle() {
        check("first summon starts",WitherDance.PARTY.toggle(owner)==DanceParty.Result.STARTED);
        check("exactly five performers",crew().size()==5&&alive()==5);
        Set<Integer> phases=new HashSet<Integer>();
        for(DancingWither dancer:crew()) { phases.add(dancer.danceIndex()); check("performer bound to owner "+dancer.danceIndex(),dancer.owner()==owner); }
        check("five synchronized dance phases",phases.size()==5);
        check("toggle dismisses immediately",WitherDance.PARTY.toggle(owner)==DanceParty.Result.DISMISSED&&alive()==0);
        check("cooldown prevents spam",WitherDance.PARTY.toggle(owner)==DanceParty.Result.COOLDOWN&&alive()==0);
        advance(99); check("cooldown lasts 100 ticks",WitherDance.PARTY.toggle(owner)==DanceParty.Result.COOLDOWN);
        advance(1); check("cooldown expires",WitherDance.PARTY.toggle(owner)==DanceParty.Result.STARTED);
        check("one crew after repeated toggle",crew().size()==5&&alive()==5);
        check("second player gets own crew",WitherDance.PARTY.toggle(other)==DanceParty.Result.STARTED&&WitherDance.PARTY.dancers(other).size()==5);
        WitherDance.PARTY.toggle(owner);
        check("dismissal preserves other crew",WitherDance.PARTY.dancers(other).size()==5&&alive()==5);
    }
    private void harmless() {
        check("harmless setup",WitherDance.PARTY.toggle(owner)==DanceParty.Result.STARTED);
        DancingWither dancer=crew().get(0); double x=dancer.posX,z=dancer.posZ,y=dancer.posY;
        check("performer is not hostile EntityMob",!EntityMob.class.isAssignableFrom(dancer.getClass()));
        check("AI disabled",dancer.isAIDisabled()&&dancer.tasks.taskEntries.isEmpty()&&dancer.targetTasks.taskEntries.isEmpty());
        check("no collision or pushing",!dancer.canBePushed()&&!dancer.canBeCollidedWith()&&!dancer.isPushedByWater());
        float health=dancer.getHealth();
        check("player damage ignored",!dancer.attackEntityFrom(DamageSource.causePlayerDamage(owner),999));
        check("environment damage ignored",!dancer.attackEntityFrom(DamageSource.outOfWorld,999));
        near("health unchanged",health,dancer.getHealth());
        EntityZombie target=new EntityZombie(world); target.setPosition(dancer.posX+1,64,dancer.posZ); world.spawnEntityInWorld(target);
        float targetHealth=target.getHealth(); dancer.setAttackTarget(target);
        check("target assignment ignored",dancer.getAttackTarget()==null);
        check("melee attack disabled",!dancer.attackEntityAsMob(target));
        check("cannot attack classes",!dancer.canAttackClass(EntityPlayer.class));
        check("not saved as ordinary entity",!dancer.writeToNBTOptional(new NBTTagCompound()));
        check("not saved as mount",!dancer.writeMountToNBT(new NBTTagCompound()));
        advance(5); check("dancer visibly bobs",dancer.posY>y+.2); near("stage x fixed",x,dancer.posX); near("stage z fixed",z,dancer.posZ);
        float turn=dancer.rotationYaw; advance(5); check("dancer turns",Math.abs(turn-dancer.rotationYaw)>.1);
        advance(190); near("nearby mob receives no damage",targetHealth,target.getHealth());
        check("no skull projectiles spawned",world.getEntitiesWithinAABB(net.minecraft.entity.projectile.EntityWitherSkull.class,owner.getEntityBoundingBox().expand(20,20,20)).isEmpty());
        check("no terrain destroyed",world.getBlockState(new BlockPos(160,63,160)).getBlock()==Blocks.stone);
        check("still five performers after 200 ticks",alive()==5);
        BlockPos block=new BlockPos(dancer.posX,dancer.posY+1,dancer.posZ); world.setBlockState(block,Blocks.stone.getDefaultState(),2);
        advance(1); check("terrain obstruction removes performer",dancer.isDead); world.setBlockToAir(block);
    }
    private void peaceful() {
        world.getWorldInfo().setDifficulty(EnumDifficulty.PEACEFUL);
        check("peaceful summon starts",WitherDance.PARTY.toggle(owner)==DanceParty.Result.STARTED);
        advance(40); check("peaceful vanilla onUpdate preserves five",alive()==5);
        check("peaceful has no forced difficulty change",world.getDifficulty()==EnumDifficulty.PEACEFUL);
    }
    private void cleanup() {
        check("expiry setup",WitherDance.PARTY.toggle(owner)==DanceParty.Result.STARTED);
        advance(599); check("crew alive until lifetime boundary",alive()==5);
        advance(1); check("crew expires at thirty seconds",alive()==0&&crew().isEmpty());
        WitherDance.PARTY.clear(); WitherDance.PARTY.toggle(owner); owner.setHealth(0); advance(1);
        check("owner death clears crew",alive()==0&&crew().isEmpty());
        owner.setHealth(20); owner.isDead=false; WitherDance.PARTY.clear(); WitherDance.PARTY.toggle(owner);
        owner.setPosition(300,64,300); advance(1); check("owner distance clears crew",alive()==0); owner.setPosition(160.5,64,160.5);
        WitherDance.PARTY.clear(); WitherDance.PARTY.toggle(owner); owner.dimension=1; advance(1);
        check("owner dimension change clears crew",alive()==0&&crew().isEmpty()); owner.dimension=0;
        WitherDance.PARTY.clear(); WitherDance.PARTY.toggle(owner);
        WitherDance.PARTY.logout(new PlayerEvent.PlayerLoggedOutEvent(owner));
        check("logout clears crew immediately",alive()==0&&crew().isEmpty());
        check("logout clears cooldown",WitherDance.PARTY.toggle(owner)==DanceParty.Result.STARTED);
        WitherDance.PARTY.clear(); check("server stop clears all crew",alive()==0&&crew().isEmpty());
        owner.theItemInWorldManager.setGameType(WorldSettings.GameType.SPECTATOR);
        check("spectator cannot summon",WitherDance.PARTY.toggle(owner)==DanceParty.Result.UNAVAILABLE);
    }
    private void safeSpawn() {
        owner.setPosition(160,254,160);
        check("world ceiling prevents spawn",WitherDance.PARTY.toggle(owner)==DanceParty.Result.NO_ROOM&&alive()==0);
        owner.setPosition(160.5,64,160.5);
        check("failed space check consumes no cooldown",WitherDance.PARTY.toggle(owner)==DanceParty.Result.STARTED);
        WitherDance.PARTY.clear();
        for(int x=154;x<=166;x++) for(int z=154;z<=166;z++) for(int y=64;y<=70;y++) world.setBlockState(new BlockPos(x,y,z),Blocks.stone.getDefaultState(),2);
        check("blocked stage fails all or none",WitherDance.PARTY.toggle(owner)==DanceParty.Result.NO_ROOM&&alive()==0);
        reset(); rejectThird=true; joins=0;
        check("spawn cancellation returns unavailable",WitherDance.PARTY.toggle(owner)==DanceParty.Result.UNAVAILABLE);
        check("partial spawn rollback removes all",alive()==0&&crew().isEmpty()); rejectThird=false;
        check("spawn rollback consumes no cooldown",WitherDance.PARTY.toggle(owner)==DanceParty.Result.STARTED);
        WitherDance.PARTY.clear();
        owner.setPosition(2000000,64,2000000);
        int before=world.loadedEntityList.size();
        check("unloaded terrain rejected",WitherDance.PARTY.toggle(owner)==DanceParty.Result.NO_ROOM);
        check("unloaded terrain creates no performers",world.loadedEntityList.size()==before);
    }
    @SubscribeEvent public void joining(EntityJoinWorldEvent event) {
        if(rejectThird&&event.entity instanceof DancingWither&&++joins==3) event.setCanceled(true);
    }
    private void packet() {
        ByteBuf buffer=Unpooled.buffer(); new DanceNetwork.Toggle().toBytes(buffer);
        check("toggle serializes zero fields",buffer.readableBytes()==0); new DanceNetwork.Toggle().fromBytes(buffer); buffer.release();
        buffer=Unpooled.buffer(); buffer.writeInt(5); boolean rejected=false;
        try { new DanceNetwork.Toggle().fromBytes(buffer); } catch(IllegalArgumentException expected) { rejected=true; }
        finally { buffer.release(); }
        check("extra client fields rejected",rejected);
    }
    private static final class SmokePlayer extends FakePlayer {
        SmokePlayer(WorldServer world,String name) {
            super(world,new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),name));
            playerNetServerHandler=new NetHandlerPlayServer(MinecraftServer.getServer(),new NetworkManager(EnumPacketDirection.SERVERBOUND),this) {
                @Override public void sendPacket(Packet packet) {}
            };
            theItemInWorldManager.setGameType(WorldSettings.GameType.SURVIVAL);
        }
        @Override public void addChatMessage(IChatComponent component) {}
    }
}
