package com.github.tacowasa059.multiscreenxray.client.screen;

import com.github.tacowasa059.multiscreenxray.client.window.MultiWindowManager;
import com.github.tacowasa059.multiscreenxray.config.ConfigManager;
import com.github.tacowasa059.multiscreenxray.config.SelectorRule;
import com.github.tacowasa059.multiscreenxray.config.XrayConfig;
import com.github.tacowasa059.multiscreenxray.config.XrayPresets;
import com.github.tacowasa059.multiscreenxray.config.XrayProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.function.IntConsumer;

/** Compact card based editor for X-ray windows and their saved profiles. */
public final class XraySettingsScreen extends Screen {
    private static final int ACCENT = 0xFF40D9FF;
    private static final int ACCENT_DARK = 0xFF13677D;
    private static final int PANEL = 0xEE101620;
    private static final int CARD = 0xFF1C2633;
    private static final int CARD_HOVER = 0xFF27384A;
    private static final int MUTED = 0xFF8292A6;

    private int selected;
    private EditBox nameField;
    private EditBox blockField;
    private EditBox entityField;
    private int panelX;
    private int panelWidth;
    private int observedWindowCount;

    public XraySettingsScreen() { this(0); }

    public static Screen blockLibrary(int screenIndex) { return new BlockPickerScreen(screenIndex); }

    public static Screen entityLibrary(int screenIndex) { return new EntityPickerScreen(screenIndex); }

    XraySettingsScreen(int selected) {
        super(Component.literal("MultiScreen X-ray settings"));
        this.selected = selected;
    }

    @Override
    protected void init() {
        XrayConfig config = ConfigManager.get();
        config.normalize();
        observedWindowCount = config.windowCount;
        selected = config.windowCount == 0 ? 0 : Math.max(0, Math.min(selected, config.windowCount - 1));
        panelWidth = Math.min(620, width - 16);
        panelX = (width - panelWidth) / 2;
        int innerX = panelX + 10;
        int innerWidth = panelWidth - 20;

        buildScreenTabs(config, innerX, innerWidth, 28);
        if (config.windowCount > 0) {
            XrayProfile profile = config.screen(selected);
            buildPresetCards(profile, innerX, innerWidth, 58);
            buildFields(profile, innerX, innerWidth, 117);
            buildToggles(profile, innerX, innerWidth, 151);
            buildRangeControls(config, innerX, innerWidth, 180);
        }
        addRenderableWidget(new ModernButton(width / 2 - 60, height - 25, 120, 19,
                Component.literal("Done"), Icon.CHECK, true, this::onClose));
    }

    private void buildScreenTabs(XrayConfig config, int x, int available, int y) {
        int activeScreens = config.windowCount;
        int actionWidth = 26;
        int tabsAvailable = available - actionWidth * 2 - 8;
        int tabWidth = Math.min(42, Math.max(25, tabsAvailable / Math.max(1, activeScreens)));
        int totalWidth = tabWidth * activeScreens;
        int start = x + Math.max(0, (tabsAvailable - totalWidth) / 2);
        for (int index = 0; index < activeScreens; index++) {
            final int target = index;
            addRenderableWidget(new ScreenTab(start + index * tabWidth, y, tabWidth - 3, 23,
                    index + 1, index == selected, () -> switchScreen(target)));
        }
        int actionX = x + available - actionWidth * 2 - 4;
        ModernButton remove = new ModernButton(actionX, y, 24, 23, Component.literal("Remove window"),
                Icon.MINUS, false, this::removeWindow);
        remove.active = config.windowCount > 0;
        addRenderableWidget(remove);
        ModernButton add = new ModernButton(actionX + actionWidth + 2, y, 24, 23,
                Component.literal("Add window"), Icon.PLUS, true, this::addWindow);
        add.active = config.windowCount < XrayConfig.MAX_WINDOWS;
        addRenderableWidget(add);
    }

    private void buildPresetCards(XrayProfile profile, int x, int available, int y) {
        List<String> presets = XrayPresets.names();
        int gap = 4;
        int cardWidth = (available - gap * (presets.size() - 1)) / presets.size();
        for (int index = 0; index < presets.size(); index++) {
            String preset = presets.get(index);
            addRenderableWidget(new PresetCard(x + index * (cardWidth + gap), y, cardWidth, 44,
                    preset, presetItem(preset), preset.equals(profile.preset), () -> applyPreset(preset)));
        }
    }

    private void buildFields(XrayProfile profile, int x, int available, int y) {
        int gap = 5;
        int nameWidth = Math.max(78, available / 4);
        int selectorWidth = (available - nameWidth - gap * 2) / 2;
        nameField = field(x, y, nameWidth, "Profile name", profile.name, 40);
        int blockX = x + nameWidth + gap;
        blockField = field(blockX, y, selectorWidth - 27, "Blocks: IDs or #tags",
                String.join(", ", profile.blocks), 2048);
        addRenderableWidget(new ModernButton(blockX + selectorWidth - 24, y, 24, 22,
                Component.literal("Browse blocks"), Icon.BLOCK, true, this::openBlockPicker));
        int entityX = x + nameWidth + selectorWidth + gap * 2;
        entityField = field(entityX, y, selectorWidth - 27,
                "Entities: IDs, #tags or *", String.join(", ", profile.entities), 2048);
        addRenderableWidget(new ModernButton(entityX + selectorWidth - 24, y, 24, 22,
                Component.literal("Browse entities"), Icon.ENTITY, true, this::openEntityPicker));
    }

    private EditBox field(int x, int y, int width, String hint, String value, int maxLength) {
        EditBox field = new EditBox(font, x, y, width, 22, Component.literal(hint));
        field.setHint(Component.literal(hint));
        field.setMaxLength(maxLength);
        field.setValue(value);
        addRenderableWidget(field);
        return field;
    }

    private void buildToggles(XrayProfile profile, int x, int available, int y) {
        int gap = 5;
        int width = (available - gap * 2) / 3;
        addRenderableWidget(new ToggleChip(x, y, width, 22, "Outlines", Icon.OUTLINE,
                profile.showOutlines, () -> { storeFields(); profile.showOutlines = !profile.showOutlines; saveAndRefresh(); }));
        addRenderableWidget(new ToggleChip(x + width + gap, y, width, 22, "Fluids", Icon.DROP,
                profile.showFluids, () -> { storeFields(); profile.showFluids = !profile.showFluids; saveAndRefresh(); }));
        addRenderableWidget(new ToggleChip(x + (width + gap) * 2, y, width, 22, "Hand", Icon.HAND,
                profile.showHand, () -> { storeFields(); profile.showHand = !profile.showHand; saveAndRefresh(); }));
    }

    private void buildRangeControls(XrayConfig config, int x, int available, int y) {
        int gap = 5;
        int controlWidth = (available - gap) / 2;
        addRenderableWidget(new ModernIntSlider(x, y, controlWidth, 22, "Radius", "chunks", Icon.RADAR,
                1, 12, 1, config.scanRadiusChunks, value -> config.scanRadiusChunks = value,
                this::commitRangeChange));
        addRenderableWidget(new ModernIntSlider(x + controlWidth + gap, y, controlWidth, 22,
                "Height", "blocks", Icon.HEIGHT, 16, 256, 16, config.verticalRadius,
                value -> config.verticalRadius = value, this::commitRangeChange));
    }

    private static ItemStack presetItem(String preset) {
        return switch (preset) {
            case "valuables" -> new ItemStack(Items.EMERALD);
            case "nether" -> new ItemStack(Items.ANCIENT_DEBRIS);
            case "structures" -> new ItemStack(Items.CHEST);
            case "entities" -> new ItemStack(Items.SPYGLASS);
            default -> new ItemStack(Items.DIAMOND_ORE);
        };
    }

    private void addWindow() {
        storeAndSave();
        XrayConfig config = ConfigManager.get();
        config.windowCount++;
        config.normalize();
        selected = config.windowCount - 1;
        saveAndRefresh();
    }

    private void removeWindow() {
        storeAndSave();
        XrayConfig config = ConfigManager.get();
        config.windowCount--;
        config.normalize();
        selected = Math.max(0, Math.min(selected, config.windowCount - 1));
        saveAndRefresh();
    }

    private void switchScreen(int target) {
        if (target == selected) return;
        storeAndSave();
        selected = target;
        if (minecraft != null) minecraft.setScreen(new XraySettingsScreen(selected));
    }

    private void applyPreset(String preset) {
        storeFields();
        XrayPresets.apply(ConfigManager.get().screen(selected), preset);
        saveAndRefresh();
    }

    private void openBlockPicker() {
        storeAndSave();
        if (minecraft != null) minecraft.setScreen(new BlockPickerScreen(selected));
    }

    private void openEntityPicker() {
        storeAndSave();
        if (minecraft != null) minecraft.setScreen(new EntityPickerScreen(selected));
    }

    private void storeFields() {
        if (nameField == null || blockField == null || entityField == null
                || ConfigManager.get().windowCount == 0) return;
        XrayProfile profile = ConfigManager.get().screen(selected);
        String name = nameField.getValue().strip();
        List<String> blocks = SelectorRule.parseCsv(blockField.getValue());
        List<String> entities = SelectorRule.parseCsv(entityField.getValue());
        boolean changed = !name.equals(profile.name) || !blocks.equals(profile.blocks) || !entities.equals(profile.entities);
        profile.name = name;
        profile.blocks = blocks;
        profile.entities = entities;
        if (changed) profile.preset = "custom";
    }

    private void storeAndSave() { storeFields(); ConfigManager.changed(); }

    private void saveAndRefresh() {
        ConfigManager.changed();
        if (minecraft != null) {
            MultiWindowManager.applyConfig(minecraft);
            minecraft.setScreen(new XraySettingsScreen(selected));
        }
    }

    private void commitRangeChange() {
        storeAndSave();
        if (minecraft != null) MultiWindowManager.applyConfig(minecraft);
    }

    @Override
    public void onClose() {
        storeAndSave();
        if (minecraft != null) {
            MultiWindowManager.applyConfig(minecraft);
            super.onClose();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(panelX, 4, panelX + panelWidth, height - 3, PANEL);
        graphics.fill(panelX, 4, panelX + panelWidth, 6, ACCENT_DARK);
        graphics.drawString(font, "MULTISCREEN", panelX + 10, 11, ACCENT, false);
        graphics.drawString(font, "X-RAY", panelX + 83, 11, 0xFFFFFFFF, false);
        int activeScreens = ConfigManager.get().windowCount;
        String status = activeScreens == 0 ? "NO ACTIVE WINDOWS" : "SCREEN " + (selected + 1) + " / " + activeScreens;
        graphics.drawString(font, status, panelX + panelWidth - font.width(status) - 10, 11, MUTED, false);
        if (activeScreens == 0) {
            graphics.drawCenteredString(font, "No X-ray windows are open", width / 2, 84, 0xFFD8E1EB);
            graphics.drawCenteredString(font, "Use + or F8 to add one", width / 2, 101, MUTED);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void tick() {
        super.tick();
        int currentWindowCount = ConfigManager.get().windowCount;
        if (currentWindowCount != observedWindowCount && minecraft != null) {
            storeAndSave();
            minecraft.setScreen(new XraySettingsScreen(Math.min(selected, Math.max(0, currentWindowCount - 1))));
        }
    }

    @Override public boolean isPauseScreen() { return false; }

    private enum Icon { PLUS, MINUS, CHECK, OUTLINE, DROP, HAND, RADAR, HEIGHT, BLOCK, ENTITY }

    private static class ModernButton extends AbstractButton {
        private final Icon icon;
        private final boolean accent;
        private final Runnable action;

        ModernButton(int x, int y, int width, int height, Component message, Icon icon, boolean accent, Runnable action) {
            super(x, y, width, height, message);
            this.icon = icon;
            this.accent = accent;
            this.action = action;
        }

        @Override public void onPress(InputWithModifiers input) { action.run(); }

        @Override
        protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int color = !active ? 0x88404A56 : accent ? (isHoveredOrFocused() ? 0xFF5DE2FF : ACCENT_DARK)
                    : (isHoveredOrFocused() ? CARD_HOVER : CARD);
            graphics.fill(getX(), getY(), getX() + width, getY() + height, color);
            if (isHoveredOrFocused() && active) graphics.fill(getX(), getY() + height - 2, getX() + width, getY() + height, ACCENT);
            int iconWidth = icon == null ? 0 : 12;
            int textWidth = Minecraft.getInstance().font.width(getMessage());
            boolean showText = width >= textWidth + iconWidth + 10;
            if (!showText) textWidth = 0;
            int start = getX() + Math.max(5, (width - textWidth - iconWidth) / 2);
            if (icon != null) drawIcon(graphics, icon, start, getY() + height / 2 - 4, active ? 0xFFFFFFFF : MUTED);
            if (showText) graphics.drawString(Minecraft.getInstance().font, getMessage(), start + iconWidth,
                    getY() + (height - 8) / 2, active ? 0xFFFFFFFF : MUTED, false);
        }

        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }

    private static final class ScreenTab extends AbstractButton {
        private final int number;
        private final boolean selected;
        private final Runnable action;

        ScreenTab(int x, int y, int width, int height, int number, boolean selected, Runnable action) {
            super(x, y, width, height, Component.literal("Screen " + number));
            this.number = number;
            this.selected = selected;
            this.action = action;
        }

        @Override public void onPress(InputWithModifiers input) { action.run(); }

        @Override
        protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int color = selected ? ACCENT_DARK : isHoveredOrFocused() ? CARD_HOVER : CARD;
            graphics.fill(getX(), getY(), getX() + width, getY() + height, color);
            int cx = getX() + width / 2;
            graphics.fill(cx - 7, getY() + 4, cx + 7, getY() + 14, selected ? 0xFFFFFFFF : MUTED);
            graphics.fill(cx - 5, getY() + 6, cx + 5, getY() + 12, selected ? 0xFF10202A : 0xFF1C2633);
            graphics.fill(cx - 1, getY() + 14, cx + 1, getY() + 17, selected ? 0xFFFFFFFF : MUTED);
            graphics.drawCenteredString(Minecraft.getInstance().font, Integer.toString(number), cx, getY() + 6,
                    selected ? ACCENT : 0xFFD8E1EB);
            if (selected) graphics.fill(getX(), getY() + height - 2, getX() + width, getY() + height, ACCENT);
        }

        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }

    private static final class PresetCard extends AbstractButton {
        private final ItemStack item;
        private final boolean selected;
        private final Runnable action;

        PresetCard(int x, int y, int width, int height, String id, ItemStack item, boolean selected, Runnable action) {
            super(x, y, width, height, Component.literal(displayName(id)));
            this.item = item;
            this.selected = selected;
            this.action = action;
        }

        @Override public void onPress(InputWithModifiers input) { action.run(); }

        @Override
        protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(getX(), getY(), getX() + width, getY() + height,
                    selected ? 0xFF173A48 : isHoveredOrFocused() ? CARD_HOVER : CARD);
            if (selected) {
                graphics.fill(getX(), getY(), getX() + 2, getY() + height, ACCENT);
                graphics.fill(getX(), getY() + height - 2, getX() + width, getY() + height, ACCENT);
            }
            graphics.renderItem(item, getX() + width / 2 - 8, getY() + 5);
            graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(), getX() + width / 2,
                    getY() + 27, selected ? 0xFFFFFFFF : 0xFFCAD4DF);
        }

        private static String displayName(String id) {
            return switch (id) {
                case "valuables" -> "Valuables";
                case "nether" -> "Nether";
                case "structures" -> "Structures";
                case "entities" -> "Entities";
                default -> "Ores";
            };
        }

        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }

    private static final class ToggleChip extends ModernButton {
        ToggleChip(int x, int y, int width, int height, String name, Icon icon, boolean enabled, Runnable action) {
            super(x, y, width, height, Component.literal(name + (enabled ? "  ON" : "  OFF")), icon, enabled, action);
        }
    }

    private static final class ModernIntSlider extends AbstractSliderButton {
        private final String label;
        private final String unit;
        private final Icon icon;
        private final int minimum;
        private final int maximum;
        private final int step;
        private final IntConsumer setter;
        private final Runnable commit;
        private int currentValue;
        private int committedValue;

        ModernIntSlider(int x, int y, int width, int height, String label, String unit, Icon icon,
                        int minimum, int maximum, int step, int currentValue,
                        IntConsumer setter, Runnable commit) {
            super(x, y, width, height, Component.empty(),
                    (double) (currentValue - minimum) / (double) (maximum - minimum));
            this.label = label;
            this.unit = unit;
            this.icon = icon;
            this.minimum = minimum;
            this.maximum = maximum;
            this.step = step;
            this.setter = setter;
            this.commit = commit;
            this.currentValue = currentValue;
            this.committedValue = currentValue;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            if (label != null) setMessage(Component.literal(label + "  " + currentValue + " " + unit));
        }

        @Override
        protected void applyValue() {
            int steps = (maximum - minimum) / step;
            currentValue = Math.max(minimum, Math.min(maximum,
                    minimum + (int) Math.round(value * steps) * step));
            value = (double) (currentValue - minimum) / (double) (maximum - minimum);
            setter.accept(currentValue);
        }

        @Override
        public void onRelease(MouseButtonEvent event) {
            super.onRelease(event);
            commitIfChanged();
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            boolean handled = super.keyPressed(event);
            if (handled) commitIfChanged();
            return handled;
        }

        private void commitIfChanged() {
            if (currentValue == committedValue) return;
            committedValue = currentValue;
            commit.run();
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            int y = getY();
            graphics.fill(x, y, x + width, y + height, isHoveredOrFocused() ? CARD_HOVER : CARD);
            int trackLeft = x + 3;
            int trackRight = x + width - 3;
            int trackY = y + height - 4;
            int knobX = trackLeft + (int) Math.round(value * (trackRight - trackLeft));
            graphics.fill(trackLeft, trackY, trackRight, trackY + 2, 0xFF334457);
            graphics.fill(trackLeft, trackY, knobX, trackY + 2, ACCENT_DARK);
            graphics.fill(knobX - 2, trackY - 2, knobX + 2, trackY + 4, ACCENT);
            drawIcon(graphics, icon, x + 7, y + 5, 0xFFD8E1EB);
            graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(), x + width / 2, y + 6,
                    0xFFFFFFFF);
        }
    }

    private static void drawIcon(GuiGraphics graphics, Icon icon, int x, int y, int color) {
        switch (icon) {
            case PLUS -> { graphics.fill(x + 4, y, x + 6, y + 10, color); graphics.fill(x, y + 4, x + 10, y + 6, color); }
            case MINUS -> graphics.fill(x, y + 4, x + 10, y + 6, color);
            case CHECK -> { graphics.fill(x, y + 5, x + 3, y + 8, color); graphics.fill(x + 2, y + 7, x + 5, y + 10, color); graphics.fill(x + 4, y + 3, x + 7, y + 8, color); graphics.fill(x + 6, y + 1, x + 9, y + 5, color); }
            case OUTLINE -> { graphics.fill(x, y, x + 10, y + 1, color); graphics.fill(x, y + 9, x + 10, y + 10, color); graphics.fill(x, y, x + 1, y + 10, color); graphics.fill(x + 9, y, x + 10, y + 10, color); }
            case DROP -> { graphics.fill(x + 4, y, x + 6, y + 3, color); graphics.fill(x + 2, y + 3, x + 8, y + 8, color); graphics.fill(x + 3, y + 8, x + 7, y + 10, color); }
            case HAND -> { graphics.fill(x + 2, y + 3, x + 9, y + 9, color); graphics.fill(x, y + 5, x + 3, y + 8, color); graphics.fill(x + 3, y, x + 4, y + 5, color); graphics.fill(x + 5, y + 1, x + 6, y + 5, color); }
            case RADAR -> { graphics.fill(x, y + 4, x + 10, y + 6, color); graphics.fill(x + 4, y, x + 6, y + 10, color); graphics.fill(x + 5, y + 2, x + 9, y + 3, color); }
            case HEIGHT -> { graphics.fill(x + 4, y, x + 6, y + 10, color); graphics.fill(x + 2, y, x + 8, y + 2, color); graphics.fill(x + 2, y + 8, x + 8, y + 10, color); }
            case BLOCK -> { graphics.fill(x + 1, y + 2, x + 9, y + 10, color); graphics.fill(x + 3, y, x + 11, y + 8, 0xFFB9F2FF); graphics.fill(x + 3, y + 2, x + 9, y + 8, 0xFF24596A); }
            case ENTITY -> { graphics.fill(x + 4, y, x + 8, y + 4, color); graphics.fill(x + 3, y + 4, x + 9, y + 8, color); graphics.fill(x + 1, y + 5, x + 3, y + 9, color); graphics.fill(x + 9, y + 5, x + 11, y + 9, color); graphics.fill(x + 3, y + 8, x + 5, y + 11, color); graphics.fill(x + 7, y + 8, x + 9, y + 11, color); }
        }
    }
}
