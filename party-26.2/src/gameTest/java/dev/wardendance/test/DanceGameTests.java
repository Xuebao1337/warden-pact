package dev.wardendance.test;

import dev.wardendance.DanceParty;
import dev.wardendance.DanceRequest;
import dev.wardendance.DancingWarden;
import dev.wardendance.WardenDance;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(DanceGameTests.ID)
public final class DanceGameTests {
    public static final String ID = "wardendancetests";
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, ID);
    private static final List<String> NAMES = List.of("toggle_and_cooldown", "expires", "harmless", "no_room",
            "owner_logout", "owner_death", "owner_distance", "packet_request", "spectator", "independent_groups", "peaceful");

    public DanceGameTests(IEventBus bus) {
        FUNCTIONS.register("toggle_and_cooldown", () -> DanceGameTests::toggleAndCooldown);
        FUNCTIONS.register("expires", () -> DanceGameTests::expires);
        FUNCTIONS.register("harmless", () -> DanceGameTests::harmless);
        FUNCTIONS.register("no_room", () -> DanceGameTests::noRoom);
        FUNCTIONS.register("owner_logout", () -> DanceGameTests::ownerLogout);
        FUNCTIONS.register("owner_death", () -> DanceGameTests::ownerDeath);
        FUNCTIONS.register("owner_distance", () -> DanceGameTests::ownerDistance);
        FUNCTIONS.register("packet_request", () -> DanceGameTests::packetRequest);
        FUNCTIONS.register("spectator", () -> DanceGameTests::spectator);
        FUNCTIONS.register("independent_groups", () -> DanceGameTests::independentGroups);
        FUNCTIONS.register("peaceful", () -> DanceGameTests::peaceful);
        FUNCTIONS.register(bus);
        bus.addListener(DanceGameTests::register);
    }
    private static void register(RegisterGameTestsEvent event) {
        for (String name : NAMES) {
            Identifier id = Identifier.fromNamespaceAndPath(ID, name);
            var environment = event.registerEnvironment(id, new TestEnvironmentDefinition.AllOf());
            var data = new TestData<>(environment, Identifier.fromNamespaceAndPath(ID, "arena"), 660, 0, true);
            event.registerTest(id, new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, id), data));
        }
    }
    private static ServerPlayer player(GameTestHelper h) {
        return player(h, GameType.SURVIVAL);
    }
    private static ServerPlayer player(GameTestHelper h, GameType mode) {
        ServerPlayer player = (ServerPlayer) h.makeMockServerPlayer(mode);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false));
        player.setGameMode(mode);
        player.connection.markClientLoaded();
        BlockPos pos = h.absolutePos(new BlockPos(8, 1, 8));
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return player;
    }
    private static List<DancingWarden> crew(GameTestHelper h, ServerPlayer player) {
        return h.getLevel().getEntitiesOfClass(DancingWarden.class, h.getBounds().inflate(5),
                d -> player.getUUID().equals(d.ownerId()) && !d.isRemoved());
    }
    private static List<DancingWarden> start(GameTestHelper h, ServerPlayer player) {
        h.assertTrue(WardenDance.PARTIES.toggle(player) == DanceParty.Result.STARTED, "Party should start on clear ground");
        var dancers = crew(h, player);
        h.assertTrue(dancers.size() == 5, "Exactly five dancers per player");
        return dancers;
    }
    private static void finish(ServerPlayer player) {
        WardenDance.PARTIES.logout(new PlayerEvent.PlayerLoggedOutEvent(player));
        player.discard();
    }
    private static void toggleAndCooldown(GameTestHelper h) {
        ServerPlayer player = player(h);
        var dancers = start(h, player);
        h.assertTrue(WardenDance.PARTIES.toggle(player) == DanceParty.Result.DISMISSED, "Second key dismisses instead of duplicating");
        h.assertTrue(dancers.stream().allMatch(d -> d.isRemoved()), "Dismiss all five together");
        h.assertTrue(WardenDance.PARTIES.toggle(player) == DanceParty.Result.COOLDOWN, "Rapid requests cannot respawn crew");
        h.runAfterDelay(105, () -> { start(h, player); finish(player); h.succeed(); });
    }
    private static void expires(GameTestHelper h) {
        ServerPlayer player = player(h);
        var dancers = start(h, player);
        h.runAfterDelay(590, () -> h.assertTrue(dancers.stream().allMatch(d -> !d.isRemoved()), "Crew lasts for 30 seconds"));
        h.runAfterDelay(605, () -> { h.assertTrue(dancers.stream().allMatch(d -> d.isRemoved()), "Crew expires automatically"); finish(player); h.succeed(); });
    }
    private static void harmless(GameTestHelper h) {
        ServerPlayer player = player(h);
        var dancers = start(h, player);
        var sheep = h.spawn(EntityTypes.SHEEP, new BlockPos(8, 1, 10));
        float health = sheep.getHealth();
        for (var dancer : dancers) {
            h.assertFalse(dancer.canTargetEntity(player), "Never target player");
            h.assertFalse(dancer.canTargetEntity(sheep), "Never target animals");
            h.assertFalse(dancer.doHurtTarget(h.getLevel(), sheep), "No melee damage");
            h.assertFalse(dancer.hurtServer(h.getLevel(), h.getLevel().damageSources().playerAttack(player), 1000), "No farming dancers for drops");
            h.assertFalse(dancer.isPushable(), "Crew must not be pushed off stage");
            h.assertFalse(dancer.isPreventingPlayerRest(h.getLevel(), player), "Party does not prevent sleep");
            h.assertTrue(dancer.isNoAi() && dancer.isNoGravity(), "No vanilla warden brain or falling");
        }
        h.assertFalse(WardenDance.DANCER.get().canSerialize(), "Dancers never enter world save");
        h.assertTrue(WardenDance.DANCER.get().getDefaultLootTable().isEmpty(), "No loot table");
        h.runAfterDelay(145, () -> {
            h.assertFalse(player.hasEffect(MobEffects.DARKNESS), "No darkness aura");
            h.assertTrue(sheep.getHealth() == health, "No damage over time");
            h.assertTrue(dancers.stream().allMatch(d -> d.getTarget() == null), "No attack target");
            finish(player); h.succeed();
        });
    }
    private static void noRoom(GameTestHelper h) {
        ServerPlayer player = player(h);
        for (int x = 2; x <= 14; x++) for (int z = 2; z <= 14; z++)
            for (int y = 2; y <= 5; y++) h.setBlock(x, y, z, Blocks.GLASS);
        h.assertTrue(WardenDance.PARTIES.toggle(player) == DanceParty.Result.NO_ROOM, "Blocked headroom rejects entire group");
        h.assertTrue(crew(h, player).isEmpty(), "Failed spawn leaves no partial dancers");
        for (int x = 2; x <= 14; x++) for (int z = 2; z <= 14; z++)
            for (int y = 2; y <= 5; y++) h.setBlock(x, y, z, Blocks.AIR);
        start(h, player);
        finish(player); h.succeed();
    }
    private static void ownerLogout(GameTestHelper h) {
        ServerPlayer player = player(h);
        var dancers = start(h, player);
        finish(player);
        h.assertTrue(dancers.stream().allMatch(d -> d.isRemoved()), "Logout clears crew immediately");
        h.succeed();
    }
    private static void ownerDeath(GameTestHelper h) {
        ServerPlayer player = player(h);
        var dancers = start(h, player);
        player.setHealth(0);
        h.runAfterDelay(3, () -> { h.assertTrue(dancers.stream().allMatch(d -> d.isRemoved()), "Death clears crew"); finish(player); h.succeed(); });
    }
    private static void ownerDistance(GameTestHelper h) {
        ServerPlayer player = player(h);
        var dancers = start(h, player);
        player.setPos(player.position().add(75, 0, 0));
        h.runAfterDelay(3, () -> { h.assertTrue(dancers.stream().allMatch(d -> d.isRemoved()), "Leaving the stage clears crew"); finish(player); h.succeed(); });
    }
    private static void packetRequest(GameTestHelper h) {
        ServerPlayer player = player(h);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        DanceRequest.CODEC.encode(buffer, DanceRequest.INSTANCE);
        h.assertTrue(buffer.readableBytes() == 0, "Client payload contains no coordinates or entity counts");
        DanceRequest request = DanceRequest.CODEC.decode(buffer);
        buffer.release();
        IPayloadContext context = (IPayloadContext) Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(),
                new Class<?>[]{IPayloadContext.class}, (proxy, method, args) -> {
                    if (method.getName().equals("player")) return player;
                    throw new UnsupportedOperationException(method.getName());
                });
        DanceRequest.handle(request, context);
        h.assertTrue(crew(h, player).size() == 5, "Decoded request invokes the actual server handler");
        DanceRequest.handle(request, context);
        h.assertTrue(crew(h, player).isEmpty(), "Network request also dismisses group");
        finish(player); h.succeed();
    }
    private static void spectator(GameTestHelper h) {
        ServerPlayer player = player(h, GameType.SPECTATOR);
        h.assertTrue(WardenDance.PARTIES.toggle(player) == DanceParty.Result.UNAVAILABLE, "Spectators cannot summon");
        h.assertTrue(crew(h, player).isEmpty(), "No spectator crew");
        finish(player); h.succeed();
    }
    private static void independentGroups(GameTestHelper h) {
        ServerPlayer first = player(h), second = player(h);
        second.setYRot(18);
        var one = start(h, first);
        var two = start(h, second);
        WardenDance.PARTIES.toggle(first);
        h.assertTrue(one.stream().allMatch(d -> d.isRemoved()), "First owner dismisses own crew");
        h.assertTrue(two.stream().allMatch(d -> !d.isRemoved()), "Other owner's crew is preserved");
        finish(first); finish(second); h.succeed();
    }
    private static void peaceful(GameTestHelper h) {
        ServerPlayer player = player(h);
        var dancers = start(h, player);
        h.getLevel().getServer().setDifficulty(Difficulty.PEACEFUL, true);
        h.runAfterDelay(25, () -> {
            h.assertTrue(dancers.stream().allMatch(d -> !d.isRemoved()), "Dance party works in peaceful mode");
            h.assertTrue(dancers.stream().anyMatch(d -> d.getY() > d.stage().y() + 0.05), "Dancers actually hop");
            finish(player); h.succeed();
        });
    }
}
