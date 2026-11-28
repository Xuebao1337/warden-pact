package dev.witherpact;

import java.util.List;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

/** Wither animation, with replaced AI: no vanilla blast, random attacks or griefing. */
public final class PactWither extends EntityWither {
    private long expires;
    private int fireCooldown,vertical;
    private long lastControl;
    private EntityLivingBase focus;
    public PactWither(World world) {
        super(world); experienceValue=0; isImmuneToFire=true;
        // Replacing updateAITasks alone leaves the vanilla target selector and
        // navigation tasks running before it. This entity uses only pact AI.
        tasks.taskEntries.clear(); targetTasks.taskEntries.clear();
    }
    @Override protected void entityInit() {
        super.entityInit(); dataWatcher.addObject(21,Byte.valueOf((byte)0)); dataWatcher.addObject(22,"");
    }
    public void configure(EntityPlayer owner,boolean mount,int ticks) {
        dataWatcher.updateObject(21,Byte.valueOf((byte)(mount?1:0)));
        dataWatcher.updateObject(22,owner.getUniqueID().toString());
        expires=mount?Long.MAX_VALUE:worldObj.getTotalWorldTime()+ticks;
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(mount?60D:300D);
        setHealth(getMaxHealth());
        setCustomNameTag(owner.getName()+(mount?"'s Wither Mount":"'s Pact Wither"));
        setSize(mount?1.4F:.9F,mount?4.6F:3.5F);
    }
    public boolean mount() { return dataWatcher.getWatchableObjectByte(21)==1; }
    public EntityPlayer owner() {
        try { return worldObj.getPlayerEntityByUUID(UUID.fromString(dataWatcher.getWatchableObjectString(22))); }
        catch(IllegalArgumentException exception) { return null; }
    }
    public boolean owned(EntityPlayer player) { return player!=null&&player.getUniqueID().toString().equals(dataWatcher.getWatchableObjectString(22)); }
    public void focus(EntityLivingBase target) { if(!mount()) focus=target; }
    public void controls(EntityPlayer player,int direction) {
        if(!owned(player)||riddenByEntity!=player||!mount()) return;
        vertical=Math.max(-1,Math.min(1,direction)); lastControl=worldObj.getTotalWorldTime();
    }
    public void interactOwner(EntityPlayer player) {
        if(!mount()) return;
        if(!owned(player)) { WitherPact.message(player,"message.witherpact.not_yours"); return; }
        ItemStack held=player.getHeldItem();
        if(held!=null&&held.getItem()==WitherPact.shard&&getHealth()<getMaxHealth()) {
            heal(8); if(!player.capabilities.isCreativeMode&&--held.stackSize<=0) player.inventory.setInventorySlotContents(player.inventory.currentItem,null);
        } else if(riddenByEntity==null&&!player.isSneaking()) player.mountEntity(this);
    }
    @Override public void onLivingUpdate() {
        if(!worldObj.isRemote) {
            EntityPlayer owner=owner();
            if(owner==null||!owner.isEntityAlive()||owner.isSpectator()||worldObj.getTotalWorldTime()>=expires) { setDead(); return; }
            if(riddenByEntity!=null&&(!(riddenByEntity instanceof EntityPlayer)||!owned((EntityPlayer)riddenByEntity))) riddenByEntity.mountEntity(null);
        }
        if(mount()&&width<1.3F) setSize(1.4F,4.6F);
        super.onLivingUpdate(); fallDistance=0;
    }
    @Override protected void updateAITasks() {
        EntityPlayer player=owner(); if(player==null) return;
        // Never call the vanilla method: it explodes, selects passive targets,
        // launches terrain-damaging skulls and breaks nearby blocks.
        for(int head=0;head<3;head++) updateWatchedTargetId(head,0);
        if(mount()&&riddenByEntity!=null) return;
        if(!mount()) {
            if(focus!=null&&(!WitherPact.hostile(player,focus)||getDistanceSqToEntity(focus)>48*48)) focus=null;
            if(ticksExisted%10==0&&focus==null) {
                double nearest=WitherPact.defendRadius*WitherPact.defendRadius;
                List<EntityLivingBase> list=worldObj.getEntitiesWithinAABB(EntityLivingBase.class,player.getEntityBoundingBox().expand(WitherPact.defendRadius,WitherPact.defendRadius,WitherPact.defendRadius));
                for(EntityLivingBase entity:list) if(WitherPact.hostile(player,entity)&&player.getDistanceSqToEntity(entity)<nearest) {
                    focus=entity; nearest=player.getDistanceSqToEntity(entity);
                }
            }
            if(focus!=null) {
                getLookHelper().setLookPositionWithEntity(focus,30,30);
                updateWatchedTargetId(1,focus.getEntityId()); updateWatchedTargetId(2,focus.getEntityId());
                if(--fireCooldown<=0&&getDistanceSqToEntity(focus)<40*40&&canEntityBeSeen(focus)) {
                    fireCooldown=35;
                    PactSkull skull=new PactSkull(worldObj,this,focus.posX-posX,
                            focus.posY+focus.height*.5-(posY+2.4),focus.posZ-posZ);
                    skull.setPosition(posX,posY+2.4,posZ); worldObj.spawnEntityInWorld(skull);
                    worldObj.playSoundAtEntity(this,"mob.wither.shoot",.35F,1.15F);
                }
            }
        }
        EntityLivingBase follow=focus!=null&&!mount()?focus:player;
        double desiredY=follow.posY+(follow==player?1.2:2.0);
        double dx=follow.posX-posX,dz=follow.posZ-posZ;
        double length=Math.sqrt(dx*dx+dz*dz);
        if(follow==player&&length>32&&riddenByEntity==null) {
            BlockPos location=new BlockPos(player.posX,player.posY+1,player.posZ);
            AxisAlignedBB next=getEntityBoundingBox().offset(player.posX-posX,player.posY+1-posY,player.posZ-posZ);
            if(worldObj.isBlockLoaded(location)&&worldObj.getCollidingBoundingBoxes(this,next).isEmpty()) {
                setPositionAndUpdate(player.posX,player.posY+1,player.posZ); motionX=motionY=motionZ=0; return;
            }
        }
        double stop=follow==player?4:7;
        motionX+=(length>stop?dx/Math.max(1,length)*.24:0)-motionX*.30;
        motionZ+=(length>stop?dz/Math.max(1,length)*.24:0)-motionZ*.30;
        motionY+=Math.max(-.16,Math.min(.16,(desiredY-posY)*.08))-motionY*.35;
    }
    @Override public void moveEntityWithHeading(float strafe,float forward) {
        if(mount()&&riddenByEntity instanceof EntityPlayer&&owned((EntityPlayer)riddenByEntity)) {
            EntityPlayer player=(EntityPlayer)riddenByEntity;
            rotationYaw=player.rotationYaw; rotationPitch=player.rotationPitch*.25F;
            renderYawOffset=rotationYaw; rotationYawHead=rotationYaw;
            if(!worldObj.isRemote||player==WitherPact.proxy.localPlayer()) {
                int up=worldObj.isRemote?WitherPact.proxy.verticalInput():worldObj.getTotalWorldTime()-lastControl<=20?vertical:0;
                double[] movement=PactRules.flight(rotationYaw,player.moveStrafing*.65F,player.moveForward,.42);
                motionX=movement[0]; motionZ=movement[1]; motionY=up*.30;
                if(posY+height>=255&&motionY>0||posY<=1&&motionY<0) motionY=0;
                moveEntity(motionX,motionY,motionZ); fallDistance=0;
            }
        } else { moveEntity(motionX,motionY,motionZ); motionX*=.91; motionY*=.91; motionZ*=.91; }
    }
    @Override public boolean attackEntityFrom(DamageSource source,float amount) {
        Entity entity=source.getEntity();
        if(entity instanceof PactWither||entity instanceof EntityPlayer&&owned((EntityPlayer)entity)) return false;
        if(entity instanceof PactSkull) return false;
        return super.attackEntityFrom(source,amount);
    }
    @Override public void attackEntityWithRangedAttack(EntityLivingBase target,float distance) { /* Custom safe skull AI only. */ }
    @Override public int getTotalArmorValue() { return mount()?8:4; }
    @Override protected boolean canDespawn() { return false; }
    @Override protected void dropFewItems(boolean hit,int looting) {}
    @Override protected void dropEquipment(boolean hit,int looting) {}
    @Override protected int getExperiencePoints(EntityPlayer player) { return 0; }
    @Override public boolean writeToNBTOptional(NBTTagCompound tag) { return false; }
    @Override public void fall(float distance,float multiplier) {}
    @Override public double getMountedYOffset() { return 2.5; }
    @Override public void setDead() {
        if(riddenByEntity!=null) riddenByEntity.mountEntity(null);
        super.setDead();
    }
}
