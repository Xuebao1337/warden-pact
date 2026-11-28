package dev.witherpact;

import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.*;
import net.minecraft.item.*;
import net.minecraft.potion.*;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent;

/** All authoritative abilities run on the logical server. */
public final class PactEvents {
    private final Map<UUID,PactWither> allies=new HashMap<UUID,PactWither>(), mounts=new HashMap<UUID,PactWither>();
    private final Map<UUID,Long> summons=new HashMap<UUID,Long>(), reins=new HashMap<UUID,Long>(),
            senses=new HashMap<UUID,Long>(), pulses=new HashMap<UUID,Long>(), tools=new HashMap<UUID,Long>(),
            safeFall=new HashMap<UUID,Long>();
    private final Map<UUID,PotionEffect> night=new HashMap<UUID,PotionEffect>();
    private final Set<UUID> riders=new HashSet<UUID>();
    private final List<DamageJob> damage=new ArrayList<DamageJob>();
    private static final UUID SPEED=UUID.fromString("aebbbf6a-e4a2-422c-bf89-d0f3fbda0101"),
            KNOCKBACK=UUID.fromString("aebbbf6a-e4a2-422c-bf89-d0f3fbda0102");
    private boolean pulseDamage;
    private static final class DamageJob {
        final EntityLivingBase target; final EntityPlayerMP player; final boolean sword;
        final float before; final ItemStack weapon;
        DamageJob(EntityLivingBase target,EntityPlayerMP player,boolean sword) {
            this.target=target; this.player=player; this.sword=sword;
            before=target.getHealth()+target.getAbsorptionAmount();
            weapon=player.getHeldItem()==null?null:player.getHeldItem().copy();
        }
    }
    private static long now(EntityPlayer player) { return player.worldObj.getTotalWorldTime(); }
    private static boolean ready(Map<UUID,Long> clocks,EntityPlayer player) {
        Long until=clocks.get(player.getUniqueID()); return until==null||now(player)>=until;
    }
    private static boolean wears(EntityPlayer player,int slot,Item item) {
        ItemStack stack=player.inventory.armorInventory[slot]; return stack!=null&&stack.getItem()==item;
    }
    public PactWither ally(EntityPlayer player) { return active(allies,player); }
    public PactWither mount(EntityPlayer player) { return active(mounts,player); }
    private PactWither active(Map<UUID,PactWither> map,EntityPlayer player) {
        PactWither entity=map.get(player.getUniqueID());
        if(entity!=null&&(entity.isDead||entity.worldObj!=player.worldObj||!player.worldObj.loadedEntityList.contains(entity))) {
            entity.setDead(); map.remove(player.getUniqueID()); return null;
        }
        return entity;
    }
    /** Find an empty volume without loading chunks or placing inside ceilings. */
    private boolean place(PactWither entity,EntityPlayer player) {
        double[][] offsets={{2,1,0},{-2,1,0},{0,1,2},{0,1,-2},{3,0,3},{-3,0,-3},{0,2,0}};
        for(double[] offset:offsets) {
            double x=player.posX+offset[0],y=player.posY+offset[1],z=player.posZ+offset[2];
            BlockPos pos=new BlockPos(x,y,z);
            if(y<1||y+entity.height>255||!player.worldObj.isAreaLoaded(pos.add(-2,0,-2),pos.add(2,5,2))
                    ||!player.worldObj.getWorldBorder().contains(pos)) continue;
            entity.setPosition(x,y,z);
            if(player.worldObj.getCollidingBoundingBoxes(entity,entity.getEntityBoundingBox()).isEmpty()
                    &&player.worldObj.checkNoEntityCollision(entity.getEntityBoundingBox(),entity)) return true;
        }
        return false;
    }
    public boolean summon(EntityPlayerMP player,EntityLivingBase target,ItemStack weapon) {
        if(target!=null&&!WitherPact.hostile(player,target)||player.isSpectator()||!player.isEntityAlive()) return false;
        PactWither old=ally(player);
        if(old!=null) { old.focus(target); return true; }
        if(!ready(summons,player)) return false;
        int level=WitherPact.level(WitherPact.resonance,weapon);
        PactWither entity=new PactWither(player.worldObj);
        entity.configure(player,false,PactRules.lifetimeTicks(WitherPact.lifetime,level));
        if(!place(entity,player)) { WitherPact.message(player,"message.witherpact.no_space"); return false; }
        entity.focus(target);
        if(!player.worldObj.spawnEntityInWorld(entity)) return false;
        allies.put(player.getUniqueID(),entity);
        summons.put(player.getUniqueID(),now(player)+PactRules.cooldownTicks(WitherPact.cooldown,level));
        return true;
    }
    public void reins(EntityPlayerMP player) {
        if(player.isSpectator()||!player.isEntityAlive()||!ready(reins,player)) return;
        PactWither entity=mount(player);
        if(player.isSneaking()) {
            if(entity==null) return;
            if(entity.riddenByEntity!=null) { WitherPact.message(player,"message.witherpact.dismount_first"); return; }
            entity.setDead(); mounts.remove(player.getUniqueID());
            reins.put(player.getUniqueID(),now(player)+40); return;
        }
        if(entity!=null) {
            if(entity.riddenByEntity!=null) return;
            double x=entity.posX,y=entity.posY,z=entity.posZ;
            if(!place(entity,player)) { entity.setPosition(x,y,z); WitherPact.message(player,"message.witherpact.no_space"); return; }
            entity.setPositionAndUpdate(entity.posX,entity.posY,entity.posZ);
        } else {
            entity=new PactWither(player.worldObj); entity.configure(player,true,0);
            if(!place(entity,player)) { WitherPact.message(player,"message.witherpact.no_space"); return; }
            if(!player.worldObj.spawnEntityInWorld(entity)) return;
            mounts.put(player.getUniqueID(),entity);
        }
        reins.put(player.getUniqueID(),now(player)+40);
        WitherPact.message(player,"message.witherpact.mount_ready");
    }
    @SubscribeEvent public void interact(EntityInteractEvent event) {
        if(event.target instanceof PactWither) {
            event.setCanceled(true);
            if(!event.entityPlayer.worldObj.isRemote) ((PactWither)event.target).interactOwner(event.entityPlayer);
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void hurt(LivingHurtEvent event) {
        if(event.entityLiving.worldObj.isRemote||event.ammount<=0) return;
        Entity attacker=event.source.getEntity();
        if(attacker instanceof PactWither&&!(event.entityLiving instanceof net.minecraft.entity.monster.IMob)) {
            event.setCanceled(true); return;
        }
        if(event.entityLiving instanceof EntityPlayerMP) {
            EntityPlayerMP player=(EntityPlayerMP)event.entityLiving;
            if(WitherPact.hostile(player,attacker)) {
                int levels=0;
                for(ItemStack armor:player.inventory.armorInventory) levels+=Math.min(4,WitherPact.level(WitherPact.guard,armor));
                event.ammount=PactRules.guard(event.ammount,levels);
                if(wears(player,2,EchoGear.chest)&&player.getDistanceSqToEntity(attacker)<=16)
                    damage.add(new DamageJob(player,player,false));
            }
        }
        if(!pulseDamage&&attacker instanceof EntityPlayerMP&&"player".equals(event.source.getDamageType())
                &&event.source.getSourceOfDamage()==attacker) {
            EntityPlayerMP player=(EntityPlayerMP)attacker;
            if(WitherPact.sword(player.getHeldItem())&&WitherPact.hostile(player,event.entityLiving))
                damage.add(new DamageJob(event.entityLiving,player,true));
        }
    }
    @SubscribeEvent public void serverTick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END) return;
        List<DamageJob> jobs=new ArrayList<DamageJob>(damage); damage.clear();
        for(DamageJob job:jobs) {
            if(job.player.isDead||job.player.worldObj!=job.target.worldObj
                    ||job.target.getHealth()+job.target.getAbsorptionAmount()>=job.before) continue;
            if(job.sword) summon(job.player,job.target.isEntityAlive()?job.target:null,job.weapon);
            else if(job.player.isEntityAlive()&&wears(job.player,2,EchoGear.chest)&&ready(pulses,job.player)) pulse(job.player);
        }
    }
    private void pulse(EntityPlayerMP player) {
        List<EntityLivingBase> targets=player.worldObj.getEntitiesWithinAABB(EntityLivingBase.class,player.getEntityBoundingBox().expand(4,4,4));
        boolean hit=false;
        pulseDamage=true;
        try {
            for(EntityLivingBase target:targets) if(WitherPact.hostile(player,target)&&player.getDistanceSqToEntity(target)<=16) {
                if(target.attackEntityFrom(DamageSource.causePlayerDamage(player),4)) {
                    double dx=target.posX-player.posX,dz=target.posZ-player.posZ,len=Math.max(.01,Math.sqrt(dx*dx+dz*dz));
                    target.addVelocity(dx/len*.6,.25,dz/len*.6); target.velocityChanged=true; hit=true;
                }
            }
        } finally { pulseDamage=false; }
        if(hit) { pulses.put(player.getUniqueID(),now(player)+160); player.worldObj.playSoundAtEntity(player,"mob.wither.hurt",.35F,1.6F); }
    }
    @SubscribeEvent public void tick(TickEvent.PlayerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||event.player.worldObj.isRemote||!(event.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP player=(EntityPlayerMP)event.player; UUID id=player.getUniqueID();
        if(!player.isEntityAlive()||player.isSpectator()) { cleanup(player); return; }
        boolean helmet=wears(player,3,EchoGear.helmet);
        PotionEffect owned=night.get(id),current=player.getActivePotionEffect(Potion.nightVision);
        if(owned!=null&&(owned!=current||current.getAmplifier()!=0||!current.getIsAmbient()
                ||current.getDuration()>400)) { night.remove(id); owned=null; }
        if(!helmet) {
            if(owned!=null&&owned==current) player.removePotionEffect(Potion.nightVision.id);
            night.remove(id);
        } else if(current==null||current==owned&&current.getDuration()<=300) {
            PotionEffect effect=new PotionEffect(Potion.nightVision.id,400,0,true,false);
            if(current==owned&&owned!=null) player.removePotionEffect(Potion.nightVision.id);
            player.addPotionEffect(effect); night.put(id,player.getActivePotionEffect(Potion.nightVision));
        }
        modify(player.getEntityAttribute(SharedMonsterAttributes.movementSpeed),SPEED,wears(player,1,EchoGear.legs)?.15:0,2);
        int pieces=0; Item[] armor={EchoGear.boots,EchoGear.legs,EchoGear.chest,EchoGear.helmet};
        for(int i=0;i<4;i++) if(wears(player,i,armor[i])) pieces++;
        modify(player.getEntityAttribute(SharedMonsterAttributes.knockbackResistance),KNOCKBACK,.1*pieces,0);
        if(helmet&&player.isSneaking()&&ready(senses,player)) {
            List<EntityLivingBase> targets=player.worldObj.getEntitiesWithinAABB(EntityLivingBase.class,player.getEntityBoundingBox().expand(16,16,16));
            List<Integer> ids=new ArrayList<Integer>();
            for(EntityLivingBase target:targets) if(ids.size()<128&&WitherPact.hostile(player,target)&&player.getDistanceSqToEntity(target)<=256) ids.add(target.getEntityId());
            int[] packet=new int[ids.size()]; for(int i=0;i<packet.length;i++) packet[i]=ids.get(i);
            PactNetwork.CHANNEL.sendTo(new PactNetwork.Sense(packet,100),player);
            senses.put(id,now(player)+160);
        }
        boolean riding=player.ridingEntity instanceof PactWither&&((PactWither)player.ridingEntity).owned(player);
        if(riding) riders.add(id);
        else if(riders.remove(id)) safeFall.put(id,now(player)+1200);
        Long grace=safeFall.get(id);
        if(grace!=null) {
            if(player.onGround||player.isInWater()||now(player)>grace) safeFall.remove(id);
            else { player.fallDistance=0; if(player.motionY<-.35) { player.motionY=-.35; player.velocityChanged=true; } }
        }
    }
    private void modify(IAttributeInstance attribute,UUID uuid,double amount,int operation) {
        AttributeModifier old=attribute.getModifier(uuid);
        if(old!=null&&old.getAmount()==amount) return;
        if(old!=null) attribute.removeModifier(old);
        if(amount!=0) attribute.applyModifier(new AttributeModifier(uuid,"Wither Pact equipment",amount,operation).setSaved(false));
    }
    @SubscribeEvent public void fall(LivingFallEvent event) {
        if(!(event.entityLiving instanceof EntityPlayer)||event.entityLiving.worldObj.isRemote) return;
        EntityPlayer player=(EntityPlayer)event.entityLiving;
        if(riders.contains(player.getUniqueID())||safeFall.containsKey(player.getUniqueID())) event.damageMultiplier=0;
        else if(wears(player,0,EchoGear.boots)) event.damageMultiplier*=.5F;
    }
    @SubscribeEvent public void speed(net.minecraftforge.event.entity.player.PlayerEvent.BreakSpeed event) {
        int level=Math.min(3,WitherPact.level(WitherPact.mining,event.entityPlayer.getHeldItem()));
        event.newSpeed*=1+.2F*level;
    }
    @SubscribeEvent public void tool(PlayerInteractEvent event) {
        if(event.action!=PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;
        EntityPlayer player=event.entityPlayer; ItemStack held=player.getHeldItem(); if(held==null) return;
        Item item=held.getItem(); boolean sneak=player.isSneaking();
        boolean chain=sneak&&(item==EchoGear.pickaxe||item==EchoGear.axe);
        boolean area=(sneak&&item==EchoGear.hoe)||item==EchoGear.shovel;
        if(!chain&&!area) return;
        if(player.worldObj.isRemote) { event.setCanceled(true); return; }
        if(!(player instanceof EntityPlayerMP)) return;
        event.setCanceled(true);
        if((sneak||chain)&&!ready(tools,player)) return;
        int count=chain?EchoGear.chain((EntityPlayerMP)player,event.pos,item==EchoGear.axe)
                :EchoGear.area((EntityPlayerMP)player,event.pos,item==EchoGear.hoe,sneak);
        if(count>0&&sneak) tools.put(player.getUniqueID(),now(player)+40);
    }
    @SubscribeEvent public void logout(PlayerLoggedOutEvent event) { cleanup(event.player); }
    @SubscribeEvent public void dimension(PlayerChangedDimensionEvent event) { cleanup(event.player); }
    @SubscribeEvent public void death(LivingDeathEvent event) {
        if(event.entityLiving instanceof EntityPlayer&&!event.entityLiving.worldObj.isRemote) cleanup((EntityPlayer)event.entityLiving);
    }
    private void cleanup(EntityPlayer player) {
        UUID id=player.getUniqueID();
        PactWither a=allies.remove(id),m=mounts.remove(id); if(a!=null) a.setDead(); if(m!=null) m.setDead();
        PotionEffect effect=night.remove(id);
        if(effect!=null&&effect==player.getActivePotionEffect(Potion.nightVision)) player.removePotionEffect(Potion.nightVision.id);
        modify(player.getEntityAttribute(SharedMonsterAttributes.movementSpeed),SPEED,0,2);
        modify(player.getEntityAttribute(SharedMonsterAttributes.knockbackResistance),KNOCKBACK,0,0);
        summons.remove(id); reins.remove(id); senses.remove(id); pulses.remove(id); tools.remove(id); safeFall.remove(id); riders.remove(id);
    }
    public void clear() {
        for(PactWither entity:allies.values()) entity.setDead(); for(PactWither entity:mounts.values()) entity.setDead();
        allies.clear(); mounts.clear(); summons.clear(); reins.clear(); senses.clear(); pulses.clear(); tools.clear();
        safeFall.clear(); riders.clear(); night.clear(); damage.clear();
    }
}
