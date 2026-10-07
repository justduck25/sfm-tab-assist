package com.sfmaddon.tabassist.mixin;

import ca.teamdman.sfm.client.screen.ManagerScreen;
import ca.teamdman.sfm.common.containermenu.ManagerContainerMenu;
import com.sfmaddon.tabassist.network.SFMTabAssistPackets;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ManagerScreen.class)
public abstract class ManagerScreenMixin extends AbstractContainerScreen<ManagerContainerMenu> {
    public ManagerScreenMixin(ManagerContainerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void sfmTabAssist$onInit(CallbackInfo ci) {
        if (this.menu != null && this.menu.MANAGER_POSITION != null) {
            com.sfmaddon.tabassist.context.ClientCableContextCache.setActiveManagerPos(this.menu.MANAGER_POSITION);
            SFMTabAssistPackets.requestContext(this.menu.MANAGER_POSITION);
        }
    }
}
