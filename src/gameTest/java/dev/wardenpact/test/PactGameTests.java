package dev.wardenpact.test;

import dev.wardenpact.FriendlyWarden;
import dev.wardenpact.WardenPact;
import java.util.List;
import java.util.function.Consumer;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Loaded only by runGameTestServer, never included in the distributed mod jar. */
@Mod(PactGameTests.ID)
public final class PactGameTests {
    public static final String ID = "wardenpacttests";
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, ID);

    public PactGameTests(IEventBus bus) {
        FUNCTIONS.register("summons_and_redirects", () -> PactGameTests::summonsAndRedirects);
        FUNCTIONS.register("protects_players_and_pets", () -> PactGameTests::protectsPlayersAndPets);
        FUNCTIONS.register("expires", () -> PactGameTests::expires);
        FUNCTIONS.register("attacks_monsters", () -> PactGameTests::attacksMonsters);
        FUNCTIONS.register("follows_owner", () -> PactGameTests::followsOwner);
        FUNCTIONS.register("checks_spawn_space", () -> PactGameTests::checksSpawnSpace);
        FUNCTIONS.register("ignores_friendly_targets", () -> PactGameTests::ignoresFriendlyTargets);
        FUNCTIONS.register(bus);
        bus.addListener(PactGameTests::register);
    }

    private static void register(RegisterGameTestsEvent event) {
        for (String name : List.of("summons_and_redirects", "protects_players_and_pets", "expires",
                "attacks_monsters", "follows_owner", "checks_spawn_space", "ignores_friendly_targets")) {
            Identifier id = Identifier.fromNamespaceAndPath(ID, name);
            var environment = event.registerEnvironment(id, new TestEnvironmentDefinition.AllOf());
            var data = new TestData<>(environment, Identifier.fromNamespaceAndPath(ID, "arena"), 240, 0, true);
            event.registerTest(id, new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, id), data));
        }
    }

    private static ServerPlayer player(GameTestHelper h) {
        h.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
        ServerPlayer player = (ServerPlayer) h.makeMockServerPlayer(GameType.SURVIVAL);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false));
        player.setGameMode(GameType.SURVIVAL);
        player.connection.markClientLoaded();
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
        target.invulnerableTime = 0;
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

    private static void summonsAndRedirects(GameTestHelper h) {
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

    private static void protectsPlayersAndPets(GameTestHelper h) {
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
        owner.invulnerableTime = 0;
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

    private static void expires(GameTestHelper h) {
        ServerPlayer owner = player(h);
        FriendlyWarden warden = summon(h, owner);
        warden.bind(owner, 4);
        h.runAfterDelay(8, () -> {
            h.assertTrue(warden.isRemoved(), "A guardian must disappear when its lifetime ends");
            h.assertFalse(WardenPact.FRIENDLY_WARDEN.get().canSerialize(), "Temporary summons must not persist across reloads");
            owner.discard();
            h.succeed();
        });
    }

    private static void attacksMonsters(GameTestHelper h) {
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

    private static void followsOwner(GameTestHelper h) {
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

    private static void checksSpawnSpace(GameTestHelper h) {
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
        hit(h, owner, target);
        h.assertTrue(guardians(h, owner).size() == 1, "A failed summon must not consume the cooldown");
        guardians(h, owner).forEach(FriendlyWarden::discard);
        owner.discard();
        h.succeed();
    }

    private static void ignoresFriendlyTargets(GameTestHelper h) {
        ServerPlayer owner = player(h);
        hit(h, owner, h.spawn(EntityTypes.COW, new BlockPos(10, 1, 8)));
        h.assertTrue(guardians(h, owner).isEmpty(), "Hitting a passive animal must not summon a warden");
        owner.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STICK));
        hit(h, owner, zombie(h, 11, 8));
        h.assertTrue(guardians(h, owner).isEmpty(), "Non-sword damage must not summon a warden");
        owner.discard();
        h.succeed();
    }
}
