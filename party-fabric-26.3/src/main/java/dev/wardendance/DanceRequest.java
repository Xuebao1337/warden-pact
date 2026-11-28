package dev.wardendance;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** The client supplies no count, entity type or coordinates. Fabric dispatches on the server thread. */
public record DanceRequest() implements CustomPacketPayload {
    public static final DanceRequest INSTANCE = new DanceRequest();
    public static final Type<DanceRequest> TYPE = new Type<>(Identifier.fromNamespaceAndPath(WardenDance.ID, "dance"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DanceRequest> CODEC = StreamCodec.unit(INSTANCE);
    @Override public Type<DanceRequest> type() { return TYPE; }
    public static void handle(ServerPlayer player) { WardenDance.PARTIES.toggle(player); }
}
