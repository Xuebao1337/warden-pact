package dev.wardenpact;

import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/** A temporary pact creature: vanilla horse controls, a sculk beast appearance, no storage or drops. */
public final class SculkMount extends Horse {
    private UUID ownerId;

    public SculkMount(EntityType<? extends SculkMount> type, Level level) {
        super(type, level);
        xpReward = 0;
        setPersistenceRequired();
        setTamed(true);
        // The native saddle component enables client prediction, steering and charged jumps.
        // This internal saddle cannot be removed through a horse inventory or dropped on death.
        ItemStack saddle = new ItemStack(Items.SADDLE);
        saddle.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.SADDLE)
                .setAllowedEntities(type).setDispensable(false).setSwappable(false)
                .setEquipOnInteract(false).setCanBeSheared(false).build());
        setItemSlot(EquipmentSlot.SADDLE, saddle);
    }

    public static AttributeSupplier.Builder createMountAttributes() {
        return createBaseHorseAttributes().add(Attributes.MAX_HEALTH, 60)
                .add(Attributes.MOVEMENT_SPEED, 0.28).add(Attributes.JUMP_STRENGTH, 0.7)
                .add(Attributes.ARMOR, 8).add(Attributes.KNOCKBACK_RESISTANCE, 0.5);
    }

    public void bind(ServerPlayer player) {
        ownerId = player.getUUID();
        setOwner(player);
        setHealth(getMaxHealth());
        setCustomName(Component.translatable("entity.wardenpact.owned_sculk_mount", player.getName()));
        setCustomNameVisible(true);
    }

    public boolean belongsTo(Player player) {
        return player.getUUID().equals(ownerId);
    }

    public ServerPlayer owner() {
        return ownerId != null && level() instanceof ServerLevel level
                && level.getPlayerByUUID(ownerId) instanceof ServerPlayer player ? player : null;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!(level() instanceof ServerLevel)) return InteractionResult.SUCCESS;
        if (!belongsTo(player)) {
            player.sendOverlayMessage(Component.translatable("message.wardenpact.mount_not_yours"));
            return InteractionResult.FAIL;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(Items.ECHO_SHARD) && getHealth() < getMaxHealth()) {
            heal(8);
            stack.consume(1, player);
            playSound(SoundEvents.WARDEN_HEARTBEAT, 0.7F, 1.2F);
            return InteractionResult.SUCCESS;
        }
        if (!player.isSecondaryUseActive() && !isVehicle()) {
            player.startRiding(this);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel) {
            ServerPlayer player = owner();
            if (player == null || !player.isAlive()) discard();
        }
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(3, new FollowOwnerGoal());
    }

    @Override
    public void openCustomInventoryScreen(Player player) {
        if (!level().isClientSide()) {
            player.sendOverlayMessage(Component.translatable("message.wardenpact.mount_no_inventory"));
        }
    }

    @Override
    protected boolean canDispenserEquipIntoSlot(EquipmentSlot slot) { return false; }

    @Override
    protected void dropEquipment(ServerLevel level) {}

    // EntityType.noSave blocks startRiding in 26.3. Refuse storage at the entity level instead.
    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    public boolean saveAsPassenger(ValueOutput output) { return false; }

    @Override
    public boolean canMate(Animal other) { return false; }

    @Override
    public boolean isFood(ItemStack stack) { return false; }

    @Override
    public boolean canEatGrass() { return false; }

    @Override
    protected boolean canPerformRearing() { return false; }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        return new Vec3(0, dimensions.height() - 0.35F * scale, 0);
    }

    @Override
    protected SoundEvent getAmbientSound() { return SoundEvents.WARDEN_AMBIENT; }

    @Override
    protected SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
        return SoundEvents.WARDEN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.WARDEN_DEATH; }

    private final class FollowOwnerGoal extends Goal {
        private int delay;

        private FollowOwnerGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }

        @Override
        public boolean canUse() {
            ServerPlayer player = owner();
            return !isVehicle() && player != null && player.isAlive() && distanceToSqr(player) > 36;
        }

        @Override
        public boolean canContinueToUse() {
            ServerPlayer player = owner();
            return !isVehicle() && player != null && player.isAlive() && distanceToSqr(player) > 9;
        }

        @Override
        public void start() { delay = 0; }

        @Override
        public void stop() { getNavigation().stop(); }

        @Override
        public void tick() {
            ServerPlayer player = owner();
            if (player != null && --delay <= 0) {
                delay = 10;
                getLookControl().setLookAt(player, 10, 20);
                getNavigation().moveTo(player, 1.0);
            }
        }
    }
}
