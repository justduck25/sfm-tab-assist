package com.sfmaddon.tabassist.context;

import ca.teamdman.sfm.common.block_network.CableNetwork;
import ca.teamdman.sfm.common.block_network.CableNetworkManager;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.util.BlockPosSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class CableContextScanner {

    public static Optional<CableContextData> scanNetwork(
            ServerLevel level,
            @Nullable BlockPos managerPos,
            @Nullable ServerPlayer player
    ) {
        Optional<CableNetwork> optNet = Optional.empty();

        if (managerPos != null && !managerPos.equals(BlockPos.ZERO)) {
            optNet = CableNetworkManager.getOrRegisterNetworkFromCablePosition(level, managerPos);
            // If pos is a connected block (e.g. chest), check adjacent sides for cables
            if (optNet.isEmpty()) {
                for (Direction dir : Direction.values()) {
                    optNet = CableNetworkManager.getOrRegisterNetworkFromCablePosition(level, managerPos.relative(dir));
                    if (optNet.isPresent()) break;
                }
            }
        }

        // Fallback: If not found via managerPos, search cable networks within 16 blocks of player
        if (optNet.isEmpty() && player != null) {
            optNet = CableNetworkManager.getNetworksInRange(level, player.blockPosition(), 16).findFirst();
        }

        if (optNet.isEmpty()) {
            return Optional.empty();
        }

        CableNetwork network = optNet.get();
        BlockPos finalManagerPos = (managerPos != null && !managerPos.equals(BlockPos.ZERO))
                ? managerPos
                : network.getCablePositions().iterator().next().immutable();

        // 1. Collect labels from disks of all Managers in network
        Map<String, BlockPosSet> labelMap = new HashMap<>();
        for (BlockPos cPos : network.getCablePositions()) {
            if (level.getBlockEntity(cPos) instanceof ManagerBlockEntity manager) {
                LabelPositionHolder holder = LabelPositionHolder.from(manager.getDisk());
                if (holder != null && holder.labels() != null) {
                    labelMap.putAll(holder.labels());
                }
            }
        }

        // Fallback: Check labels from Disk held in player's hands
        if (labelMap.isEmpty() && player != null) {
            for (ItemStack held : List.of(player.getMainHandItem(), player.getOffhandItem())) {
                if (held.getItem() instanceof DiskItem) {
                    LabelPositionHolder holder = LabelPositionHolder.from(held);
                    if (holder != null && holder.labels() != null) {
                        labelMap.putAll(holder.labels());
                    }
                }
            }
        }

        Set<BlockPos> scannedPositions = new HashSet<>();
        List<ConnectedBlockInfo> connectedBlocks = new ArrayList<>();

        // 2. Iterate through all cables in the network
        for (BlockPos cablePos : network.getCablePositions()) {
            for (Direction dir : Direction.values()) {
                BlockPos neighborPos = cablePos.relative(dir);

                // Skip if it's another cable position or already scanned
                if (network.containsCablePosition(neighborPos) || !scannedPositions.add(neighborPos)) {
                    continue;
                }

                BlockState state = level.getBlockState(neighborPos);
                if (state.isAir()) continue;

                String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                String displayName = state.getBlock().getName().getString();

                // Find labels pointing to this block
                List<String> matchedLabels = new ArrayList<>();
                for (Map.Entry<String, BlockPosSet> entry : labelMap.entrySet()) {
                    if (entry.getValue().contains(neighborPos)) {
                        matchedLabels.add(entry.getKey());
                    }
                }

                // Scan capabilities and sample items
                Set<String> sampleItems = new LinkedHashSet<>();
                Set<String> sampleFluids = new LinkedHashSet<>();
                List<String> supportedSides = new ArrayList<>();

                BlockEntity be = level.getBlockEntity(neighborPos);

                int maxSlotsFound = 0;

                // Fallback: Read vanilla Container directly
                if (be instanceof Container container) {
                    supportedSides.add("ALL");
                    int size = container.getContainerSize();
                    maxSlotsFound = Math.max(maxSlotsFound, size);
                    int sampleLimit = Math.min(size, 108);
                    for (int slot = 0; slot < sampleLimit; slot++) {
                        ItemStack stack = container.getItem(slot);
                        if (!stack.isEmpty()) {
                            var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
                            String cleanName = "minecraft".equals(key.getNamespace()) ? key.getPath() : key.toString();
                            sampleItems.add(cleanName);
                        }
                    }
                }

                // Check directions and null face for Capabilities
                Direction[] sidesToCheck = new Direction[]{
                        dir.getOpposite(),
                        null,
                        Direction.DOWN,
                        Direction.UP,
                        Direction.NORTH,
                        Direction.SOUTH,
                        Direction.WEST,
                        Direction.EAST
                };

                boolean hasEnergy = false;
                boolean hasFluid = false;

                for (Direction side : sidesToCheck) {
                    String sideName = (side == null) ? "ALL" : side.name();
                    try {
                        ResourceHandler<ItemResource> itemRes = level.getCapability(Capabilities.Item.BLOCK, neighborPos, side);
                        if (itemRes != null) {
                            if (!supportedSides.contains(sideName)) {
                                supportedSides.add(sideName);
                            }
                            IItemHandler itemHandler = IItemHandler.of(itemRes);
                            int slots = itemHandler.getSlots();
                            maxSlotsFound = Math.max(maxSlotsFound, slots);
                            int slotsToSample = Math.min(slots, 108);
                            for (int slot = 0; slot < slotsToSample; slot++) {
                                ItemStack stack = itemHandler.getStackInSlot(slot);
                                if (!stack.isEmpty()) {
                                    var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
                                    String cleanName = "minecraft".equals(key.getNamespace()) ? key.getPath() : key.toString();
                                    sampleItems.add(cleanName);
                                }
                            }
                        }
                    } catch (Throwable ignored) {
                    }

                    try {
                        ResourceHandler<FluidResource> fluidRes = level.getCapability(Capabilities.Fluid.BLOCK, neighborPos, side);
                        if (fluidRes != null) {
                            hasFluid = true;
                            if (!supportedSides.contains(sideName)) {
                                supportedSides.add(sideName);
                            }
                            IFluidHandler fluidHandler = IFluidHandler.of(fluidRes);
                            for (int tank = 0; tank < fluidHandler.getTanks(); tank++) {
                                var fluidStack = fluidHandler.getFluidInTank(tank);
                                if (!fluidStack.isEmpty()) {
                                    sampleFluids.add(BuiltInRegistries.FLUID.getKey(fluidStack.getFluid()).toString());
                                }
                            }
                        }
                    } catch (Throwable ignored) {
                    }

                    try {
                        if (level.getCapability(Capabilities.Energy.BLOCK, neighborPos, side) != null) {
                            hasEnergy = true;
                        }
                    } catch (Throwable ignored) {
                    }
                }

                String lowerBlockId = blockId.toLowerCase(Locale.ROOT);
                boolean isMachine = state.getBlock() instanceof AbstractFurnaceBlock
                        || hasEnergy
                        || lowerBlockId.contains("furnace")
                        || lowerBlockId.contains("smelter")
                        || lowerBlockId.contains("machine")
                        || lowerBlockId.contains("crusher")
                        || lowerBlockId.contains("generator")
                        || lowerBlockId.contains("inscriber")
                        || lowerBlockId.contains("infuser")
                        || lowerBlockId.contains("chamber")
                        || lowerBlockId.contains("pulverizer")
                        || lowerBlockId.contains("insolator")
                        || lowerBlockId.contains("centrifuge")
                        || lowerBlockId.contains("press")
                        || lowerBlockId.contains("mixer")
                        || lowerBlockId.contains("basin")
                        || lowerBlockId.contains("orb")
                        || lowerBlockId.contains("brewing")
                        || lowerBlockId.contains("smoker")
                        || lowerBlockId.contains("crafter")
                        || lowerBlockId.contains("assembler")
                        || lowerBlockId.contains("reactor");

                boolean isInventory = be instanceof Container
                        || !sampleItems.isEmpty()
                        || lowerBlockId.contains("chest")
                        || lowerBlockId.contains("barrel")
                        || lowerBlockId.contains("hopper")
                        || lowerBlockId.contains("shulker")
                        || lowerBlockId.contains("storage")
                        || lowerBlockId.contains("drawer")
                        || lowerBlockId.contains("vault")
                        || lowerBlockId.contains("interface")
                        || lowerBlockId.contains("cache");

                List<Integer> inputSlots = new ArrayList<>();
                List<Integer> outputSlots = new ArrayList<>();
                List<String> inputSides = new ArrayList<>();
                List<String> outputSides = new ArrayList<>();

                if (isMachine) {
                    detectMachineIO(blockId, be, level, neighborPos, maxSlotsFound, inputSlots, outputSlots, inputSides, outputSides);
                }

                // If it is an inventory, machine, has capabilities, block entity, or has labels
                if (isInventory || isMachine || !supportedSides.isEmpty() || !matchedLabels.isEmpty() || be != null) {
                    connectedBlocks.add(new ConnectedBlockInfo(
                            neighborPos,
                            blockId,
                            displayName,
                            matchedLabels,
                            new ArrayList<>(sampleItems),
                            new ArrayList<>(sampleFluids),
                            supportedSides,
                            isMachine,
                            maxSlotsFound,
                            hasEnergy,
                            hasFluid,
                            inputSlots,
                            outputSlots,
                            inputSides,
                            outputSides
                    ));
                }
            }
        }

        return Optional.of(new CableContextData(finalManagerPos, connectedBlocks));
    }

    private static void detectMachineIO(
            String blockId,
            @Nullable BlockEntity be,
            ServerLevel level,
            BlockPos pos,
            int maxSlots,
            List<Integer> inputSlots,
            List<Integer> outputSlots,
            List<String> inputSides,
            List<String> outputSides
    ) {
        String lowerId = blockId.toLowerCase(Locale.ROOT);

        // 1. Check WorldlyContainer (Vanilla sided container: Furnace, Brewing Stand, Crafter, etc.)
        if (be instanceof WorldlyContainer sided) {
            for (Direction d : Direction.values()) {
                int[] slots = sided.getSlotsForFace(d);
                for (int slot : slots) {
                    if (sided.canTakeItemThroughFace(slot, ItemStack.EMPTY, d)) {
                        if (!outputSlots.contains(slot)) outputSlots.add(slot);
                        String sName = d.getName().toLowerCase(Locale.ROOT);
                        if (!outputSides.contains(sName)) outputSides.add(sName);
                    }
                    if (sided.canPlaceItemThroughFace(slot, ItemStack.EMPTY, d)) {
                        if (!inputSlots.contains(slot)) inputSlots.add(slot);
                        String sName = d.getName().toLowerCase(Locale.ROOT);
                        if (!inputSides.contains(sName)) inputSides.add(sName);
                    }
                }
            }
        }

        // 2. Probe capability per side (Mekanism Side Config, Thermal Config, etc.)
        for (Direction d : Direction.values()) {
            try {
                ResourceHandler<ItemResource> res = level.getCapability(Capabilities.Item.BLOCK, pos, d);
                if (res != null) {
                    IItemHandler handler = IItemHandler.of(res);
                    int slots = handler.getSlots();
                    String sideName = d.getName().toLowerCase(Locale.ROOT);
                    for (int s = 0; s < slots; s++) {
                        ItemStack extracted = handler.extractItem(s, 1, true);
                        if (!extracted.isEmpty()) {
                            if (!outputSides.contains(sideName)) outputSides.add(sideName);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        // 3. Normalized machine profiles for known common machinery
        if (lowerId.contains("inscriber")) {
            inputSlots.clear();
            inputSlots.addAll(List.of(0, 1, 2));
            outputSlots.clear();
            outputSlots.add(3);
            if (inputSides.isEmpty()) inputSides.addAll(List.of("top", "bottom", "north", "south", "west", "east"));
            if (outputSides.isEmpty()) outputSides.addAll(List.of("west", "east", "south"));
        } else if (lowerId.contains("infuser")) {
            inputSlots.clear();
            inputSlots.addAll(List.of(0, 1));
            outputSlots.clear();
            outputSlots.add(2);
        } else if (lowerId.contains("crusher") || lowerId.contains("enrichment") || lowerId.contains("purification")) {
            inputSlots.clear();
            inputSlots.add(0);
            outputSlots.clear();
            outputSlots.add(1);
        } else if (lowerId.contains("furnace") || lowerId.contains("smoker") || lowerId.contains("blast")) {
            inputSlots.clear();
            inputSlots.addAll(List.of(0, 1));
            outputSlots.clear();
            outputSlots.add(2);
            if (inputSides.isEmpty()) inputSides.addAll(List.of("top", "north", "south", "west", "east"));
            if (outputSides.isEmpty()) outputSides.add("bottom");
        } else if (lowerId.contains("brewing")) {
            inputSlots.clear();
            inputSlots.addAll(List.of(0, 1, 2, 3, 4));
            outputSlots.clear();
            outputSlots.addAll(List.of(0, 1, 2));
            if (inputSides.isEmpty()) inputSides.addAll(List.of("top", "north", "south", "west", "east"));
            if (outputSides.isEmpty()) outputSides.add("bottom");
        } else if (lowerId.contains("pulverizer") || lowerId.contains("smelter") || lowerId.contains("insolator") || lowerId.contains("centrifuge")) {
            inputSlots.clear();
            inputSlots.addAll(List.of(0, 1));
            outputSlots.clear();
            outputSlots.add(2);
        } else if (maxSlots > 0) {
            if (maxSlots == 2) {
                if (inputSlots.isEmpty()) inputSlots.add(0);
                if (outputSlots.isEmpty()) outputSlots.add(1);
            } else if (maxSlots == 3) {
                if (inputSlots.isEmpty()) inputSlots.addAll(List.of(0, 1));
                if (outputSlots.isEmpty()) outputSlots.add(2);
            } else if (maxSlots == 4) {
                if (inputSlots.isEmpty()) inputSlots.addAll(List.of(0, 1, 2));
                if (outputSlots.isEmpty()) outputSlots.add(3);
            } else {
                if (inputSlots.isEmpty()) inputSlots.add(0);
                if (outputSlots.isEmpty()) outputSlots.add(1);
            }
        }
    }
}
