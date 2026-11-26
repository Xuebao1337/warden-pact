package dev.wardenpact.mixin;

import dev.wardenpact.SculkMount;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {
    /** Do not embed a temporary mount (or an empty RootVehicle) into its rider's player data. */
    @Inject(method = "saveParentVehicle", at = @At("HEAD"), cancellable = true)
    private void wardenpact$skipTemporaryMount(ValueOutput output, CallbackInfo callback) {
        if (((ServerPlayer) (Object) this).getRootVehicle() instanceof SculkMount) callback.cancel();
    }
}
