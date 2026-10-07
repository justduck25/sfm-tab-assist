package com.sfmaddon.tabassist.context;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

public class ClientCableContextCache {
    private static @Nullable CableContextData currentContext = null;

    public static void setContext(CableContextData context) {
        currentContext = context;
    }

    public static @Nullable CableContextData getCurrentContext() {
        return currentContext;
    }

    public static boolean isForManager(BlockPos managerPos) {
        return currentContext != null && currentContext.managerPos().equals(managerPos);
    }

    public static void clear() {
        currentContext = null;
    }
}
