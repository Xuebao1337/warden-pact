package dev.witherdance;
import java.util.UUID;
import net.minecraft.entity.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
/** A peaceful performer with vanilla wither geometry, never a hostile boss. */
public final class DancingWither extends EntityCreature {
    private double stageX,stageY,stageZ;
    private float facing;
    private long started,expires;
    public DancingWither(World world) {
        super(world); setSize(1.2F,3.5F);
        tasks.taskEntries.clear(); targetTasks.taskEntries.clear();
        setNoAI(true); setSilent(true); isImmuneToFire=true; experienceValue=0;
    }
    @Override protected void entityInit() {
        super.entityInit(); dataWatcher.addObject(20,""); dataWatcher.addObject(21,Byte.valueOf((byte)0));
    }
    public void bind(EntityPlayer owner,int index,int lifetime) {
        dataWatcher.updateObject(20,owner.getUniqueID().toString()); dataWatcher.updateObject(21,Byte.valueOf((byte)index));
        stageX=posX; stageY=posY; stageZ=posZ; started=worldObj.getTotalWorldTime(); expires=started+lifetime;
        facing=owner.rotationYaw+180;
        rotationYaw=prevRotationYaw=renderYawOffset=prevRenderYawOffset=rotationYawHead=prevRotationYawHead=facing;
    }
    public int danceIndex() { return dataWatcher.getWatchableObjectByte(21); }
    public EntityPlayer owner() {
        try { return worldObj.getPlayerEntityByUUID(UUID.fromString(dataWatcher.getWatchableObjectString(20))); }
        catch(IllegalArgumentException ignored) { return null; }
    }
    @Override public void onLivingUpdate() {
        if(!worldObj.isRemote) {
            EntityPlayer owner=owner();
            if(owner==null||!owner.isEntityAlive()||owner.isSpectator()||owner.worldObj!=worldObj
                    ||owner.dimension!=dimension||getDistanceSqToEntity(owner)>64*64
                    ||worldObj.getTotalWorldTime()>=expires) { setDead(); return; }
        }
        super.onLivingUpdate(); motionX=motionY=motionZ=0; fallDistance=0;
        if(!worldObj.isRemote) {
            long elapsed=worldObj.getTotalWorldTime()-started;
            double nextY=stageY+0.28*Math.abs(Math.sin(elapsed*Math.PI/10));
            AxisAlignedBB room=getEntityBoundingBox().offset(stageX-posX,nextY-posY,stageZ-posZ);
            if(!DanceParty.loadedAndInside(worldObj,room)||!worldObj.getCollidingBoundingBoxes(this,room).isEmpty()
                    ||worldObj.isAnyLiquid(room)) { setDead(); return; }
            setPosition(stageX,nextY,stageZ);
            rotationYaw=facing+(float)Math.sin(elapsed*Math.PI/20+danceIndex()*.5)*32;
            renderYawOffset=rotationYaw; rotationYawHead=rotationYaw;
            if(elapsed%5==0) ((WorldServer)worldObj).spawnParticle(EnumParticleTypes.NOTE,false,
                    posX,posY+3.7,posZ,0,(danceIndex()*5+elapsed/5)%24/24.0,0.0D,0.0D,1.0D,new int[0]);
        }
    }
    @Override public void moveEntityWithHeading(float strafe,float forward) { motionX=motionY=motionZ=0; }
    @Override public boolean attackEntityFrom(DamageSource source,float damage) { return false; }
    @Override public boolean attackEntityAsMob(Entity target) { return false; }
    @Override public void setAttackTarget(EntityLivingBase target) {}
    @Override public boolean canAttackClass(Class<? extends EntityLivingBase> type) { return false; }
    @Override public boolean canBePushed() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public void applyEntityCollision(Entity entity) {}
    @Override public boolean isPushedByWater() { return false; }
    @Override protected boolean canDespawn() { return false; }
    @Override protected void dropFewItems(boolean hit,int looting) {}
    @Override protected void dropEquipment(boolean hit,int looting) {}
    @Override protected void addRandomDrop() {}
    @Override protected int getExperiencePoints(EntityPlayer player) { return 0; }
    @Override public boolean writeToNBTOptional(NBTTagCompound tag) { return false; }
    @Override public boolean writeMountToNBT(NBTTagCompound tag) { return false; }
    @Override public void readEntityFromNBT(NBTTagCompound tag) { super.readEntityFromNBT(tag); setDead(); }
    @Override public void fall(float distance,float multiplier) {}
}
