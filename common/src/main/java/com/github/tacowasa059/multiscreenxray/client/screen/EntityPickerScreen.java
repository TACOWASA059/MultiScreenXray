package com.github.tacowasa059.multiscreenxray.client.screen;

import com.github.tacowasa059.multiscreenxray.config.ConfigManager;
import com.github.tacowasa059.multiscreenxray.config.XrayProfile;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Searchable visual entity selector for a single X-ray screen profile. */
final class EntityPickerScreen extends Screen {
    private static final int ACCENT = 0xFF40D9FF;
    private static final int PANEL = 0xF0101620;
    private static final int ROW_WIDTH = 142;
    private static final int ROW_HEIGHT = 29;
    private static final List<QuickOption> QUICK = List.of(
            new QuickOption("*", "All", Items.ENDER_EYE),
            new QuickOption("minecraft:player", "Players", Items.PLAYER_HEAD),
            new QuickOption("minecraft:item", "Dropped items", Items.DIAMOND),
            new QuickOption("minecraft:armor_stand", "Armor stands", Items.ARMOR_STAND),
            new QuickOption("#minecraft:skeletons", "Skeletons", Items.BONE),
            new QuickOption("#minecraft:raiders", "Raiders", Items.CROSSBOW));

    private final int screenIndex;
    private final Set<String> selected = new LinkedHashSet<>();
    private final List<EntityEntry> allEntities = new ArrayList<>();
    private EditBox search;
    private Button applyButton;
    private Button viewButton;
    private boolean selectedOnly;
    private int scrollRows;
    private int gridX;
    private int gridY;
    private int gridWidth;
    private int gridBottom;

    EntityPickerScreen(int screenIndex) {
        super(Component.literal("Select entities"));
        this.screenIndex = screenIndex;
        selected.addAll(ConfigManager.get().screen(screenIndex).entities);
        for (ResourceLocation id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
            allEntities.add(new EntityEntry(id, type.getDescription(), new ItemStack(categoryIcon(type, id))));
        }
        allEntities.sort(Comparator.comparing(entry -> entry.id.toString()));
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(680, width - 12);
        gridX = (width - panelWidth) / 2 + 8;
        gridWidth = panelWidth - 16;
        gridY = 108;
        gridBottom = height - 30;
        search = new EditBox(font, gridX, 27, gridWidth - 105, 20, Component.literal("Search entities"));
        search.setHint(Component.literal("Search entities by name or namespace..."));
        search.setResponder(value -> scrollRows = 0);
        addRenderableWidget(search);
        viewButton = Button.builder(viewLabel(), button -> {
            selectedOnly = !selectedOnly;
            scrollRows = 0;
            button.setMessage(viewLabel());
        }).bounds(gridX + gridWidth - 100, 27, 100, 20).build();
        addRenderableWidget(viewButton);
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> cancel())
                .bounds(width / 2 - 103, height - 24, 98, 19).build());
        applyButton = Button.builder(Component.literal("Apply  " + selected.size()), button -> apply())
                .bounds(width / 2 + 5, height - 24, 98, 19).build();
        addRenderableWidget(applyButton);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int panelX = gridX - 8;
        graphics.fill(panelX, 4, gridX + gridWidth + 8, height - 3, PANEL);
        graphics.fill(panelX, 4, gridX + gridWidth + 8, 6, 0xFF13677D);
        graphics.drawString(font, "ENTITY LIBRARY", gridX, 11, ACCENT, false);
        String count = selected.size() + " SELECTED";
        graphics.drawString(font, count, gridX + gridWidth - font.width(count), 11, 0xFF9AABBD, false);
        renderQuickOptions(graphics, mouseX, mouseY);
        renderEntityRows(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderQuickOptions(GuiGraphics graphics, int mouseX, int mouseY) {
        int gap = 3;
        int quickColumns = 3;
        int width = (gridWidth - gap * (quickColumns - 1)) / quickColumns;
        for (int index = 0; index < QUICK.size(); index++) {
            QuickOption option = QUICK.get(index);
            int x = gridX + index % quickColumns * (width + gap);
            int y = 51 + index / quickColumns * 27;
            boolean active = selected.contains(option.selector);
            boolean hovered = inside(mouseX, mouseY, x, y, width, 25);
            graphics.fill(x, y, x + width, y + 25, active ? 0xFF326F83 : hovered ? 0xFF28394B : 0xFF1C2633);
            if (active) graphics.fill(x, y + 23, x + width, y + 25, ACCENT);
            graphics.renderItem(new ItemStack(option.item), x + 4, y + 4);
            graphics.drawString(font, font.plainSubstrByWidth(option.label, width - 26),
                    x + 23, y + 9, 0xFFE6F4FA, false);
        }
    }

    private void renderEntityRows(GuiGraphics graphics, int mouseX, int mouseY) {
        List<EntityEntry> filtered = filteredEntities();
        int columns = Math.max(1, gridWidth / ROW_WIDTH);
        int cellWidth = gridWidth / columns;
        int visibleRows = Math.max(1, (gridBottom - gridY) / ROW_HEIGHT);
        int maxScroll = Math.max(0, (filtered.size() + columns - 1) / columns - visibleRows);
        scrollRows = Math.max(0, Math.min(scrollRows, maxScroll));
        int first = scrollRows * columns;
        int last = Math.min(filtered.size(), first + visibleRows * columns);
        graphics.enableScissor(gridX, gridY, gridX + gridWidth, gridBottom);
        for (int index = first; index < last; index++) {
            EntityEntry entry = filtered.get(index);
            int visible = index - first;
            int x = gridX + visible % columns * cellWidth;
            int y = gridY + visible / columns * ROW_HEIGHT;
            boolean active = selected.contains(entry.id.toString());
            boolean hovered = inside(mouseX, mouseY, x, y, cellWidth - 3, ROW_HEIGHT - 3);
            graphics.fill(x, y, x + cellWidth - 3, y + ROW_HEIGHT - 3,
                    active ? 0xFF174657 : hovered ? 0xFF29394A : 0xFF1A222D);
            if (active) {
                graphics.fill(x, y, x + cellWidth - 3, y + 2, ACCENT);
                graphics.fill(x, y, x + 2, y + ROW_HEIGHT - 3, ACCENT);
            }
            graphics.renderItem(entry.icon, x + 5, y + 5);
            String label = font.plainSubstrByWidth(entry.name.getString(), cellWidth - 36);
            graphics.drawString(font, label, x + 25, y + 9, active ? 0xFFFFFFFF : 0xFFD0DAE5, false);
            if (active) graphics.drawString(font, "+", x + cellWidth - 12, y + 9, 0xFFFFFFFF, false);
        }
        graphics.disableScissor();

        EntityEntry hovered = entityAt(mouseX, mouseY, filtered, columns, visibleRows);
        if (hovered != null) {
            String state = selected.contains(hovered.id.toString()) ? "Selected - click to remove" : "Click to select";
            graphics.renderTooltip(font, List.of(Component.literal(hovered.id.toString()), Component.literal(state)),
                    java.util.Optional.empty(), mouseX, mouseY);
        }
        if (maxScroll > 0) {
            int trackX = gridX + gridWidth - 3;
            int trackHeight = gridBottom - gridY;
            int thumbHeight = Math.max(12, trackHeight * visibleRows / (visibleRows + maxScroll));
            int thumbY = gridY + (trackHeight - thumbHeight) * scrollRows / maxScroll;
            graphics.fill(trackX, gridY, trackX + 3, gridBottom, 0xFF26313E);
            graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbHeight, ACCENT);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        int gap = 3;
        int quickColumns = 3;
        int width = (gridWidth - gap * (quickColumns - 1)) / quickColumns;
        for (int index = 0; index < QUICK.size(); index++) {
            int x = gridX + index % quickColumns * (width + gap);
            int y = 51 + index / quickColumns * 27;
            if (inside(mouseX, mouseY, x, y, width, 25)) {
                toggle(QUICK.get(index).selector);
                return true;
            }
        }
        List<EntityEntry> filtered = filteredEntities();
        int columns = Math.max(1, gridWidth / ROW_WIDTH);
        int visibleRows = Math.max(1, (gridBottom - gridY) / ROW_HEIGHT);
        EntityEntry entry = entityAt(mouseX, mouseY, filtered, columns, visibleRows);
        if (entry != null) {
            toggle(entry.id.toString());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        scrollRows -= (int) Math.signum(amount);
        return true;
    }

    private EntityEntry entityAt(double mouseX, double mouseY, List<EntityEntry> filtered, int columns, int visibleRows) {
        int cellWidth = gridWidth / columns;
        if (!inside(mouseX, mouseY, gridX, gridY, columns * cellWidth, visibleRows * ROW_HEIGHT)) return null;
        int column = ((int) mouseX - gridX) / cellWidth;
        int row = ((int) mouseY - gridY) / ROW_HEIGHT;
        int index = (scrollRows + row) * columns + column;
        return index >= 0 && index < filtered.size() ? filtered.get(index) : null;
    }

    private List<EntityEntry> filteredEntities() {
        String query = search == null ? "" : search.getValue().strip().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return allEntities;
        return allEntities.stream().filter(entry -> entry.id.toString().toLowerCase(Locale.ROOT).contains(query)
                || entry.name.getString().toLowerCase(Locale.ROOT).contains(query))
                .filter(entry -> !selectedOnly || selected.contains(entry.id.toString()))
                .toList();
    }

    private void toggle(String selector) {
        if (!selected.remove(selector)) selected.add(selector);
        applyButton.setMessage(Component.literal("Apply  " + selected.size()));
        viewButton.setMessage(viewLabel());
    }

    private Component viewLabel() {
        return Component.literal(selectedOnly ? "All entities" : "Selected " + selected.size());
    }

    private void apply() {
        XrayProfile profile = ConfigManager.get().screen(screenIndex);
        profile.entities = new ArrayList<>(selected);
        profile.preset = "custom";
        ConfigManager.changed();
        if (minecraft != null) minecraft.setScreen(new XraySettingsScreen(screenIndex));
    }

    private void cancel() {
        if (minecraft != null) minecraft.setScreen(new XraySettingsScreen(screenIndex));
    }

    @Override public void onClose() { cancel(); }
    @Override public boolean isPauseScreen() { return false; }

    private static Item categoryIcon(EntityType<?> type, ResourceLocation id) {
        if (id.getPath().equals("player")) return Items.PLAYER_HEAD;
        if (id.getPath().equals("item")) return Items.DIAMOND;
        MobCategory category = type.getCategory();
        return switch (category) {
            case MONSTER -> Items.ROTTEN_FLESH;
            case CREATURE -> Items.WHEAT;
            case AMBIENT -> Items.FEATHER;
            case WATER_CREATURE, WATER_AMBIENT, UNDERGROUND_WATER_CREATURE, AXOLOTLS -> Items.COD;
            default -> Items.ARMOR_STAND;
        };
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private record EntityEntry(ResourceLocation id, Component name, ItemStack icon) { }
    private record QuickOption(String selector, String label, Item item) { }
}
