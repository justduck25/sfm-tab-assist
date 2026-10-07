package com.sfmaddon.tabassist;

import com.mojang.logging.LogUtils;
import com.sfmaddon.tabassist.config.SFMTabAssistConfig;
import com.sfmaddon.tabassist.network.SFMTabAssistPackets;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(SFMTabAssist.MOD_ID)
public class SFMTabAssist {
    public static final String MOD_ID = "sfm_tab_assist";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SFMTabAssist(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("SFM Tab Assist is initializing...");

        // Register client configuration
        modContainer.registerConfig(ModConfig.Type.CLIENT, SFMTabAssistConfig.SPEC);

        // Register network payloads
        modEventBus.addListener(SFMTabAssistPackets::register);

        // Register client in-world highlight renderer
        if (net.neoforged.fml.loading.FMLEnvironment.getDist().isClient()) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                    com.sfmaddon.tabassist.resolver.WorldHighlightRenderer::onRenderLevelStage
            );
        }
    }
}
