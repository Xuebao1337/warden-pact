package dev.wardendance.test;

import dev.wardendance.DanceParty;
import dev.wardendance.DanceRequest;
import dev.wardendance.DancingWarden;
import dev.wardendance.WardenDance;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
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

public final class DanceGameTests {
    public static final String ID = "wardendancetests";
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
        player.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
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
        WardenDance.PARTIES.logout(player);
        player.discard();
    }
    @net.fabricmc.fabric.api.gametest.v1.GameTest(structure = "wardendancetests:arena", maxTicks = 660)
    public void toggleAndCooldown(GameTestHelper h) {
        ServerPlayer player = player(h);
        var dancers = start(h, player);
        h.assertTrue(WardenDance.PARTIES.toggle(player) == DanceParty.Result.DISMISSED, "Second key dismisses instead of duplicating");
        h.assertTrue(dancers.stream().allMatch(d -> d.isRemoved()), "Dismiss all five together");
        h.assertTrue(WardenDance.PARTIES.toggle(player) == DanceParty.Result.COOLDOWN, "Rapid requests cannot respawn crew");
        h.runAfterDelay(105, () -> { start(h, player); finish(player); h.succeed(); });
    }
    @net.fabricmc.fabric.api.gametest.v1.GameTest(structure = "wardendancetests:arena", maxTicks = 660)
    public void expires(GameTestHelper h) {
        ServerPlayer player = player(h);
        var dancers = start(h, player);
        h.runAfterDelay(590, () -> h.assertTrue(dancers.stream().allMatch(d -> !d.isRemoved()), "Crew lasts for 30 seconds"));
        h.runAfterDelay(605, () -> { h.assertTrue(dancers.stream().allMatch(d -> d.isRemoved()), "Crew expires automatically"); finish(player); h.succeed(); });
    }
    @net.fabricmc.fabric.api.gametest.v1.GameTest(structure = "wardendancetests:arena", maxTicks = 660)
    public void harmless(GameTestHelper h) {
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
        h.assertFalse(WardenDance.DANCER.canSerialize(), "Dancers never enter world save");
        h.assertTrue(WardenDance.DANCER.getDefaultLootTable().isEmpty(), "No loot table");
        h.runAfterDelay(145, () -> {
            h.assertFalse(player.hasEffect(MobEffects.DARKNESS), "No darkness aura");
            h.assertTrue(sheep.getHealth() == health, "No damage over time");
            h.assertTrue(dancers.stream().allMatch(d -> d.getTarget() == null), "No attack target");
            finish(player); h.succeed();
        });
    }
    @net.fabricmc.fabric.api.gametest.v1.GameTest(structure = "wardendancetests:arena", maxTicks = 660)
    public void noRoom(GameTestHelper h) {
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
    @net.fabricmc.fabric.api.gametest.v1.GameTest(structure = "wardendancetests:arena", maxTicks = 660)
    public void ownerLogout(GameTestHelper h) {
        ServerPlayer player = player(h);
        var dancers = start(h, player);
        finish(player);
        h.assertTrue(dancers.stream().allMatch(d -> d.isRemoved()), "Logout clears crew immediately");
        h.succeed();
    }
    @net.fabricmc.fabric.api.gametest.v1.GameTest(structure = "wardendancetests:arena", maxTicks = 660)
    public void ownerDeath(GameTestHelper h) {
        ServerPlayer player = player(h);
        var dancers = start(h, player);
        player.setHealth(0);
        h.runAfterDelay(3, () -> { h.assertTrue(dancers.stream().allMatch(d -> d.isRemoved()), "Death clears crew"); finish(player); h.succeed(); });
    }
    @net.fabricmc.fabric.api.gametest.v1.GameTest(structure = "wardendancetests:arena", maxTicks = 660)
    public void ownerDistance(GameTestHelper h) {
        ServerPlayer player = player(h);
        var dancers = start(h, player);
        player.setPos(player.position().add(75, 0, 0));
        h.runAfterDelay(3, () -> { h.assertTrue(dancers.stream().allMatch(d -> d.isRemoved()), "Leaving the stage clears crew"); finish(player); h.succeed(); });
    }
    @net.fabricmc.fabric.api.gametest.v1.GameTest(structure = "wardendancetests:arena", maxTicks = 660)
    public void packetRequest(GameTestHelper h) {
        ServerPlayer player = player(h);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        DanceRequest.CODEC.encode(buffer, DanceRequest.INSTANCE);
        h.assertTrue(buffer.readableBytes() == 0, "Client payload contains no coordinates or entity counts");
        DanceRequest request = DanceRequest.CODEC.decode(buffer);
        buffer.release();
        DanceRequest.handle(player);
        h.assertTrue(crew(h, player).size() == 5, "Decoded request invokes the actual server handler");
        DanceRequest.handle(player);
        h.assertTrue(crew(h, player).isEmpty(), "Network request also dismisses group");
        finish(player); h.succeed();
    }
    @net.fabricmc.fabric.api.gametest.v1.GameTest(structure = "wardendancetests:arena", maxTicks = 660)
    public void spectator(GameTestHelper h) {
        ServerPlayer player = player(h, GameType.SPECTATOR);
        h.assertTrue(WardenDance.PARTIES.toggle(player) == DanceParty.Result.UNAVAILABLE, "Spectators cannot summon");
        h.assertTrue(crew(h, player).isEmpty(), "No spectator crew");
        finish(player); h.succeed();
    }
    @net.fabricmc.fabric.api.gametest.v1.GameTest(structure = "wardendancetests:arena", maxTicks = 660)
    public void independentGroups(GameTestHelper h) {
        ServerPlayer first = player(h), second = player(h);
        second.setYRot(18);
        var one = start(h, first);
        var two = start(h, second);
        WardenDance.PARTIES.toggle(first);
        h.assertTrue(one.stream().allMatch(d -> d.isRemoved()), "First owner dismisses own crew");
        h.assertTrue(two.stream().allMatch(d -> !d.isRemoved()), "Other owner's crew is preserved");
        finish(first); finish(second); h.succeed();
    }
    @net.fabricmc.fabric.api.gametest.v1.GameTest(structure = "wardendancetests:arena", maxTicks = 660)
    public void peaceful(GameTestHelper h) {
        ServerPlayer player = player(h);
        var dancers = start(h, player);
        h.getLevel().getServer().setDifficulty(Difficulty.PEACEFUL, true);
        h.runAfterDelay(25, () -> {
            h.assertTrue(dancers.stream().allMatch(d -> !d.isRemoved()), "Dance party works in peaceful mode");
            h.assertTrue(dancers.stream().anyMatch(d -> d.getY() > d.stage().y() + 0.05), "Dancers actually hop");
            finish(player); h.succeed();
        });
    }

    @net.fabricmc.fabric.api.gametest.v1.GameTest(structure = "wardendancetests:arena", maxTicks = 120)
    public void dancersStayOnTheirStage(GameTestHelper h) {
        ServerPlayer player = player(h);
        var dancers = start(h, player);
        h.runAfterDelay(81, () -> {
            for (var dancer : dancers) {
                h.assertFalse(dancer.isRemoved(), "Unobstructed dancers stay for full performance");
                h.assertTrue(Math.abs(dancer.getX() - dancer.stage().x()) < 0.0001
                        && Math.abs(dancer.getZ() - dancer.stage().z()) < 0.0001, "Dancers never wander off stage");
                double hop = dancer.getY() - dancer.stage().y();
                h.assertTrue(hop >= -0.0001 && hop <= 0.2801, "Dance hopping stays in reserved headroom");
            }
            finish(player); h.succeed();
        });
    }

    @net.fabricmc.fabric.api.gametest.v1.GameTest(structure = "wardendancetests:arena", maxTicks = 40)
    public void newObstructionRemovesOnlyBlockedDancer(GameTestHelper h) {
        ServerPlayer player = player(h);
        var dancers = start(h, player);
        var blocked = dancers.getFirst();
        h.getLevel().setBlockAndUpdate(BlockPos.containing(blocked.stage()).above(), Blocks.GLASS.defaultBlockState());
        h.runAfterDelay(3, () -> {
            h.assertTrue(blocked.isRemoved(), "New stage obstruction safely removes the affected dancer");
            h.assertTrue(dancers.stream().skip(1).allMatch(d -> !d.isRemoved()), "Unblocked performers continue");
            finish(player); h.succeed();
        });
    }
}
