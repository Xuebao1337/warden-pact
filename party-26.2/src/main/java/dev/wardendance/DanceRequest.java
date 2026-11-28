package dev.wardendance;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A toggle only: the client cannot supply entities, coordinates or a count. */
public record DanceRequest() implements CustomPacketPayload {
    public static final DanceRequest INSTANCE = new DanceRequest();
    public static final Type<DanceRequest> TYPE = new Type<>(Identifier.fromNamespaceAndPath(WardenDance.ID, "dance"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DanceRequest> CODEC = StreamCodec.unit(INSTANCE);
    @Override public Type<DanceRequest> type() { return TYPE; }
    public static void handle(DanceRequest request, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) WardenDance.PARTIES.toggle(player);
    }
}
