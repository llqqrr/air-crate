package com.llqqrr.aircrate.client;

import com.llqqrr.aircrate.content.creature.CreatureCaptureService;
import com.llqqrr.aircrate.content.creature.CreatureFilterData;
import com.llqqrr.aircrate.network.AirCrateNetwork;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** A Create list-filter layout with creature previews in the selected slots. */
public final class CreatureFilterScreen extends Screen {
    private static final int WIDTH = 214;
    private static final int HEIGHT = 208;
    private final Set<String> selected;
    private boolean whitelist;
    private EditBox search;
    private IconButton whitelistButton;
    private IconButton blacklistButton;
    private final List<Button> rows = new ArrayList<>();
    private final List<String> visibleIds = new ArrayList<>();

    public CreatureFilterScreen(CreatureFilterData data) {
        super(Component.translatable("screen.aircrate.creature_filter"));
        selected = new LinkedHashSet<>(data.entityTypes());
        whitelist = data.whitelist();
    }

    @Override
    protected void init() {
        int left = (width - WIDTH) / 2;
        int top = (height - HEIGHT) / 2;
        search = addRenderableWidget(new EditBox(font, left + 9, top + 111, WIDTH - 18, 18,
                Component.translatable("screen.aircrate.filter_search")));
        search.setHint(Component.translatable("screen.aircrate.filter_search"));
        search.setResponder(ignored -> refreshRows());

        blacklistButton = new IconButton(left + 18, top + 75, AllIcons.I_BLACKLIST);
        blacklistButton.green = !whitelist;
        blacklistButton.setToolTip(Component.translatable("tooltip.aircrate.filter_blacklist"));
        blacklistButton.withCallback(() -> setWhitelist(false));
        addRenderableWidget(blacklistButton);

        whitelistButton = new IconButton(left + 36, top + 75, AllIcons.I_WHITELIST);
        whitelistButton.green = whitelist;
        whitelistButton.setToolTip(Component.translatable("tooltip.aircrate.filter_whitelist"));
        whitelistButton.withCallback(() -> setWhitelist(true));
        addRenderableWidget(whitelistButton);

        IconButton trash = new IconButton(left + WIDTH - 62, top + 75, AllIcons.I_TRASH);
        trash.setToolTip(Component.translatable("screen.aircrate.filter_remove"));
        trash.withCallback(() -> {
            for (String id : List.copyOf(selected))
                AirCrateNetwork.filterAction(AirCrateNetwork.FilterAction.REMOVE, id);
            selected.clear();
            refreshRows();
        });
        addRenderableWidget(trash);

        IconButton confirm = new IconButton(left + WIDTH - 33, top + 75, AllIcons.I_CONFIRM);
        confirm.green = true;
        confirm.setToolTip(Component.translatable("gui.done"));
        confirm.withCallback(this::onClose);
        addRenderableWidget(confirm);

        for (int row = 0; row < 4; row++) {
            final int index = row;
            Button button = Button.builder(Component.empty(), ignored -> toggleEntry(index))
                    .bounds(left + 10, top + 133 + row * 17, WIDTH - 20, 16).build();
            rows.add(addRenderableWidget(button));
        }
        refreshRows();
        setInitialFocus(search);
    }

    private void setWhitelist(boolean value) {
        if (whitelist == value) return;
        whitelist = value;
        whitelistButton.green = whitelist;
        blacklistButton.green = !whitelist;
        AirCrateNetwork.filterAction(AirCrateNetwork.FilterAction.TOGGLE_MODE, "");
    }

    private void toggleEntry(int index) {
        if (index < 0 || index >= visibleIds.size()) return;
        String id = visibleIds.get(index);
        boolean remove = !selected.add(id);
        if (remove) selected.remove(id);
        AirCrateNetwork.filterAction(remove ? AirCrateNetwork.FilterAction.REMOVE
                : AirCrateNetwork.FilterAction.ADD, id);
        refreshRows();
    }

    private void refreshRows() {
        if (search == null) return;
        String query = search.getValue().toLowerCase(Locale.ROOT).trim();
        visibleIds.clear();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            Component name = type.getDescription();
            if (!query.isEmpty() && !id.toString().toLowerCase(Locale.ROOT).contains(query)
                    && !name.getString().toLowerCase(Locale.ROOT).contains(query)) continue;
            if (!CreatureCaptureService.isSupportedType(type, Minecraft.getInstance().level)) continue;
            if (visibleIds.size() >= rows.size()) break;
            visibleIds.add(id.toString());
        }
        for (int i = 0; i < rows.size(); i++) {
            Button row = rows.get(i);
            row.visible = i < visibleIds.size();
            if (!row.visible) continue;
            String id = visibleIds.get(i);
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(id));
            row.setMessage(Component.literal(selected.contains(id) ? "[x] " : "[ ] ").append(type.getDescription()));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        drawBackdrop(graphics);
        int left = (width - WIDTH) / 2;
        int top = (height - HEIGHT) / 2;
        AllGuiTextures.FILTER.render(graphics, left, top);
        graphics.drawString(font, title, left + 8, top + 4, 0xff202020, false);
        renderSelected(graphics, left, top);
        super.render(graphics, mouseX, mouseY, partialTick);
        for (int i = 0; i < visibleIds.size(); i++) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(visibleIds.get(i)));
            net.minecraft.world.item.SpawnEggItem egg = net.minecraft.world.item.SpawnEggItem.byId(type);
            if (egg != null) graphics.renderItem(new net.minecraft.world.item.ItemStack(egg), left + 12, top + 133 + i * 17);
        }
    }

    private void renderSelected(GuiGraphics graphics, int left, int top) {
        int index = 0;
        for (String id : selected) {
            if (index >= 18) break;
            int x = left + 22 + (index % 9) * 18;
            int y = top + 24 + (index / 9) * 18;
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(id));
            Entity entity = Minecraft.getInstance().level == null ? null : type.create(Minecraft.getInstance().level);
            if (entity instanceof LivingEntity living)
                InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, x, y, x + 18, y + 18,
                        10, 0f, 0f, 0f, living);
            index++;
        }
    }

    private void drawBackdrop(GuiGraphics graphics) {
        graphics.fill(0, 0, width, height, 0x66000000);
    }

    /** Keep the Create panel sharp when FancyMenu is installed in the pack. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Do not delegate to Screen: its default implementation blurs the world.
    }

    @Override public boolean isPauseScreen() { return false; }
}
