package com.llqqrr.aircrate.client;

import com.llqqrr.aircrate.network.AirCrateNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour.ValueSettings;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsScreen;

import java.util.List;

/** Transparent Create-inspired mode selector; it deliberately exposes one mode only. */
final class AviationCrateModeScreen extends ValueSettingsScreen {
    private final BlockPos controllerPos;
    private final int initialValue;

    AviationCrateModeScreen(BlockPos controllerPos, boolean breeding) {
        super(controllerPos,
                new ValueSettingsBoard(Component.translatable("screen.aircrate.mode"), 1, 1,
                        List.of(Component.translatable("screen.aircrate.breeding")),
                        new ValueSettingsFormatter(settings -> Component.translatable(
                                settings.value() == 1 ? "screen.aircrate.breeding_on" : "screen.aircrate.breeding_off"))),
                new ValueSettings(0, breeding ? 1 : 0), settings -> { }, 0);
        this.controllerPos = controllerPos;
        this.initialValue = breeding ? 1 : 0;
    }

    @Override
    protected void saveAndClose(double mouseX, double mouseY) {
        ValueSettings selected = getClosestCoordinate((int) mouseX, (int) mouseY);
        int value = selected.value() < 0 ? initialValue : selected.value();
        AirCrateNetwork.setBreeding(controllerPos, value == 1);
        onClose();
    }

    @Override
    public void renderBackground(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY,
                                 float partialTick) {
        // Create's value box deliberately leaves the world sharp behind its brass panel.
    }
}
