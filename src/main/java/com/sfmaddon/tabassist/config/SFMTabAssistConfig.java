package com.sfmaddon.tabassist.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client configuration for SFM Tab Assist.
 */
public class SFMTabAssistConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue TAB_ASSIST_ENABLED;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Configuration for SFM Tab Assist Addon Mod");
        builder.push("general");

        TAB_ASSIST_ENABLED = builder
                .comment("Enable or disable smart Ghost Text and Tab autocompletion in SFM Text Editor")
                .define("tabAssistEnabled", true);

        builder.pop();
        SPEC = builder.build();
    }

    public static boolean isEnabled() {
        return TAB_ASSIST_ENABLED.get();
    }

    public static void setEnabled(boolean enabled) {
        TAB_ASSIST_ENABLED.set(enabled);
        SPEC.save();
    }

    public static boolean toggle() {
        boolean newState = !isEnabled();
        setEnabled(newState);
        return newState;
    }
}
