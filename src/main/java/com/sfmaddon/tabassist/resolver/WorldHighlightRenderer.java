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
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.List;

/**
 * Renders in-world X-ray outlines and floating billboard tags for candidate blocks
 * discovered by the Semantic Label Resolver.
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

        VertexConsumer lineBuffer = bufferSource.getBuffer(RenderTypes.lines());

        for (ResolvedBlockCandidate candidate : candidates) {
            BlockPos pos = candidate.pos();
            double rx = pos.getX() - camera.position().x;
            double ry = pos.getY() - camera.position().y;
            double rz = pos.getZ() - camera.position().z;

            int color = candidate.getColorHex();

            // 1. Draw block bounding box outline
            ShapeRenderer.renderShape(
                    poseStack,
                    lineBuffer,
                    Shapes.block(),
                    rx, ry, rz,
                    color,
                    3.5f
            );

            // 2. Draw 3D billboard text banner floating above the block
            poseStack.pushPose();
            poseStack.translate(
                    pos.getX() + 0.5 - camera.position().x,
                    pos.getY() + 1.25 - camera.position().y,
                    pos.getZ() + 0.5 - camera.position().z
            );
            poseStack.mulPose(camera.rotation());
            poseStack.mulPose(Axis.YP.rotationDegrees(180));
            poseStack.scale(-0.025f, -0.025f, 0.025f);

            String tagText = candidate.block().blockDisplayName() + " [" + candidate.getPercentage() + "%]";
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
    }
}
