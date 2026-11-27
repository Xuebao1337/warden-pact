package dev.wardenpact;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;

/** Innate armor powers do not require or replace enchantments. State is server-owned. */
public final class EchoGearAbilities {
    private final Map<UUID, Long> nextSense = new HashMap<>();
    private final Map<UUID, Long> nextPulse = new HashMap<>();
    private final Map<UUID, MobEffectInstance> helmetVision = new HashMap<>();

    private void updateNightVision(ServerPlayer wearer) {
        UUID id = wearer.getUUID();
        MobEffectInstance current = wearer.getEffect(MobEffects.NIGHT_VISION);
        // Ordinary potion effects must survive taking the helmet off, including ones
        // that have replaced or upgraded our effect through vanilla effect merging.
        boolean owned = current != null && current == helmetVision.get(id)
                && current.isAmbient() && !current.isVisible() && !current.showIcon();
        boolean equipped = wearer.isAlive() && !wearer.isSpectator()
                && wearer.getItemBySlot(EquipmentSlot.HEAD).is(PactEquipment.ECHO_HELMET);
        if (!equipped) {
            if (owned) wearer.removeEffect(MobEffects.NIGHT_VISION);
            helmetVision.remove(id);
            return;
        }
        if (current == null || owned && current.getDuration() <= 300) {
            // Renew well above the vanilla end-of-effect flashing window. A finite
            // safety duration also prevents stranded permanent effects in saved data.
            MobEffectInstance vision = new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0,
                    true, false, false);
            if (wearer.addEffect(vision)) helmetVision.put(id, wearer.getEffect(MobEffects.NIGHT_VISION));
        } else if (!owned) {
            helmetVision.remove(id);
        }
    }

    private static boolean hostile(ServerPlayer wearer, LivingEntity entity) {
        return entity instanceof Enemy && entity.isAlive() && !FriendlyWarden.isProtected(entity)
                && !wearer.isAlliedTo(entity);
    }

    public void tick(MinecraftServer server) {
        for (ServerPlayer wearer : server.getPlayerList().getPlayers()) updateNightVision(wearer);
        long now = server.overworld().getGameTime();
        if (now % 10 != 0) return;
        for (ServerPlayer wearer : server.getPlayerList().getPlayers()) {
            if (!wearer.isAlive() || wearer.isSpectator() || !wearer.isShiftKeyDown()
                    || !wearer.getItemBySlot(EquipmentSlot.HEAD).is(PactEquipment.ECHO_HELMET)
                    || nextSense.getOrDefault(wearer.getUUID(), 0L) > now) continue;
            nextSense.put(wearer.getUUID(), now + 160);
            ServerLevel level = (ServerLevel) wearer.level();
            var targets = level.getEntitiesOfClass(LivingEntity.class, wearer.getBoundingBox().inflate(16),
                    target -> hostile(wearer, target) && wearer.distanceToSqr(target) <= 16 * 16);
            targets.forEach(target -> target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0), wearer));
            level.playSound(null, wearer.blockPosition(), SoundEvents.WARDEN_HEARTBEAT,
                    SoundSource.PLAYERS, 0.35F, 1.4F);
            wearer.sendOverlayMessage(Component.translatable("message.wardenpact.echo_sense", targets.size()));
        }
        nextSense.entrySet().removeIf(entry -> entry.getValue() <= now);
        nextPulse.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    /** Called only after a real, nonzero damage result, so blocked hits cannot trigger free pulses. */
    public void afterDamage(LivingEntity wearer, DamageSource incoming, float damage) {
        if (!(wearer instanceof ServerPlayer player) || !player.isAlive() || damage <= 0
                || !player.getItemBySlot(EquipmentSlot.CHEST).is(PactEquipment.ECHO_CHESTPLATE)
                || !(incoming.getEntity() instanceof LivingEntity attacker) || !hostile(player, attacker)
                || player.distanceToSqr(attacker) > 16) return;
        ServerLevel level = (ServerLevel) player.level();
        long now = level.getServer().overworld().getGameTime();
        if (nextPulse.getOrDefault(player.getUUID(), 0L) > now) return;
        nextPulse.put(player.getUUID(), now + 160);
        // Thorns damage retains player attribution without pretending the pulse was a sword swing.
        DamageSource pulse = level.damageSources().thorns(player);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(4), entity -> hostile(player, entity)
                        && player.distanceToSqr(entity) <= 16)) {
            if (target.hurtServer(level, pulse, 4)) {
                target.knockback(0.8, player.getX() - target.getX(), player.getZ() - target.getZ(), pulse, 1);
            }
        }
        level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1, player.getZ(),
                20, 1.5, 0.5, 1.5, 0.03);
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE,
                SoundSource.PLAYERS, 0.5F, 1.6F);
        player.sendOverlayMessage(Component.translatable("message.wardenpact.echo_pulse"));
    }

    public void clear() {
        nextSense.clear();
        nextPulse.clear();
        helmetVision.clear();
    }
}
