package com.sfmaddon.tabassist.resolver;

import net.minecraft.client.Minecraft;

import java.util.Collections;
import java.util.List;

/**
 * Manages active in-world block highlights resulting from semantic label resolution.
 */
public class WorldHighlightManager {

    private static String currentLabel = "";
    private static List<ResolvedBlockCandidate> activeCandidates = Collections.emptyList();
    private static long expireAtGameTime = 0;

    /**
     * Activates in-world highlights for the resolved candidates.
     *
     * @param labelQuery    The label searched for (e.g. "iron_chest").
     * @param candidates    The ranked list of resolved block candidates.
     * @param durationTicks The duration to highlight in world ticks (e.g. 300 ticks = 15s).
     */
    public static synchronized void setHighlights(String labelQuery, List<ResolvedBlockCandidate> candidates, int durationTicks) {
        currentLabel = labelQuery;
        activeCandidates = (candidates != null && !candidates.isEmpty())
                ? List.copyOf(candidates)
                : Collections.emptyList();

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            expireAtGameTime = mc.level.getGameTime() + durationTicks;
        } else {
            expireAtGameTime = Long.MAX_VALUE;
        }
    }

    /**
     * Clears all active highlights.
     */
    public static synchronized void clear() {
        currentLabel = "";
        activeCandidates = Collections.emptyList();
        expireAtGameTime = 0;
    }

    /**
     * Checks if there are active highlights that have not yet expired.
     */
    public static synchronized boolean hasHighlights() {
        if (activeCandidates.isEmpty()) {
            return false;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getGameTime() > expireAtGameTime) {
            clear();
            return false;
        }

        return true;
    }

    public static synchronized List<ResolvedBlockCandidate> getActiveCandidates() {
        return activeCandidates;
    }

    public static synchronized String getCurrentLabel() {
        return currentLabel;
    }
}
