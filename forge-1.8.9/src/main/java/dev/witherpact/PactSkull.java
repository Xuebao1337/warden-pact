package dev.witherpact;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityWitherSkull;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;

/** Direct-hit skull: never explodes or changes blocks, even with mobGriefing. */
public final class PactSkull extends EntityWitherSkull {
    public PactSkull(World world) { super(world); }
    public PactSkull(World world,EntityLivingBase shooter,double x,double y,double z) { super(world,shooter,x,y,z); }
    @Override protected void onImpact(MovingObjectPosition hit) {
        if(worldObj.isRemote) return;
        EntityPlayer owner=shootingEntity instanceof PactWither?((PactWither)shootingEntity).owner():null;
        if(owner!=null&&WitherPact.hostile(owner,hit.entityHit)) {
            EntityLivingBase target=(EntityLivingBase)hit.entityHit;
            if(target.attackEntityFrom(DamageSource.causeIndirectMagicDamage(this,owner),8))
                target.addPotionEffect(new PotionEffect(Potion.wither.id,100,0));
        }
        worldObj.playSoundAtEntity(this,"random.fizz",.4F,.7F); setDead();
    }
    @Override public void onUpdate() {
        if(!worldObj.isRemote&&(ticksExisted>100||shootingEntity==null||shootingEntity.isDead)) { setDead(); return; }
        super.onUpdate();
    }
    @Override public boolean writeToNBTOptional(net.minecraft.nbt.NBTTagCompound tag) { return false; }
}
