package nl.ljack2k.ae2organizer.client.gui;

import appeng.client.gui.Icon;
import appeng.client.gui.me.common.MEStorageScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.ljack2k.ae2organizer.client.ClientEvents;
import nl.ljack2k.ae2organizer.client.TabManager;
import nl.ljack2k.ae2organizer.filter.Tab;
import nl.ljack2k.ae2organizer.mixin.AbstractContainerScreenAccessor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A vertical strip of tabs on the right exterior of an AE2 terminal, drawn with
 * AE2's themed panel ({@link Ae2Style}) and {@link Icon#COG} so it matches AE2
 * and inherits AE2 dark-mode packs. Row 0 is "All", the middle rows are the
 * user's tabs (mouse-wheel scrollable when they overflow), and the last row
 * opens the editor. Rebuilt every screen init from {@link TabManager} state.
 */
public final class TabBarWidget extends AbstractWidget {
    private static final int CELL = 22;
    private static final int LABEL_CELL_W = 104;
    private static final int ICON_INSET = 3;
    private static final int GAP = 2;

    private final MEStorageScreen<?> terminal;
    private int scroll = 0;

    private enum RowType { ALL, TAB, GEAR }

    private record RowEntry(RowType type, @Nullable Tab tab) {}

    public TabBarWidget(MEStorageScreen<?> terminal) {
        super(0, 0, CELL, CELL, Component.literal("AE2 Organizer Tabs"));
        this.terminal = terminal;
        setX(barX());
        setY(terminal.getGuiTop());
        setWidth(cellW());
        setHeight(availableRows() * CELL);
    }

    private boolean labels() {
        return TabManager.getSettings().showTabLabels();
    }

    private int cellW() {
        return labels() ? LABEL_CELL_W : CELL;
    }

    private int barX() {
        return terminal.getGuiLeft() + imageWidth() + GAP;
    }

    private int imageWidth() {
        return ((AbstractContainerScreenAccessor) terminal).ae2organizer$getImageWidth();
    }

    private int availableRows() {
        int height = ((AbstractContainerScreenAccessor) terminal).ae2organizer$getImageHeight();
        return Math.max(3, height / CELL);
    }

    private int middleRows() {
        return Math.max(1, availableRows() - 2);
    }

    private void clampScroll(int tabCount) {
        int max = Math.max(0, tabCount - middleRows());
        scroll = Math.max(0, Math.min(scroll, max));
    }

    private List<RowEntry> computeRows() {
        List<Tab> tabs = TabManager.tabs();
        clampScroll(tabs.size());
        int visible = Math.min(tabs.size(), middleRows());
        List<RowEntry> rows = new ArrayList<>(visible + 2);
        rows.add(new RowEntry(RowType.ALL, null));
        for (int i = 0; i < visible; i++) {
            rows.add(new RowEntry(RowType.TAB, tabs.get(scroll + i)));
        }
        rows.add(new RowEntry(RowType.GEAR, null));
        return rows;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        List<RowEntry> rows = computeRows();
        int x = barX();
        int top = terminal.getGuiTop();
        int w = cellW();
        boolean showLabels = labels();
        String activeId = TabManager.activeTabId();

        Component hoverTip = null;
        for (int i = 0; i < rows.size(); i++) {
            RowEntry row = rows.get(i);
            int y = top + i * CELL;
            boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + CELL;

            boolean active;
            ItemStack icon;
            Component label;
            switch (row.type()) {
                case ALL -> {
                    active = activeId == null;
                    icon = new ItemStack(Items.COMPASS);
                    label = Component.translatable("ae2organizer.tab.all");
                }
                case GEAR -> {
                    active = false;
                    icon = ItemStack.EMPTY;
                    label = Component.translatable("ae2organizer.editor.title");
                }
                default -> {
                    active = row.tab().id().equals(activeId);
                    icon = iconStack(row.tab().icon());
                    label = Component.literal(row.tab().name());
                }
            }

            Ae2Style.panel(graphics, x, y, w, CELL);
            if (row.type() == RowType.GEAR) {
                Icon.COG.getBlitter().dest(x + ICON_INSET, y + ICON_INSET, 16, 16).blit(graphics);
            } else if (!icon.isEmpty()) {
                graphics.renderItem(icon, x + ICON_INSET, y + ICON_INSET);
            }
            if (showLabels) {
                String text = Minecraft.getInstance().font.plainSubstrByWidth(label.getString(), w - 22);
                graphics.drawString(Minecraft.getInstance().font, text, x + 20, y + 7, Ae2Style.textColor(), false);
            } else if (hovered) {
                hoverTip = label;
            }
            if (active) {
                int c = Ae2Style.selectionColor();
                graphics.fill(x, y, x + w, y + 1, c);
                graphics.fill(x, y + CELL - 1, x + w, y + CELL, c);
                graphics.fill(x, y, x + 1, y + CELL, c);
                graphics.fill(x + w - 1, y, x + w, y + CELL, c);
            }
        }
        if (hoverTip != null) {
            graphics.renderTooltip(Minecraft.getInstance().font, hoverTip, mouseX, mouseY);
        }
    }

    private static ItemStack iconStack(ResourceLocation id) {
        return new ItemStack(BuiltInRegistries.ITEM.getOptional(id).orElse(Items.CHEST));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isMouseOver(mouseX, mouseY)) {
            return false;
        }
        int top = terminal.getGuiTop();
        int index = (int) ((mouseY - top) / CELL);
        List<RowEntry> rows = computeRows();
        if (index < 0 || index >= rows.size()) {
            return false;
        }
        RowEntry row = rows.get(index);
        switch (row.type()) {
            case ALL -> {
                TabManager.setActive(null);
                ClientEvents.applyFilter(terminal, null);
            }
            case GEAR -> Minecraft.getInstance().setScreen(new TabEditorScreen(terminal));
            case TAB -> {
                TabManager.setActive(row.tab().id());
                ClientEvents.applyFilter(terminal, TabManager.activePredicate());
            }
        }
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }
        int tabCount = TabManager.tabs().size();
        if (tabCount <= middleRows()) {
            return false;
        }
        scroll += (scrollY < 0) ? 1 : -1;
        clampScroll(tabCount);
        return true;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        int x = barX();
        int top = terminal.getGuiTop();
        return mouseX >= x && mouseX < x + cellW() && mouseY >= top && mouseY < top + availableRows() * CELL;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
    }
}
