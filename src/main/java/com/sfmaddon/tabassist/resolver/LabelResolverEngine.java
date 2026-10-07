package com.sfmaddon.tabassist.resolver;

import com.sfmaddon.tabassist.context.CableContextData;
import com.sfmaddon.tabassist.context.ConnectedBlockInfo;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Semantic Label Resolver Engine.
 * Resolves user-defined script labels (e.g. "iron_chest", "smelter", "input_iron")
 * to candidate in-world blocks connected to the SFM cable network, computing
 * confidence scores based on lexical similarity, inventory contents, machine heuristics,
 * and flow direction.
 */
public class LabelResolverEngine {

    public static final float MINIMUM_CONFIDENCE_THRESHOLD = 0.20f;

    /**
     * Resolves matching block candidates for a given label token.
     *
     * @param labelToken   The label string (e.g. "iron_chest", "\"input_iron\"").
     * @param isInputFlow  True if the context is "INPUT FROM", false if "OUTPUT TO".
     * @param context      The current cable network context data.
     * @return List of ranked candidates sorted descending by confidence score.
     */
    public static List<ResolvedBlockCandidate> resolveCandidates(
            @Nullable String labelToken,
            boolean isInputFlow,
            @Nullable CableContextData context
    ) {
        if (labelToken == null || context == null || context.connectedBlocks().isEmpty()) {
            return Collections.emptyList();
        }

        String cleanLabel = cleanToken(labelToken);
        if (cleanLabel.isEmpty()) {
            return Collections.emptyList();
        }

        String lowerLabel = cleanLabel.toLowerCase(Locale.ROOT);
        List<String> labelTokens = tokenize(lowerLabel);

        List<ResolvedBlockCandidate> candidates = new ArrayList<>();

        for (ConnectedBlockInfo block : context.connectedBlocks()) {
            // 1. Exact Label Match (100% confidence)
            if (isExactMatch(block, cleanLabel)) {
                candidates.add(new ResolvedBlockCandidate(
                        block.pos(),
                        block,
                        1.0f,
                        "Exact assigned label match: \"" + cleanLabel + "\""
                ));
                continue;
            }

            // Otherwise, calculate heuristic confidence score
            float score = 0.0f;
            List<String> reasons = new ArrayList<>();

            // A. Lexical / Block ID Similarity (Up to 0.40)
            float lexicalScore = computeLexicalScore(block, labelTokens, lowerLabel);
            if (lexicalScore > 0.0f) {
                score += lexicalScore;
                reasons.add(String.format("Name matches (%d%%)", Math.round(lexicalScore * 100)));
            }

            // B. Inventory Content Match (Up to 0.35)
            float contentScore = computeContentScore(block, labelTokens);
            if (contentScore > 0.0f) {
                score += contentScore;
                reasons.add(String.format("Inventory contents match (%d%%)", Math.round(contentScore * 100)));
            }

            // C. Machine Role & Capability Match (Up to 0.25)
            float machineScore = computeMachineRoleScore(block, labelTokens);
            if (machineScore > 0.0f) {
                score += machineScore;
                reasons.add(String.format("Machine role match (%d%%)", Math.round(machineScore * 100)));
            }

            // D. Flow Direction Bonus (Up to 0.10)
            float flowScore = computeFlowScore(block, isInputFlow);
            score += flowScore;

            // Clamp confidence to max 0.99f for heuristic inferences (1.0f is reserved for exact matches)
            float finalConfidence = Math.min(0.99f, score);

            if (finalConfidence >= MINIMUM_CONFIDENCE_THRESHOLD) {
                String primaryReason = reasons.isEmpty()
                        ? "Potential candidate block"
                        : String.join(", ", reasons);

                candidates.add(new ResolvedBlockCandidate(
                        block.pos(),
                        block,
                        finalConfidence,
                        primaryReason
                ));
            }
        }

        Collections.sort(candidates);
        return candidates;
    }

    private static String cleanToken(String token) {
        String trimmed = token.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private static List<String> tokenize(String input) {
        String[] parts = input.split("[_\\-\\s]+");
        List<String> tokens = new ArrayList<>();
        for (String p : parts) {
            String t = p.trim();
            // Filter out common noise words like "the", "a", "my"
            if (!t.isEmpty() && !t.equals("the") && !t.equals("a") && !t.equals("my")) {
                tokens.add(t);
            }
        }
        return tokens;
    }

    private static boolean isExactMatch(ConnectedBlockInfo block, String cleanLabel) {
        for (String l : block.labels()) {
            if (l.equalsIgnoreCase(cleanLabel)) return true;
        }
        return block.getPreferredLabel().equalsIgnoreCase(cleanLabel);
    }

    private static float computeLexicalScore(ConnectedBlockInfo block, List<String> labelTokens, String fullLabel) {
        String blockId = block.blockId().toLowerCase(Locale.ROOT);
        int colon = blockId.indexOf(':');
        String blockPath = (colon != -1) ? blockId.substring(colon + 1) : blockId;
        String displayName = block.blockDisplayName().toLowerCase(Locale.ROOT);

        // Check if full label is an exact substring of block path or display name
        if (blockPath.contains(fullLabel) || displayName.contains(fullLabel)) {
            return 0.40f;
        }

        if (labelTokens.isEmpty()) return 0.0f;

        int matchedTokens = 0;
        for (String token : labelTokens) {
            if (blockPath.contains(token) || displayName.contains(token)) {
                matchedTokens++;
            }
        }

        if (matchedTokens > 0) {
            float ratio = (float) matchedTokens / labelTokens.size();
            return 0.40f * ratio;
        }

        return 0.0f;
    }

    private static float computeContentScore(ConnectedBlockInfo block, List<String> labelTokens) {
        if (labelTokens.isEmpty()) return 0.0f;

        int matchedTokens = 0;

        for (String token : labelTokens) {
            // Ignore generic flow words for content matching
            if (token.equals("input") || token.equals("output") || token.equals("in") || token.equals("out")) {
                continue;
            }

            boolean foundInItems = false;
            for (String item : block.sampleItemIds()) {
                String lowerItem = item.toLowerCase(Locale.ROOT);
                if (lowerItem.contains(token)) {
                    foundInItems = true;
                    break;
                }
            }

            boolean foundInFluids = false;
            for (String fluid : block.sampleFluidIds()) {
                String lowerFluid = fluid.toLowerCase(Locale.ROOT);
                if (lowerFluid.contains(token)) {
                    foundInFluids = true;
                    break;
                }
            }

            if (foundInItems || foundInFluids) {
                matchedTokens++;
            }
        }

        if (matchedTokens > 0) {
            // High boost when content matches tokens like "iron", "gold", "coal", "lava"
            return Math.min(0.35f, 0.20f + (matchedTokens * 0.15f));
        }

        return 0.0f;
    }

    private static float computeMachineRoleScore(ConnectedBlockInfo block, List<String> labelTokens) {
        String blockId = block.blockId().toLowerCase(Locale.ROOT);

        for (String token : labelTokens) {
            // Smelting / cooking role
            if (token.contains("smelt") || token.contains("furnace") || token.contains("cook")
                    || token.contains("bake") || token.contains("oven") || token.contains("blast")) {
                if (blockId.contains("furnace") || blockId.contains("smoker") || blockId.contains("smelter")) {
                    return 0.25f;
                }
            }

            // Crushing / milling role
            if (token.contains("crush") || token.contains("pulveriz") || token.contains("grind") || token.contains("mill")) {
                if (blockId.contains("crusher") || blockId.contains("pulverizer") || blockId.contains("mill")) {
                    return 0.25f;
                }
            }

            // Infuser role
            if (token.contains("infus")) {
                if (blockId.contains("infuser")) {
                    return 0.25f;
                }
            }

            // Inscriber / press role
            if (token.contains("inscrib") || token.contains("press")) {
                if (blockId.contains("inscriber")) {
                    return 0.25f;
                }
            }

            // Brewing role
            if (token.contains("brew") || token.contains("potion") || token.contains("alchemy")) {
                if (blockId.contains("brewing") || blockId.contains("brewery")) {
                    return 0.25f;
                }
            }

            // Liquid / Tank storage
            if (token.contains("tank") || token.contains("fluid") || token.contains("cauldron") || token.contains("drum")) {
                if (block.hasFluid() || !block.sampleFluidIds().isEmpty() || blockId.contains("tank") || blockId.contains("cauldron")) {
                    return 0.25f;
                }
            }

            // Energy / Generator role
            if (token.contains("generator") || token.contains("battery") || token.contains("energy") || token.contains("power")) {
                if (block.hasEnergy() || blockId.contains("generator") || blockId.contains("battery")) {
                    return 0.25f;
                }
            }

            // Chest / storage role
            if (token.contains("chest") || token.contains("barrel") || token.contains("drawer") || token.contains("storage") || token.contains("crate")) {
                if (!block.isMachine() && (blockId.contains("chest") || blockId.contains("barrel") || blockId.contains("drawer") || blockId.contains("crate"))) {
                    return 0.25f;
                }
            }
        }

        return 0.0f;
    }

    private static float computeFlowScore(ConnectedBlockInfo block, boolean isInputFlow) {
        if (isInputFlow) {
            // INPUT FROM: prefers blocks containing items/fluids or having output slots
            if (!block.sampleItemIds().isEmpty() || !block.sampleFluidIds().isEmpty() || !block.outputSlots().isEmpty()) {
                return 0.05f;
            }
        } else {
            // OUTPUT TO: prefers blocks that accept inputs (machines with input slots or storage containers)
            if (!block.inputSlots().isEmpty() || (!block.isMachine() && block.totalSlots() > 0)) {
                return 0.05f;
            }
        }
        return 0.0f;
    }
}
