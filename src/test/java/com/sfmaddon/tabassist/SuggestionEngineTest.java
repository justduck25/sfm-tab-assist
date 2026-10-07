package com.sfmaddon.tabassist;

import com.sfmaddon.tabassist.context.CableContextData;
import com.sfmaddon.tabassist.context.ConnectedBlockInfo;
import com.sfmaddon.tabassist.engine.Suggestion;
import com.sfmaddon.tabassist.engine.SuggestionEngine;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

public class SuggestionEngineTest {

    @Test
    public void testEmptyLineOnFirstLineEmptyDoc() {
        Optional<Suggestion> sug = SuggestionEngine.computeSuggestion("", 0, 0, true, null);
        Assertions.assertTrue(sug.isPresent());
        Assertions.assertEquals("every 20 ticks do", sug.get().ghostText());
    }

    @Test
    public void testEmptyLineOnNewLineDoesNotSpam() {
        Optional<Suggestion> sug = SuggestionEngine.computeSuggestion("", 0, 1, false, null);
        Assertions.assertTrue(sug.isEmpty(), "Must not spam suggestions immediately upon Enter to a new line!");
    }

    @Test
    public void testInputSuggestsFromChest() {
        ConnectedBlockInfo chest = new ConnectedBlockInfo(
                new BlockPos(0, 64, 0),
                "minecraft:chest",
                "Chest",
                List.of(),
                List.of("coal", "raw_iron"),
                List.of(),
                List.of("ALL"),
                false
        );
        CableContextData context = new CableContextData(new BlockPos(0, 63, 0), List.of(chest));

        // User types "in" -> completes to "put from chest"
        Optional<Suggestion> sug = SuggestionEngine.computeSuggestion("in", 2, 1, false, context);
        Assertions.assertTrue(sug.isPresent());
        Assertions.assertEquals("put from chest", sug.get().ghostText());
    }

    @Test
    public void testTypingRawIronMatchesEvenIfCoalIsFirstInChest() {
        // Chest has coal in slot 0, raw iron in slot 1
        ConnectedBlockInfo chest = new ConnectedBlockInfo(
                new BlockPos(0, 64, 0),
                "minecraft:chest",
                "Chest",
                List.of(),
                List.of("coal", "raw_iron"),
                List.of(),
                List.of("ALL"),
                false
        );
        CableContextData context = new CableContextData(new BlockPos(0, 63, 0), List.of(chest));

        // User types "input raw" -> matches "raw_iron" instead of coal!
        Optional<Suggestion> sug = SuggestionEngine.computeSuggestion("input raw", 9, 1, false, context);
        Assertions.assertTrue(sug.isPresent());
        Assertions.assertEquals("_iron from chest", sug.get().ghostText());
    }

    @Test
    public void testSmeltingOreGoesToFurnaceTopSide() {
        ConnectedBlockInfo furnace = new ConnectedBlockInfo(
                new BlockPos(1, 64, 0),
                "minecraft:furnace",
                "Furnace",
                List.of(),
                List.of(),
                List.of(),
                List.of("ALL"),
                true
        );
        CableContextData context = new CableContextData(new BlockPos(0, 63, 0), List.of(furnace));

        // Output raw iron to furnace -> uses slots 0 (smelting input)!
        Optional<Suggestion> sug = SuggestionEngine.computeSuggestion("output raw_iron", 15, 1, false, context);
        Assertions.assertTrue(sug.isPresent());
        Assertions.assertEquals(" to furnace slots 0", sug.get().ghostText());
    }

    @Test
    public void testFuelCoalGoesToFurnaceBottomSide() {
        ConnectedBlockInfo furnace = new ConnectedBlockInfo(
                new BlockPos(1, 64, 0),
                "minecraft:furnace",
                "Furnace",
                List.of(),
                List.of(),
                List.of(),
                List.of("ALL"),
                true
        );
        CableContextData context = new CableContextData(new BlockPos(0, 63, 0), List.of(furnace));

        // Output coal to furnace -> uses slots 1 (fuel slot)!
        Optional<Suggestion> sug = SuggestionEngine.computeSuggestion("output coal", 11, 1, false, context);
        Assertions.assertTrue(sug.isPresent());
        Assertions.assertEquals(" to furnace slots 1", sug.get().ghostText());
    }

    @Test
    public void testDefaultOutputWithBothOreAndFuelPrioritizesSmeltableFirst() {
        ConnectedBlockInfo chest = new ConnectedBlockInfo(
                new BlockPos(0, 64, 0),
                "minecraft:chest",
                "Chest",
                List.of(),
                List.of("coal", "raw_iron"),
                List.of(),
                List.of("ALL"),
                false
        );
        ConnectedBlockInfo furnace = new ConnectedBlockInfo(
                new BlockPos(1, 64, 0),
                "minecraft:furnace",
                "Furnace",
                List.of(),
                List.of(),
                List.of(),
                List.of("ALL"),
                true
        );
        CableContextData context = new CableContextData(new BlockPos(0, 63, 0), List.of(chest, furnace));

        // Typing "output " -> automatically prioritizes smeltable ore to slots 0 first
        Optional<Suggestion> sug = SuggestionEngine.computeSuggestion("output ", 7, 1, false, context);
        Assertions.assertTrue(sug.isPresent());
        Assertions.assertEquals("raw_iron to furnace slots 0", sug.get().ghostText());
    }

    @Test
    public void testSideCompletion() {
        Optional<Suggestion> sug = SuggestionEngine.computeSuggestion("output raw_iron to furnace top", 30, 1, false, null);
        Assertions.assertTrue(sug.isPresent());
        Assertions.assertEquals(" side", sug.get().ghostText());
    }

    @Test
    public void testEndCompletion() {
        Optional<Suggestion> sug = SuggestionEngine.computeSuggestion("en", 2, 1, false, null);
        Assertions.assertTrue(sug.isPresent());
        Assertions.assertEquals("d", sug.get().ghostText());
    }

    @Test
    public void testIfConditionWithChestContext() {
        ConnectedBlockInfo chest = new ConnectedBlockInfo(
                new BlockPos(0, 64, 0),
                "minecraft:chest",
                "Chest",
                List.of(),
                List.of("coal", "raw_iron"),
                List.of(),
                List.of("ALL"),
                false
        );
        CableContextData context = new CableContextData(new BlockPos(0, 63, 0), List.of(chest));

        Optional<Suggestion> sug = SuggestionEngine.computeSuggestion("if", 2, 1, false, context);
        Assertions.assertTrue(sug.isPresent());
        Assertions.assertEquals(" chest has gt 0 coal then", sug.get().ghostText());
    }

    @Test
    public void testIfRedstoneCondition() {
        Optional<Suggestion> sug = SuggestionEngine.computeSuggestion("if red", 6, 1, false, null);
        Assertions.assertTrue(sug.isPresent());
        Assertions.assertEquals("stone eq 15 then", sug.get().ghostText());

        Optional<Suggestion> sug2 = SuggestionEngine.computeSuggestion("if redstone ", 12, 1, false, null);
        Assertions.assertTrue(sug2.isPresent());
        Assertions.assertEquals("eq 15 then", sug2.get().ghostText());
    }

    @Test
    public void testSlotsCompletion() {
        Optional<Suggestion> sug = SuggestionEngine.computeSuggestion("input from a slots", 18, 1, false, null);
        Assertions.assertTrue(sug.isPresent());
        Assertions.assertEquals(" 0", sug.get().ghostText());

        Optional<Suggestion> sug2 = SuggestionEngine.computeSuggestion("input from a slots ", 19, 1, false, null);
        Assertions.assertTrue(sug2.isPresent());
        Assertions.assertEquals("0", sug2.get().ghostText());
    }

    @Test
    public void testRetainCompletion() {
        ConnectedBlockInfo chest = new ConnectedBlockInfo(
                new BlockPos(0, 64, 0),
                "minecraft:chest",
                "Chest",
                List.of(),
                List.of("coal"),
                List.of(),
                List.of("ALL"),
                false
        );
        CableContextData context = new CableContextData(new BlockPos(0, 63, 0), List.of(chest));

        Optional<Suggestion> sug = SuggestionEngine.computeSuggestion("input ret", 9, 1, false, context);
        Assertions.assertTrue(sug.isPresent());
        Assertions.assertEquals("ain 1 from chest", sug.get().ghostText());
    }

    @Test
    public void testForgetCompletion() {
        Optional<Suggestion> sug = SuggestionEngine.computeSuggestion("forg", 4, 1, false, null);
        Assertions.assertTrue(sug.isPresent());
        Assertions.assertEquals("et", sug.get().ghostText());
    }

    @Test
    public void testRoundRobinCompletion() {
        Optional<Suggestion> sug = SuggestionEngine.computeSuggestion("round", 5, 1, false, null);
        Assertions.assertTrue(sug.isPresent());
        Assertions.assertEquals(" robin by label", sug.get().ghostText());
    }

    @Test
    public void testExceptCompletion() {
        ConnectedBlockInfo chest = new ConnectedBlockInfo(
                new BlockPos(0, 64, 0),
                "minecraft:chest",
                "Chest",
                List.of(),
                List.of("coal", "raw_iron"),
                List.of(),
                List.of("ALL"),
                false
        );
        CableContextData context = new CableContextData(new BlockPos(0, 63, 0), List.of(chest));

        Optional<Suggestion> sug = SuggestionEngine.computeSuggestion("input * exc", 11, 1, false, context);
        Assertions.assertTrue(sug.isPresent());
        Assertions.assertEquals("ept raw_iron from chest", sug.get().ghostText());
    }

    @Test
    public void testNameProgramCompletion() {
        Optional<Suggestion> sug = SuggestionEngine.computeSuggestion("nam", 3, 0, false, null);
        Assertions.assertTrue(sug.isPresent());
        Assertions.assertEquals("e \"My Program\"", sug.get().ghostText());
    }

    @Test
    public void testEveryRedstonePulseTrigger() {
        Optional<Suggestion> sug = SuggestionEngine.computeSuggestion("every red", 9, 0, false, null);
        Assertions.assertTrue(sug.isPresent());
        Assertions.assertEquals("stone pulse do", sug.get().ghostText());
    }

    @Test
    public void testInputCoalFollowedByOutputRoutesCoalToFurnaceSlots1() {
        ConnectedBlockInfo chest = new ConnectedBlockInfo(
                new BlockPos(0, 64, 0),
                "minecraft:chest",
                "Chest",
                List.of(),
                List.of("coal", "raw_iron"),
                List.of(),
                List.of("ALL"),
                false
        );
        ConnectedBlockInfo furnace = new ConnectedBlockInfo(
                new BlockPos(1, 64, 0),
                "minecraft:furnace",
                "Furnace",
                List.of(),
                List.of(),
                List.of(),
                List.of("ALL"),
                true
        );
        CableContextData context = new CableContextData(new BlockPos(0, 63, 0), List.of(chest, furnace));

        // Previous line: "input coal from chest"
        List<String> previousLines = List.of(
                "every 20 ticks do",
                "    input coal from chest"
        );

        // Current line: type "output " -> MUST suggest coal to furnace slots 1, MUST NOT suggest raw_iron!
        Optional<Suggestion> sug = SuggestionEngine.computeSuggestion("    output ", 11, 2, false, previousLines, context);
        Assertions.assertTrue(sug.isPresent());
        Assertions.assertEquals("coal to furnace slots 1", sug.get().ghostText());
    }

    @Test
    public void testInputFromFurnaceFollowedByOutputRoutesToChestNotFurnace() {
        ConnectedBlockInfo chest = new ConnectedBlockInfo(
                new BlockPos(0, 64, 0),
                "minecraft:chest",
                "Chest",
                List.of(),
                List.of(),
                List.of(),
                List.of("ALL"),
                false
        );
        ConnectedBlockInfo furnace = new ConnectedBlockInfo(
                new BlockPos(1, 64, 0),
                "minecraft:furnace",
                "Furnace",
                List.of(),
                List.of(),
                List.of(),
                List.of("ALL"),
                true
        );
        CableContextData context = new CableContextData(new BlockPos(0, 63, 0), List.of(chest, furnace));

        // Previous line: "input from furnace slots 2"
        List<String> previousLines = List.of(
                "every 20 ticks do",
                "    input from furnace slots 2"
        );

        // Current line: type "output " -> source is furnace, so output MUST route to chest, NEVER to furnace!
        Optional<Suggestion> sug = SuggestionEngine.computeSuggestion("    output ", 11, 2, false, previousLines, context);
        Assertions.assertTrue(sug.isPresent());
        Assertions.assertEquals("to chest", sug.get().ghostText());
    }

    @Test
    public void testChainedOutputAfterRawIronSuggestsRemainingCoal() {
        ConnectedBlockInfo chest = new ConnectedBlockInfo(
                new BlockPos(0, 64, 0),
                "minecraft:chest",
                "Chest",
                List.of(),
                List.of("raw_iron", "coal"),
                List.of(),
                List.of("ALL"),
                false
        );
        ConnectedBlockInfo furnace = new ConnectedBlockInfo(
                new BlockPos(1, 64, 0),
                "minecraft:furnace",
                "Furnace",
                List.of(),
                List.of(),
                List.of(),
                List.of("ALL"),
                true
        );
        CableContextData context = new CableContextData(new BlockPos(0, 63, 0), List.of(chest, furnace));

        // Line 1: input from chest
        // Line 2: output raw_iron to furnace slots 0 (ore has been pushed!)
        List<String> previousLines = List.of(
                "every 20 ticks do",
                "    input from chest",
                "    output raw_iron to furnace slots 0"
        );

        // Line 3: type "output " -> recognizes raw_iron is already transferred, suggests remaining coal into slots 1!
        Optional<Suggestion> sug = SuggestionEngine.computeSuggestion("    output ", 11, 3, false, previousLines, context);
        Assertions.assertTrue(sug.isPresent());
        Assertions.assertEquals("coal to furnace slots 1", sug.get().ghostText());
    }

    @Test
    public void testInputFromFurnaceSuggestsSlots2() {
        ConnectedBlockInfo furnace = new ConnectedBlockInfo(
                new BlockPos(1, 64, 0),
                "minecraft:furnace",
                "Furnace",
                List.of(),
                List.of(),
                List.of(),
                List.of("ALL"),
                true
        );
        CableContextData context = new CableContextData(new BlockPos(0, 63, 0), List.of(furnace));

        // Extract from furnace: type "input from furnace" -> suggests " slots 2" (no redundant bottom side)
        Optional<Suggestion> sug = SuggestionEngine.computeSuggestion("input from furnace", 18, 1, false, context);
        Assertions.assertTrue(sug.isPresent());
        Assertions.assertEquals(" slots 2", sug.get().ghostText());
    }

    @Test
    public void testContextualSlotNumberSuggestions() {
        // Output coal to furnace -> typing slots suggests slot 1
        Optional<Suggestion> sug1 = SuggestionEngine.computeSuggestion("output coal to furnace slots ", 30, 1, false, null);
        Assertions.assertTrue(sug1.isPresent());
        Assertions.assertEquals("1", sug1.get().ghostText());

        // Output ore to furnace -> typing slots suggests slot 0
        Optional<Suggestion> sug2 = SuggestionEngine.computeSuggestion("output raw_iron to furnace slots ", 34, 1, false, null);
        Assertions.assertTrue(sug2.isPresent());
        Assertions.assertEquals("0", sug2.get().ghostText());

        // Extract from furnace -> typing slots suggests slot 2
        Optional<Suggestion> sug3 = SuggestionEngine.computeSuggestion("input from furnace slots ", 25, 1, false, null);
        Assertions.assertTrue(sug3.isPresent());
        Assertions.assertEquals("2", sug3.get().ghostText());
    }

    @Test
    public void testInscriberRoutingAndExtractionSlots() {
        ConnectedBlockInfo inscriber = new ConnectedBlockInfo(
                new BlockPos(2, 64, 0),
                "appliedenergistics2:inscriber",
                "inscriber",
                List.of("inscriber"),
                List.of(),
                List.of(),
                List.of("ALL"),
                true,
                4,
                true,
                false
        );
        CableContextData context = new CableContextData(new BlockPos(0, 63, 0), List.of(inscriber));

        // Extract finished processor from inscriber -> slot 3
        Optional<Suggestion> sugExtract = SuggestionEngine.computeSuggestion("input from inscriber", 20, 1, false, context);
        Assertions.assertTrue(sugExtract.isPresent());
        Assertions.assertEquals(" slots 3", sugExtract.get().ghostText());

        // Top press mold -> slots 0
        Optional<Suggestion> sugPress = SuggestionEngine.computeSuggestion("output calculation_circuit_press to inscriber", 47, 1, false, context);
        Assertions.assertTrue(sugPress.isPresent());
        Assertions.assertEquals(" slots 0", sugPress.get().ghostText());

        // Bottom silicon press mold -> slots 2
        Optional<Suggestion> sugBottomPress = SuggestionEngine.computeSuggestion("output silicon_press to inscriber", 34, 1, false, context);
        Assertions.assertTrue(sugBottomPress.isPresent());
        Assertions.assertEquals(" slots 2", sugBottomPress.get().ghostText());

        // Middle raw material (diamond, silicon, gold, certus) -> slots 1
        Optional<Suggestion> sugMaterial = SuggestionEngine.computeSuggestion("output diamond to inscriber", 27, 1, false, context);
        Assertions.assertTrue(sugMaterial.isPresent());
        Assertions.assertEquals(" slots 1", sugMaterial.get().ghostText());
    }

    @Test
    public void testMekanismInfuserRoutingAndSlots() {
        ConnectedBlockInfo infuser = new ConnectedBlockInfo(
                new BlockPos(3, 64, 0),
                "mekanism:metallurgic_infuser",
                "infuser",
                List.of("infuser"),
                List.of(),
                List.of(),
                List.of("ALL"),
                true,
                3,
                true,
                false
        );
        CableContextData context = new CableContextData(new BlockPos(0, 63, 0), List.of(infuser));

        // Extract product from infuser -> slots 2
        Optional<Suggestion> sugExtract = SuggestionEngine.computeSuggestion("input from infuser", 18, 1, false, context);
        Assertions.assertTrue(sugExtract.isPresent());
        Assertions.assertEquals(" slots 2", sugExtract.get().ghostText());

        // Infuse material (redstone, carbon, diamond) -> slots 0 (infuse buffer)
        Optional<Suggestion> sugInfuse = SuggestionEngine.computeSuggestion("output redstone to infuser", 26, 1, false, context);
        Assertions.assertTrue(sugInfuse.isPresent());
        Assertions.assertEquals(" slots 0", sugInfuse.get().ghostText());

        // Base metal (iron_ingot, enriched_iron) -> slots 1
        Optional<Suggestion> sugBase = SuggestionEngine.computeSuggestion("output iron_ingot to infuser", 28, 1, false, context);
        Assertions.assertTrue(sugBase.isPresent());
        Assertions.assertEquals(" slots 1", sugBase.get().ghostText());
    }

    @Test
    public void testBrewingStandRoutingAndSlots() {
        ConnectedBlockInfo brewing = new ConnectedBlockInfo(
                new BlockPos(4, 64, 0),
                "minecraft:brewing_stand",
                "brewing_stand",
                List.of("brewing_stand"),
                List.of(),
                List.of(),
                List.of("ALL"),
                true,
                5,
                false,
                false
        );
        CableContextData context = new CableContextData(new BlockPos(0, 63, 0), List.of(brewing));

        // Extract brewed potions -> slots 0-2
        Optional<Suggestion> sugExtract = SuggestionEngine.computeSuggestion("input from brewing_stand", 24, 1, false, context);
        Assertions.assertTrue(sugExtract.isPresent());
        Assertions.assertEquals(" slots 0-2", sugExtract.get().ghostText());

        // Blaze powder fuel -> slots 4
        Optional<Suggestion> sugFuel = SuggestionEngine.computeSuggestion("output blaze_powder to brewing_stand", 37, 1, false, context);
        Assertions.assertTrue(sugFuel.isPresent());
        Assertions.assertEquals(" slots 4", sugFuel.get().ghostText());

        // Water bottles / potions -> slots 0-2
        Optional<Suggestion> sugBottles = SuggestionEngine.computeSuggestion("output water_bottle to brewing_stand", 36, 1, false, context);
        Assertions.assertTrue(sugBottles.isPresent());
        Assertions.assertEquals(" slots 0-2", sugBottles.get().ghostText());

        // Brewing ingredient (nether wart) -> slots 3
        Optional<Suggestion> sugIngredient = SuggestionEngine.computeSuggestion("output nether_wart to brewing_stand", 35, 1, false, context);
        Assertions.assertTrue(sugIngredient.isPresent());
        Assertions.assertEquals(" slots 3", sugIngredient.get().ghostText());
    }

    @Test
    public void testCrusherAndSingleInputMachines() {
        ConnectedBlockInfo crusher = new ConnectedBlockInfo(
                new BlockPos(5, 64, 0),
                "mekanism:crusher",
                "crusher",
                List.of("crusher"),
                List.of(),
                List.of(),
                List.of("ALL"),
                true,
                2,
                true,
                false
        );
        CableContextData context = new CableContextData(new BlockPos(0, 63, 0), List.of(crusher));

        // Extract crushed output -> slots 1
        Optional<Suggestion> sugExtract = SuggestionEngine.computeSuggestion("input from crusher", 18, 1, false, context);
        Assertions.assertTrue(sugExtract.isPresent());
        Assertions.assertEquals(" slots 1", sugExtract.get().ghostText());
    }

    @Test
    public void testFluidAndEnergySuggestions() {
        ConnectedBlockInfo tank = new ConnectedBlockInfo(
                new BlockPos(6, 64, 0),
                "minecraft:lava_cauldron",
                "tank",
                List.of("tank"),
                List.of(),
                List.of("minecraft:lava"),
                List.of("ALL"),
                false,
                0,
                false,
                true
        );
        ConnectedBlockInfo generator = new ConnectedBlockInfo(
                new BlockPos(7, 64, 0),
                "mekanism:generator",
                "generator",
                List.of("generator"),
                List.of(),
                List.of(),
                List.of("ALL"),
                true,
                0,
                true,
                false
        );
        CableContextData context = new CableContextData(new BlockPos(0, 63, 0), List.of(tank, generator));

        // Type "input fluid::" -> recognizes lava from tank
        Optional<Suggestion> sugFluid = SuggestionEngine.computeSuggestion("input fluid::", 13, 1, false, context);
        Assertions.assertTrue(sugFluid.isPresent());
        Assertions.assertEquals("minecraft:lava from tank", sugFluid.get().ghostText());

        // Type "input fe::" -> suggests energy extraction from generator
        Optional<Suggestion> sugFe = SuggestionEngine.computeSuggestion("input fe::", 10, 1, false, context);
        Assertions.assertTrue(sugFe.isPresent());
        Assertions.assertEquals(" from generator", sugFe.get().ghostText());
    }

    @Test
    public void testDynamicMachineInputOutputSlotDetection() {
        ConnectedBlockInfo chest = new ConnectedBlockInfo(
                new BlockPos(0, 64, 0),
                "minecraft:chest",
                "chest",
                List.of("chest"),
                List.of("glass"),
                List.of(),
                List.of("ALL"),
                false
        );
        ConnectedBlockInfo chamber = new ConnectedBlockInfo(
                new BlockPos(8, 64, 0),
                "industrialforegoing:dissolution_chamber",
                "chamber",
                List.of("chamber"),
                List.of(),
                List.of(),
                List.of("ALL"),
                true,
                9,
                true,
                true,
                List.of(0, 1, 2, 3, 4, 5, 6, 7), // Input slots: 0-7
                List.of(8),                      // Output slots: 8
                List.of("top", "north"),
                List.of("bottom")
        );
        CableContextData context = new CableContextData(new BlockPos(0, 63, 0), List.of(chest, chamber));

        // Extract from machine -> automatically identifies output slot 8!
        Optional<Suggestion> sugExtract = SuggestionEngine.computeSuggestion("input from chamber", 18, 1, false, context);
        Assertions.assertTrue(sugExtract.isPresent());
        Assertions.assertEquals(" slots 8", sugExtract.get().ghostText());

        // Continue typing slots -> automatically suggests slot 8
        Optional<Suggestion> sugExtractSlot = SuggestionEngine.computeSuggestion("input from chamber slots ", 25, 1, false, context);
        Assertions.assertTrue(sugExtractSlot.isPresent());
        Assertions.assertEquals("8", sugExtractSlot.get().ghostText());

        // Insert into machine -> automatically identifies input slot 0!
        Optional<Suggestion> sugInsert = SuggestionEngine.computeSuggestion("output glass to chamber", 23, 1, false, context);
        Assertions.assertTrue(sugInsert.isPresent());
        Assertions.assertEquals(" slots 0", sugInsert.get().ghostText());

        // Continue typing slots when inserting -> automatically suggests slot 0
        Optional<Suggestion> sugInsertSlot = SuggestionEngine.computeSuggestion("output glass to chamber slots ", 30, 1, false, context);
        Assertions.assertTrue(sugInsertSlot.isPresent());
        Assertions.assertEquals("0", sugInsertSlot.get().ghostText());

        // Check sequential context: After extracting from chamber, output MUST route back to chest, NEVER to chamber
        List<String> prevLines = List.of(
                "every 20 ticks do",
                "    input from chamber slots 8"
        );
        Optional<Suggestion> sugChained = SuggestionEngine.computeSuggestion("    output ", 11, 2, false, prevLines, context);
        Assertions.assertTrue(sugChained.isPresent());
        Assertions.assertEquals("glass to chest", sugChained.get().ghostText());
    }
}
