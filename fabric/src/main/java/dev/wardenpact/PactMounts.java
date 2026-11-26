package dev.wardenpact;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Temporary summons stay on the server thread and are never written to chunk entity storage. */
public final class PactMounts {
    private final Map<UUID, SculkMount> mounts = new HashMap<>();

    public InteractionResult use(ServerPlayer player, ItemStack reins) {
        ServerLevel level = (ServerLevel) player.level();
        UUID ownerId = player.getUUID();
        SculkMount current = mounts.get(ownerId);
        if (current != null && (!current.isAlive() || current.isRemoved())) {
            mounts.remove(ownerId);
            current = null;
        }
        if (player.getCooldowns().isOnCooldown(reins)) return InteractionResult.FAIL;
        if (player.isShiftKeyDown()) {
            if (current != null) {
                if (current.isVehicle() && !current.onGround()) {
                    player.sendOverlayMessage(Component.translatable("message.wardenpact.mount_land_first"));
                    return InteractionResult.FAIL;
                }
                current.ejectPassengers();
                current.discard();
                mounts.remove(ownerId);
                player.sendOverlayMessage(Component.translatable("message.wardenpact.mount_dismissed"));
                player.getCooldowns().addCooldown(reins, 40);
            }
            return InteractionResult.SUCCESS;
        }
        if (current != null && current.isVehicle()) {
            player.sendOverlayMessage(Component.translatable("message.wardenpact.mount_dismount_first"));
            return InteractionResult.FAIL;
        }
        boolean reuse = current != null && current.level() == level;
        SculkMount mount = reuse ? current : WardenPact.SCULK_MOUNT.create(level, EntitySpawnReason.TRIGGERED);
        if (mount == null) return InteractionResult.FAIL;
        Vec3 position = safePosition(level, player, mount);
        if (position == null) {
            if (!reuse) mount.discard();
            player.sendOverlayMessage(Component.translatable("message.wardenpact.mount_no_room"));
            return InteractionResult.FAIL;
        }
        mount.getNavigation().stop();
        mount.setDeltaMovement(Vec3.ZERO);
        mount.snapTo(position.x, position.y, position.z, player.getYRot(), 0);
        if (!reuse) {
            mount.bind(player);
            if (!level.addFreshEntity(mount)) {
                mount.discard();
                return InteractionResult.FAIL;
            }
            if (current != null) current.discard();
        }
        mounts.put(ownerId, mount);
        player.getCooldowns().addCooldown(reins, 40);
        level.sendParticles(ParticleTypes.SCULK_SOUL, position.x, position.y + 1, position.z,
                24, 0.7, 0.8, 0.7, 0.03);
        level.playSound(null, mount.blockPosition(), SoundEvents.WARDEN_EMERGE, SoundSource.NEUTRAL, 0.7F, 1.3F);
        player.sendOverlayMessage(Component.translatable(reuse
                ? "message.wardenpact.mount_recalled" : "message.wardenpact.mount_summoned"));
        return InteractionResult.SUCCESS;
    }

    private static Vec3 safePosition(ServerLevel level, ServerPlayer player, SculkMount mount) {
        BlockPos origin = player.blockPosition();
        for (int radius = 2; radius <= 5; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    for (int dy : new int[] {0, 1, -1, 2, -2, 3, -3}) {
                        BlockPos pos = origin.offset(dx, dy, dz);
                        if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) continue;
                        BlockPos ground = pos.below();
                        if (!level.getBlockState(ground).isFaceSturdy(level, ground, Direction.UP)) continue;
                        var box = mount.getDimensions(Pose.STANDING)
                                .makeBoundingBox(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                        if (level.noCollision(mount, box) && !level.containsAnyLiquid(box)) {
                            return new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                        }
                    }
                }
            }
        }
        return null;
    }

    public void clear() { mounts.clear(); }
}
