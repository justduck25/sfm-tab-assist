package com.sfmaddon.tabassist.resolver;

import com.sfmaddon.tabassist.context.ConnectedBlockInfo;
import net.minecraft.core.BlockPos;

/**
 * Represents a resolved block candidate in the world matching a semantic label,
 * with an associated confidence score and reasoning.
 */
public record ResolvedBlockCandidate(
        BlockPos pos,
        ConnectedBlockInfo block,
        float confidence,
        String primaryReason
) implements Comparable<ResolvedBlockCandidate> {

    public int getPercentage() {
        return Math.round(confidence * 100.0f);
    }

    /**
     * Gets a hex ARGB color representing the confidence level:
     * - Green for high confidence (>= 80%)
     * - Gold / Yellow for medium confidence (50% - 79%)
     * - Orange for low confidence (< 50%)
     */
    public int getColorHex() {
        if (confidence >= 0.80f) {
            return 0xFF00FF66; // High: Bright Green
        } else if (confidence >= 0.50f) {
            return 0xFFFFD700; // Medium: Gold / Yellow
        } else {
            return 0xFFFF8C00; // Low: Orange
        }
    }

    @Override
    public int compareTo(ResolvedBlockCandidate other) {
        // Sort descending by confidence
        return Float.compare(other.confidence, this.confidence);
    }
}
