package dev.wardenpact;

import java.util.Comparator;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.warden.WardenAi;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** A temporary ally using the vanilla warden model, navigation, melee and sonic boom. */
public final class FriendlyWarden extends Warden {
    private UUID ownerId;
    private LivingEntity orderedTarget;
    private int remainingTicks = 20 * 60;

    public FriendlyWarden(EntityType<? extends FriendlyWarden> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        setPersistenceRequired();
    }

    public void bind(ServerPlayer owner, int lifetimeTicks) {
        ownerId = owner.getUUID();
        remainingTicks = lifetimeTicks;
        // EntityType.create does not call finalizeSpawn; initialize this memory explicitly.
        getBrain().setMemoryWithExpiry(MemoryModuleType.DIG_COOLDOWN, Unit.INSTANCE, lifetimeTicks + 200L);
        setCustomName(Component.translatable("entity.wardenpact.owned_warden", owner.getName()));
        setCustomNameVisible(true);
    }

    public void command(LivingEntity target) {
        if (canTargetEntity(target)) {
            orderedTarget = target;
            if (getTarget() != target) {
                increaseAngerAt(target, 150, false);
                setAttackTarget(target);
            }
        }
    }

    public ServerPlayer owner() {
        return ownerId != null && level() instanceof ServerLevel server
                ? server.getPlayerByUUID(ownerId) instanceof ServerPlayer player ? player : null
                : null;
    }

    public static boolean isProtected(Entity entity) {
        return entity instanceof Player || entity instanceof FriendlyWarden
                || entity instanceof TamableAnimal animal && animal.isTame();
    }

    @Override
    public boolean canTargetEntity(Entity entity) {
        if (!(entity instanceof Enemy) || !entity.isAlive() || isProtected(entity) || !super.canTargetEntity(entity)) {
            return false;
        }
        ServerPlayer owner = owner();
        return owner != null && !owner.isAlliedTo(entity);
    }

    @Override
    protected boolean considersEntityAsAlly(Entity entity) {
        if (isProtected(entity)) {
            return true;
        }
        ServerPlayer owner = owner();
        return super.considersEntityAsAlly(entity) || owner != null && owner.isAlliedTo(entity);
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel) {
            ServerPlayer owner = owner();
            if (--remainingTicks <= 0 || owner == null || !owner.isAlive()) {
                discard();
            }
        }
    }

    @Override
    public boolean isPreventingPlayerRest(ServerLevel level, Player player) {
        return false;
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        ServerPlayer owner = owner();
        if (owner == null || !owner.isAlive()) {
            return;
        }

        // Do not call Warden.customServerAiStep: it applies darkness to nearby players.
        // Keep its actual brain and anger system, with a strict hostile-only target filter.
        if (tickCount % 10 == 0) {
            LivingEntity target = orderedTarget;
            if (target == null || !canTargetEntity(target) || target.distanceToSqr(owner) > 48.0 * 48.0) {
                orderedTarget = null;
                target = level.getEntitiesOfClass(LivingEntity.class,
                                owner.getBoundingBox().inflate(PactConfig.DEFEND_RADIUS.get()), this::canTargetEntity)
                        .stream().min(Comparator.comparingDouble(owner::distanceToSqr)).orElse(null);
            }
            if (target != null) {
                command(target);
            } else {
                getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
                getBrain().eraseMemory(MemoryModuleType.ROAR_TARGET);
                if (distanceToSqr(owner) > 16.0) {
                    getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(owner, 1.1F, 3));
                }
            }
        }
        if (getTarget() != null && !canTargetEntity(getTarget())) {
            getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }
        getBrain().tick(level, this);
        if (tickCount % 20 == 0) {
            getAngerManagement().tick(level, this::canTargetEntity);
        }
        WardenAi.updateActivity(getBrain());
    }
}
