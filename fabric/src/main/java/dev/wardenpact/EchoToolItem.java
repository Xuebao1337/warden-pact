package dev.wardenpact;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Special actions are explicit sneak-use; ordinary tool components and interactions are retained. */
public final class EchoToolItem extends Item {
    public enum Skill { ORE, LOGS, PATH, FARM }
    private static final TagKey<Block> ORES = TagKey.create(Registries.BLOCK, WardenPact.id("echo_ores"));
    private final Skill skill;

    public EchoToolItem(Properties properties, Skill skill) {
        super(properties);
        this.skill = skill;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() == null || !context.getPlayer().isShiftKeyDown()
                || context.getHand() != InteractionHand.MAIN_HAND) return super.useOn(context);
        BlockState start = context.getLevel().getBlockState(context.getClickedPos());
        if ((skill == Skill.ORE && !start.is(ORES)) || (skill == Skill.LOGS && !start.is(BlockTags.LOGS))) {
            return super.useOn(context);
        }
        if (!(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.SUCCESS;
        if ((!player.gameMode.isSurvival() && !player.gameMode.isCreative())
                || player.getCooldowns().isOnCooldown(context.getItemInHand())) return InteractionResult.FAIL;
        ServerLevel level = (ServerLevel) player.level();
        int changed = skill == Skill.ORE || skill == Skill.LOGS
                ? breakConnected(player, level, context, start) : transformArea(player, level, context);
        if (changed == 0) return InteractionResult.PASS;
        player.getCooldowns().addCooldown(new ItemStack(this), 40);
        player.sendOverlayMessage(Component.translatable("message.wardenpact.echo_tool", changed));
        return InteractionResult.SUCCESS;
    }

    private boolean allowed(ServerPlayer player, ServerLevel level, BlockPos pos, UseOnContext context) {
        return level.hasChunkAt(pos) && level.getWorldBorder().isWithinBounds(pos)
                && level.mayInteract(player, pos)
                && player.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand());
    }

    private int breakConnected(ServerPlayer player, ServerLevel level, UseOnContext context, BlockState start) {
        BlockPos origin = context.getClickedPos();
        if (player.distanceToSqr(Vec3.atCenterOf(origin)) > 64) return 0;
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(origin);
        int changed = 0;
        while (!queue.isEmpty() && changed < 16 && !context.getItemInHand().isEmpty()
                && context.getItemInHand().is(this)) {
            BlockPos pos = queue.removeFirst();
            if (!visited.add(pos) || origin.distSqr(pos) > 64 || !allowed(player, level, pos, context)) continue;
            BlockState state = level.getBlockState(pos);
            if (!state.is(start.getBlock()) || state.getDestroySpeed(level, pos) < 0
                    || !context.getItemInHand().isCorrectToolForDrops(state)) continue;
            // The real player break path preserves loot, Fortune/Silk Touch, durability and break events.
            if (!player.gameMode.destroyBlock(pos)) continue;
            changed++;
            for (Direction direction : Direction.values()) queue.addLast(pos.relative(direction));
        }
        return changed;
    }

    private int transformArea(ServerPlayer player, ServerLevel level, UseOnContext context) {
        if (context.getClickedFace() != Direction.UP) return 0;
        int changed = 0;
        // Center first: a nearly broken tool must always act on the block the player selected.
        int[][] offsets = {{0, 0}, {-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}};
        for (int[] offset : offsets) {
            if (context.getItemInHand().isEmpty() || !context.getItemInHand().is(this)) break;
            BlockPos pos = context.getClickedPos().offset(offset[0], 0, offset[1]);
            if (!allowed(player, level, pos, context)) continue;
            UseOnContext next = new UseOnContext(player, context.getHand(),
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
            if (super.useOn(next).consumesAction()) changed++;
            else if (changed == 0) return 0;
        }
        return changed;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, lines, flag);
        lines.accept(Component.translatable(getDescriptionId() + ".ability").withStyle(ChatFormatting.AQUA));
        lines.accept(Component.translatable("tooltip.wardenpact.echo_tool_controls").withStyle(ChatFormatting.GRAY));
    }
}
