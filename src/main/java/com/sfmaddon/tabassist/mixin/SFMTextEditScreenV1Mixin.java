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
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
        BlockPos mgrPos = ClientCableContextCache.getActiveManagerPos();
        if (mgrPos != null && !mgrPos.equals(BlockPos.ZERO)) {
            SFMTabAssistPackets.requestContext(mgrPos);
        } else {
            ClientCableContextCache.clear();
        }

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
        // Render the locator hint at the top of the editor
        Font font = this.font;
        int hintX = this.width / 2 - 198;
        int hintY = this.height / 2 - 110 - 16;
        if (ClientCableContextCache.isInsideManager()) {
            if (net.minecraft.client.Minecraft.getInstance().hasControlDown()) {
                SFMFontUtils.draw(graphics, font, "§b[Ctrl + Click]: §fLocate label in world", hintX, hintY, 0xFFFFFFFF, false);
            } else {
                SFMFontUtils.draw(graphics, font, "§3[Ctrl + Click]: §bLocate label in world", hintX, hintY, 0xFF38BDF8, false);
            }
        } else {
            SFMFontUtils.draw(graphics, font, "§8[Ctrl + Click]: Offline (Insert disk into Factory Manager to locate)", hintX, hintY, 0xFF888888, false);
        }

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

}
