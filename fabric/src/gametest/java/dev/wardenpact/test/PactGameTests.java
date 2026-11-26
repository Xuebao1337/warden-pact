package dev.wardenpact.test;

import dev.wardenpact.FriendlyWarden;
import dev.wardenpact.WardenPact;
import dev.wardenpact.PactConfig;
import dev.wardenpact.PactEnchantments;
import dev.wardenpact.PactEquipment;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;

/** Loaded only by runGameTestServer, never included in the distributed mod jar. */
public final class PactGameTests {
    public static final String ID = "wardenpacttests";
    private static ServerPlayer player(GameTestHelper h) {
        h.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
        ServerPlayer player = (ServerPlayer) h.makeMockServerPlayer(GameType.SURVIVAL);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false));
        player.setGameMode(GameType.SURVIVAL);
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        BlockPos pos = h.absolutePos(new BlockPos(8, 1, 8));
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        return player;
    }

    private static Zombie zombie(GameTestHelper h, int x, int z) {
        Zombie zombie = h.spawn(EntityTypes.ZOMBIE, new BlockPos(x, 1, z));
        zombie.setNoAi(true);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        return zombie;
    }

    private static void hit(GameTestHelper h, ServerPlayer player, LivingEntity target) {
        target.damageCooldownTime = 0;
        target.hurtServer(h.getLevel(), h.getLevel().damageSources().playerAttack(player), 1);
    }

    private static List<FriendlyWarden> guardians(GameTestHelper h, ServerPlayer owner) {
        return h.getLevel().getEntitiesOfClass(FriendlyWarden.class, owner.getBoundingBox().inflate(40),
                w -> w.owner() == owner && w.isAlive() && !w.isRemoved());
    }

    private static FriendlyWarden summon(GameTestHelper h, ServerPlayer owner) {
        hit(h, owner, zombie(h, 11, 8));
        List<FriendlyWarden> wardens = guardians(h, owner);
        h.assertTrue(wardens.size() == 1, "A successful sword hit must summon one warden");
        return wardens.getFirst();
    }

    @GameTest(structure = "wardenpacttests:arena", maxTicks = 240)
    public void summonsAndRedirects(GameTestHelper h) {
        ServerPlayer owner = player(h);
        FriendlyWarden warden = summon(h, owner);
        Zombie second = zombie(h, 12, 9);
        hit(h, owner, second);
        h.assertTrue(guardians(h, owner).size() == 1, "Repeated hits must not duplicate the guardian");
        h.assertTrue(warden.getTarget() == second, "A new hit must redirect the existing warden");
        warden.discard();
        hit(h, owner, zombie(h, 10, 10));
        h.assertTrue(guardians(h, owner).isEmpty(), "The per-player cooldown must prevent replacement spam");
        owner.discard();
        h.succeed();
    }

    @GameTest(structure = "wardenpacttests:arena", maxTicks = 240)
    public void protectsPlayersAndPets(GameTestHelper h) {
        ServerPlayer owner = player(h);
        FriendlyWarden warden = summon(h, owner);
        var pet = h.spawn(EntityTypes.WOLF, new BlockPos(8, 1, 10));
        pet.tame(owner);
        h.assertFalse(warden.canTargetEntity(owner), "The warden must never target a player");
        h.assertFalse(warden.canTargetEntity(pet), "The warden must never target tamed pets");
        float health = owner.getHealth();
        owner.hurtServer(h.getLevel(), h.getLevel().damageSources().generic(), 1);
        h.assertTrue(owner.getHealth() < health, "The player must be vulnerable so this verifies real friendly-fire protection");
        owner.setHealth(health);
        owner.damageCooldownTime = 0;
        owner.hurtServer(h.getLevel(), h.getLevel().damageSources().mobAttack(warden), 10);
        h.assertTrue(owner.getHealth() == health, "Friendly fire must be canceled");
        float petHealth = pet.getHealth();
        pet.hurtServer(h.getLevel(), h.getLevel().damageSources().sonicBoom(warden), 10);
        h.assertTrue(pet.getHealth() == petHealth, "Sonic booms must not harm pets");
        h.runAfterDelay(140, () -> {
            h.assertFalse(owner.hasEffect(MobEffects.DARKNESS), "The ally must not apply darkness to its owner");
            warden.discard();
            owner.discard();
            h.succeed();
        });
    }

    @GameTest(structure = "wardenpacttests:arena", maxTicks = 240)
    public void expires(GameTestHelper h) {
        ServerPlayer owner = player(h);
        FriendlyWarden warden = summon(h, owner);
        warden.bind(owner, 4);
        h.runAfterDelay(8, () -> {
            h.assertTrue(warden.isRemoved(), "A guardian must disappear when its lifetime ends");
            h.assertFalse(WardenPact.FRIENDLY_WARDEN.canSerialize(), "Temporary summons must not persist across reloads");
            owner.discard();
            h.succeed();
        });
    }

    @GameTest(structure = "wardenpacttests:arena", maxTicks = 240)
    public void attacksMonsters(GameTestHelper h) {
        ServerPlayer owner = player(h);
        Zombie target = zombie(h, 11, 8);
        hit(h, owner, target);
        h.runAfterDelay(160, () -> {
            h.assertTrue(target.isDeadOrDying() || target.isRemoved(), "The summoned warden must actually kill its ordered target");
            guardians(h, owner).forEach(FriendlyWarden::discard);
            owner.discard();
            h.succeed();
        });
    }

    @GameTest(structure = "wardenpacttests:arena", maxTicks = 240)
    public void followsOwner(GameTestHelper h) {
        ServerPlayer owner = player(h);
        FriendlyWarden warden = summon(h, owner);
        warden.getTarget().discard();
        BlockPos pos = h.absolutePos(new BlockPos(22, 1, 8));
        owner.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        double initialDistance = warden.distanceToSqr(owner);
        h.runAfterDelay(180, () -> {
            h.assertTrue(warden.distanceToSqr(owner) < initialDistance / 2,
                    "An idle guardian must follow its owner; initial distance squared=" + initialDistance
                    + ", final=" + warden.distanceToSqr(owner) + ", warden=" + warden.position()
                    + ", owner=" + owner.position() + ", pose=" + warden.getPose()
                    + ", target=" + warden.getTarget() + ", path done=" + warden.getNavigation().isDone()
                    + ", walking=" + warden.getBrain().getMemory(MemoryModuleType.WALK_TARGET));
            warden.discard();
            owner.discard();
            h.succeed();
        });
    }

    @GameTest(structure = "wardenpacttests:arena", maxTicks = 240)
    public void checksSpawnSpace(GameTestHelper h) {
        ServerPlayer owner = player(h);
        for (BlockPos pos : BlockPos.betweenClosed(1, 1, 1, 15, 8, 15)) {
            h.setBlock(pos, Blocks.STONE);
        }
        Zombie target = zombie(h, 9, 8);
        hit(h, owner, target);
        h.assertTrue(guardians(h, owner).isEmpty(), "Do not summon inside solid blocks");
        for (BlockPos pos : BlockPos.betweenClosed(1, 1, 1, 15, 8, 15)) {
            h.setBlock(pos, Blocks.AIR);
        }
        float before = target.getHealth();
        hit(h, owner, target);
        h.assertTrue(guardians(h, owner).size() == 1, "A failed summon must not consume the cooldown; before="
                + before + ", after=" + target.getHealth() + ", timer=" + target.damageCooldownTime
                + ", target alive=" + target.isAlive() + ", position=" + owner.position());
        guardians(h, owner).forEach(FriendlyWarden::discard);
        owner.discard();
        h.succeed();
    }

    @GameTest(structure = "wardenpacttests:arena", maxTicks = 240)
    public void ignoresFriendlyTargets(GameTestHelper h) {
        ServerPlayer owner = player(h);
        hit(h, owner, h.spawn(EntityTypes.COW, new BlockPos(10, 1, 8)));
        h.assertTrue(guardians(h, owner).isEmpty(), "Hitting a passive animal must not summon a warden");
        owner.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STICK));
        hit(h, owner, zombie(h, 11, 8));
        h.assertTrue(guardians(h, owner).isEmpty(), "Non-sword damage must not summon a warden");
        owner.discard();
        h.succeed();
    }

    @GameTest(structure = "wardenpacttests:arena", maxTicks = 240)
    public void fatalSwordHitSummons(GameTestHelper h) {
        ServerPlayer owner = player(h);
        Zombie target = zombie(h, 11, 8);
        target.setHealth(0.5F);
        hit(h, owner, target);
        h.assertTrue(target.isDeadOrDying(), "The test must deal a fatal sword hit");
        h.assertTrue(guardians(h, owner).size() == 1, "Fatal sword hits must also summon a guardian");
        guardians(h, owner).forEach(FriendlyWarden::discard);
        owner.discard();
        h.succeed();
    }

    @GameTest(structure = "wardenpacttests:arena", maxTicks = 240)
    public void rejectedDamageDoesNotSummon(GameTestHelper h) {
        ServerPlayer owner = player(h);
        Zombie target = zombie(h, 11, 8);
        target.setPermanentlyInvulnerable(true);
        hit(h, owner, target);
        h.assertTrue(guardians(h, owner).isEmpty(), "An invulnerable target must not trigger a summon");
        target.setPermanentlyInvulnerable(false);
        hit(h, owner, target);
        h.assertTrue(guardians(h, owner).size() == 1, "Rejected damage must not consume the summon cooldown");
        guardians(h, owner).forEach(FriendlyWarden::discard);
        owner.discard();
        h.succeed();
    }

    @GameTest(structure = "wardenpacttests:arena", maxTicks = 240)
    public void equipmentComponents(GameTestHelper h) {
        ServerPlayer owner = player(h);
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        Item[] vanillaArmor = {Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE,
                Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS};
        for (int i = 0; i < slots.length; i++) {
            ItemStack armor = new ItemStack(PactEquipment.ARMOR.get(i));
            var equippable = armor.get(DataComponents.EQUIPPABLE);
            h.assertTrue(equippable != null && equippable.slot() == slots[i], "Armor must use its native equipment slot");
            h.assertTrue(equippable.assetId().orElseThrow().equals(PactEquipment.ARMOR_MATERIAL.assetId()),
                    "Armor must render the custom worn equipment asset");
            h.assertTrue(armor.getMaxDamage() == new ItemStack(vanillaArmor[i]).getMaxDamage(),
                    "Armor must retain netherite durability");
            owner.setItemSlot(slots[i], armor);
        }
        ItemStack pickaxe = new ItemStack(PactEquipment.ECHO_PICKAXE);
        h.assertTrue(pickaxe.is(ItemTags.PICKAXES) && pickaxe.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState()),
                "The pickaxe must harvest obsidian as a real netherite-tier pickaxe");
        h.assertTrue(pickaxe.getMaxDamage() == new ItemStack(Items.NETHERITE_PICKAXE).getMaxDamage(),
                "The pickaxe must retain netherite durability");
        h.runAfterDelay(2, () -> {
            // Mock connections are not in the server connection ticker; exercise the real player tick.
            owner.doTick();
            h.assertTrue(owner.getArmorValue() == 20, "The armor set must provide 20 armor; actual=" + owner.getArmorValue());
            h.assertTrue(Math.abs(owner.getAttributeValue(Attributes.ARMOR_TOUGHNESS) - 12) < 0.001,
                    "The armor set must provide 12 toughness");
            owner.discard();
            h.succeed();
        });
    }

    private static void useTool(GameTestHelper h, ServerPlayer owner, Item item,
            net.minecraft.world.level.block.Block before, net.minecraft.world.level.block.Block after, int x) {
        BlockPos pos = new BlockPos(x, 1, 6);
        h.setBlock(pos, before);
        owner.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item));
        BlockPos absolute = h.absolutePos(pos);
        owner.getMainHandItem().useOn(new UseOnContext(owner, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false)));
        h.assertBlockPresent(after, pos);
        h.assertTrue(owner.getMainHandItem().getDamageValue() == 1, "Tool actions must consume durability");
    }

    @GameTest(structure = "wardenpacttests:arena", maxTicks = 240)
    public void toolActions(GameTestHelper h) {
        ServerPlayer owner = player(h);
        useTool(h, owner, PactEquipment.ECHO_AXE, Blocks.OAK_LOG, Blocks.STRIPPED_OAK_LOG, 5);
        useTool(h, owner, PactEquipment.ECHO_SHOVEL, Blocks.DIRT, Blocks.DIRT_PATH, 6);
        useTool(h, owner, PactEquipment.ECHO_HOE, Blocks.DIRT, Blocks.FARMLAND, 7);
        owner.discard();
        h.succeed();
    }

    @GameTest(structure = "wardenpacttests:arena", maxTicks = 240)
    public void enchantmentAvailability(GameTestHelper h) {
        var registry = h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var resonance = registry.getOrThrow(PactEnchantments.CONTRACT_RESONANCE);
        var guard = registry.getOrThrow(PactEnchantments.SCULK_GUARD);
        var mining = registry.getOrThrow(PactEnchantments.ECHO_MINING);
        for (var enchantment : List.of(resonance, guard, mining)) {
            h.assertTrue(enchantment.is(EnchantmentTags.IN_ENCHANTING_TABLE), "Custom enchantments must appear in enchanting tables");
            h.assertTrue(enchantment.is(EnchantmentTags.TRADEABLE) && enchantment.is(EnchantmentTags.ON_RANDOM_LOOT),
                    "Custom enchantment books must join vanilla trade and loot pools");
        }
        h.assertTrue(resonance.value().canEnchant(new ItemStack(WardenPact.SUMMONING_SWORD)),
                "Resonance must apply to the summoning sword");
        h.assertFalse(resonance.value().canEnchant(new ItemStack(PactEquipment.ECHO_PICKAXE)),
                "Resonance must not apply to a pickaxe");
        for (var armor : PactEquipment.ARMOR) {
            h.assertTrue(guard.value().canEnchant(new ItemStack(armor)), "Guard must apply to every new armor piece");
        }
        for (var tool : PactEquipment.TOOLS) {
            h.assertTrue(mining.value().canEnchant(new ItemStack(tool)), "Echo Mining must apply to every new tool");
        }
        h.assertTrue(resonance.value().getMaxLevel() == 3 && guard.value().getMaxLevel() == 4
                && mining.value().getMaxLevel() == 3, "Enchantment levels must match the documented limits");
        h.succeed();
    }

    @GameTest(structure = "wardenpacttests:arena", maxTicks = 240)
    public void resonanceEffect(GameTestHelper h) {
        ServerPlayer owner = player(h);
        ItemStack sword = new ItemStack(WardenPact.SUMMONING_SWORD);
        sword.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(PactEnchantments.CONTRACT_RESONANCE), 3);
        owner.setItemSlot(EquipmentSlot.MAINHAND, sword);
        int oldCooldown = PactConfig.COOLDOWN_SECONDS.get();
        int oldLifetime = PactConfig.LIFETIME_SECONDS.get();
        FriendlyWarden warden;
        try {
            PactConfig.COOLDOWN_SECONDS.set(20);
            PactConfig.LIFETIME_SECONDS.set(5);
            h.assertTrue(PactEnchantments.summonLifetimeSeconds(owner, sword) == 50,
                    "Resonance III must add 45 seconds to the configured summon lifetime");
            warden = summon(h, owner);
        } finally {
            PactConfig.COOLDOWN_SECONDS.set(oldCooldown);
            PactConfig.LIFETIME_SECONDS.set(oldLifetime);
        }
        h.runAfterDelay(110, () -> {
            h.assertTrue(warden.isAlive() && !warden.isRemoved(),
                    "The enchanted summon must actually outlive the configured five-second base lifetime");
            warden.discard();
            hit(h, owner, zombie(h, 10, 9));
            h.assertTrue(guardians(h, owner).isEmpty(), "Resonance must still enforce a cooldown");
        });
        h.runAfterDelay(221, () -> {
            hit(h, owner, zombie(h, 11, 9));
            h.assertTrue(guardians(h, owner).size() == 1, "Resonance III must allow a new summon after 11 seconds");
            guardians(h, owner).forEach(FriendlyWarden::discard);
            owner.discard();
            h.succeed();
        });
    }

    private static float damageTaken(GameTestHelper h, ServerPlayer player, DamageSource source) {
        player.setHealth(player.getMaxHealth());
        player.damageCooldownTime = 0;
        float before = player.getHealth();
        player.hurtServer(h.getLevel(), source, 10);
        return before - player.getHealth();
    }

    @GameTest(structure = "wardenpacttests:arena", maxTicks = 240)
    public void sculkGuard(GameTestHelper h) {
        ServerPlayer owner = player(h);
        for (var item : PactEquipment.ARMOR) {
            ItemStack armor = new ItemStack(item);
            owner.setItemSlot(armor.get(DataComponents.EQUIPPABLE).slot(), armor);
        }
        Zombie attacker = zombie(h, 13, 10);
        h.runAfterDelay(2, () -> {
            owner.doTick();
            DamageSource hostile = h.getLevel().damageSources().mobAttack(attacker);
            DamageSource neutral = h.getLevel().damageSources().generic();
            float plainHostile = damageTaken(h, owner, hostile);
            float plainNeutral = damageTaken(h, owner, neutral);
            var guard = h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(PactEnchantments.SCULK_GUARD);
            for (var slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
                owner.getItemBySlot(slot).enchant(guard, 4);
            }
            float guarded = damageTaken(h, owner, hostile);
            float neutralWithGuard = damageTaken(h, owner, neutral);
            h.assertTrue(plainHostile > 0 && guarded > 0 && guarded < plainHostile,
                    "Guard must reduce actual hostile damage without granting invulnerability; plain="
                    + plainHostile + ", guarded=" + guarded + ", neutral=" + plainNeutral
                    + ", neutral guarded=" + neutralWithGuard);
            h.assertTrue(Math.abs(plainNeutral - neutralWithGuard) < 0.001,
                    "Guard must not reduce non-hostile generic damage");
            owner.discard();
            h.succeed();
        });
    }

    @GameTest(structure = "wardenpacttests:arena", maxTicks = 240)
    public void echoMining(GameTestHelper h) {
        ServerPlayer owner = player(h);
        ItemStack pickaxe = new ItemStack(PactEquipment.ECHO_PICKAXE);
        pickaxe.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(PactEnchantments.ECHO_MINING), 3);
        owner.setItemSlot(EquipmentSlot.MAINHAND, pickaxe);
        h.runAfterDelay(3, () -> {
            owner.doTick();
            h.assertTrue(Math.abs(owner.getAttributeValue(Attributes.BLOCK_BREAK_SPEED) - 1.6) < 0.001,
                    "Echo Mining III must actually increase block break speed by 60 percent; actual="
                    + owner.getAttributeValue(Attributes.BLOCK_BREAK_SPEED));
            owner.setItemSlot(EquipmentSlot.OFFHAND, pickaxe.copy());
            owner.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(PactEquipment.ECHO_PICKAXE));
            h.runAfterDelay(3, () -> {
                owner.doTick();
                h.assertTrue(Math.abs(owner.getAttributeValue(Attributes.BLOCK_BREAK_SPEED) - 1.0) < 0.001,
                        "Echo Mining must stop applying when the enchanted tool leaves the main hand");
                owner.discard();
                h.succeed();
            });
        });
    }

    @GameTest(structure = "wardenpacttests:arena", maxTicks = 240)
    public void smithingPreservesComponents(GameTestHelper h) {
        Item[] bases = {Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS,
                Items.NETHERITE_BOOTS, Items.NETHERITE_PICKAXE, Items.NETHERITE_AXE, Items.NETHERITE_SHOVEL,
                Items.NETHERITE_HOE, Items.NETHERITE_SWORD};
        Item[] results = {PactEquipment.ECHO_HELMET, PactEquipment.ECHO_CHESTPLATE,
                PactEquipment.ECHO_LEGGINGS, PactEquipment.ECHO_BOOTS, PactEquipment.ECHO_PICKAXE,
                PactEquipment.ECHO_AXE, PactEquipment.ECHO_SHOVEL, PactEquipment.ECHO_HOE,
                WardenPact.SUMMONING_SWORD};
        var unbreaking = h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING);
        for (int i = 0; i < bases.length; i++) {
            ItemStack base = new ItemStack(bases[i]);
            base.set(DataComponents.CUSTOM_NAME, Component.literal("Retained Gear"));
            base.setDamageValue(7);
            base.enchant(unbreaking, 3);
            var input = new SmithingRecipeInput(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                    base, new ItemStack(Items.ECHO_SHARD));
            var recipe = h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.SMITHING, input, h.getLevel());
            h.assertTrue(recipe.isPresent(), "Every equipment item must have a loaded smithing recipe");
            ItemStack output = recipe.orElseThrow().value().assemble(input);
            h.assertTrue(output.is(results[i]), "Smithing must produce the corresponding pact equipment");
            h.assertTrue(output.getDamageValue() == 7
                    && Component.literal("Retained Gear").equals(output.get(DataComponents.CUSTOM_NAME))
                    && EnchantmentHelper.getItemEnchantmentLevel(unbreaking, output) == 3,
                    "Smithing upgrades must preserve damage, names and enchantments");
        }
        h.succeed();
    }
}
