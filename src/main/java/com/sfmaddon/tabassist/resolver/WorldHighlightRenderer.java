package com.sfmaddon.tabassist.resolver;

import ca.teamdman.sfm.client.screen.SFMFontUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Renders in-world X-ray outlines and floating billboard tags for candidate blocks
 * discovered by the Semantic Label Resolver, with full support for multi-block structures
 * (double chests, beds, doors, etc.).
 */
public class WorldHighlightRenderer {

    public static void onRenderLevelStage(RenderLevelStageEvent.AfterTranslucentParticles event) {
        if (!WorldHighlightManager.hasHighlights()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        List<ResolvedBlockCandidate> candidates = WorldHighlightManager.getActiveCandidates();
        if (candidates.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Camera camera = minecraft.gameRenderer.getMainCamera();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        Font font = minecraft.font;

        // Pass 1: Render all block bounding box outlines
        try {
            VertexConsumer lineBuffer = bufferSource.getBuffer(RenderTypes.lines());
            Set<BlockPos> outlinedPositions = new HashSet<>();
            for (ResolvedBlockCandidate candidate : candidates) {
                if (candidate == null || candidate.pos() == null) continue;
                List<BlockPos> positions = getConnectedBlockPositions(minecraft.level, candidate.pos());
                int color = candidate.getColorHex();

                for (BlockPos p : positions) {
                    if (!outlinedPositions.add(p)) continue;
                    double rx = p.getX() - camera.position().x;
                    double ry = p.getY() - camera.position().y;
                    double rz = p.getZ() - camera.position().z;

                    BlockState st = minecraft.level.getBlockState(p);
                    VoxelShape shape = st.getShape(minecraft.level, p);
                    if (shape.isEmpty()) shape = Shapes.block();

                    ShapeRenderer.renderShape(
                            poseStack,
                            lineBuffer,
                            shape,
                            rx, ry, rz,
                            color,
                            3.5f
                    );
                }
            }
            bufferSource.endBatch(RenderTypes.lines());
        } catch (Exception e) {
            // Protect against rendering errors crashing the client
        }

        // Pass 2: Render all 3D billboard text banners centered over target(s)
        try {
            Set<BlockPos> taggedPositions = new HashSet<>();
            for (ResolvedBlockCandidate candidate : candidates) {
                if (candidate == null || candidate.pos() == null || candidate.block() == null) continue;
                if (!taggedPositions.add(candidate.pos())) continue;

                List<BlockPos> positions = getConnectedBlockPositions(minecraft.level, candidate.pos());
                taggedPositions.addAll(positions);

                double totalX = 0, totalZ = 0;
                double maxY = Double.NEGATIVE_INFINITY;
                for (BlockPos p : positions) {
                    totalX += p.getX();
                    totalZ += p.getZ();
                    if (p.getY() > maxY) maxY = p.getY();
                }
                double centerX = (totalX / positions.size()) + 0.5;
                double centerZ = (totalZ / positions.size()) + 0.5;
                double tagY = maxY + 1.25;

                poseStack.pushPose();
                poseStack.translate(
                        centerX - camera.position().x,
                        tagY - camera.position().y,
                        centerZ - camera.position().z
                );
                poseStack.mulPose(camera.rotation());
                poseStack.mulPose(Axis.YP.rotationDegrees(180));
                poseStack.scale(-0.025f, -0.025f, 0.025f);

                String name = candidate.block().blockDisplayName() != null ? candidate.block().blockDisplayName() : "Block";
                String tagText = name + " [" + candidate.getPercentage() + "%]";
                SFMFontUtils.drawInBatch(
                        tagText,
                        font,
                        -font.width(tagText) / 2f,
                        0,
                        false, // dropShadow
                        true,  // seeThrough (visible through walls)
                        poseStack.last().pose(),
                        bufferSource
                );
                poseStack.popPose();
            }
            bufferSource.endBatch();
        } catch (Exception e) {
            // Protect against font batch rendering errors crashing the client
        }
    }

    /**
     * Resolves all connected block positions for multi-block structures such as
     * Double Chests, Beds, and Doors.
     */
    public static List<BlockPos> getConnectedBlockPositions(Level level, BlockPos pos) {
        if (level == null || pos == null) return List.of();
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return List.of(pos);

        // 1. Double Chest
        if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE)) {
            if (state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                Direction dir = ChestBlock.getConnectedDirection(state);
                BlockPos partner = pos.relative(dir);
                if (level.getBlockState(partner).is(state.getBlock())) {
                    return List.of(pos, partner);
                }
            }
        }

        // 2. Bed
        if (state.getBlock() instanceof BedBlock
                && state.hasProperty(BedBlock.PART)
                && state.hasProperty(BedBlock.FACING)) {
            BedPart part = state.getValue(BedBlock.PART);
            Direction facing = state.getValue(BedBlock.FACING);
            BlockPos partner = (part == BedPart.FOOT)
                    ? pos.relative(facing)
                    : pos.relative(facing.getOpposite());
            if (level.getBlockState(partner).is(state.getBlock())) {
                return List.of(pos, partner);
            }
        }

        // 3. Door
        if (state.getBlock() instanceof DoorBlock
                && state.hasProperty(DoorBlock.HALF)) {
            DoubleBlockHalf half = state.getValue(DoorBlock.HALF);
            BlockPos partner = (half == DoubleBlockHalf.LOWER)
                    ? pos.above()
                    : pos.below();
            if (level.getBlockState(partner).is(state.getBlock())) {
                return List.of(pos, partner);
            }
        }

        return List.of(pos);
    }
}
