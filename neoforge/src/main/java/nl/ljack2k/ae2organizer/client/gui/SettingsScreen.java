package nl.ljack2k.ae2organizer.client.gui;

import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.AECheckbox;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.ljack2k.ae2organizer.client.TabManager;
import nl.ljack2k.ae2organizer.filter.Settings;
import org.jetbrains.annotations.Nullable;

/** Windowed, client-only general settings, themed via {@link Ae2Style}. */
public final class SettingsScreen extends Screen {

    private final Screen parent;
    private boolean resetFilterOnOpen;
    private boolean showTabLabels;
    private double tabScale;
    private boolean clearSearchOnTabSelect;

    @Nullable
    private AECheckbox resetBox;
    @Nullable
    private AECheckbox labelsBox;
    @Nullable
    private AECheckbox clearSearchBox;

    private int left;
    private int top;
    private int panelW;
    private int panelH;
    private int previewY;

    public SettingsScreen(Screen parent) {
        super(Component.literal("AE2 Organizer Settings"));
        this.parent = parent;
        Settings current = TabManager.getSettings();
        this.resetFilterOnOpen = current.resetFilterOnOpen();
        this.showTabLabels = current.showTabLabels();
        this.tabScale = current.clampedScale();
        this.clearSearchOnTabSelect = current.clearSearchOnTabSelect();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        panelW = Math.min(360, this.width - 20);
        panelH = Math.min(212, this.height - 20);
        left = (this.width - panelW) / 2;
        top = (this.height - panelH) / 2;
        previewY = top + 134;

        ScreenStyle style = Ae2Style.style();
        if (style != null) {
            resetBox = new AECheckbox(left + 10, top + 28, panelW - 20, 18, style,
                    Component.literal("Reset filter when opening a terminal"));
            resetBox.setSelected(resetFilterOnOpen);
            addRenderableWidget(resetBox);

            labelsBox = new AECheckbox(left + 10, top + 52, panelW - 20, 18, style,
                    Component.literal("Show tab names as labels"));
            labelsBox.setSelected(showTabLabels);
            addRenderableWidget(labelsBox);

            clearSearchBox = new AECheckbox(left + 10, top + 76, panelW - 20, 18, style,
                    Component.literal("Clear search bar when selecting a tab"));
            clearSearchBox.setSelected(clearSearchOnTabSelect);
            addRenderableWidget(clearSearchBox);
        } else {
            addRenderableWidget(CycleButton.onOffBuilder(resetFilterOnOpen)
                    .create(left + 10, top + 28, panelW - 20, 18,
                            Component.literal("Reset filter when opening a terminal"),
                            (btn, val) -> resetFilterOnOpen = val));
            addRenderableWidget(CycleButton.onOffBuilder(showTabLabels)
                    .create(left + 10, top + 52, panelW - 20, 18,
                            Component.literal("Show tab names as labels"),
                            (btn, val) -> showTabLabels = val));
            addRenderableWidget(CycleButton.onOffBuilder(clearSearchOnTabSelect)
                    .create(left + 10, top + 76, panelW - 20, 18,
                            Component.literal("Clear search bar when selecting a tab"),
                            (btn, val) -> clearSearchOnTabSelect = val));
        }

        addRenderableWidget(new SizeSlider(left + 10, top + 100, panelW - 20, 18));

        int actionY = top + panelH - 26;
        addRenderableWidget(new AE2Button(left + panelW - 130, actionY, 58, 20,
                Component.literal("Save"), b -> {
            boolean reset = resetBox != null ? resetBox.isSelected() : resetFilterOnOpen;
            boolean labels = labelsBox != null ? labelsBox.isSelected() : showTabLabels;
            boolean clearSearch = clearSearchBox != null ? clearSearchBox.isSelected() : clearSearchOnTabSelect;
            TabManager.setSettings(new Settings(reset, labels, tabScale, clearSearch));
            onClose();
        }));
        addRenderableWidget(new AE2Button(left + panelW - 68, actionY, 58, 20,
                Component.literal("Cancel"), b -> onClose()));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, Ae2Style.DIM);
        Ae2Style.panel(graphics, left, top, panelW, panelH);
        int tc = Ae2Style.textColor();
        graphics.drawString(this.font, getTitle(), left + 10, top + 9, tc, false);
        graphics.drawString(this.font, "Preview", left + 10, previewY - 10, tc, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        drawPreview(graphics, left + 10, previewY);
    }

    /** A sample tab row at the current scale, so the slider has a live preview. */
    private void drawPreview(GuiGraphics graphics, int x, int y) {
        double s = tabScale;
        int rowH = Math.max(9, (int) Math.round(13 * s));
        int iconDraw = Math.max(8, (int) Math.round(11 * s));
        int iconCell = Math.max(10, (int) Math.round(15 * s));
        float ts = (float) (0.85 * s);
        int labelW = (int) Math.round(82 * s);
        int w = iconCell + 2 + labelW;
        Ae2Style.bevelButton(graphics, x, y, w, rowH, false, false);
        Ae2Style.scaledItem(graphics, new ItemStack(Items.COMPASS), x + 2, y + (rowH - iconDraw) / 2, iconDraw);
        Ae2Style.scaledText(graphics, this.font, "Filter name", x + iconCell + 2,
                y + (rowH - Math.round(8 * ts)) / 2, Ae2Style.textColor(), ts);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    private final class SizeSlider extends AbstractSliderButton {
        SizeSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(),
                    (tabScale - Settings.MIN_SCALE) / (Settings.MAX_SCALE - Settings.MIN_SCALE));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Tab size: " + Math.round(tabScale * 100) + "%"));
        }

        @Override
        protected void applyValue() {
            tabScale = Settings.MIN_SCALE + this.value * (Settings.MAX_SCALE - Settings.MIN_SCALE);
        }
    }
}
