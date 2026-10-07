package com.sfmaddon.tabassist.network;

import com.sfmaddon.tabassist.SFMTabAssist;
import com.sfmaddon.tabassist.context.CableContextData;
import com.sfmaddon.tabassist.context.ClientCableContextCache;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncCableContextPayload(CableContextData contextData) implements CustomPacketPayload {
    public static final Type<SyncCableContextPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SFMTabAssist.MOD_ID, "sync_cable_context")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncCableContextPayload> STREAM_CODEC = StreamCodec.ofMember(
            (payload, buf) -> payload.contextData().write(buf),
            buf -> new SyncCableContextPayload(CableContextData.read(buf))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncCableContextPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientCableContextCache.setContext(payload.contextData());
        });
    }
}
