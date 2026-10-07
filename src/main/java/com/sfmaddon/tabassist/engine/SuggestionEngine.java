package com.sfmaddon.tabassist.engine;

import com.sfmaddon.tabassist.context.CableContextData;
import com.sfmaddon.tabassist.context.ConnectedBlockInfo;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class SuggestionEngine {

    public static class BlockContext {
        public List<String> inputItems = new ArrayList<>();
        public List<String> inputLabels = new ArrayList<>();
        public List<String> outputItems = new ArrayList<>();
        public List<String> outputLabels = new ArrayList<>();
        public boolean hasInputFromFurnace = false;
        public boolean hasInputFromChest = false;
        public @Nullable String lastInputItem = null;
        public @Nullable String lastInputLabel = null;
    }

    public static BlockContext parseBlockContext(@Nullable List<String> previousLines) {
        BlockContext ctx = new BlockContext();
        if (previousLines == null || previousLines.isEmpty()) {
            return ctx;
        }

        int startIndex = 0;
        for (int i = previousLines.size() - 1; i >= 0; i--) {
            String line = previousLines.get(i).trim().toUpperCase(Locale.ROOT);
            if (line.startsWith("EVERY") && line.endsWith("DO")) {
                startIndex = i;
                break;
            }
        }

        for (int i = startIndex; i < previousLines.size(); i++) {
            String line = previousLines.get(i).trim();
            String upper = line.toUpperCase(Locale.ROOT);

            if (upper.equals("FORGET") || upper.startsWith("FORGET ")) {
                ctx.inputItems.clear();
                ctx.inputLabels.clear();
                ctx.lastInputItem = null;
                ctx.lastInputLabel = null;
                continue;
            }

            if (upper.startsWith("INPUT") || upper.contains(" INPUT ")) {
                String src = extractLabelFromClause(line, "FROM");
                if (!src.isEmpty()) {
                    ctx.inputLabels.add(src);
                    ctx.lastInputLabel = src;
                    if (src.toLowerCase(Locale.ROOT).contains("furnace")) {
                        ctx.hasInputFromFurnace = true;
                    }
                    if (src.toLowerCase(Locale.ROOT).contains("chest")) {
                        ctx.hasInputFromChest = true;
                    }
                }
                String item = extractItemFromInput(line);
                if (item != null && !item.isEmpty()) {
                    ctx.inputItems.add(item);
                    ctx.lastInputItem = item;
                }
            } else if (upper.startsWith("OUTPUT") || upper.contains(" OUTPUT ")) {
                String dest = extractLabelFromClause(line, "TO");
                if (!dest.isEmpty()) {
                    ctx.outputLabels.add(dest);
                }
                String item = extractItemFromOutput(line);
                if (item != null && !item.isEmpty()) {
                    ctx.outputItems.add(item);
                }
            }
        }

        return ctx;
    }

    public static @Nullable String extractItemFromInput(String line) {
        String upper = line.toUpperCase(Locale.ROOT);
        int inputIdx = upper.indexOf("INPUT ");
        int fromIdx = upper.indexOf(" FROM");
        if (inputIdx != -1 && fromIdx != -1 && fromIdx > inputIdx + 5) {
            String between = line.substring(inputIdx + 6, fromIdx).trim();
            if (between.isEmpty()) return null;
            String[] tokens = between.split("\\s+");
            for (int i = tokens.length - 1; i >= 0; i--) {
                String t = tokens[i];
                if (!t.matches("\\d+") && !t.equalsIgnoreCase("retain") && !t.equalsIgnoreCase("each") && !t.equals("*")) {
                    return t.replace("\"", "");
                }
            }
        }
        return null;
    }

    public static Optional<Suggestion> computeSuggestion(
            String currentLine,
            int cursorColumn,
            int lineIndex,
            boolean isDocumentEmpty,
            @Nullable CableContextData context
    ) {
        return computeSuggestion(currentLine, cursorColumn, lineIndex, isDocumentEmpty, null, context);
    }

    public static Optional<Suggestion> computeSuggestion(
            String currentLine,
            int cursorColumn,
            int lineIndex,
            boolean isDocumentEmpty,
            @Nullable List<String> previousLines,
            @Nullable CableContextData context
    ) {
        if (cursorColumn < 0 || cursorColumn > currentLine.length()) {
            cursorColumn = currentLine.length();
        }

        String prefix = currentLine.substring(0, cursorColumn);
        String trimmedPrefix = prefix.trim();
        String upperPrefix = prefix.toUpperCase(Locale.ROOT);
        String upperTrimmed = trimmedPrefix.toUpperCase(Locale.ROOT);

        boolean isUpperCase = !trimmedPrefix.isEmpty() && Character.isUpperCase(trimmedPrefix.charAt(0));
        BlockContext blockCtx = parseBlockContext(previousLines);

        // 1. Empty line
        if (trimmedPrefix.isEmpty()) {
            if (lineIndex == 0 && isDocumentEmpty) {
                return Optional.of(Suggestion.append(isUpperCase ? "EVERY 20 TICKS DO" : "every 20 ticks do"));
            }
            return Optional.empty();
        }

        // 2. NAME statement
        if (upperTrimmed.equals("NAM") || upperTrimmed.equals("NAME")) {
            String nameWord = isUpperCase ? "NAME \"My Program\"" : "name \"My Program\"";
            return Optional.of(Suggestion.append(nameWord.substring(upperTrimmed.length())));
        }
        if (upperTrimmed.equals("NAME ")) {
            return Optional.of(Suggestion.append("\"My Program\""));
        }

        // 3. EVERY trigger
        if (upperTrimmed.equals("E") || upperTrimmed.equals("EV") || upperTrimmed.equals("EVE") || upperTrimmed.equals("EVER")) {
            String full = isUpperCase ? "EVERY 20 TICKS DO" : "every 20 ticks do";
            return Optional.of(Suggestion.append(full.substring(upperTrimmed.length())));
        }
        if (upperTrimmed.equals("EVERY")) {
            return Optional.of(Suggestion.append(isUpperCase ? " 20 TICKS DO" : " 20 ticks do"));
        }
        if (upperTrimmed.equals("EVERY 20")) {
            return Optional.of(Suggestion.append(isUpperCase ? " TICKS DO" : " ticks do"));
        }
        if (upperTrimmed.equals("EVERY 20 TICKS")) {
            return Optional.of(Suggestion.append(isUpperCase ? " DO" : " do"));
        }
        if (upperTrimmed.equals("EVERY RED") || upperTrimmed.equals("EVERY REDSTONE")) {
            String pulseStr = isUpperCase ? " REDSTONE PULSE DO" : " redstone pulse do";
            return Optional.of(Suggestion.append(pulseStr.substring(upperTrimmed.length() - 5)));
        }
        if (upperTrimmed.equals("EVERY REDSTONE PULSE")) {
            return Optional.of(Suggestion.append(isUpperCase ? " DO" : " do"));
        }

        // 4. FORGET statement
        if (upperTrimmed.equals("FO") || upperTrimmed.equals("FOR") || upperTrimmed.equals("FORG") || upperTrimmed.equals("FORGE")) {
            String forgetWord = isUpperCase ? "FORGET" : "forget";
            return Optional.of(Suggestion.append(forgetWord.substring(upperTrimmed.length())));
        }

        // 5. ROUND ROBIN
        if (upperTrimmed.endsWith("ROU") || upperTrimmed.endsWith("ROUN") || upperTrimmed.endsWith("ROUND")) {
            String full = isUpperCase ? "ROUND ROBIN BY LABEL" : "round robin by label";
            int idx = upperTrimmed.lastIndexOf("ROU");
            String sub = upperTrimmed.substring(idx);
            return Optional.of(Suggestion.append(full.substring(sub.length())));
        }
        if (upperTrimmed.endsWith("ROUND ROBIN")) {
            String byLabel = isUpperCase ? "BY LABEL" : "by label";
            return Optional.of(Suggestion.append(prefix.endsWith(" ") ? byLabel : " " + byLabel));
        }
        if (upperTrimmed.endsWith("ROUND ROBIN BY")) {
            String labelWord = isUpperCase ? "LABEL" : "label";
            return Optional.of(Suggestion.append(prefix.endsWith(" ") ? labelWord : " " + labelWord));
        }

        // 6. IF / ELSE IF / ELSE conditionals
        if (upperTrimmed.equals("EL") || upperTrimmed.equals("ELS")) {
            String elseWord = isUpperCase ? "ELSE" : "else";
            return Optional.of(Suggestion.append(elseWord.substring(upperTrimmed.length())));
        }
        if (upperTrimmed.equals("ELSE")) {
            String ifWord = isUpperCase ? "IF " : "if ";
            return Optional.of(Suggestion.append(prefix.endsWith(" ") ? ifWord : " " + ifWord));
        }
        if (upperTrimmed.equals("ELSE IF")) {
            String cond = buildIfCondition(context, isUpperCase);
            return Optional.of(Suggestion.append(prefix.endsWith(" ") ? cond : " " + cond));
        }

        if (upperTrimmed.equals("IF")) {
            String cond = buildIfCondition(context, isUpperCase);
            return Optional.of(Suggestion.append(prefix.endsWith(" ") ? cond : " " + cond));
        }

        // Redstone conditions in IF
        if (upperTrimmed.startsWith("IF RED") || upperTrimmed.startsWith("ELSE IF RED")) {
            if (upperTrimmed.endsWith("RED")) {
                return Optional.of(Suggestion.append(isUpperCase ? "STONE EQ 15 THEN" : "stone eq 15 then"));
            }
            if (upperTrimmed.endsWith("REDSTONE")) {
                String eqThen = isUpperCase ? "EQ 15 THEN" : "eq 15 then";
                return Optional.of(Suggestion.append(prefix.endsWith(" ") ? eqThen : " " + eqThen));
            }
        }
        if (upperTrimmed.endsWith("REDSTONE EQ") || upperTrimmed.endsWith("REDSTONE GT")
                || upperTrimmed.endsWith("REDSTONE GE") || upperTrimmed.endsWith("REDSTONE LT")
                || upperTrimmed.endsWith("REDSTONE LE")) {
            return Optional.of(Suggestion.append(prefix.endsWith(" ") ? (isUpperCase ? "15 THEN" : "15 then") : (isUpperCase ? " 15 THEN" : " 15 then")));
        }
        if (prefix.matches("(?i).*(IF|ELSE IF)\\s+REDSTONE\\s+(EQ|GT|GE|LT|LE|==|>=|<=|>|<)\\s+\\d+$")) {
            return Optional.of(Suggestion.append(isUpperCase ? " THEN" : " then"));
        }

        // HAS conditions in IF
        if (upperTrimmed.endsWith(" HAS")) {
            String rem = buildHasRemainder(context, prefix, isUpperCase);
            return Optional.of(Suggestion.append(prefix.endsWith(" ") ? rem : " " + rem));
        }
        if (upperTrimmed.endsWith(" HAS GT") || upperTrimmed.endsWith(" HAS GE")
                || upperTrimmed.endsWith(" HAS LT") || upperTrimmed.endsWith(" HAS LE")
                || upperTrimmed.endsWith(" HAS EQ")) {
            String itemRem = buildHasNumberAndItem(context, prefix, isUpperCase);
            return Optional.of(Suggestion.append(prefix.endsWith(" ") ? itemRem : " " + itemRem));
        }
        if (prefix.matches("(?i).*(IF|ELSE IF)\\s+.*\\s+HAS\\s+(GT|GE|LT|LE|EQ|>|<|>=|<=|=)\\s+\\d+$")) {
            String itemRem = buildHasItemOnly(context, prefix, isUpperCase);
            return Optional.of(Suggestion.append(" " + itemRem));
        }
        if (prefix.matches("(?i).*(IF|ELSE IF)\\s+.*\\s+HAS\\s+(GT|GE|LT|LE|EQ|>|<|>=|<=|=)\\s+\\d+\\s+[a-zA-Z0-9_:]+$")) {
            return Optional.of(Suggestion.append(isUpperCase ? " THEN" : " then"));
        }
        if (upperTrimmed.endsWith(" TH")) {
            return Optional.of(Suggestion.append(isUpperCase ? "EN" : "en"));
        }
        if (upperTrimmed.endsWith(" THE")) {
            return Optional.of(Suggestion.append(isUpperCase ? "N" : "n"));
        }

        // 7. SLOTS specification for machines / furnaces
        if (upperTrimmed.endsWith("SLO")) {
            String slotsWord = isUpperCase ? "SLOTS" : "slots";
            return Optional.of(Suggestion.append(slotsWord.substring(3)));
        }
        if (upperTrimmed.endsWith("SLOT") || upperTrimmed.endsWith("SLOTS")) {
            boolean hasSpace = prefix.endsWith(" ");
            String slotNum = getContextualSlotNumber(prefix, context, blockCtx);
            String sp = hasSpace ? "" : (upperTrimmed.endsWith("SLOTS") ? " " : "s ");
            return Optional.of(Suggestion.append(sp + slotNum));
        }
        if (upperTrimmed.endsWith("SLOTS 0-") || upperTrimmed.endsWith("SLOT 0-")) {
            return Optional.of(Suggestion.append("8"));
        }

        // 8. RETAIN limit
        if (upperTrimmed.endsWith("RET") || upperTrimmed.endsWith("RETA") || upperTrimmed.endsWith("RETAIN")) {
            String retainWord = isUpperCase ? "RETAIN 1" : "retain 1";
            int rIdx = upperTrimmed.lastIndexOf("RET");
            String sub = upperTrimmed.substring(rIdx);
            String rem = retainWord.substring(sub.length());
            if (upperTrimmed.endsWith("RETAIN")) {
                rem = prefix.endsWith(" ") ? "1" : " 1";
            }
            if (upperPrefix.contains("INPUT ") && !upperPrefix.contains(" FROM")) {
                String inLabel = findFirstInputLabel(context);
                String fromWord = isUpperCase ? " FROM " : " from ";
                return Optional.of(Suggestion.append(rem + fromWord + formatLabel(inLabel, false)));
            }
            if (upperPrefix.contains("OUTPUT ") && !upperPrefix.contains(" TO")) {
                String destLabel = findDestinationLabel(context, prefix, blockCtx);
                String toWord = isUpperCase ? " TO " : " to ";
                return Optional.of(Suggestion.append(rem + toWord + formatLabel(destLabel, false)));
            }
            return Optional.of(Suggestion.append(rem));
        }

        // 9. EXCEPT resource exclusion
        if (upperTrimmed.endsWith("EXC") || upperTrimmed.endsWith("EXCE") || upperTrimmed.endsWith("EXCEP") || upperTrimmed.endsWith("EXCEPT")) {
            String excWord = isUpperCase ? "EXCEPT" : "except";
            int eIdx = upperTrimmed.lastIndexOf("EXC");
            String sub = upperTrimmed.substring(eIdx);
            String rem = excWord.substring(sub.length());
            String excItem = findExclusionItem(context);
            if (upperTrimmed.endsWith("EXCEPT")) {
                rem = prefix.endsWith(" ") ? excItem : " " + excItem;
            } else {
                rem = rem + " " + excItem;
            }
            String fromWord = isUpperCase ? " FROM " : " from ";
            String inLabel = findFirstInputLabel(context);
            if (upperPrefix.contains("INPUT ") && !upperPrefix.contains(" FROM")) {
                return Optional.of(Suggestion.append(rem + fromWord + formatLabel(inLabel, false)));
            }
            return Optional.of(Suggestion.append(rem));
        }

        // 10. EACH qualifier
        if (upperTrimmed.endsWith("EAC") || upperTrimmed.endsWith("EACH")) {
            String eachWord = isUpperCase ? "EACH" : "each";
            int eIdx = upperTrimmed.lastIndexOf("EAC");
            String sub = upperTrimmed.substring(eIdx);
            String rem = eachWord.substring(sub.length());
            if (upperPrefix.contains("FROM ")) {
                String inLabel = findFirstInputLabel(context);
                return Optional.of(Suggestion.append(rem + " " + formatLabel(inLabel, false)));
            }
            if (upperPrefix.contains("TO ")) {
                String destLabel = findDestinationLabel(context, prefix, blockCtx);
                return Optional.of(Suggestion.append(rem + " " + formatLabel(destLabel, false)));
            }
            return Optional.of(Suggestion.append(rem + (isUpperCase ? " SIDE" : " side")));
        }
        if (upperPrefix.endsWith("FROM EACH ") || upperPrefix.endsWith("from each ")) {
            String inLabel = findFirstInputLabel(context);
            return Optional.of(Suggestion.append(formatLabel(inLabel, false)));
        }
        if (upperPrefix.endsWith("TO EACH ") || upperPrefix.endsWith("to each ")) {
            String destLabel = findDestinationLabel(context, prefix, blockCtx);
            return Optional.of(Suggestion.append(formatLabel(destLabel, false)));
        }

        // 11. Energy & Fluids
        if (upperTrimmed.endsWith("FE:") || upperTrimmed.endsWith("FE::")) {
            String rem = upperTrimmed.endsWith("FE:") ? ": " : (prefix.endsWith(" ") ? "" : " ");
            if (upperPrefix.contains("OUTPUT ")) {
                String toWord = isUpperCase ? "TO " : "to ";
                String destLabel = findEnergyDestLabel(context, prefix, blockCtx);
                return Optional.of(Suggestion.append(rem + toWord + formatLabel(destLabel, false)));
            }
            String fromWord = isUpperCase ? "FROM " : "from ";
            String inLabel = findEnergySourceLabel(context);
            return Optional.of(Suggestion.append(rem + fromWord + formatLabel(inLabel, false)));
        }
        if (upperTrimmed.endsWith("FLUID") || upperTrimmed.endsWith("FLUID:") || upperTrimmed.endsWith("FLUID::")) {
            String colonPart = upperTrimmed.endsWith("FLUID::") ? "" : (upperTrimmed.endsWith("FLUID:") ? ":" : "::");
            String fluidId = findFirstFluid(context);
            String fromWord = isUpperCase ? " FROM " : " from ";
            String inLabel = findFluidSourceLabel(context);
            if (upperPrefix.contains("OUTPUT ")) {
                String toWord = isUpperCase ? " TO " : " to ";
                String destLabel = findFluidDestLabel(context, prefix, blockCtx);
                return Optional.of(Suggestion.append(colonPart + fluidId + toWord + formatLabel(destLabel, false)));
            }
            return Optional.of(Suggestion.append(colonPart + fluidId + fromWord + formatLabel(inLabel, false)));
        }

        // 12. Handle INPUT statement
        // 12a. User typing INPUT prefix (I, IN, INP, INPU, INPUT)
        if (upperTrimmed.equals("I") || upperTrimmed.equals("IN") || upperTrimmed.equals("INP") || upperTrimmed.equals("INPU") || upperTrimmed.equals("INPUT")) {
            String keyword = isUpperCase ? "INPUT" : "input";
            String fromWord = isUpperCase ? "FROM" : "from";
            String keywordPart = keyword.substring(Math.min(5, upperTrimmed.length()));
            String spacePrefix = keywordPart.isEmpty() ? " " : keywordPart + " ";

            String inLabel = findFirstInputLabel(context);
            return Optional.of(Suggestion.append(spacePrefix + fromWord + " " + formatLabel(inLabel, false)));
        }

        // 12b. After "INPUT " with trailing space
        if (upperPrefix.endsWith("INPUT ")) {
            String fromWord = isUpperCase ? "FROM" : "from";
            String inLabel = findFirstInputLabel(context);
            return Optional.of(Suggestion.append(fromWord + " " + formatLabel(inLabel, false)));
        }

        // 12c. Typing after INPUT (item name, count, or FROM)
        if (upperPrefix.contains("INPUT ") && !upperPrefix.contains(" FROM") && !upperPrefix.contains(" FROM ") && !upperPrefix.endsWith("FROM")) {
            int inputIdx = upperPrefix.lastIndexOf("INPUT ");
            String afterInput = prefix.substring(inputIdx + 6).trim();

            if (prefix.endsWith(" ") && afterInput.matches(".*\\d+$")) {
                String fromWord = isUpperCase ? "FROM" : "from";
                String inLabel = findFirstInputLabel(context);
                return Optional.of(Suggestion.append(fromWord + " " + formatLabel(inLabel, false)));
            }

            if (upperTrimmed.endsWith("F") || upperTrimmed.endsWith("FR") || upperTrimmed.endsWith("FRO")) {
                int fIdx = upperTrimmed.lastIndexOf('F');
                String sub = upperTrimmed.substring(fIdx);
                String fromFull = isUpperCase ? "FROM" : "from";
                String rem = fromFull.substring(sub.length());
                String inLabel = findFirstInputLabel(context);
                return Optional.of(Suggestion.append(rem + " " + formatLabel(inLabel, false)));
            }

            if (context != null && !afterInput.isEmpty() && !afterInput.contains("\"")) {
                for (ConnectedBlockInfo b : context.connectedBlocks()) {
                    for (String item : b.sampleItemIds()) {
                        if (itemMatches(item, afterInput)) {
                            String remItem = getItemRemainder(item, afterInput);
                            String fromWord = isUpperCase ? "FROM" : "from";
                            return Optional.of(Suggestion.append(remItem + " " + fromWord + " " + formatLabel(b.getPreferredLabel(), false)));
                        }
                    }
                }
            }
        }

        // 13. Completing FROM keyword
        if (upperTrimmed.endsWith("FROM")) {
            String inLabel = findFirstInputLabel(context);
            return Optional.of(Suggestion.append(" " + formatLabel(inLabel, false)));
        }

        // 14. Completing label name after FROM
        int lastFromQuote = prefix.lastIndexOf("FROM \"");
        if (lastFromQuote == -1) lastFromQuote = prefix.lastIndexOf("from \"");
        if (lastFromQuote != -1 && lastFromQuote + 6 <= prefix.length()) {
            String partialLabel = prefix.substring(lastFromQuote + 6);
            if (!partialLabel.contains("\"")) {
                if (context != null) {
                    for (String label : context.getAllCandidateLabels()) {
                        if (label.toLowerCase(Locale.ROOT).startsWith(partialLabel.toLowerCase(Locale.ROOT)) && !label.equalsIgnoreCase(partialLabel)) {
                            return Optional.of(Suggestion.append(label.substring(partialLabel.length()) + "\""));
                        }
                    }
                }
            }
        }

        int lastFromNoQuote = upperPrefix.lastIndexOf("FROM ");
        if (lastFromNoQuote != -1 && lastFromQuote == -1) {
            String partialLabel = prefix.substring(lastFromNoQuote + 5).trim();
            if (!partialLabel.isEmpty()) {
                if (context != null) {
                    for (String label : context.getAllCandidateLabels()) {
                        if (label.toLowerCase(Locale.ROOT).startsWith(partialLabel.toLowerCase(Locale.ROOT)) && !label.equalsIgnoreCase(partialLabel)) {
                            return Optional.of(Suggestion.append(label.substring(partialLabel.length())));
                        }
                    }
                }
            }
        }

        // 15. After completed FROM label (e.g., "input from furnace", "input from inscriber")
        if (prefix.matches("(?i).*FROM\\s+(\"[^\"]+\"|[a-zA-Z0-9_]+)\\s*$")) {
            String sourceLabel = extractLabelFromClause(prefix, "FROM");
            ConnectedBlockInfo srcBlock = findBlockByLabelOrCandidate(context, sourceLabel);
            if ((srcBlock != null && srcBlock.isMachine()) || isKnownMachineLabel(sourceLabel)) {
                if (!upperPrefix.contains("SLOT")) {
                    String slot = getExtractionSlot(srcBlock, sourceLabel);
                    return Optional.of(Suggestion.append(isUpperCase ? " SLOTS " + slot : " slots " + slot));
                }
            }
        }

        // 16. Handle OUTPUT statement
        // 16a. After "OUTPUT " with trailing space
        if (upperPrefix.endsWith("OUTPUT ")) {
            String toWord = isUpperCase ? "TO" : "to";
            String destLabel = findDestinationLabel(context, prefix, blockCtx);
            ConnectedBlockInfo destBlock = findBlockByLabelOrCandidate(context, destLabel);
            String bestItem = findBestItemForDestination(context, destBlock, blockCtx);
            String itemPart = (bestItem != null) ? bestItem + " " : "";
            String sideBonus = getSideForDestination(destBlock, bestItem, isUpperCase);

            return Optional.of(Suggestion.append(itemPart + toWord + " " + formatLabel(destLabel, false) + sideBonus));
        }

        // 16b. Partial typing OUTPUT (O, OU, OUT, OUTP, OUTPU, OUTPUT)
        int lastOutIdx = upperTrimmed.lastIndexOf('O');
        if (lastOutIdx != -1) {
            String candidate = upperTrimmed.substring(lastOutIdx);
            if ("OUTPUT".startsWith(candidate) && !candidate.equals("O")) {
                String keyword = isUpperCase ? "OUTPUT" : "output";
                String toWord = isUpperCase ? "TO" : "to";
                String keywordPart = keyword.substring(candidate.length());
                String space = keywordPart.isEmpty() ? " " : keywordPart + " ";

                String destLabel = findDestinationLabel(context, prefix, blockCtx);
                ConnectedBlockInfo destBlock = findBlockByLabelOrCandidate(context, destLabel);
                String bestItem = findBestItemForDestination(context, destBlock, blockCtx);
                String itemPart = (bestItem != null) ? bestItem + " " : "";
                String sideBonus = getSideForDestination(destBlock, bestItem, isUpperCase);

                return Optional.of(Suggestion.append(space + itemPart + toWord + " " + formatLabel(destLabel, false) + sideBonus));
            }
        }

        // 16c. Typing item after OUTPUT (e.g., output raw... or output c...)
        if (upperPrefix.contains("OUTPUT ") && !upperPrefix.contains(" TO") && !upperPrefix.contains(" TO ") && !upperPrefix.endsWith("TO")) {
            int outIdx = upperPrefix.lastIndexOf("OUTPUT ");
            String afterOut = prefix.substring(outIdx + 7).trim();

            if (prefix.endsWith(" ") && afterOut.matches(".*\\d+$")) {
                String toWord = isUpperCase ? "TO" : "to";
                String destLabel = findDestinationLabel(context, prefix, blockCtx);
                ConnectedBlockInfo destBlock = findBlockByLabelOrCandidate(context, destLabel);
                String sideBonus = getSideForDestination(destBlock, null, isUpperCase);
                return Optional.of(Suggestion.append(toWord + " " + formatLabel(destLabel, false) + sideBonus));
            }

            if (context != null && !afterOut.isEmpty() && !afterOut.contains("\"")) {
                for (ConnectedBlockInfo b : context.connectedBlocks()) {
                    for (String item : b.sampleItemIds()) {
                        if (itemMatches(item, afterOut)) {
                            String remItem = getItemRemainder(item, afterOut);
                            String toWord = isUpperCase ? "TO" : "to";
                            String destLabel = findDestinationLabel(context, prefix, blockCtx);
                            ConnectedBlockInfo destBlock = findBlockByLabelOrCandidate(context, destLabel);
                            String sideBonus = getSideForDestination(destBlock, item, isUpperCase);
                            return Optional.of(Suggestion.append(remItem + " " + toWord + " " + formatLabel(destLabel, false) + sideBonus));
                        }
                    }
                }
            }
        }

        // 16d. After "OUTPUT <item> " or "OUTPUT <item>" item typed, suggest TO
        if (upperPrefix.contains("OUTPUT ") && !upperPrefix.contains(" TO") && !upperPrefix.contains(" TO ")) {
            int outIdx = upperPrefix.lastIndexOf("OUTPUT ");
            String afterOut = prefix.substring(outIdx + 7).trim();
            if (!afterOut.isEmpty() && (isKnownItem(context, afterOut) || afterOut.matches("^[a-zA-Z0-9_:]+$"))) {
                String toWord = isUpperCase ? "TO" : "to";
                String destLabel = findDestinationLabel(context, prefix, blockCtx);
                ConnectedBlockInfo destBlock = findBlockByLabelOrCandidate(context, destLabel);
                String sideBonus = getSideForDestination(destBlock, afterOut, isUpperCase);
                String space = prefix.endsWith(" ") ? "" : " ";
                return Optional.of(Suggestion.append(space + toWord + " " + formatLabel(destLabel, false) + sideBonus));
            }
        }

        // 17. After "TO " with trailing space
        if (upperPrefix.endsWith("TO ")) {
            String destLabel = findDestinationLabel(context, prefix, blockCtx);
            ConnectedBlockInfo destBlock = findBlockByLabelOrCandidate(context, destLabel);
            String itemUsed = extractItemFromOutput(prefix);
            if (itemUsed == null && blockCtx != null) {
                itemUsed = blockCtx.lastInputItem;
            }
            String sideBonus = getSideForDestination(destBlock, itemUsed, isUpperCase);
            return Optional.of(Suggestion.append(formatLabel(destLabel, false) + sideBonus));
        }

        // 18. Typing partial label after TO (e.g., TO fur...)
        int lastToQuote = prefix.lastIndexOf("TO \"");
        if (lastToQuote == -1) lastToQuote = prefix.lastIndexOf("to \"");
        if (lastToQuote != -1 && lastToQuote + 4 <= prefix.length()) {
            String partialLabel = prefix.substring(lastToQuote + 4);
            if (!partialLabel.contains("\"")) {
                if (context != null) {
                    for (String label : context.getAllCandidateLabels()) {
                        if (label.toLowerCase(Locale.ROOT).startsWith(partialLabel.toLowerCase(Locale.ROOT)) && !label.equalsIgnoreCase(partialLabel)) {
                            ConnectedBlockInfo destBlock = findBlockByLabelOrCandidate(context, label);
                            String itemUsed = extractItemFromOutput(prefix);
                            if (itemUsed == null && blockCtx != null) itemUsed = blockCtx.lastInputItem;
                            String sideBonus = getSideForDestination(destBlock, itemUsed, isUpperCase);
                            return Optional.of(Suggestion.append(label.substring(partialLabel.length()) + "\"" + sideBonus));
                        }
                    }
                }
            }
        }

        int lastToNoQuote = upperPrefix.lastIndexOf("TO ");
        if (lastToNoQuote != -1 && lastToQuote == -1) {
            String partialLabel = prefix.substring(lastToNoQuote + 3).trim();
            if (!partialLabel.isEmpty()) {
                if (context != null) {
                    for (String label : context.getAllCandidateLabels()) {
                        if (label.toLowerCase(Locale.ROOT).startsWith(partialLabel.toLowerCase(Locale.ROOT)) && !label.equalsIgnoreCase(partialLabel)) {
                            ConnectedBlockInfo destBlock = findBlockByLabelOrCandidate(context, label);
                            String itemUsed = extractItemFromOutput(prefix);
                            if (itemUsed == null && blockCtx != null) itemUsed = blockCtx.lastInputItem;
                            String sideBonus = getSideForDestination(destBlock, itemUsed, isUpperCase);
                            return Optional.of(Suggestion.append(label.substring(partialLabel.length()) + sideBonus));
                        }
                    }
                }
            }
        }

        // 19. After machine label TO <dest>, suggest corresponding slot
        if (prefix.matches("(?i).*TO\\s+(\"[^\"]+\"|[a-zA-Z0-9_]+)\\s*$")) {
            String targetLabel = extractLabelFromClause(prefix, "TO");
            ConnectedBlockInfo block = findBlockByLabelOrCandidate(context, targetLabel);
            if ((block != null && block.isMachine()) || targetLabel.toLowerCase(Locale.ROOT).contains("furnace")) {
                String itemUsed = extractItemFromOutput(prefix);
                if (itemUsed == null && blockCtx != null) {
                    itemUsed = blockCtx.lastInputItem;
                }
                String sideBonus = getSideForDestination(block, itemUsed, isUpperCase);
                if (!sideBonus.isEmpty()) {
                    return Optional.of(Suggestion.append(sideBonus));
                }
            }
        }

        // 20. When side name is present (TOP, BOTTOM, NORTH...) but missing SIDE keyword
        String[] allSides = new String[]{"TOP", "BOTTOM", "NORTH", "SOUTH", "EAST", "WEST", "LEFT", "RIGHT", "FRONT", "BACK"};
        for (String side : allSides) {
            if (upperPrefix.endsWith(" " + side)) {
                return Optional.of(Suggestion.append(isUpperCase ? " SIDE" : " side"));
            }
            if (upperPrefix.endsWith(" " + side + " ")) {
                return Optional.of(Suggestion.append(isUpperCase ? "SIDE" : "side"));
            }
        }

        // 21. Partial typing SIDE keyword (S, SI, SID)
        if (upperTrimmed.endsWith(" S") || upperTrimmed.endsWith(" SI") || upperTrimmed.endsWith(" SID")) {
            int sIdx = upperTrimmed.lastIndexOf('S');
            String sub = upperTrimmed.substring(sIdx);
            if ("SIDE".startsWith(sub)) {
                String sideWord = isUpperCase ? "SIDE" : "side";
                return Optional.of(Suggestion.append(sideWord.substring(sub.length())));
            }
        }

        // 22. Partial typing END keyword (EN, END)
        if (upperTrimmed.equals("EN") || upperTrimmed.equals("END")) {
            if (upperTrimmed.equals("EN")) {
                return Optional.of(Suggestion.append(isUpperCase ? "D" : "d"));
            }
        }

        return Optional.empty();
    }

    // --- Helper Methods ---

    private static String getContextualSlotNumber(
            String prefix,
            @Nullable CableContextData context,
            @Nullable BlockContext blockCtx
    ) {
        String upper = prefix.toUpperCase(Locale.ROOT);
        if (upper.contains("INPUT")) {
            String srcLabel = extractLabelFromClause(prefix, "FROM");
            if (srcLabel.isEmpty() && blockCtx != null && blockCtx.lastInputLabel != null) {
                srcLabel = blockCtx.lastInputLabel;
            }
            ConnectedBlockInfo srcBlock = findBlockByLabelOrCandidate(context, srcLabel);
            if (srcBlock != null && !srcBlock.outputSlots().isEmpty()) {
                if (srcBlock.blockId().contains("brewing") || srcLabel.toLowerCase(Locale.ROOT).contains("brewing")) {
                    return "0-2";
                }
                return String.valueOf(srcBlock.outputSlots().get(0));
            }

            String srcLower = srcLabel.toLowerCase(Locale.ROOT);
            if (srcLower.contains("inscriber") || upper.contains("INSCRIBER")) {
                return "3";
            }
            if (srcLower.contains("brewing") || upper.contains("BREWING")) {
                return "0-2";
            }
            if (srcLower.contains("crusher") || upper.contains("CRUSHER")) {
                return "1";
            }
            if (srcLower.contains("infuser") || upper.contains("INFUSER")) {
                return "2";
            }
            if (srcLower.contains("furnace") || upper.contains("FURNACE") || (blockCtx != null && blockCtx.hasInputFromFurnace)) {
                return "2";
            }
            if (isKnownMachineLabel(srcLabel)) {
                return getExtractionSlot(srcBlock, srcLabel);
            }
            return "0";
        }
        if (upper.contains("OUTPUT")) {
            String destLabel = extractLabelFromClause(prefix, "TO");
            String item = extractItemFromOutput(prefix);
            if (item == null && blockCtx != null) item = blockCtx.lastInputItem;

            ConnectedBlockInfo destBlock = findBlockByLabelOrCandidate(context, destLabel);

            String destLower = destLabel.toLowerCase(Locale.ROOT);
            if (destLower.contains("inscriber") || upper.contains("INSCRIBER")) {
                if (item != null && isInscriberPress(item)) {
                    if (item.contains("silicon") || item.contains("bottom")) return "2";
                    return "0";
                }
                return "1";
            }
            if (destLower.contains("infuser") || upper.contains("INFUSER")) {
                if (item != null && isInfuseMaterial(item)) return "0";
                return "1";
            }
            if (destLower.contains("brewing") || upper.contains("BREWING")) {
                if (item != null && item.contains("blaze_powder")) return "4";
                if (item != null && isPotionOrBottle(item)) return "0-2";
                if (item != null && isBrewingIngredient(item)) return "3";
                return "3";
            }
            if (item != null && isFurnaceFuel(item)) return "1";
            if (destBlock != null && !destBlock.inputSlots().isEmpty()) {
                return String.valueOf(destBlock.inputSlots().get(0));
            }
            if (upper.contains("BOTTOM")) return "1";
            return "0";
        }
        return "0";
    }

    private static String buildIfCondition(@Nullable CableContextData context, boolean isUpperCase) {
        String inLabel = findFirstInputLabel(context);
        ConnectedBlockInfo srcBlock = findBlockByLabelOrCandidate(context, inLabel);
        String hasWord = isUpperCase ? "HAS GT 0" : "has gt 0";
        String thenWord = isUpperCase ? "THEN" : "then";
        if (srcBlock != null && !srcBlock.sampleItemIds().isEmpty()) {
            return formatLabel(inLabel, false) + " " + hasWord + " " + srcBlock.sampleItemIds().get(0) + " " + thenWord;
        }
        return formatLabel(inLabel, false) + " " + hasWord + " " + thenWord;
    }

    private static String buildHasRemainder(@Nullable CableContextData context, String prefix, boolean isUpperCase) {
        String label = extractLabelBeforeHas(prefix);
        ConnectedBlockInfo block = findBlockByLabelOrCandidate(context, label);
        String gtWord = isUpperCase ? "GT 0" : "gt 0";
        String thenWord = isUpperCase ? "THEN" : "then";
        if (block != null && !block.sampleItemIds().isEmpty()) {
            return gtWord + " " + block.sampleItemIds().get(0) + " " + thenWord;
        }
        return gtWord + " " + thenWord;
    }

    private static String buildHasNumberAndItem(@Nullable CableContextData context, String prefix, boolean isUpperCase) {
        String label = extractLabelBeforeHas(prefix);
        ConnectedBlockInfo block = findBlockByLabelOrCandidate(context, label);
        String thenWord = isUpperCase ? "THEN" : "then";
        if (block != null && !block.sampleItemIds().isEmpty()) {
            return "0 " + block.sampleItemIds().get(0) + " " + thenWord;
        }
        return "0 " + thenWord;
    }

    private static String buildHasItemOnly(@Nullable CableContextData context, String prefix, boolean isUpperCase) {
        String label = extractLabelBeforeHas(prefix);
        ConnectedBlockInfo block = findBlockByLabelOrCandidate(context, label);
        String thenWord = isUpperCase ? "THEN" : "then";
        if (block != null && !block.sampleItemIds().isEmpty()) {
            return block.sampleItemIds().get(0) + " " + thenWord;
        }
        return thenWord;
    }

    private static String extractLabelBeforeHas(String prefix) {
        int hasIdx = prefix.toUpperCase(Locale.ROOT).lastIndexOf(" HAS");
        if (hasIdx == -1) return "";
        String sub = prefix.substring(0, hasIdx).trim();
        int lastSpace = sub.lastIndexOf(' ');
        if (lastSpace != -1) {
            sub = sub.substring(lastSpace + 1).trim();
        }
        return sub.replace("\"", "");
    }

    private static String findExclusionItem(@Nullable CableContextData context) {
        if (context != null) {
            for (ConnectedBlockInfo b : context.connectedBlocks()) {
                if (b.sampleItemIds().size() > 1) {
                    return b.sampleItemIds().get(1);
                }
            }
            for (ConnectedBlockInfo b : context.connectedBlocks()) {
                if (!b.sampleItemIds().isEmpty()) {
                    return b.sampleItemIds().get(0);
                }
            }
        }
        return "cobblestone";
    }

    public static boolean isFurnaceFuel(String itemId) {
        String lower = itemId.toLowerCase(Locale.ROOT);
        return lower.equals("coal") || lower.equals("charcoal")
                || lower.contains("coal") || lower.equals("lava_bucket")
                || lower.equals("blaze_rod") || lower.equals("dried_kelp_block");
    }

    public static boolean isSmeltable(String itemId) {
        String lower = itemId.toLowerCase(Locale.ROOT);
        return lower.startsWith("raw_") || lower.endsWith("_ore") || lower.contains("ore")
                || lower.contains("sand") || lower.contains("cobble") || lower.contains("log")
                || lower.contains("beef") || lower.contains("pork") || lower.contains("chicken")
                || lower.contains("mutton") || lower.contains("potato") || lower.contains("kelp");
    }

    public static boolean isInscriberPress(String itemId) {
        String lower = itemId.toLowerCase(Locale.ROOT);
        return lower.contains("press");
    }

    public static boolean isInfuseMaterial(String itemId) {
        String lower = itemId.toLowerCase(Locale.ROOT);
        return lower.contains("redstone") || lower.contains("coal") || lower.contains("charcoal")
                || lower.contains("carbon") || lower.contains("diamond") || lower.contains("obsidian")
                || lower.contains("bio_fuel");
    }

    public static boolean isPotionOrBottle(String itemId) {
        String lower = itemId.toLowerCase(Locale.ROOT);
        return lower.contains("potion") || lower.contains("bottle");
    }

    public static boolean isBrewingIngredient(String itemId) {
        String lower = itemId.toLowerCase(Locale.ROOT);
        return lower.contains("wart") || lower.contains("glowstone") || lower.contains("redstone")
                || lower.contains("spider_eye") || lower.contains("ghast_tear") || lower.contains("magma_cream")
                || lower.contains("carrot") || lower.contains("pufferfish") || lower.contains("rabbit_foot")
                || lower.contains("membrane") || lower.contains("sugar") || lower.contains("glistering_melon");
    }

    public static boolean isKnownMachineLabel(String label) {
        String lower = label.toLowerCase(Locale.ROOT);
        return lower.contains("furnace") || lower.contains("smoker") || lower.contains("blast")
                || lower.contains("inscriber") || lower.contains("infuser") || lower.contains("crusher")
                || lower.contains("chamber") || lower.contains("pulverizer") || lower.contains("smelter")
                || lower.contains("brewing") || lower.contains("crafter") || lower.contains("generator")
                || lower.contains("orb") || lower.contains("press") || lower.contains("mixer")
                || lower.contains("basin") || lower.contains("sawmill") || lower.contains("centrifuge")
                || lower.contains("insolator");
    }

    public static String getExtractionSlot(@Nullable ConnectedBlockInfo srcBlock, String sourceLabel) {
        if (srcBlock != null && !srcBlock.outputSlots().isEmpty()) {
            if (srcBlock.blockId().contains("brewing") || sourceLabel.toLowerCase(Locale.ROOT).contains("brewing")) {
                return "0-2";
            }
            return String.valueOf(srcBlock.outputSlots().get(0));
        }
        String idOrLabel = (srcBlock != null ? srcBlock.blockId() + " " : "") + sourceLabel.toLowerCase(Locale.ROOT);
        if (idOrLabel.contains("inscriber")) {
            return "3";
        }
        if (idOrLabel.contains("infuser")) {
            return "2";
        }
        if (idOrLabel.contains("brewing")) {
            return "0-2";
        }
        if (idOrLabel.contains("crusher") || idOrLabel.contains("enrichment") || idOrLabel.contains("purification")) {
            return "1";
        }
        if (idOrLabel.contains("furnace") || idOrLabel.contains("smoker") || idOrLabel.contains("blast")) {
            return "2";
        }
        if (srcBlock != null && srcBlock.totalSlots() > 0) {
            if (srcBlock.totalSlots() == 2) return "1";
            if (srcBlock.totalSlots() == 3) return "2";
            if (srcBlock.totalSlots() == 4) return "3";
        }
        return "2";
    }

    public static String findFirstFluid(@Nullable CableContextData context) {
        if (context != null) {
            for (ConnectedBlockInfo b : context.connectedBlocks()) {
                if (!b.sampleFluidIds().isEmpty()) {
                    return b.sampleFluidIds().get(0);
                }
            }
        }
        return "minecraft:lava";
    }

    public static String formatLabel(String label, boolean forceQuotes) {
        if (forceQuotes) {
            return "\"" + label + "\"";
        }
        if (label.matches("^[a-zA-Z_][a-zA-Z0-9_]*$") && !label.equalsIgnoreCase("redstone")) {
            return label;
        }
        return "\"" + label + "\"";
    }

    public static String getSideForDestination(@Nullable ConnectedBlockInfo destBlock, @Nullable String itemId, boolean isUpperCase) {
        if (destBlock == null || !destBlock.isMachine()) {
            return "";
        }
        String idOrLabel = (destBlock.blockId() + " " + destBlock.getPreferredLabel()).toLowerCase(Locale.ROOT);

        if (idOrLabel.contains("inscriber")) {
            if (itemId != null && isInscriberPress(itemId)) {
                if (itemId.contains("silicon") || itemId.contains("bottom")) {
                    return isUpperCase ? " SLOTS 2" : " slots 2";
                }
                return isUpperCase ? " SLOTS 0" : " slots 0";
            }
            return isUpperCase ? " SLOTS 1" : " slots 1";
        }

        if (idOrLabel.contains("infuser")) {
            if (itemId != null && isInfuseMaterial(itemId)) {
                return isUpperCase ? " SLOTS 0" : " slots 0";
            }
            return isUpperCase ? " SLOTS 1" : " slots 1";
        }

        if (idOrLabel.contains("brewing")) {
            if (itemId != null && itemId.contains("blaze_powder")) {
                return isUpperCase ? " SLOTS 4" : " slots 4";
            }
            if (itemId != null && isPotionOrBottle(itemId)) {
                return isUpperCase ? " SLOTS 0-2" : " slots 0-2";
            }
            if (itemId != null && isBrewingIngredient(itemId)) {
                return isUpperCase ? " SLOTS 3" : " slots 3";
            }
            return isUpperCase ? " SLOTS 3" : " slots 3";
        }

        if (idOrLabel.contains("furnace") || idOrLabel.contains("smoker") || idOrLabel.contains("blast")) {
            if (itemId != null && isFurnaceFuel(itemId)) {
                return isUpperCase ? " SLOTS 1" : " slots 1";
            }
            return isUpperCase ? " SLOTS 0" : " slots 0";
        }

        if (!destBlock.inputSlots().isEmpty()) {
            int inSlot = destBlock.inputSlots().get(0);
            return isUpperCase ? " SLOTS " + inSlot : " slots " + inSlot;
        }

        String slot0 = isUpperCase ? " SLOTS 0" : " slots 0";
        String slot1 = isUpperCase ? " SLOTS 1" : " slots 1";

        if (itemId != null && isFurnaceFuel(itemId)) {
            return slot1;
        }
        if (itemId != null && isSmeltable(itemId)) {
            return slot0;
        }
        return slot0;
    }

    private static boolean itemMatches(String fullItem, String query) {
        String q = query.toLowerCase(Locale.ROOT);
        String item = fullItem.toLowerCase(Locale.ROOT);
        if (item.startsWith(q)) return true;
        int colon = item.indexOf(':');
        if (colon != -1 && item.substring(colon + 1).startsWith(q)) return true;
        return false;
    }

    private static String getItemRemainder(String fullItem, String query) {
        String item = fullItem;
        if (item.toLowerCase(Locale.ROOT).startsWith(query.toLowerCase(Locale.ROOT))) {
            return item.substring(query.length());
        }
        int colon = item.indexOf(':');
        if (colon != -1) {
            String path = item.substring(colon + 1);
            if (path.toLowerCase(Locale.ROOT).startsWith(query.toLowerCase(Locale.ROOT))) {
                return path.substring(query.length());
            }
        }
        return "";
    }

    private static boolean isKnownItem(@Nullable CableContextData context, String query) {
        if (context == null || query.isEmpty()) return false;
        for (ConnectedBlockInfo b : context.connectedBlocks()) {
            for (String item : b.sampleItemIds()) {
                if (item.equalsIgnoreCase(query)) return true;
                int colon = item.indexOf(':');
                if (colon != -1 && item.substring(colon + 1).equalsIgnoreCase(query)) return true;
            }
        }
        return false;
    }

    private static @Nullable String extractItemFromOutput(String prefix) {
        String upper = prefix.toUpperCase(Locale.ROOT);
        int outIdx = upper.lastIndexOf("OUTPUT ");
        if (outIdx == -1) return null;
        int toIdx = upper.indexOf(" TO ", outIdx);
        if (toIdx == -1 && upper.endsWith(" TO")) {
            toIdx = upper.lastIndexOf(" TO");
        }
        if (toIdx != -1 && toIdx > outIdx + 6) {
            String between = prefix.substring(outIdx + 7, toIdx).trim();
            if (!between.isEmpty()) {
                return between.replace("\"", "");
            }
        }
        return null;
    }

    private static String extractLabelFromClause(String prefix, String keyword) {
        String upper = prefix.toUpperCase(Locale.ROOT);
        int kwIdx = upper.indexOf(" " + keyword.toUpperCase(Locale.ROOT) + " ");
        if (kwIdx == -1 && upper.startsWith(keyword.toUpperCase(Locale.ROOT) + " ")) {
            kwIdx = 0;
        } else if (kwIdx != -1) {
            kwIdx += 1;
        }
        if (kwIdx != -1) {
            String remainder = prefix.substring(kwIdx + keyword.length() + 1).trim();
            int end = remainder.indexOf(' ');
            if (end == -1) end = remainder.length();
            String label = remainder.substring(0, end).replace("\"", "");
            return label;
        }
        return "";
    }

    private static String findFirstInputLabel(@Nullable CableContextData context) {
        if (context == null || context.connectedBlocks().isEmpty()) {
            return "chest";
        }
        for (ConnectedBlockInfo b : context.connectedBlocks()) {
            if (!b.isMachine()) {
                return b.getPreferredLabel();
            }
        }
        return context.connectedBlocks().get(0).getPreferredLabel();
    }

    private static String findDestinationLabel(
            @Nullable CableContextData context,
            String currentPrefix,
            @Nullable BlockContext blockCtx
    ) {
        if (context == null || context.connectedBlocks().isEmpty()) {
            return "furnace";
        }

        String sourceLabel = extractLabelFromClause(currentPrefix, "FROM");
        if (sourceLabel.isEmpty() && blockCtx != null && blockCtx.lastInputLabel != null) {
            sourceLabel = blockCtx.lastInputLabel;
        }

        boolean sourceIsMachine = false;
        ConnectedBlockInfo srcBlock = findBlockByLabelOrCandidate(context, sourceLabel);
        if (srcBlock != null && srcBlock.isMachine()) {
            sourceIsMachine = true;
        } else if (sourceLabel.toLowerCase(Locale.ROOT).contains("furnace")) {
            sourceIsMachine = true;
        }

        if (sourceIsMachine) {
            for (ConnectedBlockInfo b : context.connectedBlocks()) {
                if (!b.isMachine()) {
                    return b.getPreferredLabel();
                }
            }
            return "chest";
        }

        for (ConnectedBlockInfo b : context.connectedBlocks()) {
            String label = b.getPreferredLabel();
            if (b.isMachine() && !label.equalsIgnoreCase(sourceLabel)) {
                return label;
            }
        }

        for (ConnectedBlockInfo b : context.connectedBlocks()) {
            String label = b.getPreferredLabel();
            if (!label.equalsIgnoreCase(sourceLabel)) {
                return label;
            }
        }

        return "furnace";
    }

    private static String findEnergySourceLabel(@Nullable CableContextData context) {
        if (context != null) {
            for (ConnectedBlockInfo b : context.connectedBlocks()) {
                if (b.hasEnergy() || b.getPreferredLabel().toLowerCase(Locale.ROOT).contains("generator")
                        || b.getPreferredLabel().toLowerCase(Locale.ROOT).contains("battery")
                        || b.getPreferredLabel().toLowerCase(Locale.ROOT).contains("cell")) {
                    return b.getPreferredLabel();
                }
            }
        }
        return findFirstInputLabel(context);
    }

    private static String findEnergyDestLabel(@Nullable CableContextData context, String prefix, @Nullable BlockContext blockCtx) {
        if (context != null) {
            for (ConnectedBlockInfo b : context.connectedBlocks()) {
                if (b.hasEnergy() && b.isMachine()) {
                    return b.getPreferredLabel();
                }
            }
            for (ConnectedBlockInfo b : context.connectedBlocks()) {
                if (b.hasEnergy()) {
                    return b.getPreferredLabel();
                }
            }
        }
        return findDestinationLabel(context, prefix, blockCtx);
    }

    private static String findFluidSourceLabel(@Nullable CableContextData context) {
        if (context != null) {
            for (ConnectedBlockInfo b : context.connectedBlocks()) {
                if (b.hasFluid() || !b.sampleFluidIds().isEmpty()
                        || b.getPreferredLabel().toLowerCase(Locale.ROOT).contains("tank")) {
                    return b.getPreferredLabel();
                }
            }
        }
        return findFirstInputLabel(context);
    }

    private static String findFluidDestLabel(@Nullable CableContextData context, String prefix, @Nullable BlockContext blockCtx) {
        if (context != null) {
            for (ConnectedBlockInfo b : context.connectedBlocks()) {
                if ((b.hasFluid() || b.isMachine()) && !b.sampleFluidIds().isEmpty()) {
                    return b.getPreferredLabel();
                }
            }
            for (ConnectedBlockInfo b : context.connectedBlocks()) {
                if (b.hasFluid()) {
                    return b.getPreferredLabel();
                }
            }
        }
        return findDestinationLabel(context, prefix, blockCtx);
    }

    private static @Nullable String findBestItemForDestination(
            @Nullable CableContextData context,
            @Nullable ConnectedBlockInfo destBlock,
            @Nullable BlockContext blockCtx
    ) {
        if (blockCtx != null && blockCtx.lastInputItem != null) {
            if (!blockCtx.outputItems.contains(blockCtx.lastInputItem)) {
                return blockCtx.lastInputItem;
            }
        }

        if (context == null || destBlock == null) return null;

        if (destBlock.isMachine()) {
            String machineTag = (destBlock.blockId() + " " + destBlock.getPreferredLabel()).toLowerCase(Locale.ROOT);

            // Inscriber: check for press or recipe materials
            if (machineTag.contains("inscriber")) {
                for (ConnectedBlockInfo b : context.connectedBlocks()) {
                    for (String item : b.sampleItemIds()) {
                        if (isInscriberPress(item) && (blockCtx == null || !blockCtx.outputItems.contains(item))) {
                            return item;
                        }
                    }
                }
                for (ConnectedBlockInfo b : context.connectedBlocks()) {
                    for (String item : b.sampleItemIds()) {
                        String lower = item.toLowerCase(Locale.ROOT);
                        if ((lower.contains("silicon") || lower.contains("diamond") || lower.contains("gold")
                                || lower.contains("certus") || lower.contains("circuit") || lower.contains("processor")
                                || lower.contains("redstone"))
                                && (blockCtx == null || !blockCtx.outputItems.contains(item))) {
                            return item;
                        }
                    }
                }
            }

            // Infuser: check for base items first, then infuse materials
            if (machineTag.contains("infuser")) {
                for (ConnectedBlockInfo b : context.connectedBlocks()) {
                    for (String item : b.sampleItemIds()) {
                        String lower = item.toLowerCase(Locale.ROOT);
                        if ((lower.contains("iron") || lower.contains("steel") || lower.contains("osmium"))
                                && (blockCtx == null || !blockCtx.outputItems.contains(item))) {
                            return item;
                        }
                    }
                }
                for (ConnectedBlockInfo b : context.connectedBlocks()) {
                    for (String item : b.sampleItemIds()) {
                        if (isInfuseMaterial(item) && (blockCtx == null || !blockCtx.outputItems.contains(item))) {
                            return item;
                        }
                    }
                }
            }

            // Brewing Stand: check for ingredients, then potions/bottles, then blaze powder
            if (machineTag.contains("brewing")) {
                for (ConnectedBlockInfo b : context.connectedBlocks()) {
                    for (String item : b.sampleItemIds()) {
                        if (isBrewingIngredient(item) && (blockCtx == null || !blockCtx.outputItems.contains(item))) {
                            return item;
                        }
                    }
                }
                for (ConnectedBlockInfo b : context.connectedBlocks()) {
                    for (String item : b.sampleItemIds()) {
                        if (isPotionOrBottle(item) && (blockCtx == null || !blockCtx.outputItems.contains(item))) {
                            return item;
                        }
                    }
                }
                for (ConnectedBlockInfo b : context.connectedBlocks()) {
                    for (String item : b.sampleItemIds()) {
                        if (item.toLowerCase(Locale.ROOT).contains("blaze_powder") && (blockCtx == null || !blockCtx.outputItems.contains(item))) {
                            return item;
                        }
                    }
                }
            }

            // Furnace / Smoker / Blast Furnace / generic machines
            for (ConnectedBlockInfo b : context.connectedBlocks()) {
                for (String item : b.sampleItemIds()) {
                    if (isSmeltable(item)) {
                        if (blockCtx == null || !blockCtx.outputItems.contains(item)) {
                            return item;
                        }
                    }
                }
            }
            for (ConnectedBlockInfo b : context.connectedBlocks()) {
                for (String item : b.sampleItemIds()) {
                    if (isFurnaceFuel(item)) {
                        if (blockCtx == null || !blockCtx.outputItems.contains(item)) {
                            return item;
                        }
                    }
                }
            }
        }

        for (ConnectedBlockInfo b : context.connectedBlocks()) {
            for (String item : b.sampleItemIds()) {
                if (blockCtx == null || !blockCtx.outputItems.contains(item)) {
                    return item;
                }
            }
        }

        for (ConnectedBlockInfo b : context.connectedBlocks()) {
            if (!b.sampleItemIds().isEmpty()) {
                return b.sampleItemIds().get(0);
            }
        }

        return null;
    }

    private static @Nullable ConnectedBlockInfo findBlockByLabelOrCandidate(@Nullable CableContextData context, String label) {
        if (context == null || label.isEmpty()) return null;
        for (ConnectedBlockInfo b : context.connectedBlocks()) {
            if (b.getPreferredLabel().equalsIgnoreCase(label) || b.labels().contains(label)) {
                return b;
            }
        }
        return null;
    }
}
