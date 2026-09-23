package com.llqqrr.aircrate.client;

import com.llqqrr.aircrate.content.frogport.CreatureAreaSelection;
import com.llqqrr.aircrate.content.frogport.CreatureFrogportBlockEntity;
import com.llqqrr.aircrate.network.AirCrateNetwork;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class CreatureFrogportScreen extends Screen {
    private static final int WIDTH = 220;
    private static final int HEIGHT = 99;
    private final BlockPos pos;
    private CreatureFrogportBlockEntity.Mode mode;
    private final CreatureAreaSelection selection;
    private final ItemStack filter;

    public CreatureFrogportScreen(BlockPos pos, CreatureFrogportBlockEntity.Mode mode,
                                  CreatureAreaSelection selection, ItemStack filter) {
        super(Component.translatable("screen.aircrate.creature_frogport"));
        this.pos = pos;
        this.mode = mode;
        this.selection = selection;
        this.filter = filter.copy();
    }

    @Override
    protected void init() {
        int left = (width - WIDTH) / 2;
        int top = (height - HEIGHT) / 2;
        IconButton reselect = new IconButton(left + 8, top + 75, AllIcons.I_ROTATE_PLACE);
        reselect.setToolTip(Component.translatable("screen.aircrate.reselect"));
        reselect.withCallback(() -> {
            AirCrateNetwork.frogportAction(pos, AirCrateNetwork.FrogportAction.RESELECT);
            onClose();
        });
        addRenderableWidget(reselect);

        IconButton toggle = new IconButton(left + 42, top + 75,
                mode == CreatureFrogportBlockEntity.Mode.CAPTURE ? AllIcons.I_ACTIVE : AllIcons.I_PASSIVE);
        toggle.green = mode == CreatureFrogportBlockEntity.Mode.CAPTURE;
        toggle.setToolTip(modeLabel());
        toggle.withCallback(() -> {
            mode = mode == CreatureFrogportBlockEntity.Mode.CAPTURE
                    ? CreatureFrogportBlockEntity.Mode.RELEASE : CreatureFrogportBlockEntity.Mode.CAPTURE;
            AirCrateNetwork.frogportAction(pos, AirCrateNetwork.FrogportAction.TOGGLE_MODE);
            toggle.setIcon(mode == CreatureFrogportBlockEntity.Mode.CAPTURE ? AllIcons.I_ACTIVE : AllIcons.I_PASSIVE);
            toggle.green = mode == CreatureFrogportBlockEntity.Mode.CAPTURE;
            toggle.setToolTip(modeLabel());
        });
        addRenderableWidget(toggle);

        IconButton confirm = new IconButton(left + WIDTH - 33, top + 75, AllIcons.I_CONFIRM);
        confirm.green = true;
        confirm.setToolTip(Component.translatable("gui.done"));
        confirm.withCallback(this::onClose);
        addRenderableWidget(confirm);
    }

    private Component modeLabel() {
        return Component.translatable(mode == CreatureFrogportBlockEntity.Mode.CAPTURE
                ? "screen.aircrate.capture" : "screen.aircrate.release");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawBackdrop(graphics);
        int left = (width - WIDTH) / 2;
        int top = (height - HEIGHT) / 2;
        AllGuiTextures.FROGPORT_HEADER.render(graphics, left + 3, top);
        AllGuiTextures.FROGPORT_BG.render(graphics, left, top + 17);
        graphics.drawString(font, title, left + 8, top + 4, 0xff202020, false);
        AllGuiTextures.FROGPORT_SLOT.render(graphics, left + 190, top + 52);
        if (!filter.isEmpty()) graphics.renderItem(filter, left + 191, top + 53);
        Component area = selection == null ? Component.translatable("screen.aircrate.no_selection")
                : Component.translatable("screen.aircrate.selection_size",
                selection.max().getX() - selection.min().getX() + 1,
                selection.max().getY() - selection.min().getY() + 1,
                selection.max().getZ() - selection.min().getZ() + 1);
        graphics.drawString(font, area, left + 8, top + 58, 0xff404040, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawBackdrop(GuiGraphics graphics) {
        graphics.fill(0, 0, width, height, 0x66000000);
    }

    /** FancyMenu/vanilla Screen.render otherwise applies the full-screen Gaussian blur. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The Create texture already supplies the panel background.
    }

    @Override public boolean isPauseScreen() { return false; }
}
