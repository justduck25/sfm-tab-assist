package com.sfmaddon.tabassist.context;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Detailed information about a block connected to the SFM cable network.
 */
public record ConnectedBlockInfo(
        BlockPos pos,
        String blockId,
        String blockDisplayName,
        List<String> labels,
        List<String> sampleItemIds,
        List<String> sampleFluidIds,
        List<String> supportedSides,
        boolean isMachine,
        int totalSlots,
        boolean hasEnergy,
        boolean hasFluid,
        List<Integer> inputSlots,
        List<Integer> outputSlots,
        List<String> inputSides,
        List<String> outputSides
) {
    public ConnectedBlockInfo(
            BlockPos pos,
            String blockId,
            String blockDisplayName,
            List<String> labels,
            List<String> sampleItemIds,
            List<String> sampleFluidIds,
            List<String> supportedSides,
            boolean isMachine,
            int totalSlots,
            boolean hasEnergy,
            boolean hasFluid
    ) {
        this(pos, blockId, blockDisplayName, labels, sampleItemIds, sampleFluidIds, supportedSides, isMachine, totalSlots, hasEnergy, hasFluid, List.of(), List.of(), List.of(), List.of());
    }

    public ConnectedBlockInfo(
            BlockPos pos,
            String blockId,
            String blockDisplayName,
            List<String> labels,
            List<String> sampleItemIds,
            List<String> sampleFluidIds,
            List<String> supportedSides,
            boolean isMachine
    ) {
        this(pos, blockId, blockDisplayName, labels, sampleItemIds, sampleFluidIds, supportedSides, isMachine, 0, false, !sampleFluidIds.isEmpty(), List.of(), List.of(), List.of(), List.of());
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeUtf(blockId);
        buf.writeUtf(blockDisplayName);
        
        buf.writeVarInt(labels.size());
        for (String label : labels) buf.writeUtf(label);

        buf.writeVarInt(sampleItemIds.size());
        for (String item : sampleItemIds) buf.writeUtf(item);

        buf.writeVarInt(sampleFluidIds.size());
        for (String fluid : sampleFluidIds) buf.writeUtf(fluid);

        buf.writeVarInt(supportedSides.size());
        for (String side : supportedSides) buf.writeUtf(side);

        buf.writeBoolean(isMachine);
        buf.writeVarInt(totalSlots);
        buf.writeBoolean(hasEnergy);
        buf.writeBoolean(hasFluid);

        buf.writeVarInt(inputSlots.size());
        for (int s : inputSlots) buf.writeVarInt(s);

        buf.writeVarInt(outputSlots.size());
        for (int s : outputSlots) buf.writeVarInt(s);

        buf.writeVarInt(inputSides.size());
        for (String side : inputSides) buf.writeUtf(side);

        buf.writeVarInt(outputSides.size());
        for (String side : outputSides) buf.writeUtf(side);
    }

    public static ConnectedBlockInfo read(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String blockId = buf.readUtf();
        String blockDisplayName = buf.readUtf();

        int labelCount = buf.readVarInt();
        List<String> labels = new ArrayList<>(labelCount);
        for (int i = 0; i < labelCount; i++) labels.add(buf.readUtf());

        int itemCount = buf.readVarInt();
        List<String> items = new ArrayList<>(itemCount);
        for (int i = 0; i < itemCount; i++) items.add(buf.readUtf());

        int fluidCount = buf.readVarInt();
        List<String> fluids = new ArrayList<>(fluidCount);
        for (int i = 0; i < fluidCount; i++) fluids.add(buf.readUtf());

        int sideCount = buf.readVarInt();
        List<String> sides = new ArrayList<>(sideCount);
        for (int i = 0; i < sideCount; i++) sides.add(buf.readUtf());

        boolean isMachine = buf.readBoolean();
        int totalSlots = buf.readVarInt();
        boolean hasEnergy = buf.readBoolean();
        boolean hasFluid = buf.readBoolean();

        int inSlotCount = buf.readVarInt();
        List<Integer> inputSlots = new ArrayList<>(inSlotCount);
        for (int i = 0; i < inSlotCount; i++) inputSlots.add(buf.readVarInt());

        int outSlotCount = buf.readVarInt();
        List<Integer> outputSlots = new ArrayList<>(outSlotCount);
        for (int i = 0; i < outSlotCount; i++) outputSlots.add(buf.readVarInt());

        int inSideCount = buf.readVarInt();
        List<String> inputSides = new ArrayList<>(inSideCount);
        for (int i = 0; i < inSideCount; i++) inputSides.add(buf.readUtf());

        int outSideCount = buf.readVarInt();
        List<String> outputSides = new ArrayList<>(outSideCount);
        for (int i = 0; i < outSideCount; i++) outputSides.add(buf.readUtf());

        return new ConnectedBlockInfo(pos, blockId, blockDisplayName, labels, items, fluids, sides, isMachine, totalSlots, hasEnergy, hasFluid, inputSlots, outputSlots, inputSides, outputSides);
    }

    /**
     * Gets preferred label: first custom label if assigned, or block path ID (e.g. "chest", "furnace").
     */
    public String getPreferredLabel() {
        if (!labels.isEmpty()) {
            return labels.get(0);
        }
        int colon = blockId.indexOf(':');
        return colon != -1 ? blockId.substring(colon + 1) : blockId;
    }
}
