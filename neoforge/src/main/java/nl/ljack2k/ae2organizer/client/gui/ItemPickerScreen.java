package nl.ljack2k.ae2organizer.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * A modal, searchable grid of all items. Click one to invoke the callback (the
 * caller is responsible for navigating away, e.g. back to the editor). ESC /
 * clicking outside cancels back to the parent.
 */
public final class ItemPickerScreen extends Screen {
    private static final int COLS = 9;
    private static final int CELL = 18;

    private final Screen parent;
    private final Consumer<Item> onPick;
    private final List<Item> all;
    private List<Item> filtered;
    private int scrollRow = 0;

    private int left;
    private int top;
    private int panelW;
    private int panelH;
    private int gridLeft;
    private int gridTop;
    private int visibleRows;

    public ItemPickerScreen(Screen parent, Component title, Consumer<Item> onPick) {
        super(title);
        this.parent = parent;
        this.onPick = onPick;
        this.all = BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR).toList();
        this.filtered = this.all;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        panelW = Math.min(COLS * CELL + 16, this.width - 20);
        panelH = Math.min(220, this.height - 20);
        left = (this.width - panelW) / 2;
        top = (this.height - panelH) / 2;
        gridLeft = left + 8;
        gridTop = top + 44;
        visibleRows = Math.max(1, (top + panelH - 8 - gridTop) / CELL);

        EditBox search = Ae2Style.textField(this.font, left + 8, top + 20, panelW - 16, 16, Component.literal("Search"));
        search.setHint(Component.literal("search items…"));
        search.setResponder(this::applyFilter);
        addRenderableWidget(search);
        setInitialFocus(search);
    }

    private void applyFilter(String query) {
        scrollRow = 0;
        String needle = query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            filtered = all;
            return;
        }
        List<Item> out = new ArrayList<>();
        for (Item item : all) {
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            if (id.contains(needle)
                    || new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT).contains(needle)) {
                out.add(item);
            }
        }
        filtered = out;
    }

    private int maxScrollRow() {
        int rows = (filtered.size() + COLS - 1) / COLS;
        return Math.max(0, rows - visibleRows);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, Ae2Style.DIM);
        Ae2Style.panel(graphics, left, top, panelW, panelH);
        graphics.drawString(this.font, getTitle(), left + 8, top + 7, Ae2Style.textColor(), false);
        super.render(graphics, mouseX, mouseY, partialTick);

        Item hovered = null;
        int cells = visibleRows * COLS;
        for (int i = 0; i < cells; i++) {
            int idx = scrollRow * COLS + i;
            if (idx >= filtered.size()) {
                break;
            }
            int cx = gridLeft + (i % COLS) * CELL;
            int cy = gridTop + (i / COLS) * CELL;
            boolean hov = mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL;
            if (hov) {
                graphics.fill(cx, cy, cx + CELL, cy + CELL, 0x80FFFFFF);
                hovered = filtered.get(idx);
            }
            graphics.renderItem(new ItemStack(filtered.get(idx)), cx + 1, cy + 1);
        }
        if (hovered != null) {
            graphics.renderTooltip(this.font, new ItemStack(hovered), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= gridLeft && mouseY >= gridTop) {
            int col = (int) ((mouseX - gridLeft) / CELL);
            int row = (int) ((mouseY - gridTop) / CELL);
            if (col >= 0 && col < COLS && row >= 0 && row < visibleRows) {
                int idx = (scrollRow + row) * COLS + col;
                if (idx >= 0 && idx < filtered.size()) {
                    onPick.accept(filtered.get(idx));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollRow = Math.max(0, Math.min(scrollRow - (int) Math.signum(scrollY), maxScrollRow()));
        return true;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
