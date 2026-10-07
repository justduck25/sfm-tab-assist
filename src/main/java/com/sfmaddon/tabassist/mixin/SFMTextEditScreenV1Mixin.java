package com.sfmaddon.tabassist.mixin;

import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextEditorUtils;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextEditScreenV1;
import ca.teamdman.sfm.client.text_editor.ISFMTextEditScreenOpenContext;
import com.sfmaddon.tabassist.config.SFMTabAssistConfig;
import com.sfmaddon.tabassist.context.ClientCableContextCache;
import com.sfmaddon.tabassist.engine.Suggestion;
import com.sfmaddon.tabassist.engine.SuggestionEngine;
import com.sfmaddon.tabassist.network.SFMTabAssistPackets;
import com.sfmaddon.tabassist.resolver.LabelResolverEngine;
import com.sfmaddon.tabassist.resolver.ResolvedBlockCandidate;
import com.sfmaddon.tabassist.resolver.WorldHighlightManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(SFMTextEditScreenV1.class)
public abstract class SFMTextEditScreenV1Mixin extends Screen {
    protected SFMTextEditScreenV1Mixin(Component title) {
        super(title);
    }

    @Unique
    private MultiLineEditBox sfmTabAssist$getTextarea() {
        for (var listener : this.children()) {
            if (listener instanceof MultiLineEditBox box) {
                return box;
            }
        }
        return null;
    }

    @Unique
    private Suggestion sfmTabAssist$currentSuggestion = null;

    @Inject(method = "init", at = @At("RETURN"))
    private void sfmTabAssist$onInit(CallbackInfo ci) {
        BlockPos targetPos = BlockPos.ZERO;
        try {
            ISFMTextEditScreenOpenContext ctx = ((SFMTextEditScreenV1) (Object) this).openContext();
            if (ctx != null && ctx.labelPositionHolder() != null && ctx.labelPositionHolder().labels() != null) {
                for (var set : ctx.labelPositionHolder().labels().values()) {
                    for (long p : set) {
                        targetPos = BlockPos.of(p);
                        break;
                    }
                    if (!targetPos.equals(BlockPos.ZERO)) break;
                }
            }
        } catch (Throwable ignored) {
        }
        SFMTabAssistPackets.requestContext(targetPos);

        int btnWidth = 72;
        int btnHeight = 16;
        int btnX = this.width / 2 + 200 - btnWidth;
        int btnY = this.height / 2 - 110 - btnHeight - 2;

        this.addRenderableWidget(
                Button.builder(
                        sfmTabAssist$getButtonText(),
                        btn -> {
                            boolean enabled = SFMTabAssistConfig.toggle();
                            btn.setMessage(sfmTabAssist$getButtonText());
                            btn.setTooltip(sfmTabAssist$getButtonTooltip());
                            if (!enabled) {
                                this.sfmTabAssist$currentSuggestion = null;
                            }
                        }
                )
                .bounds(btnX, btnY, btnWidth, btnHeight)
                .tooltip(sfmTabAssist$getButtonTooltip())
                .build()
        );
    }

    @Unique
    private Component sfmTabAssist$getButtonText() {
        return Component.literal(SFMTabAssistConfig.isEnabled() ? "§a[Tab: ON]" : "§c[Tab: OFF]");
    }

    @Unique
    private Tooltip sfmTabAssist$getButtonTooltip() {
        return Tooltip.create(Component.literal(
                SFMTabAssistConfig.isEnabled()
                        ? "SFM Tab Assist: Enabled\nClick to disable Tab autocompletion."
                        : "SFM Tab Assist: Disabled\nClick to enable Tab autocompletion."
        ));
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void sfmTabAssist$renderSuggestion(
            GuiGraphicsExtractor graphics,
            int mx,
            int my,
            float partialTicks,
            CallbackInfo ci
    ) {
        if (!SFMTabAssistConfig.isEnabled()) {
            this.sfmTabAssist$currentSuggestion = null;
            return;
        }

        MultiLineEditBox textarea = sfmTabAssist$getTextarea();
        if (textarea == null || !textarea.isFocused()) {
            this.sfmTabAssist$currentSuggestion = null;
            return;
        }

        MultilineTextField textField = ((MultiLineEditBoxAccessor) textarea).sfmTabAssist$getTextField();
        String content = textField.value();
        int cursor = textField.cursor();

        if (content == null || cursor < 0 || cursor > content.length()) {
            this.sfmTabAssist$currentSuggestion = null;
            return;
        }

        // If characters following the cursor on the same line are not whitespace, hide ghost text to avoid overlapping
        int lineEnd = content.indexOf('\n', cursor);
        if (lineEnd == -1) lineEnd = content.length();
        String lineSuffix = content.substring(cursor, lineEnd);
        if (!lineSuffix.trim().isEmpty()) {
            this.sfmTabAssist$currentSuggestion = null;
            return;
        }

        // Get current line prefix before the cursor
        int lineStart = content.lastIndexOf('\n', cursor - 1);
        lineStart = (lineStart == -1) ? 0 : lineStart + 1;
        String linePrefix = content.substring(lineStart, cursor);

        int currentLineIndex = 0;
        for (int i = 0; i < lineStart; i++) {
            if (content.charAt(i) == '\n') currentLineIndex++;
        }

        int totalLines = 1;
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '\n') totalLines++;
        }

        boolean isDocumentEmpty = content.trim().isEmpty();

        java.util.List<String> previousLines = new java.util.ArrayList<>();
        if (lineStart > 0) {
            String beforeCurrent = content.substring(0, lineStart - 1);
            String[] split = beforeCurrent.split("\n", -1);
            for (String s : split) {
                previousLines.add(s);
            }
        }

        Optional<Suggestion> sug = SuggestionEngine.computeSuggestion(
                linePrefix,
                linePrefix.length(),
                currentLineIndex,
                isDocumentEmpty,
                previousLines,
                ClientCableContextCache.getCurrentContext()
        );

        if (sug.isPresent()) {
            this.sfmTabAssist$currentSuggestion = sug.get();
            String ghost = this.sfmTabAssist$currentSuggestion.ghostText();
            if (ghost.isEmpty()) return;

            Font font = this.font;
            int innerPadding = 4;
            int lineNumberWidth = SFMTextEditorUtils.shouldShowLineNumbers()
                    ? SFMTextEditorUtils.getLineNumberWidth(font, totalLines)
                    : 0;

            int lineX = textarea.getX() + innerPadding + lineNumberWidth;
            int cursorX = lineX + font.width(linePrefix);

            int lineHeight = font.lineHeight;
            int cursorY = textarea.getY() + innerPadding + (currentLineIndex * lineHeight) - (int) Math.round(textarea.scrollAmount());

            int topLimit = textarea.getY() + innerPadding;
            int bottomLimit = textarea.getY() + textarea.getHeight() - innerPadding;
            if (cursorY < topLimit - 2 || cursorY + lineHeight > bottomLimit + 2) {
                // Cursor scrolled outside of editor viewport
                return;
            }

            // Apply scissor to prevent overflowing outside textarea bounds
            graphics.enableScissor(
                    textarea.getX() + innerPadding,
                    topLimit,
                    textarea.getX() + textarea.getWidth() - 8,
                    bottomLimit
            );

            // 1. Ghost text rendered directly next to cursor (dimmed gray like Copilot)
            int ghostColor = 0xFFA0A5A8;
            SFMFontUtils.draw(graphics, font, ghost, cursorX, cursorY, ghostColor, false);

            // 2. Compact [Tab] badge right after ghost text
            int ghostWidth = font.width(ghost);
            int badgeX = cursorX + ghostWidth + 5;
            String badgeText = "Tab";
            int badgeTextWidth = font.width(badgeText);
            int badgeWidth = badgeTextWidth + 6;
            int badgeHeight = lineHeight - 1;

            graphics.fill(badgeX, cursorY, badgeX + badgeWidth, cursorY + badgeHeight, 0xE01C2128);
            SFMFontUtils.draw(graphics, font, badgeText, badgeX + 3, cursorY, 0xFF7EE787, false);

            graphics.disableScissor();
        } else {
            this.sfmTabAssist$currentSuggestion = null;
        }

        // Render hint when Ctrl is held
        if (net.minecraft.client.Minecraft.getInstance().hasControlDown()) {
            Font font = this.font;
            int hintX = this.width / 2 - 198;
            int hintY = this.height / 2 - 110 - 16;
            SFMFontUtils.draw(graphics, font, "§3[Ctrl + Click]: §bLocate label in world", hintX, hintY, 0xFF38BDF8, false);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void sfmTabAssist$handleTabKey(
            KeyEvent event,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!SFMTabAssistConfig.isEnabled()) {
            return;
        }

        if (event.key() == GLFW.GLFW_KEY_TAB && this.sfmTabAssist$currentSuggestion != null) {
            MultiLineEditBox textarea = sfmTabAssist$getTextarea();
            if (textarea != null) {
                String insert = this.sfmTabAssist$currentSuggestion.ghostText();
                MultilineTextField textField = ((MultiLineEditBoxAccessor) textarea).sfmTabAssist$getTextField();
                textField.insertText(insert);

                this.sfmTabAssist$currentSuggestion = null;
                cir.setReturnValue(true);
                cir.cancel();
                return;
            }
        }

        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            this.sfmTabAssist$currentSuggestion = null;
        }
    }

    @Inject(method = "mouseClicked", at = @At("RETURN"), cancellable = true)
    private void sfmTabAssist$onMouseClicked(
            net.minecraft.client.input.MouseButtonEvent event,
            boolean doubleClick,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (event.button() != 0 || !event.hasControlDown()) {
            return;
        }

        MultiLineEditBox textarea = sfmTabAssist$getTextarea();
        if (textarea == null) return;

        MultilineTextField textField = ((MultiLineEditBoxAccessor) textarea).sfmTabAssist$getTextField();
        String content = textField.value();
        int cursor = textField.cursor();
        if (content == null || cursor < 0 || cursor > content.length()) return;

        sfmTabAssist$TokenLocation loc = sfmTabAssist$findTokenAtCursor(content, cursor);
        if (loc == null || loc.token.isEmpty() || sfmTabAssist$isKeyword(loc.token)) return;

        java.util.List<ResolvedBlockCandidate> candidates = LabelResolverEngine.resolveCandidates(
                loc.token,
                loc.isInputFlow,
                ClientCableContextCache.getCurrentContext()
        );

        LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        if (!candidates.isEmpty()) {
            WorldHighlightManager.setHighlights(loc.token, candidates, 300); // 15 seconds (300 ticks)

            net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
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

    @Unique
    private record sfmTabAssist$TokenLocation(String token, boolean isInputFlow) {}

    @Unique
    private static sfmTabAssist$TokenLocation sfmTabAssist$findTokenAtCursor(String content, int cursor) {
        if (content.isEmpty()) return null;
        cursor = Math.min(cursor, content.length());

        int lineStart = content.lastIndexOf('\n', Math.max(0, cursor - 1));
        lineStart = (lineStart == -1) ? 0 : lineStart + 1;
        int lineEnd = content.indexOf('\n', cursor);
        if (lineEnd == -1) lineEnd = content.length();

        String line = content.substring(lineStart, lineEnd);
        int offset = cursor - lineStart;
        if (line.trim().isEmpty() || offset < 0 || offset > line.length()) return null;

        String upperLine = line.toUpperCase(java.util.Locale.ROOT);
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
                        return new sfmTabAssist$TokenLocation(quoted, isInputFlow);
                    }
                    firstQuote = -1;
                }
            }
        }

        // 2. Unquoted identifier
        if (offset >= line.length() && offset > 0) {
            offset = line.length() - 1;
        }

        if (offset < line.length() && !sfmTabAssist$isTokenChar(line.charAt(offset)) && offset > 0 && sfmTabAssist$isTokenChar(line.charAt(offset - 1))) {
            offset = offset - 1;
        }

        if (offset >= line.length() || !sfmTabAssist$isTokenChar(line.charAt(offset))) {
            return null;
        }

        int start = offset;
        while (start > 0 && sfmTabAssist$isTokenChar(line.charAt(start - 1))) {
            start--;
        }
        int end = offset;
        while (end < line.length() && sfmTabAssist$isTokenChar(line.charAt(end))) {
            end++;
        }

        String word = line.substring(start, end).trim();
        return new sfmTabAssist$TokenLocation(word, isInputFlow);
    }

    @Unique
    private static boolean sfmTabAssist$isTokenChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == ':' || c == '-';
    }

    @Unique
    private static boolean sfmTabAssist$isKeyword(String word) {
        String u = word.toUpperCase(java.util.Locale.ROOT);
        return u.equals("INPUT") || u.equals("OUTPUT") || u.equals("FROM") || u.equals("TO")
                || u.equals("EVERY") || u.equals("TICKS") || u.equals("DO") || u.equals("END")
                || u.equals("FOR") || u.equals("EACH") || u.equals("IN") || u.equals("IF")
                || u.equals("THEN") || u.equals("ELSE") || u.equals("SLOT") || u.equals("SLOTS")
                || u.equals("SIDE") || u.equals("NAME") || u.equals("FORGET") || u.equals("NOT");
    }
}
