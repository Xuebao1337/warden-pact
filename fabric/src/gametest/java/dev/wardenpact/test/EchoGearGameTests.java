package dev.wardenpact.test;

import dev.wardenpact.PactEquipment;
import dev.wardenpact.WardenPact;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import static dev.wardenpact.test.PactGameTests.player;
import static dev.wardenpact.test.PactGameTests.zombie;

/** Exercise real damage, equipment updates, block interaction, loot and tool breakage. */
public final class EchoGearGameTests {
    private static InteractionResult use(GameTestHelper h, ServerPlayer owner, BlockPos relative) {
        return use(h, owner, relative, Direction.UP);
    }

    private static InteractionResult use(GameTestHelper h, ServerPlayer owner, BlockPos relative, Direction face) {
        BlockPos pos = h.absolutePos(relative);
        return owner.getMainHandItem().useOn(new UseOnContext(owner, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false)));
    }

    private static void tool(ServerPlayer owner, Item item) {
        owner.setShiftKeyDown(true);
        owner.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item));
    }

    @GameTest(structure = "wardenpacttests:arena", maxTicks = 600)
    public void helmetNightVisionLastsWhileWornAndStopsWhenRemoved(GameTestHelper h) {
        ServerPlayer owner = player(h);
        // The effect must come from the head slot; merely holding the helmet is insufficient.
        owner.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(PactEquipment.ECHO_HELMET));
        WardenPact.GEAR.tick(h.getLevel().getServer());
        h.assertFalse(owner.hasEffect(MobEffects.NIGHT_VISION), "Holding a helmet must not grant night vision");
        owner.setItemSlot(EquipmentSlot.HEAD, new ItemStack(PactEquipment.ECHO_HELMET));
        WardenPact.GEAR.tick(h.getLevel().getServer());
        var vision = owner.getEffect(MobEffects.NIGHT_VISION);
        h.assertTrue(vision != null && vision.getDuration() > 220,
                "Equipping the helmet must grant night vision without sneaking");
        h.assertFalse(vision.isVisible() || vision.showIcon(), "Helmet vision must not add potion particles or HUD clutter");
        // Mock connections are not ticked normally; tick the player to actually expire effects.
        // More than the initial 400 ticks proves that renewal really works.
        for (int tick = 0; tick < 450; tick++) {
            owner.doTick();
            WardenPact.GEAR.tick(h.getLevel().getServer());
            var active = owner.getEffect(MobEffects.NIGHT_VISION);
            h.assertTrue(active != null && active.getDuration() > 220,
                    "Night vision must stay active above the flashing window while worn; tick=" + tick);
        }
        owner.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        WardenPact.GEAR.tick(h.getLevel().getServer());
        h.assertFalse(owner.hasEffect(MobEffects.NIGHT_VISION), "Unequipping must remove the helmet-provided vision");
        owner.discard();
        h.succeed();
    }

    @GameTest(structure = "wardenpacttests:arena", maxTicks = 240)
    public void helmetNightVisionReturnsAfterMilkAndDoesNotErasePotions(GameTestHelper h) {
        ServerPlayer owner = player(h);
        owner.setItemSlot(EquipmentSlot.HEAD, new ItemStack(PactEquipment.ECHO_HELMET));
        WardenPact.GEAR.tick(h.getLevel().getServer());
        owner.removeAllEffects();
        WardenPact.GEAR.tick(h.getLevel().getServer());
        h.assertTrue(owner.hasEffect(MobEffects.NIGHT_VISION), "Clearing potion effects must not disable an equipped helmet");
        owner.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        WardenPact.GEAR.tick(h.getLevel().getServer());
        owner.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 3600, 0));
        var potion = owner.getEffect(MobEffects.NIGHT_VISION);
        owner.setItemSlot(EquipmentSlot.HEAD, new ItemStack(PactEquipment.ECHO_HELMET));
        WardenPact.GEAR.tick(h.getLevel().getServer());
        owner.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        WardenPact.GEAR.tick(h.getLevel().getServer());
        h.assertTrue(owner.getEffect(MobEffects.NIGHT_VISION) == potion && potion.getDuration() == 3600,
                "Taking off the helmet must preserve independently supplied night-vision potions");
        owner.setItemSlot(EquipmentSlot.HEAD, new ItemStack(PactEquipment.ECHO_HELMET));
        owner.removeAllEffects();
        WardenPact.GEAR.tick(h.getLevel().getServer());
        owner.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 9600, 0));
        owner.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        WardenPact.GEAR.tick(h.getLevel().getServer());
        var extended = owner.getEffect(MobEffects.NIGHT_VISION);
        h.assertTrue(extended != null && extended.getDuration() == 9600,
                "A potion applied during helmet use must not be removed with the helmet");
        owner.discard();
        h.succeed();
    }

    @GameTest(structure = "wardenpacttests:arena", maxTicks = 240)
    public void helmetSensesHostilesAndStopsWhenRemoved(GameTestHelper h) {
        ServerPlayer owner = player(h);
        owner.setItemSlot(EquipmentSlot.HEAD, new ItemStack(PactEquipment.ECHO_HELMET));
        owner.setShiftKeyDown(true);
        var nearby = zombie(h, 12, 8);
        var outside = zombie(h, 26, 8);
        var cow = h.spawn(EntityTypes.COW, new BlockPos(9, 1, 9));
        cow.setNoAi(true);
        var ally = h.spawn(WardenPact.FRIENDLY_WARDEN, new BlockPos(10, 1, 10));
        ally.bind(owner, 220);
        ally.setNoAi(true);
        h.runAfterDelay(12, () -> {
            h.assertTrue(nearby.hasEffect(MobEffects.GLOWING), "Sneaking in the helmet must reveal nearby monsters");
            h.assertFalse(outside.hasEffect(MobEffects.GLOWING), "Sense must respect the 16-block radius");
            h.assertFalse(cow.hasEffect(MobEffects.GLOWING) || ally.hasEffect(MobEffects.GLOWING)
                    || owner.hasEffect(MobEffects.GLOWING), "Sense must exclude passive mobs, players and allies");
            nearby.removeEffect(MobEffects.GLOWING);
            h.runAfterDelay(35, () -> {
                h.assertFalse(nearby.hasEffect(MobEffects.GLOWING), "Sense must respect its eight-second cooldown");
                owner.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                h.runAfterDelay(145, () -> {
                    h.assertFalse(nearby.hasEffect(MobEffects.GLOWING), "Removing the helmet must stop sense scans");
                    owner.discard();
                    ally.discard();
                    h.succeed();
                });
            });
        });
    }

    @GameTest(structure = "wardenpacttests:arena", maxTicks = 240)
    public void chestPulseDamagesOnlyNearbyHostilesAndHasCooldown(GameTestHelper h) {
        ServerPlayer owner = player(h);
        owner.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        owner.setItemSlot(EquipmentSlot.CHEST, new ItemStack(PactEquipment.ECHO_CHESTPLATE));
        owner.doTick();
        var attacker = zombie(h, 10, 8);
        var nearby = zombie(h, 9, 10);
        var outside = zombie(h, 14, 8);
        var cow = h.spawn(EntityTypes.COW, new BlockPos(9, 1, 9));
        float cowHealth = cow.getHealth();
        float attackerHealth = attacker.getHealth();
        float nearbyHealth = nearby.getHealth();
        float outsideHealth = outside.getHealth();
        var source = h.getLevel().damageSources().mobAttack(attacker);
        owner.hurtServer(h.getLevel(), source, 5);
        h.assertTrue(attacker.getHealth() < attackerHealth && nearby.getHealth() < nearbyHealth,
                "Real hostile damage must trigger a pulse affecting nearby monsters");
        h.assertTrue(outside.getHealth() == outsideHealth && cow.getHealth() == cowHealth,
                "The pulse must exclude distant and passive mobs");
        h.assertTrue(nearby.getDeltaMovement().horizontalDistanceSqr() > 0, "The pulse must knock monsters back");
        float after = nearby.getHealth();
        nearby.damageCooldownTime = 0;
        attacker.damageCooldownTime = 0;
        owner.damageCooldownTime = 0;
        owner.hurtServer(h.getLevel(), source, 5);
        h.assertTrue(nearby.getHealth() == after, "Rapid incoming hits must not bypass the pulse cooldown");
        h.assertTrue(h.getLevel().getEntitiesOfClass(dev.wardenpact.FriendlyWarden.class,
                owner.getBoundingBox().inflate(10), e -> true).isEmpty(), "A pulse must not count as a sword swing");
        owner.discard();
        h.succeed();
    }

    @GameTest(structure = "wardenpacttests:arena", maxTicks = 240)
    public void chestDoesNotPulseForZeroEnvironmentalOrDistantDamage(GameTestHelper h) {
        ServerPlayer owner = player(h);
        owner.setItemSlot(EquipmentSlot.CHEST, new ItemStack(PactEquipment.ECHO_CHESTPLATE));
        var nearby = zombie(h, 10, 8);
        var outside = zombie(h, 14, 8);
        float health = nearby.getHealth();
        owner.hurtServer(h.getLevel(), h.getLevel().damageSources().mobAttack(nearby), 0);
        owner.damageCooldownTime = 0;
        owner.hurtServer(h.getLevel(), h.getLevel().damageSources().generic(), 1);
        owner.damageCooldownTime = 0;
        owner.hurtServer(h.getLevel(), h.getLevel().damageSources().mobAttack(outside), 1);
        h.assertTrue(nearby.getHealth() == health, "Zero, environmental and distant damage must not trigger pulses");
        owner.damageCooldownTime = 0;
        owner.hurtServer(h.getLevel(), h.getLevel().damageSources().mobAttack(nearby), 2);
        h.assertTrue(nearby.getHealth() < health, "Ignored damage must not consume the chestplate cooldown");
        owner.discard();
        h.succeed();
    }

    @GameTest(structure = "wardenpacttests:arena", maxTicks = 240)
    public void leggingsAndBootsApplyAndRemoveNativeBonuses(GameTestHelper h) {
        ServerPlayer owner = player(h);
        double speed = owner.getAttributeValue(Attributes.MOVEMENT_SPEED);
        owner.setItemSlot(EquipmentSlot.LEGS, new ItemStack(PactEquipment.ECHO_LEGGINGS));
        owner.setItemSlot(EquipmentSlot.FEET, new ItemStack(PactEquipment.ECHO_BOOTS));
        owner.doTick();
        h.assertTrue(Math.abs(owner.getAttributeValue(Attributes.MOVEMENT_SPEED) - speed * 1.15) < 0.00001,
                "Leggings must grant exactly 15 percent movement speed");
        h.assertTrue(Math.abs(owner.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER) - 0.5) < 0.00001,
                "Boots must halve the native fall damage multiplier");
        owner.setHealth(owner.getMaxHealth());
        owner.causeFallDamage(11, 1, h.getLevel().damageSources().fall());
        float cushioned = owner.getMaxHealth() - owner.getHealth();
        owner.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        owner.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        owner.doTick();
        owner.damageCooldownTime = 0;
        owner.setHealth(owner.getMaxHealth());
        owner.causeFallDamage(11, 1, h.getLevel().damageSources().fall());
        float ordinary = owner.getMaxHealth() - owner.getHealth();
        h.assertTrue(cushioned > 0 && Math.abs(cushioned * 2 - ordinary) < 0.001,
                "Boots must reduce actual fall damage; with=" + cushioned + ", without=" + ordinary);
        h.assertTrue(Math.abs(owner.getAttributeValue(Attributes.MOVEMENT_SPEED) - speed) < 0.00001
                && owner.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER) == 1,
                "Unequipping the gear must immediately remove both bonuses");
        owner.discard();
        h.succeed();
    }

    @GameTest(structure = "wardenpacttests:arena", maxTicks = 240)
    public void veinMiningPreservesSilkTouchDurabilityAndCooldown(GameTestHelper h) {
        ServerPlayer owner = player(h);
        tool(owner, PactEquipment.ECHO_PICKAXE);
        owner.getMainHandItem().enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SILK_TOUCH), 1);
        BlockPos start = new BlockPos(8, 1, 10);
        for (int i = 0; i < 3; i++) h.setBlock(start.east(i), Blocks.DIAMOND_ORE);
        h.setBlock(start.north(), Blocks.GOLD_ORE);
        h.setBlock(start.west(), Blocks.STONE);
        h.assertTrue(use(h, owner, start).consumesAction(), "Sneak-use on ore must trigger vein mining");
        for (int i = 0; i < 3; i++) h.assertBlockPresent(Blocks.AIR, start.east(i));
        h.assertBlockPresent(Blocks.GOLD_ORE, start.north());
        h.assertBlockPresent(Blocks.STONE, start.west());
        h.assertTrue(owner.getMainHandItem().getDamageValue() == 3, "Three blocks must cost three durability");
        var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class,
                owner.getBoundingBox().inflate(10), e -> e.getItem().is(Items.DIAMOND_ORE));
        h.assertTrue(drops.stream().mapToInt(e -> e.getItem().getCount()).sum() == 3,
                "Silk Touch must produce exactly one ore drop per mined block");
        h.setBlock(start, Blocks.DIAMOND_ORE);
        use(h, owner, start);
        h.assertBlockPresent(Blocks.DIAMOND_ORE, start);
        h.assertTrue(owner.getMainHandItem().getDamageValue() == 3, "Cooldown must block repeated mining without durability loss");
        owner.discard();
        h.succeed();
    }

    @GameTest(structure = "wardenpacttests:arena", maxTicks = 240)
    public void veinMiningSupportsFortuneAndStopsWhenToolBreaks(GameTestHelper h) {
        ServerPlayer owner = player(h);
        tool(owner, PactEquipment.ECHO_PICKAXE);
        owner.getMainHandItem().enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.FORTUNE), 3);
        BlockPos start = new BlockPos(8, 1, 10);
        h.setBlock(start, Blocks.COPPER_ORE);
        use(h, owner, start);
        var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class,
                owner.getBoundingBox().inflate(10), e -> e.getItem().is(Items.RAW_COPPER));
        int count = drops.stream().mapToInt(e -> e.getItem().getCount()).sum();
        h.assertTrue(count >= 2 && count <= 20, "Fortune must use vanilla copper loot bounds; actual=" + count);
        owner.getCooldowns().removeCooldown(owner.getCooldowns().getCooldownGroup(owner.getMainHandItem()));
        owner.getMainHandItem().setDamageValue(owner.getMainHandItem().getMaxDamage() - 1);
        h.setBlock(start, Blocks.DIAMOND_ORE);
        h.setBlock(start.east(), Blocks.DIAMOND_ORE);
        use(h, owner, start);
        h.assertBlockPresent(Blocks.AIR, start);
        h.assertBlockPresent(Blocks.DIAMOND_ORE, start.east());
        h.assertTrue(owner.getMainHandItem().isEmpty(), "A broken pickaxe must immediately stop the chain");
        owner.discard();
        h.succeed();
    }

    @GameTest(structure = "wardenpacttests:arena", maxTicks = 240)
    public void timberStopsAtSixteenAndPreservesOtherBlocks(GameTestHelper h) {
        ServerPlayer owner = player(h);
        tool(owner, PactEquipment.ECHO_AXE);
        BlockPos start = new BlockPos(8, 1, 10);
        for (int x = 0; x < 5; x++) for (int z = 0; z < 4; z++) h.setBlock(start.offset(x, 0, z), Blocks.OAK_LOG);
        h.setBlock(start.west(), Blocks.BIRCH_LOG);
        h.setBlock(start.north(), Blocks.OAK_PLANKS);
        use(h, owner, start);
        int remaining = 0;
        for (int x = 0; x < 5; x++) for (int z = 0; z < 4; z++) {
            if (h.getLevel().getBlockState(h.absolutePos(start.offset(x, 0, z))).is(Blocks.OAK_LOG)) remaining++;
        }
        h.assertTrue(remaining == 4 && owner.getMainHandItem().getDamageValue() == 16,
                "A chain must stop after 16 logs and charge durability for exactly those blocks");
        h.assertBlockPresent(Blocks.BIRCH_LOG, start.west());
        h.assertBlockPresent(Blocks.OAK_PLANKS, start.north());
        owner.discard();
        h.succeed();
    }

    @GameTest(structure = "wardenpacttests:arena", maxTicks = 240)
    public void areaShovelAndHoeUseNativeTransformsAndDurability(GameTestHelper h) {
        ServerPlayer owner = player(h);
        BlockPos start = new BlockPos(8, 1, 10);
        Item[] tools = {PactEquipment.ECHO_SHOVEL, PactEquipment.ECHO_HOE};
        var results = new net.minecraft.world.level.block.Block[] {Blocks.DIRT_PATH, Blocks.FARMLAND};
        for (int i = 0; i < tools.length; i++) {
            tool(owner, tools[i]);
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                h.setBlock(start.offset(x, 0, z), Blocks.DIRT);
                h.setBlock(start.offset(x, 1, z), Blocks.AIR);
            }
            h.setBlock(start.offset(1, 1, 1), Blocks.STONE);
            use(h, owner, start);
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                h.assertBlockPresent(x == 1 && z == 1 ? Blocks.DIRT : results[i], start.offset(x, 0, z));
            }
            h.assertBlockPresent(Blocks.DIRT, start.east(2));
            h.assertTrue(owner.getMainHandItem().getDamageValue() == 8,
                    "Only the eight valid transformed blocks must consume durability");
        }
        owner.discard();
        h.succeed();
    }

    @GameTest(structure = "wardenpacttests:arena", maxTicks = 240)
    public void areaSkillStopsOnBreakAndLeavesInvalidCentersAlone(GameTestHelper h) {
        ServerPlayer owner = player(h);
        tool(owner, PactEquipment.ECHO_HOE);
        BlockPos start = new BlockPos(8, 1, 10);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) h.setBlock(start.offset(x, 0, z), Blocks.DIRT);
        h.setBlock(start.above(), Blocks.STONE);
        use(h, owner, start);
        h.assertTrue(owner.getMainHandItem().getDamageValue() == 0, "A blocked center must not affect neighbors");
        h.assertFalse(owner.getCooldowns().isOnCooldown(owner.getMainHandItem()), "An invalid action must not consume cooldown");
        h.setBlock(start.above(), Blocks.AIR);
        use(h, owner, start, Direction.NORTH);
        h.assertBlockPresent(Blocks.DIRT, start);
        owner.getMainHandItem().setDamageValue(owner.getMainHandItem().getMaxDamage() - 1);
        use(h, owner, start);
        h.assertBlockPresent(Blocks.FARMLAND, start);
        h.assertBlockPresent(Blocks.DIRT, start.west());
        h.assertTrue(owner.getMainHandItem().isEmpty(), "Area transforms must stop when the tool breaks");
        owner.discard();
        h.succeed();
    }

    @GameTest(structure = "wardenpacttests:arena", maxTicks = 240)
    public void skillRespectsGameModesReachAndNonOreTargets(GameTestHelper h) {
        ServerPlayer owner = player(h);
        tool(owner, PactEquipment.ECHO_PICKAXE);
        BlockPos start = new BlockPos(8, 1, 10);
        h.setBlock(start, Blocks.DIAMOND_ORE);
        owner.setGameMode(GameType.ADVENTURE);
        use(h, owner, start);
        h.assertBlockPresent(Blocks.DIAMOND_ORE, start);
        owner.setGameMode(GameType.SPECTATOR);
        use(h, owner, start);
        h.assertBlockPresent(Blocks.DIAMOND_ORE, start);
        owner.setGameMode(GameType.SURVIVAL);
        BlockPos far = new BlockPos(24, 1, 8);
        h.setBlock(far, Blocks.DIAMOND_ORE);
        use(h, owner, far);
        h.assertBlockPresent(Blocks.DIAMOND_ORE, far);
        h.setBlock(start, Blocks.STONE);
        use(h, owner, start);
        h.assertBlockPresent(Blocks.STONE, start);
        h.assertTrue(owner.getMainHandItem().getDamageValue() == 0, "Rejected actions must not damage the tool");
        h.assertFalse(owner.getCooldowns().isOnCooldown(owner.getMainHandItem()), "Rejected actions must not consume cooldown");
        h.setBlock(start, Blocks.DIAMOND_ORE);
        owner.setGameMode(GameType.CREATIVE);
        use(h, owner, start);
        h.assertBlockPresent(Blocks.AIR, start);
        h.assertTrue(owner.getMainHandItem().getDamageValue() == 0, "Creative use must not consume durability");
        h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, owner.getBoundingBox().inflate(8), e -> true).isEmpty(),
                "Creative use must not create ore drops");
        owner.discard();
        h.succeed();
    }
}
