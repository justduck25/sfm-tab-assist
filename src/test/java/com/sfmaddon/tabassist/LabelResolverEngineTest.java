package com.sfmaddon.tabassist;

import com.sfmaddon.tabassist.context.CableContextData;
import com.sfmaddon.tabassist.context.ConnectedBlockInfo;
import com.sfmaddon.tabassist.resolver.LabelResolverEngine;
import com.sfmaddon.tabassist.resolver.ResolvedBlockCandidate;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class LabelResolverEngineTest {

    @Test
    public void testExactAssignedLabelReturns100Percent() {
        ConnectedBlockInfo chest = new ConnectedBlockInfo(
                new BlockPos(10, 64, 10),
                "minecraft:chest",
                "Chest",
                List.of("my_main_chest"),
                List.of("diamond"),
                List.of(),
                List.of("ALL"),
                false
        );
        CableContextData context = new CableContextData(new BlockPos(0, 64, 0), List.of(chest));

        List<ResolvedBlockCandidate> candidates = LabelResolverEngine.resolveCandidates(
                "\"my_main_chest\"",
                true,
                context
        );

        Assertions.assertEquals(1, candidates.size());
        ResolvedBlockCandidate top = candidates.get(0);
        Assertions.assertEquals(1.0f, top.confidence());
        Assertions.assertEquals(100, top.getPercentage());
        Assertions.assertEquals(chest.pos(), top.pos());
    }

    @Test
    public void testLexicalNameSimilarityMatchesIronChest() {
        ConnectedBlockInfo ironChest = new ConnectedBlockInfo(
                new BlockPos(12, 64, 10),
                "ironchest:iron_chest",
                "Iron Chest",
                List.of(), // No custom label assigned
                List.of(),
                List.of(),
                List.of("ALL"),
                false,
                54,
                false,
                false
        );
        ConnectedBlockInfo vanillaChest = new ConnectedBlockInfo(
                new BlockPos(14, 64, 10),
                "minecraft:chest",
                "Chest",
                List.of(),
                List.of(),
                List.of(),
                List.of("ALL"),
                false,
                27,
                false,
                false
        );
        CableContextData context = new CableContextData(new BlockPos(0, 64, 0), List.of(ironChest, vanillaChest));

        List<ResolvedBlockCandidate> candidates = LabelResolverEngine.resolveCandidates(
                "iron_chest",
                true,
                context
        );

        Assertions.assertFalse(candidates.isEmpty());
        ResolvedBlockCandidate best = candidates.get(0);
        Assertions.assertEquals(ironChest.pos(), best.pos());
        // Lexical (0.40) + Storage Role (0.25) -> >= 0.65
        Assertions.assertTrue(best.confidence() >= 0.65f, "Expected confidence >= 0.65, got: " + best.confidence());
    }

    @Test
    public void testInventoryContentInferenceMatchesChestContainingIron() {
        ConnectedBlockInfo chestWithIron = new ConnectedBlockInfo(
                new BlockPos(20, 64, 0),
                "minecraft:chest",
                "Chest",
                List.of(),
                List.of("raw_iron", "iron_ingot"),
                List.of(),
                List.of("ALL"),
                false
        );
        ConnectedBlockInfo chestWithWood = new ConnectedBlockInfo(
                new BlockPos(22, 64, 0),
                "minecraft:chest",
                "Chest",
                List.of(),
                List.of("oak_log"),
                List.of(),
                List.of("ALL"),
                false
        );
        CableContextData context = new CableContextData(new BlockPos(0, 64, 0), List.of(chestWithIron, chestWithWood));

        List<ResolvedBlockCandidate> candidates = LabelResolverEngine.resolveCandidates(
                "input_iron",
                true,
                context
        );

        Assertions.assertFalse(candidates.isEmpty());
        ResolvedBlockCandidate best = candidates.get(0);
        Assertions.assertEquals(chestWithIron.pos(), best.pos());
        // Content score (+0.35) + Flow bonus (+0.05) -> >= 0.40
        Assertions.assertTrue(best.confidence() >= 0.40f);
    }

    @Test
    public void testSmelterKeywordResolvesToFurnace() {
        ConnectedBlockInfo furnace = new ConnectedBlockInfo(
                new BlockPos(30, 64, 0),
                "minecraft:furnace",
                "Furnace",
                List.of(),
                List.of(),
                List.of(),
                List.of("ALL"),
                true,
                3,
                false,
                false,
                List.of(0, 1),
                List.of(2),
                List.of("top", "bottom"),
                List.of("bottom")
        );
        ConnectedBlockInfo barrel = new ConnectedBlockInfo(
                new BlockPos(32, 64, 0),
                "minecraft:barrel",
                "Barrel",
                List.of(),
                List.of(),
                List.of(),
                List.of("ALL"),
                false
        );
        CableContextData context = new CableContextData(new BlockPos(0, 64, 0), List.of(furnace, barrel));

        List<ResolvedBlockCandidate> candidates = LabelResolverEngine.resolveCandidates(
                "smelter",
                false, // OUTPUT TO smelter
                context
        );

        Assertions.assertFalse(candidates.isEmpty());
        ResolvedBlockCandidate top = candidates.get(0);
        Assertions.assertEquals(furnace.pos(), top.pos());
        // Machine Role (0.25) + Flow bonus (0.05)
        Assertions.assertTrue(top.confidence() >= 0.30f);
    }

    @Test
    public void testCandidateRankingOrder() {
        // Candidate 1: Exact label match
        ConnectedBlockInfo exactMatch = new ConnectedBlockInfo(
                new BlockPos(40, 64, 0),
                "minecraft:chest",
                "Chest",
                List.of("iron_vault"),
                List.of(),
                List.of(),
                List.of("ALL"),
                false
        );

        // Candidate 2: Name match + Content match
        ConnectedBlockInfo nameMatch = new ConnectedBlockInfo(
                new BlockPos(42, 64, 0),
                "ironchest:iron_chest",
                "Iron Chest",
                List.of(),
                List.of("raw_iron"),
                List.of(),
                List.of("ALL"),
                false
        );

        // Candidate 3: Just container, no name or content overlap
        ConnectedBlockInfo unrelated = new ConnectedBlockInfo(
                new BlockPos(44, 64, 0),
                "minecraft:dispenser",
                "Dispenser",
                List.of(),
                List.of("arrow"),
                List.of(),
                List.of("ALL"),
                false
        );

        CableContextData context = new CableContextData(new BlockPos(0, 64, 0), List.of(exactMatch, nameMatch, unrelated));

        List<ResolvedBlockCandidate> candidates = LabelResolverEngine.resolveCandidates(
                "iron_vault",
                true,
                context
        );

        Assertions.assertFalse(candidates.isEmpty());
        Assertions.assertEquals(exactMatch.pos(), candidates.get(0).pos());
        Assertions.assertEquals(1.0f, candidates.get(0).confidence());

        // For "iron_chest", nameMatch should rank first
        List<ResolvedBlockCandidate> chestCandidates = LabelResolverEngine.resolveCandidates(
                "iron_chest",
                true,
                context
        );
        Assertions.assertFalse(chestCandidates.isEmpty());
        Assertions.assertEquals(nameMatch.pos(), chestCandidates.get(0).pos());
        Assertions.assertTrue(chestCandidates.get(0).confidence() > 0.70f);
    }
}
