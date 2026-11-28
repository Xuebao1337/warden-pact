package dev.witherpact;

import java.io.*;
import java.lang.reflect.Method;
import java.util.*;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.player.*;
import net.minecraft.init.*;
import net.minecraft.inventory.*;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.*;
import net.minecraft.potion.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.*;
import net.minecraft.world.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Runs only with -Psmoke. Not part of the distributable mod JAR. */
@Mod(modid="witherpact_smoke",name="Wither Pact integration tests",version="1",dependencies="required-after:witherpact")
public final class SmokeTests {
    private final List<String> passed=new ArrayList<String>(),failed=new ArrayList<String>();
    private WorldServer world;
    private SmokePlayer owner,other;
    private void check(String name,boolean condition) {
        if(!condition) throw new AssertionError(name);
        passed.add(name); System.out.println("WITHER_TEST PASS "+name);
    }
    private void near(String name,double expected,double actual) { check(name,Math.abs(expected-actual)<.0001); }
    private void group(String name,Runnable work) {
        try { work.run(); } catch(Throwable failure) {
            failed.add(name+": "+failure); failure.printStackTrace();
        } finally {
            WitherPact.EVENTS.clear();
            if(owner!=null) {
                owner.mountEntity(null); owner.setSneaking(false); owner.onGround=true;
                owner.setPosition(160.5,64,160.5); owner.setHealth(20); owner.hurtResistantTime=0;
                owner.removePotionEffect(Potion.nightVision.id);
                for(int slot=0;slot<4;slot++) owner.inventory.armorInventory[slot]=null;
                tick();
            }
            for(Entity entity:new ArrayList<Entity>(world.loadedEntityList))
                if(!(entity instanceof EntityPlayer)) entity.setDead();
        }
    }
    @Mod.EventHandler public void started(FMLServerStartedEvent event) {
        final MinecraftServer server=MinecraftServer.getServer(); world=server.worldServerForDimension(0);
        try {
            for(int x=9;x<=12;x++) for(int z=9;z<=12;z++) world.getChunkFromChunkCoords(x,z);
            for(int x=148;x<=184;x++) for(int z=148;z<=176;z++) {
                world.setBlockState(new BlockPos(x,63,z),Blocks.stone.getDefaultState(),2);
                for(int y=64;y<=76;y++) world.setBlockToAir(new BlockPos(x,y,z));
            }
            world.getGameRules().setOrCreateGameRule("doMobSpawning","false");
            world.getWorldInfo().setDifficulty(EnumDifficulty.NORMAL);
            owner=new SmokePlayer(world,"PactTestOwner"); other=new SmokePlayer(world,"PactTestOther");
            owner.setPosition(160.5,64,160.5); other.setPosition(170.5,64,160.5);
            world.spawnEntityInWorld(owner); world.spawnEntityInWorld(other);
            group("registration and rules",new Runnable(){public void run(){ rules(); }});
            group("upgrade recipes",new Runnable(){public void run(){ recipes(); }});
            group("summons and ownership",new Runnable(){public void run(){ summons(); }});
            group("mount and flight",new Runnable(){public void run(){ mount(); }});
            group("safe projectile",new Runnable(){public void run(){ skull(); }});
            group("armor and enchantments",new Runnable(){public void run(){ armor(); }});
            group("mining and farming",new Runnable(){public void run(){ tools(); }});
            group("confirmed combat",new Runnable(){public void run(){ combat(); }});
            group("packet bounds",new Runnable(){public void run(){ packets(); }});
        } catch(Throwable failure) { failed.add("setup: "+failure); failure.printStackTrace(); }
        finally {
            WitherPact.EVENTS.clear();
            Map<String,Object> report=new LinkedHashMap<String,Object>();
            report.put("minecraft","1.8.9"); report.put("forge","11.15.1.2318");
            report.put("passed",passed.size()); report.put("failed",failed.size()); report.put("checks",passed); report.put("failures",failed);
            try(Writer out=new OutputStreamWriter(new FileOutputStream("witherpact-smoke-results.json"),"UTF-8")) {
                new GsonBuilder().setPrettyPrinting().create().toJson(report,out);
            } catch(IOException error) { error.printStackTrace(); }
            System.out.println("WITHER_TEST RESULTS passed="+passed.size()+" failed="+failed.size());
            server.initiateShutdown();
        }
    }
    private void rules() {
        check("registered sword",Item.itemRegistry.getObject(new ResourceLocation("witherpact:summoning_sword"))==WitherPact.sword);
        check("registered armor and path",EchoGear.helmet!=null&&EchoGear.path!=null);
        near("resonance III lifetime",2100,PactRules.lifetimeTicks(60,3));
        near("resonance III cooldown",220,PactRules.cooldownTicks(20,3));
        near("resonance level clamped",220,PactRules.cooldownTicks(20,99));
        near("cooldown floor",20,PactRules.cooldownTicks(1,3));
        near("full guard IV reduction",6.8,PactRules.guard(10,16));
        double[] diagonal=PactRules.flight(0,1,1,.42);
        near("diagonal speed bounded",.42,Math.sqrt(diagonal[0]*diagonal[0]+diagonal[1]*diagonal[1]));
        double[] rotated=PactRules.flight(90,0,1,.42); near("flight yaw",-.42,rotated[0]);
        check("maximum enchantment levels",WitherPact.resonance.getMaxLevel()==3&&WitherPact.guard.getMaxLevel()==4&&WitherPact.mining.getMaxLevel()==3);
    }
    private InventoryCrafting grid() {
        return new InventoryCrafting(new Container(){ public boolean canInteractWith(EntityPlayer player){return true;} },3,3);
    }
    private void recipes() {
        PactRecipes.Upgrade recipe=new PactRecipes.Upgrade(Items.diamond_sword,WitherPact.sword);
        InventoryCrafting grid=grid(); ItemStack original=new ItemStack(Items.diamond_sword,1,123);
        original.setStackDisplayName("Ancient oath"); original.addEnchantment(Enchantment.sharpness,3);
        grid.setInventorySlotContents(0,original); grid.setInventorySlotContents(8,new ItemStack(WitherPact.shard));
        check("upgrade accepts separated ingredients",recipe.matches(grid,world)); ItemStack result=recipe.getCraftingResult(grid);
        check("upgrade item",result.getItem()==WitherPact.sword); near("upgrade wear preserved",123,result.getItemDamage());
        check("upgrade NBT preserved",original.getTagCompound().equals(result.getTagCompound()));
        result.setStackDisplayName("Changed"); check("upgrade deep-copies NBT",original.getDisplayName().equals("Ancient oath"));
        grid.setInventorySlotContents(4,new ItemStack(Items.stick)); check("extra ingredient rejected",!recipe.matches(grid,world));
        grid.setInventorySlotContents(4,new ItemStack(WitherPact.shard)); check("duplicate ingredient rejected",!recipe.matches(grid,world));
        grid.setInventorySlotContents(4,null); grid.setInventorySlotContents(8,null); check("missing shard rejected",!recipe.matches(grid,world));
    }
    private EntityZombie zombie(double x,double z) {
        EntityZombie entity=new EntityZombie(world); entity.setPosition(x,64,z); world.spawnEntityInWorld(entity); return entity;
    }
    private void summons() {
        WitherPact.EVENTS.clear(); EntityZombie target=zombie(164,160);
        check("hostile accepted",WitherPact.hostile(owner,target));
        check("player excluded",!WitherPact.hostile(owner,other));
        check("cow excluded",!WitherPact.hostile(owner,new EntityCow(world)));
        check("first summon",WitherPact.EVENTS.summon(owner,target,new ItemStack(Items.diamond_sword)));
        PactWither ally=WitherPact.EVENTS.ally(owner);
        check("ally owner",ally.owned(owner)&&!ally.owned(other));
        check("ally excluded from hostiles",!WitherPact.hostile(owner,ally));
        check("no spawn invulnerability explosion",ally.getInvulTime()==0);
        check("vanilla attack AI removed",ally.tasks.taskEntries.isEmpty()&&ally.targetTasks.taskEntries.isEmpty());
        check("retarget reuses ally",WitherPact.EVENTS.summon(owner,zombie(165,162),new ItemStack(WitherPact.sword))&&WitherPact.EVENTS.ally(owner)==ally);
        ally.setDead(); check("cooldown prevents replacement",!WitherPact.EVENTS.summon(owner,target,new ItemStack(WitherPact.sword)));
        world.getWorldInfo().setWorldTotalTime(world.getTotalWorldTime()+400);
        check("cooldown expires",WitherPact.EVENTS.summon(owner,target,new ItemStack(WitherPact.sword)));
        ally=WitherPact.EVENTS.ally(owner); world.getWorldInfo().setWorldTotalTime(world.getTotalWorldTime()+1200); ally.onLivingUpdate();
        check("ally expires",ally.isDead);
        target.setDead(); WitherPact.EVENTS.clear();
    }
    private void mount() {
        owner.inventory.setInventorySlotContents(0,new ItemStack(WitherPact.reins)); owner.inventory.currentItem=0;
        WitherPact.EVENTS.reins(owner); PactWither mount=WitherPact.EVENTS.mount(owner);
        check("wither mount summoned",mount!=null&&mount.mount()); near("mount health",60,mount.getMaxHealth());
        mount.interactOwner(other); check("stranger cannot mount",mount.riddenByEntity==null);
        mount.interactOwner(owner); check("owner can mount",mount.riddenByEntity==owner&&owner.ridingEntity==mount);
        double y=mount.posY; mount.controls(owner,1); mount.moveEntityWithHeading(0,0); check("ascend",mount.posY>y);
        mount.controls(other,-1); y=mount.posY; mount.moveEntityWithHeading(0,0); check("stranger cannot control",mount.posY>y);
        mount.controls(owner,-1); y=mount.posY; mount.moveEntityWithHeading(0,0); check("descend",mount.posY<y);
        world.getWorldInfo().setWorldTotalTime(world.getTotalWorldTime()+21); y=mount.posY; mount.moveEntityWithHeading(0,0); near("stale input expires",y,mount.posY);
        tick(); owner.mountEntity(null); owner.onGround=false; owner.motionY=-1; tick();
        near("dismount slow fall",-.35,owner.motionY);
        LivingFallEvent fall=new LivingFallEvent(owner,30,1); WitherPact.EVENTS.fall(fall); near("dismount fall protection",0,fall.damageMultiplier);
        mount.setHealth(50); owner.inventory.setInventorySlotContents(0,new ItemStack(WitherPact.shard,2));
        mount.interactOwner(owner); near("shard heals mount",58,mount.getHealth()); near("healing costs shard",1,owner.getHeldItem().stackSize);
        world.getWorldInfo().setWorldTotalTime(world.getTotalWorldTime()+40); WitherPact.EVENTS.reins(owner);
        near("recall preserves health",58,WitherPact.EVENTS.mount(owner).getHealth());
        owner.setSneaking(true); world.getWorldInfo().setWorldTotalTime(world.getTotalWorldTime()+40); WitherPact.EVENTS.reins(owner); owner.setSneaking(false);
        check("dismiss removes mount",mount.isDead&&WitherPact.EVENTS.mount(owner)==null);
        WitherPact.EVENTS.clear(); owner.onGround=true;
    }
    private void impact(PactSkull skull,MovingObjectPosition hit) {
        try { Method method=PactSkull.class.getDeclaredMethod("onImpact",MovingObjectPosition.class); method.setAccessible(true); method.invoke(skull,hit); }
        catch(Exception error) { throw new RuntimeException(error); }
    }
    private void skull() {
        PactWither ally=new PactWither(world); ally.configure(owner,false,1200); ally.setPosition(162,65,160); world.spawnEntityInWorld(ally);
        EntityZombie target=zombie(165,160); float health=target.getHealth();
        impact(new PactSkull(world,ally,1,0,0),new MovingObjectPosition(target)); check("skull damages hostile",target.getHealth()<health);
        EntityCow cow=new EntityCow(world); health=cow.getHealth(); impact(new PactSkull(world,ally,1,0,0),new MovingObjectPosition(cow));
        near("skull protects animals",health,cow.getHealth()); health=owner.getHealth();
        impact(new PactSkull(world,ally,1,0,0),new MovingObjectPosition(owner)); near("skull protects owner",health,owner.getHealth());
        BlockPos pos=new BlockPos(164,63,161); world.setBlockState(pos,Blocks.stone.getDefaultState());
        impact(new PactSkull(world,ally,1,0,0),new MovingObjectPosition(new Vec3(164,64,161),EnumFacing.UP,pos));
        check("skull does not break terrain",world.getBlockState(pos).getBlock()==Blocks.stone);
        check("summon cannot be saved or farmed",!ally.writeToNBTOptional(new NBTTagCompound()));
        check("owner cannot hurt own wither",!ally.attackEntityFrom(DamageSource.causePlayerDamage(owner),10));
        ally.setDead(); target.setDead();
    }
    private void tick() { WitherPact.EVENTS.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,owner)); }
    private void armor() {
        owner.inventory.armorInventory[3]=new ItemStack(EchoGear.helmet); tick();
        check("helmet night vision",owner.isPotionActive(Potion.nightVision));
        for(int i=0;i<450;i++) { owner.getActivePotionEffect(Potion.nightVision).onUpdate(owner); tick(); }
        check("night vision continuously renewed",owner.getActivePotionEffect(Potion.nightVision).getDuration()>300);
        owner.inventory.armorInventory[3]=null; tick(); check("owned night vision removed",!owner.isPotionActive(Potion.nightVision));
        owner.addPotionEffect(new PotionEffect(Potion.nightVision.id,3600,0));
        owner.inventory.armorInventory[3]=new ItemStack(EchoGear.helmet); tick(); owner.inventory.armorInventory[3]=null; tick();
        near("pre-existing potion preserved",3600,owner.getActivePotionEffect(Potion.nightVision).getDuration());
        owner.removePotionEffect(Potion.nightVision.id); owner.inventory.armorInventory[3]=new ItemStack(EchoGear.helmet); tick();
        owner.addPotionEffect(new PotionEffect(Potion.nightVision.id,3600,0)); tick(); owner.inventory.armorInventory[3]=null; tick();
        near("potion taken while helmet worn preserved",3600,owner.getActivePotionEffect(Potion.nightVision).getDuration());
        owner.removePotionEffect(Potion.nightVision.id);
        IAttributeInstance speed=owner.getEntityAttribute(SharedMonsterAttributes.movementSpeed); double base=speed.getAttributeValue();
        owner.inventory.armorInventory[1]=new ItemStack(EchoGear.legs); tick(); near("leggings speed",base*1.15,speed.getAttributeValue());
        tick(); near("leggings does not stack",base*1.15,speed.getAttributeValue());
        owner.inventory.armorInventory[1]=null; tick(); near("leggings removal",base,speed.getAttributeValue());
        owner.inventory.armorInventory[0]=new ItemStack(EchoGear.boots);
        LivingFallEvent fall=new LivingFallEvent(owner,20,1); WitherPact.EVENTS.fall(fall); near("boots halve fall damage",.5,fall.damageMultiplier);
        Item[] items={EchoGear.boots,EchoGear.legs,EchoGear.chest,EchoGear.helmet};
        for(int i=0;i<4;i++) { ItemStack stack=new ItemStack(items[i]); stack.addEnchantment(WitherPact.guard,4); owner.inventory.armorInventory[i]=stack; }
        tick(); near("armor knockback resistance",.4,owner.getEntityAttribute(SharedMonsterAttributes.knockbackResistance).getAttributeValue());
        EntityZombie enemy=zombie(163,160); LivingHurtEvent hurt=new LivingHurtEvent(owner,DamageSource.causeMobDamage(enemy),10);
        WitherPact.EVENTS.hurt(hurt); near("guard hostile reduction",6.8,hurt.ammount);
        LivingHurtEvent otherDamage=new LivingHurtEvent(owner,DamageSource.fall,10); WitherPact.EVENTS.hurt(otherDamage); near("guard leaves environmental damage",10,otherDamage.ammount);
        for(int i=0;i<4;i++) owner.inventory.armorInventory[i]=null; tick();
        near("armor resistance removed",0,owner.getEntityAttribute(SharedMonsterAttributes.knockbackResistance).getAttributeValue());
        enemy.setDead(); WitherPact.EVENTS.clear();
    }
    private void fill(BlockPos origin,IBlockState state,int radius) {
        for(int x=-radius;x<=radius;x++) for(int z=-radius;z<=radius;z++) { world.setBlockState(origin.add(x,0,z),state,2); world.setBlockToAir(origin.add(x,1,z)); }
    }
    private void tools() {
        owner.theItemInWorldManager.setGameType(WorldSettings.GameType.SURVIVAL);
        BlockPos ore=new BlockPos(161,64,166);
        for(int x=0;x<6;x++) for(int z=0;z<4;z++) world.setBlockState(ore.add(x,0,z),Blocks.diamond_ore.getDefaultState(),2);
        owner.inventory.setInventorySlotContents(0,new ItemStack(EchoGear.pickaxe));
        near("chain cap",16,EchoGear.chain(owner,ore,false));
        int remaining=0; for(int x=0;x<6;x++) for(int z=0;z<4;z++) if(world.getBlockState(ore.add(x,0,z)).getBlock()==Blocks.diamond_ore) remaining++;
        near("remaining vein retained",8,remaining); check("mining costs durability",owner.getHeldItem().getItemDamage()>0);
        BlockPos logs=new BlockPos(153,64,166);
        for(int y=0;y<3;y++) world.setBlockState(logs.up(y),Blocks.log.getDefaultState(),2);
        owner.inventory.setInventorySlotContents(0,new ItemStack(EchoGear.axe)); near("chain logs",3,EchoGear.chain(owner,logs,true));
        BlockPos ground=new BlockPos(175,63,164); fill(ground,Blocks.grass.getDefaultState(),1);
        owner.inventory.setInventorySlotContents(0,new ItemStack(EchoGear.shovel)); near("3x3 paths",9,EchoGear.area(owner,ground,false,true));
        check("custom path registered in world",world.getBlockState(ground).getBlock()==EchoGear.path);
        near("path durability",9,owner.getHeldItem().getItemDamage());
        fill(ground,Blocks.dirt.getDefaultState(),1); owner.inventory.setInventorySlotContents(0,new ItemStack(EchoGear.hoe));
        near("3x3 farmland",9,EchoGear.area(owner,ground,true,true)); check("native farmland",world.getBlockState(ground).getBlock()==Blocks.farmland);
        near("hoe durability",9,owner.getHeldItem().getItemDamage());
        fill(ground,Blocks.stone.getDefaultState(),1); near("invalid center rejected",0,EchoGear.area(owner,ground,true,true));
        final BlockPos protectedPos=ground;
        Object protection=new Object() { @SubscribeEvent public void deny(BlockEvent.BreakEvent event) { if(event.pos.equals(protectedPos)) event.setCanceled(true); } };
        MinecraftForge.EVENT_BUS.register(protection);
        try {
            fill(ground,Blocks.grass.getDefaultState(),1); owner.inventory.setInventorySlotContents(0,new ItemStack(EchoGear.shovel));
            near("protection cancels skill",0,EchoGear.area(owner,ground,false,true));
            check("protected ground retained",world.getBlockState(ground).getBlock()==Blocks.grass);
        } finally { MinecraftForge.EVENT_BUS.unregister(protection); }
        owner.theItemInWorldManager.setGameType(WorldSettings.GameType.ADVENTURE);
        near("adventure tools denied",0,EchoGear.area(owner,ground,false,true)); owner.theItemInWorldManager.setGameType(WorldSettings.GameType.SURVIVAL);
    }
    private void combat() {
        WitherPact.EVENTS.clear(); owner.inventory.setInventorySlotContents(0,new ItemStack(WitherPact.sword));
        EntityZombie target=zombie(164,159); target.hurtResistantTime=0;
        target.attackEntityFrom(DamageSource.causePlayerDamage(owner),2);
        WitherPact.EVENTS.serverTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
        check("real sword damage summons",WitherPact.EVENTS.ally(owner)!=null);
        WitherPact.EVENTS.clear();
        LivingHurtEvent canceled=new LivingHurtEvent(target,DamageSource.causePlayerDamage(owner),2);
        WitherPact.EVENTS.hurt(canceled); // No actual health change follows.
        WitherPact.EVENTS.serverTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
        check("unchanged health does not summon",WitherPact.EVENTS.ally(owner)==null);
        target.setDead();
        EntityZombie fragile=zombie(164,159); fragile.setHealth(1);
        fragile.attackEntityFrom(DamageSource.causePlayerDamage(owner),10);
        WitherPact.EVENTS.serverTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
        check("killing sword hit also summons",WitherPact.EVENTS.ally(owner)!=null); WitherPact.EVENTS.clear();
        owner.setHealth(20); owner.hurtResistantTime=0; owner.inventory.armorInventory[2]=new ItemStack(EchoGear.chest);
        EntityZombie nearby=zombie(162,160); float health=nearby.getHealth();
        check("hostile attack applies damage",owner.attackEntityFrom(DamageSource.causeMobDamage(nearby),4));
        WitherPact.EVENTS.serverTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
        check("actual hostile damage triggers chest pulse",nearby.getHealth()<health);
        check("pulse does not summon ally",WitherPact.EVENTS.ally(owner)==null);
        nearby.hurtResistantTime=0; health=nearby.getHealth(); owner.hurtResistantTime=0;
        owner.attackEntityFrom(DamageSource.causeMobDamage(nearby),4);
        WitherPact.EVENTS.serverTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
        near("chest pulse cooldown",health,nearby.getHealth());
        owner.inventory.armorInventory[2]=null; owner.setHealth(20); nearby.setDead(); fragile.setDead(); WitherPact.EVENTS.clear();
    }
    private void packets() {
        ByteBuf buffer=Unpooled.buffer();
        new PactNetwork.Sense(new int[]{1,12,123},100).toBytes(buffer); PactNetwork.Sense sense=new PactNetwork.Sense(); sense.fromBytes(buffer);
        check("sense packet round trip",Arrays.equals(sense.ids,new int[]{1,12,123})&&sense.ticks==100); buffer.release();
        buffer=Unpooled.buffer(); buffer.writeShort(129); boolean rejected=false;
        try { new PactNetwork.Sense().fromBytes(buffer); } catch(IllegalArgumentException expected) { rejected=true; } finally { buffer.release(); }
        check("oversized packet rejected",rejected);
    }
    private static final class SmokePlayer extends FakePlayer {
        SmokePlayer(WorldServer world,String name) {
            super(world,new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),name));
            playerNetServerHandler=new NetHandlerPlayServer(MinecraftServer.getServer(),new NetworkManager(EnumPacketDirection.SERVERBOUND),this) {
                @Override public void sendPacket(Packet packet) {}
            };
            theItemInWorldManager.setGameType(WorldSettings.GameType.SURVIVAL);
            // FakePlayer never ticks, so its normal 60-tick spawn immunity would
            // otherwise prevent every real damage test from reaching Forge events.
            net.minecraftforge.fml.relauncher.ReflectionHelper.setPrivateValue(EntityPlayerMP.class,this,0,
                    "respawnInvulnerabilityTicks","field_147101_bU");
        }
        @Override public boolean isEntityInvulnerable(DamageSource source) { return false; }
        @Override public void addChatMessage(IChatComponent component) {}
    }
}
