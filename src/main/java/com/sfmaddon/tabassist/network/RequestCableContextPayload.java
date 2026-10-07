package com.sfmaddon.tabassist.network;

import com.sfmaddon.tabassist.SFMTabAssist;
import com.sfmaddon.tabassist.context.CableContextData;
import com.sfmaddon.tabassist.context.CableContextScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;

public record RequestCableContextPayload(BlockPos managerPos) implements CustomPacketPayload {
    public static final Type<RequestCableContextPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SFMTabAssist.MOD_ID, "request_cable_context")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestCableContextPayload> STREAM_CODEC = StreamCodec.ofMember(
            RequestCableContextPayload::write,
            RequestCableContextPayload::read
    );

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(managerPos);
    }

    public static RequestCableContextPayload read(RegistryFriendlyByteBuf buf) {
        return new RequestCableContextPayload(buf.readBlockPos());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestCableContextPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel serverLevel) {
                Optional<CableContextData> data = CableContextScanner.scanNetwork(serverLevel, payload.managerPos(), player);
                data.ifPresent(contextData -> {
                    PacketDistributor.sendToPlayer(player, new SyncCableContextPayload(contextData));
                });
            }
        });
    }
}
