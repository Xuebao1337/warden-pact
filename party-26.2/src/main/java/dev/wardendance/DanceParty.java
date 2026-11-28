package dev.wardendance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Group state and payload handlers run on the server thread. */
public final class DanceParty {
    public static final int COUNT = 5;
    public static final int LIFETIME = 600;
    public static final int COOLDOWN = 100;
    private static final int[] MELODY = {0, 4, 7, 12, 7, 4, 2, 7, 11, 14, 11, 7, 0, 7, 12, 7};
    private final Map<UUID, Party> parties = new HashMap<>();
    private final Map<UUID, Long> nextStart = new HashMap<>();
    private record Party(ServerLevel level, long started, List<DancingWarden> dancers) {}
    public enum Result { STARTED, DISMISSED, COOLDOWN, NO_ROOM, UNAVAILABLE }

    public Result toggle(ServerPlayer player) {
        if (!player.isAlive() || player.isRemoved() || player.isSpectator()) return Result.UNAVAILABLE;
        UUID id = player.getUUID();
        Party existing = parties.get(id);
        if (existing != null) {
            boolean alive = existing.dancers.stream().anyMatch(d -> !d.isRemoved());
            dismiss(id);
            if (alive) {
                player.sendOverlayMessage(Component.translatable("message.wardendance.dismissed"));
                return Result.DISMISSED;
            }
        }
        ServerLevel level = (ServerLevel) player.level();
        long now = level.getServer().overworld().getGameTime();
        if (nextStart.getOrDefault(id, 0L) > now) {
            player.sendOverlayMessage(Component.translatable("message.wardendance.cooldown"));
            return Result.COOLDOWN;
        }
        List<DancingWarden> dancers = new ArrayList<>();
        // Prefer a semicircle in front of the player, then try three other orientations.
        for (int index = 0; index < COUNT; index++) {
            DancingWarden dancer = WardenDance.DANCER.get().create(level, EntitySpawnReason.TRIGGERED);
            if (dancer == null || !place(level, player, dancer, dancers, index)) {
                if (dancer != null) dancer.discard();
                dancers.forEach(DancingWarden::discard);
                player.sendOverlayMessage(Component.translatable("message.wardendance.no_room"));
                return Result.NO_ROOM;
            }
            dancer.bind(player, index, LIFETIME);
            dancers.add(dancer);
        }
        for (DancingWarden dancer : dancers) {
            if (!level.addFreshEntity(dancer)) {
                dancers.forEach(DancingWarden::discard);
                return Result.UNAVAILABLE;
            }
        }
        parties.put(id, new Party(level, level.getGameTime(), List.copyOf(dancers)));
        nextStart.put(id, now + COOLDOWN);
        player.sendOverlayMessage(Component.translatable("message.wardendance.started", COUNT, LIFETIME / 20));
        return Result.STARTED;
    }

    private static boolean place(ServerLevel level, ServerPlayer owner, DancingWarden dancer,
                                 List<DancingWarden> chosen, int index) {
        for (int turn = 0; turn < 4; turn++) {
            double angle = Math.toRadians(owner.getYRot() + turn * 90 - 72 + index * 36);
            double x = owner.getX() - Math.sin(angle) * 4.5;
            double z = owner.getZ() + Math.cos(angle) * 4.5;
            for (int dy : new int[] {0, 1, -1, 2, -2}) {
                BlockPos feet = BlockPos.containing(x, owner.getY() + dy, z);
                dancer.setPos(x, feet.getY(), z);
                var room = dancer.getBoundingBox().expandTowards(0, 0.35, 0);
                // Check all touched chunks before any block lookup; never force-load terrain.
                boolean loaded = true;
                for (int cx = ((int) Math.floor(room.minX)) >> 4; cx <= ((int) Math.floor(room.maxX)) >> 4; cx++)
                    for (int cz = ((int) Math.floor(room.minZ)) >> 4; cz <= ((int) Math.floor(room.maxZ)) >> 4; cz++)
                        loaded &= level.hasChunk(cx, cz);
                if (!loaded || !level.getWorldBorder().isWithinBounds(room)) continue;
                BlockPos ground = feet.below();
                if (!level.getBlockState(ground).isFaceSturdy(level, ground, Direction.UP)) continue;
                if (!level.noCollision(dancer, room) || level.containsAnyLiquid(room)) continue;
                if (chosen.stream().anyMatch(d -> d.getBoundingBox().inflate(0.2).intersects(room))) continue;
                return true;
            }
        }
        return false;
    }

    private void dismiss(UUID id) {
        Party party = parties.remove(id);
        if (party != null) party.dancers.forEach(DancingWarden::discard);
    }

    @SubscribeEvent public void tick(ServerTickEvent.Post event) {
        var iterator = parties.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Party party = entry.getValue();
            ServerPlayer owner = event.getServer().getPlayerList().getPlayer(entry.getKey());
            long elapsed = party.level.getGameTime() - party.started;
            if (owner == null || !owner.isAlive() || owner.isRemoved() || owner.level() != party.level
                    || elapsed >= LIFETIME || party.dancers.stream().allMatch(d -> d.isRemoved())) {
                party.dancers.forEach(DancingWarden::discard);
                iterator.remove();
                continue;
            }
            if (elapsed % 10 == 0) {
                DancingWarden leader = party.dancers.stream().filter(d -> !d.isRemoved()).findFirst().orElseThrow();
                int note = MELODY[(int) (elapsed / 10) % MELODY.length];
                party.level.playSound(null, leader.getX(), leader.getY(), leader.getZ(),
                        SoundEvents.NOTE_BLOCK_PLING, SoundSource.RECORDS, 0.45F, (float) Math.pow(2, (note - 12) / 12.0));
            }
        }
        long now = event.getServer().overworld().getGameTime();
        nextStart.entrySet().removeIf(entry -> entry.getValue() <= now);
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        dismiss(event.getEntity().getUUID());
        nextStart.remove(event.getEntity().getUUID());
    }
    @SubscribeEvent public void stopped(ServerStoppedEvent event) {
        parties.clear(); nextStart.clear();
    }
}
