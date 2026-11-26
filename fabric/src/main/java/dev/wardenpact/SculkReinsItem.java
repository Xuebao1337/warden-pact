package dev.wardenpact;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public final class SculkReinsItem extends Item {
    public SculkReinsItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return player instanceof ServerPlayer owner
                ? WardenPact.MOUNTS.use(owner, player.getItemInHand(hand)) : InteractionResult.SUCCESS;
    }
}
