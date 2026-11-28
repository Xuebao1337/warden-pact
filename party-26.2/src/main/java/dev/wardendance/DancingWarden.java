package dev.wardendance;

import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Never ticks the warden brain, anger logic or darkness aura. */
public final class DancingWarden extends Warden {
    private UUID ownerId;
    private Vec3 stage = Vec3.ZERO;
    private int remainingTicks;
    private int slot;
    private long startedAt;
    private float facing;

    public DancingWarden(EntityType<? extends DancingWarden> type, Level level) {
        super(type, level);
        setNoAi(true);
        setNoGravity(true);
        setSilent(true);
        setInvulnerable(true);
        setPersistenceRequired();
        xpReward = 0;
    }
    public void bind(ServerPlayer owner, int index, int lifetime) {
        ownerId = owner.getUUID();
        slot = index;
        stage = position();
        remainingTicks = lifetime;
        startedAt = level().getGameTime();
        facing = (float) Math.toDegrees(Math.atan2(owner.getZ() - getZ(), owner.getX() - getX())) - 90;
        setCustomName(Component.translatable("entity.wardendance.dancer_name", index + 1));
        setCustomNameVisible(true);
    }
    public UUID ownerId() { return ownerId; }
    public Vec3 stage() { return stage; }

    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        ServerPlayer owner = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
        if (--remainingTicks <= 0 || owner == null || !owner.isAlive() || owner.isRemoved()
                || owner.level() != level || owner.distanceToSqr(stage) > 64 * 64) {
            discard();
            return;
        }
        double beat = (level.getGameTime() - startedAt) * Math.PI / 10;
        Vec3 next = stage.add(0, 0.28 * Math.abs(Math.sin(beat)), 0);
        if (!level.noCollision(this, getBoundingBox().move(next.subtract(position())))) {
            discard();
            return;
        }
        setPos(next);
        setDeltaMovement(Vec3.ZERO);
        setYRot(facing + (float) (32 * Math.sin(beat * 0.5)));
        yBodyRot = getYRot();
        yHeadRot = getYRot();
        if (tickCount % 10 == 0) {
            level.sendParticles(ParticleTypes.NOTE, getX(), getY() + 3.1, getZ(), 0,
                    (slot * 4 + tickCount / 10) % 24 / 24.0, 0, 0, 1);
        }
    }
    @Override protected void customServerAiStep(ServerLevel level) {}
    @Override public boolean canTargetEntity(Entity entity) { return false; }
    @Override public boolean canAttack(LivingEntity entity) { return false; }
    @Override public boolean doHurtTarget(ServerLevel level, Entity entity) { return false; }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage) { return false; }
    @Override public boolean isPushable() { return false; }
    @Override protected void doPush(Entity entity) {}
    @Override public void push(Entity entity) {}
    @Override public void push(double x, double y, double z) {}
    @Override public boolean isPreventingPlayerRest(ServerLevel level, Player player) { return false; }
}
