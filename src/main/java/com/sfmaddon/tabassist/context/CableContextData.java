package com.sfmaddon.tabassist.context;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.*;

/**
 * Context snapshot of a cable network connected to a Manager.
 */
public record CableContextData(
        BlockPos managerPos,
        List<ConnectedBlockInfo> connectedBlocks
) {
    public static final StreamCodec<RegistryFriendlyByteBuf, CableContextData> STREAM_CODEC = StreamCodec.ofMember(
            CableContextData::write,
            CableContextData::read
    );

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(managerPos);
        buf.writeVarInt(connectedBlocks.size());
        for (ConnectedBlockInfo block : connectedBlocks) {
            block.write(buf);
        }
    }

    public static CableContextData read(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int size = buf.readVarInt();
        List<ConnectedBlockInfo> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(ConnectedBlockInfo.read(buf));
        }
        return new CableContextData(pos, list);
    }

    /**
     * Retrieves all item IDs currently stored across connected inventories.
     */
    public Set<String> getAllItemIds() {
        Set<String> items = new LinkedHashSet<>();
        for (ConnectedBlockInfo b : connectedBlocks) {
            items.addAll(b.sampleItemIds());
        }
        return items;
    }

    /**
     * Retrieves all fluid IDs currently stored across connected tanks.
     */
    public Set<String> getAllFluidIds() {
        Set<String> fluids = new LinkedHashSet<>();
        for (ConnectedBlockInfo b : connectedBlocks) {
            fluids.addAll(b.sampleFluidIds());
        }
        return fluids;
    }

    /**
     * Retrieves all user-assigned labels in the network.
     */
    public Set<String> getAllLabels() {
        Set<String> labels = new LinkedHashSet<>();
        for (ConnectedBlockInfo b : connectedBlocks) {
            labels.addAll(b.labels());
        }
        return labels;
    }

    /**
     * Retrieves all candidate labels (including synthetic labels from block names if unlabelled).
     */
    public Set<String> getAllCandidateLabels() {
        Set<String> labels = new LinkedHashSet<>();
        for (ConnectedBlockInfo b : connectedBlocks) {
            labels.add(b.getPreferredLabel());
        }
        return labels;
    }

    /**
     * Finds blocks matching a given label.
     */
    public List<ConnectedBlockInfo> findBlocksByLabel(String label) {
        List<ConnectedBlockInfo> result = new ArrayList<>();
        for (ConnectedBlockInfo b : connectedBlocks) {
            if (b.labels().contains(label)) {
                result.add(b);
            }
        }
        return result;
    }
}
