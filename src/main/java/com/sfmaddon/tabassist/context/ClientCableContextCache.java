package com.sfmaddon.tabassist.context;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

public class ClientCableContextCache {
    private static @Nullable CableContextData currentContext = null;
    private static @Nullable BlockPos activeManagerPos = null;

    public static void setContext(CableContextData context) {
        currentContext = context;
    }

    public static @Nullable CableContextData getCurrentContext() {
        return currentContext;
    }

    public static void setActiveManagerPos(@Nullable BlockPos pos) {
        activeManagerPos = pos;
    }

    public static @Nullable BlockPos getActiveManagerPos() {
        return activeManagerPos;
    }

    public static boolean isInsideManager() {
        return activeManagerPos != null;
    }

    public static boolean isForManager(BlockPos managerPos) {
        return currentContext != null && currentContext.managerPos().equals(managerPos);
    }

    public static void clear() {
        currentContext = null;
    }

    public static void clearActiveManager() {
        activeManagerPos = null;
        currentContext = null;
    }
}
