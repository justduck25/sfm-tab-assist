package com.sfmaddon.tabassist.client;

import ca.teamdman.sfm.client.screen.text_editor.SFMTextEditScreenV1;
import com.sfmaddon.tabassist.context.ClientCableContextCache;
import com.sfmaddon.tabassist.mixin.MultiLineEditBoxAccessor;
import com.sfmaddon.tabassist.resolver.LabelResolverEngine;
import com.sfmaddon.tabassist.resolver.ResolvedBlockCandidate;
import com.sfmaddon.tabassist.resolver.WorldHighlightManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.List;
import java.util.Locale;

/**
 * Handles mouse click interactions on the SFM Text Editor screen for
 * resolving semantic labels when holding Ctrl.
 */
public class LabelResolverInputHandler {

    public static void onScreenMouseClicked(ScreenEvent.MouseButtonPressed.Post event) {
        if (!(event.getScreen() instanceof SFMTextEditScreenV1 screen)) {
            return;
        }

        MouseButtonEvent mouseEvent = event.getMouseButtonEvent();
        if (mouseEvent.button() != 0 || !mouseEvent.hasControlDown()) {
            return;
        }

        MultiLineEditBox textarea = null;
        for (var child : screen.children()) {
            if (child instanceof MultiLineEditBox box) {
                textarea = box;
                break;
            }
        }
        if (textarea == null) return;

        MultilineTextField textField = ((MultiLineEditBoxAccessor) textarea).sfmTabAssist$getTextField();
        String content = textField.value();
        int cursor = textField.cursor();
        if (content == null || cursor < 0 || cursor > content.length()) return;

        TokenLocation loc = findTokenAtCursor(content, cursor);
        if (loc == null || loc.token.isEmpty() || isKeyword(loc.token)) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (!ClientCableContextCache.isInsideManager() || ClientCableContextCache.getCurrentContext() == null) {
            if (player != null) {
                player.sendOverlayMessage(
                        Component.literal("§6[SFM Resolver] §eDisk is in hand §7— insert into a Factory Manager block to locate blocks in world")
                );
            }
            return;
        }

        List<ResolvedBlockCandidate> candidates = LabelResolverEngine.resolveCandidates(
                loc.token,
                loc.isInputFlow,
                ClientCableContextCache.getCurrentContext()
        );

        if (!candidates.isEmpty()) {
            WorldHighlightManager.setHighlights(loc.token, candidates, 300); // 15 seconds (300 ticks)

            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F)
            );

            if (player != null) {
                ResolvedBlockCandidate best = candidates.get(0);
                player.sendOverlayMessage(
                        Component.literal("§6[SFM Resolver] §fLocated §a" + candidates.size() + "§f candidate(s) for §e\"" + loc.token + "\"§f (Top: §a" + best.block().blockDisplayName() + " " + best.getPercentage() + "%§f)")
                );
            }
        } else {
            if (player != null) {
                player.sendOverlayMessage(
                        Component.literal("§6[SFM Resolver] §cNo matching blocks found for \"" + loc.token + "\"")
                );
            }
        }
    }

    public static void onScreenClosing(ScreenEvent.Closing event) {
        if (event.getScreen() instanceof ca.teamdman.sfm.client.screen.ManagerScreen) {
            ClientCableContextCache.clearActiveManager();
            ClientCableContextCache.clear();
        }
    }

    private record TokenLocation(String token, boolean isInputFlow) {}

    private static TokenLocation findTokenAtCursor(String content, int cursor) {
        if (content.isEmpty()) return null;
        cursor = Math.min(cursor, content.length());

        int lineStart = content.lastIndexOf('\n', Math.max(0, cursor - 1));
        lineStart = (lineStart == -1) ? 0 : lineStart + 1;
        int lineEnd = content.indexOf('\n', cursor);
        if (lineEnd == -1) lineEnd = content.length();

        String line = content.substring(lineStart, lineEnd);
        int offset = cursor - lineStart;
        if (line.trim().isEmpty() || offset < 0 || offset > line.length()) return null;

        String upperLine = line.toUpperCase(Locale.ROOT);
        boolean isInputFlow = upperLine.contains("FROM") || (upperLine.contains("INPUT") && !upperLine.contains("TO"));

        // 1. Quoted label: "..."
        int firstQuote = -1;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == '"') {
                if (firstQuote == -1) {
                    firstQuote = i;
                } else {
                    int secondQuote = i;
                    if (offset >= firstQuote && offset <= secondQuote + 1) {
                        String quoted = line.substring(firstQuote + 1, secondQuote);
                        return new TokenLocation(quoted, isInputFlow);
                    }
                    firstQuote = -1;
                }
            }
        }

        // 2. Unquoted identifier
        if (offset >= line.length() && offset > 0) {
            offset = line.length() - 1;
        }

        if (offset < line.length() && !isTokenChar(line.charAt(offset)) && offset > 0 && isTokenChar(line.charAt(offset - 1))) {
            offset = offset - 1;
        }

        if (offset >= line.length() || !isTokenChar(line.charAt(offset))) {
            return null;
        }

        int start = offset;
        while (start > 0 && isTokenChar(line.charAt(start - 1))) {
            start--;
        }
        int end = offset;
        while (end < line.length() && isTokenChar(line.charAt(end))) {
            end++;
        }

        String word = line.substring(start, end).trim();
        return new TokenLocation(word, isInputFlow);
    }

    private static boolean isTokenChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == ':' || c == '-';
    }

    private static boolean isKeyword(String word) {
        String u = word.toUpperCase(Locale.ROOT);
        return u.equals("INPUT") || u.equals("OUTPUT") || u.equals("FROM") || u.equals("TO")
                || u.equals("EVERY") || u.equals("TICKS") || u.equals("DO") || u.equals("END")
                || u.equals("FOR") || u.equals("EACH") || u.equals("IN") || u.equals("IF")
                || u.equals("THEN") || u.equals("ELSE") || u.equals("SLOT") || u.equals("SLOTS")
                || u.equals("SIDE") || u.equals("NAME") || u.equals("FORGET") || u.equals("NOT");
    }
}
