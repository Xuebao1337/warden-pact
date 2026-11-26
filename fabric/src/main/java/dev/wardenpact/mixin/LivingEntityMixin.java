package dev.wardenpact.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.wardenpact.PactEnchantments;
import dev.wardenpact.WardenPact;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
    /** Include fatal hits; blocked hits and hits absorbed by invulnerability do not summon. */
    @WrapMethod(method = "hurtServer")
    private boolean wardenpact$damage(ServerLevel level, DamageSource source, float amount,
            Operation<Boolean> original) {
        LivingEntity self = (LivingEntity) (Object) this;
        float before = self.getHealth() + self.getAbsorptionAmount();
        boolean result = original.call(level, source, PactEnchantments.guardDamage(self, source, amount));
        float damage = before - self.getHealth() - self.getAbsorptionAmount();
        if (result && damage > 0) {
            WardenPact.SUMMONS.onSwordHit(self, source, damage);
        }
        return result;
    }
}
