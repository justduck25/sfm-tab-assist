package com.sfmaddon.tabassist.network;

import com.sfmaddon.tabassist.SFMTabAssist;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class SFMTabAssistPackets {
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(SFMTabAssist.MOD_ID)
                .versioned("1.0.0");

        registrar.playToServer(
                RequestCableContextPayload.TYPE,
                RequestCableContextPayload.STREAM_CODEC,
                RequestCableContextPayload::handle
        );

        registrar.playToClient(
                SyncCableContextPayload.TYPE,
                SyncCableContextPayload.STREAM_CODEC,
                SyncCableContextPayload::handle
        );
    }

    public static void requestContext(BlockPos managerPos) {
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new RequestCableContextPayload(managerPos));
    }
}
