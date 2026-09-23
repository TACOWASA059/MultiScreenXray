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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Visual block and tag selector used by a single X-ray screen profile. */
final class BlockPickerScreen extends Screen {
    private static final int ACCENT = 0xFF40D9FF;
    private static final int PANEL = 0xF0101620;
    private static final int TILE_WIDTH = 118;
    private static final int TILE_HEIGHT = 29;
    private static final int[] TAG_COLORS = {0xFF326F83, 0xFF326F83, 0xFF326F83, 0xFF326F83,
            0xFF326F83, 0xFF326F83, 0xFF326F83, 0xFF326F83};
    private static final List<TagOption> TAGS = List.of(
            new TagOption("#minecraft:coal_ores", "Coal", Items.COAL_ORE),
            new TagOption("#minecraft:iron_ores", "Iron", Items.IRON_ORE),
            new TagOption("#minecraft:copper_ores", "Copper", Items.COPPER_ORE),
            new TagOption("#minecraft:gold_ores", "Gold", Items.GOLD_ORE),
            new TagOption("#minecraft:redstone_ores", "Redstone", Items.REDSTONE_ORE),
            new TagOption("#minecraft:lapis_ores", "Lapis", Items.LAPIS_ORE),
            new TagOption("#minecraft:diamond_ores", "Diamond", Items.DIAMOND_ORE),
            new TagOption("#minecraft:emerald_ores", "Emerald", Items.EMERALD_ORE));

    private final int screenIndex;
    private final Set<String> selected = new LinkedHashSet<>();
    private final List<BlockEntry> allBlocks = new ArrayList<>();
    private EditBox search;
    private Button applyButton;
    private Button viewButton;
    private boolean selectedOnly;
    private int scrollRows;
    private int gridX;
    private int gridY;
    private int gridWidth;
    private int gridBottom;

    BlockPickerScreen(int screenIndex) {
        super(Component.literal("Select blocks"));
        this.screenIndex = screenIndex;
        selected.addAll(ConfigManager.get().screen(screenIndex).blocks);
        for (ResourceLocation id : BuiltInRegistries.BLOCK.keySet()) {
            Block block = BuiltInRegistries.BLOCK.get(id);
            Item item = block.asItem();
            if (item != Items.AIR) allBlocks.add(new BlockEntry(id, block.getName(), new ItemStack(item)));
        }
        allBlocks.sort(Comparator.comparing(entry -> entry.id.toString()));
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(680, width - 12);
        gridX = (width - panelWidth) / 2 + 8;
        gridWidth = panelWidth - 16;
        gridY = 108;
        gridBottom = height - 30;

        search = new EditBox(font, gridX, 27, gridWidth - 105, 20, Component.literal("Search blocks"));
        search.setHint(Component.literal("Search blocks by name or namespace..."));
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
        graphics.fillGradient(0, 0, width, height, 0xFF101A26, 0xFF070B11);
        int panelX = gridX - 8;
        graphics.fill(panelX, 4, gridX + gridWidth + 8, height - 3, PANEL);
        graphics.fill(panelX, 4, gridX + gridWidth + 8, 6, 0xFF13677D);
        // Screen renders the active blur before its widgets. Draw the custom
        // library after that pass so its labels and item icons stay sharp.
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, "BLOCK LIBRARY", gridX, 11, ACCENT, false);
        String count = selected.size() + " SELECTED";
        graphics.drawString(font, count, gridX + gridWidth - font.width(count), 11, 0xFF9AABBD, false);
        renderTagChips(graphics, mouseX, mouseY);
        renderBlockGrid(graphics, mouseX, mouseY);
    }

    private void renderTagChips(GuiGraphics graphics, int mouseX, int mouseY) {
        int gap = 3;
        int quickColumns = 4;
        int chipWidth = (gridWidth - gap * (quickColumns - 1)) / quickColumns;
        for (int index = 0; index < TAGS.size(); index++) {
            TagOption option = TAGS.get(index);
            int x = gridX + index % quickColumns * (chipWidth + gap);
            int y = 51 + index / quickColumns * 27;
            boolean active = selected.contains(option.selector);
            boolean hovered = inside(mouseX, mouseY, x, y, chipWidth, 25);
            graphics.fill(x, y, x + chipWidth, y + 25, active ? TAG_COLORS[index] : hovered ? 0xFF28394B : 0xFF1C2633);
            if (active) graphics.fill(x, y + 23, x + chipWidth, y + 25, ACCENT);
            graphics.renderItem(new ItemStack(option.item), x + 4, y + 4);
            graphics.drawString(font, option.label, x + 23, y + 9, 0xFFE6F4FA, false);
        }
    }

    private void renderBlockGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        List<BlockEntry> filtered = filteredBlocks();
        int columns = Math.max(1, gridWidth / TILE_WIDTH);
        int cellWidth = gridWidth / columns;
        int visibleRows = Math.max(1, (gridBottom - gridY) / TILE_HEIGHT);
        int maxScroll = Math.max(0, (filtered.size() + columns - 1) / columns - visibleRows);
        scrollRows = Math.max(0, Math.min(scrollRows, maxScroll));
        int first = scrollRows * columns;
        int last = Math.min(filtered.size(), first + visibleRows * columns);

        graphics.enableScissor(gridX, gridY, gridX + gridWidth, gridBottom);
        for (int index = first; index < last; index++) {
            BlockEntry entry = filtered.get(index);
            int visibleIndex = index - first;
            int x = gridX + visibleIndex % columns * cellWidth;
            int y = gridY + visibleIndex / columns * TILE_HEIGHT;
            boolean active = selected.contains(entry.id.toString());
            boolean hovered = inside(mouseX, mouseY, x, y, cellWidth - 3, TILE_HEIGHT - 3);
            graphics.fill(x, y, x + cellWidth - 3, y + TILE_HEIGHT - 3,
                    active ? 0xFF174657 : hovered ? 0xFF29394A : 0xFF1A222D);
            if (active) {
                graphics.fill(x, y, x + cellWidth - 3, y + 2, ACCENT);
                graphics.fill(x, y, x + 2, y + TILE_HEIGHT - 3, ACCENT);
            }
            graphics.renderItem(entry.stack, x + 5, y + 5);
            String label = font.plainSubstrByWidth(entry.name.getString(), cellWidth - 36);
            graphics.drawString(font, label, x + 25, y + 9, active ? 0xFFFFFFFF : 0xFFD0DAE5, false);
            if (active) graphics.drawString(font, "+", x + cellWidth - 12, y + 9, 0xFFFFFFFF, false);
        }
        graphics.disableScissor();

        BlockEntry hovered = blockAt(mouseX, mouseY, filtered, columns, visibleRows);
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
        int quickColumns = 4;
        int chipWidth = (gridWidth - gap * (quickColumns - 1)) / quickColumns;
        for (int index = 0; index < TAGS.size(); index++) {
            int x = gridX + index % quickColumns * (chipWidth + gap);
            int y = 51 + index / quickColumns * 27;
            if (inside(mouseX, mouseY, x, y, chipWidth, 25)) {
                toggle(TAGS.get(index).selector);
                return true;
            }
        }
        List<BlockEntry> filtered = filteredBlocks();
        int columns = Math.max(1, gridWidth / TILE_WIDTH);
        int visibleRows = Math.max(1, (gridBottom - gridY) / TILE_HEIGHT);
        BlockEntry entry = blockAt(mouseX, mouseY, filtered, columns, visibleRows);
        if (entry != null) {
            toggle(entry.id.toString());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        scrollRows -= (int) Math.signum(deltaY);
        return true;
    }

    private BlockEntry blockAt(double mouseX, double mouseY, List<BlockEntry> filtered, int columns, int visibleRows) {
        int cellWidth = gridWidth / columns;
        if (!inside(mouseX, mouseY, gridX, gridY, columns * cellWidth, visibleRows * TILE_HEIGHT)) return null;
        int column = ((int) mouseX - gridX) / cellWidth;
        int row = ((int) mouseY - gridY) / TILE_HEIGHT;
        int index = (scrollRows + row) * columns + column;
        return index >= 0 && index < filtered.size() ? filtered.get(index) : null;
    }

    private List<BlockEntry> filteredBlocks() {
        String query = search == null ? "" : search.getValue().strip().toLowerCase(Locale.ROOT);
        return allBlocks.stream()
                .filter(entry -> !selectedOnly || selected.contains(entry.id.toString()))
                .filter(entry -> query.isEmpty() || entry.id.toString().toLowerCase(Locale.ROOT).contains(query)
                        || entry.name.getString().toLowerCase(Locale.ROOT).contains(query))
                .toList();
    }

    private void toggle(String selector) {
        if (!selected.remove(selector)) selected.add(selector);
        applyButton.setMessage(Component.literal("Apply  " + selected.size()));
        viewButton.setMessage(viewLabel());
    }

    private Component viewLabel() {
        return Component.literal(selectedOnly ? "All blocks" : "Selected " + selected.size());
    }

    private void apply() {
        XrayProfile profile = ConfigManager.get().screen(screenIndex);
        profile.blocks = new ArrayList<>(selected);
        profile.preset = "custom";
        ConfigManager.changed();
        if (minecraft != null) minecraft.setScreen(new XraySettingsScreen(screenIndex));
    }

    private void cancel() {
        if (minecraft != null) minecraft.setScreen(new XraySettingsScreen(screenIndex));
    }

    @Override public void onClose() { cancel(); }
    @Override public boolean isPauseScreen() { return false; }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private record BlockEntry(ResourceLocation id, Component name, ItemStack stack) { }
    private record TagOption(String selector, String label, Item item) { }
}
