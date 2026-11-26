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
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** All state and world mutations live on the logical server thread. */
public final class SummonHandler {
    private final Map<UUID, FriendlyWarden> guardians = new HashMap<>();
    private final Map<UUID, Long> nextSummon = new HashMap<>();

    @SubscribeEvent
    public void onSwordHit(LivingDamageEvent.Post event) {
        if (!(event.getSource().getDirectEntity() instanceof ServerPlayer player)
                || !event.getSource().is(DamageTypes.PLAYER_ATTACK) || event.getInflictedDamage() <= 0) {
            return;
        }
        ItemStack sword = player.getMainHandItem();
        if (!sword.is(WardenPact.SUMMONING_SWORD.get())
                && !(PactConfig.ANY_SWORD.get() && sword.is(ItemTags.SWORDS))) {
            return;
        }
        LivingEntity target = event.getEntity();
        if (!(target instanceof Enemy) || FriendlyWarden.isProtected(target) || player.isAlliedTo(target)) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        UUID playerId = player.getUUID();
        FriendlyWarden current = guardians.get(playerId);
        if (current != null && current.isAlive() && !current.isRemoved()) {
            if (current.level() == level) {
                current.command(target);
                return;
            }
            current.discard();
        }
        guardians.remove(playerId);
        long now = level.getServer().overworld().getGameTime();
        long wait = nextSummon.getOrDefault(playerId, 0L) - now;
        if (wait > 0) {
            player.sendOverlayMessage(Component.translatable("message.wardenpact.cooldown", (wait + 19) / 20));
            return;
        }

        FriendlyWarden guardian = WardenPact.FRIENDLY_WARDEN.get().create(level, EntitySpawnReason.TRIGGERED);
        if (guardian == null) {
            return;
        }
        guardian.bind(player, PactConfig.LIFETIME_SECONDS.get() * 20);
        if (!findSafePosition(level, player, guardian) || !level.addFreshEntity(guardian)) {
            guardian.discard();
            player.sendOverlayMessage(Component.translatable("message.wardenpact.no_room"));
            return;
        }
        guardians.put(playerId, guardian);
        nextSummon.put(playerId, now + PactConfig.COOLDOWN_SECONDS.get() * 20L);
        guardian.command(target);
        level.sendParticles(ParticleTypes.SCULK_SOUL, guardian.getX(), guardian.getY() + 1, guardian.getZ(),
                30, 0.5, 1, 0.5, 0.04);
        level.playSound(null, guardian.blockPosition(), SoundEvents.WARDEN_EMERGE, SoundSource.NEUTRAL, 1, 1);
        player.sendOverlayMessage(Component.translatable("message.wardenpact.summoned",
                PactConfig.LIFETIME_SECONDS.get()));
    }

    private static boolean findSafePosition(ServerLevel level, ServerPlayer player, FriendlyWarden guardian) {
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
                        guardian.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                        if (level.noCollision(guardian) && !level.containsAnyLiquid(guardian.getBoundingBox())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    @SubscribeEvent
    public void protectAllies(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof FriendlyWarden guardian
                && (FriendlyWarden.isProtected(event.getEntity()) || guardian.isAlliedTo(event.getEntity()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void serverStopped(ServerStoppedEvent event) {
        guardians.clear();
        nextSummon.clear();
    }
}
