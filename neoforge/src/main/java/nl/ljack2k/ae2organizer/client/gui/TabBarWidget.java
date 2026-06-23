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
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.ljack2k.ae2organizer.client.ClientEvents;
import nl.ljack2k.ae2organizer.client.TabManager;
import nl.ljack2k.ae2organizer.filter.Tab;
import nl.ljack2k.ae2organizer.mixin.AbstractContainerScreenAccessor;
import nl.ljack2k.ae2organizer.mixin.MEStorageScreenAccessor;
import org.jetbrains.annotations.Nullable;

/**
 * The filter tabs as one AE2-themed panel on the right of an AE2 terminal. Row
 * sizing (icon + text + height) is multiplied by the user's {@code tabScale}
 * setting. "All" + the user's tabs are bevelled buttons (raised = inactive,
 * sunken = active) that scroll under a title bar (name + editor gear); the panel
 * grows only as tall as its content, capped at the terminal height.
 * <p>Rendering is here; input is driven by {@link ClientEvents} via cancelable
 * {@code ScreenEvent}s (AE2 consumes scroll/drag before added widgets see them).
 */
public final class TabBarWidget extends AbstractWidget {
    private static final int BASE_ROW_H = 13;
    private static final int BASE_ICON = 11;
    private static final int BASE_ICON_CELL = 15;
    private static final float BASE_TEXT_SCALE = 0.85f;
    private static final int BASE_LABEL_W = 82;
    private static final int ICON = 16;     // vanilla slot base size, for barX math
    private static final int PAD = 4;
    private static final int TITLE_H = 16;
    private static final int GEAR_SZ = 16;
    private static final int GAP = 2;
    private static final int SLOT_FRAME = 18;   // a slot's drawn frame is 18px (16px item + 1px border)
    private static final int SB_W = 8;
    private static final int SB_GAP = 2;

    private final MEStorageScreen<?> terminal;
    private int scroll = 0;
    private boolean draggingScrollbar = false;

    public TabBarWidget(MEStorageScreen<?> terminal) {
        super(0, 0, 1, 1, Component.literal("AE2 Organizer Tabs"));
        this.terminal = terminal;
        Layout l = layout();
        setX(l.panelX);
        setY(l.panelY);
        setWidth(l.panelW);
        setHeight(l.panelH);
    }

    private boolean labels() {
        return TabManager.getSettings().showTabLabels();
    }

    private double scale() {
        return TabManager.getSettings().clampedScale();
    }

    private int imageWidth() {
        return ((AbstractContainerScreenAccessor) terminal).ae2organizer$getImageWidth();
    }

    private int imageHeight() {
        return ((AbstractContainerScreenAccessor) terminal).ae2organizer$getImageHeight();
    }

    private int barX() {
        int guiLeft = terminal.getGuiLeft();
        int guiTop = terminal.getGuiTop();
        int bottom = guiTop + imageHeight();
        int right = guiLeft + imageWidth();
        for (Slot slot : terminal.getMenu().slots) {
            int slotRight = guiLeft + slot.x - 1 + SLOT_FRAME;   // -1: frame starts a pixel before the item
            int slotY = guiTop + slot.y;
            if (slotRight > right && slotY < bottom && slotY + ICON > guiTop) {
                right = slotRight;
            }
        }
        return right + GAP;
    }

    private Layout layout() {
        double scale = scale();
        int rowH = Math.max(9, (int) Math.round(BASE_ROW_H * scale));
        int iconDraw = Math.max(8, (int) Math.round(BASE_ICON * scale));
        int iconCell = Math.max(10, (int) Math.round(BASE_ICON_CELL * scale));
        float textScale = (float) (BASE_TEXT_SCALE * scale);
        int labelW = (int) Math.round(BASE_LABEL_W * scale);
        int listW = labels() ? iconCell + 2 + labelW : iconCell;

        int scrollCount = 1 + TabManager.tabs().size();
        int desiredPanelH = PAD * 2 + TITLE_H + 1 + scrollCount * rowH;
        int minPanelH = PAD * 2 + TITLE_H + 1 + rowH;
        int panelH = Math.min(desiredPanelH, Math.max(minPanelH, imageHeight()));

        int panelX = barX();
        int panelY = terminal.getGuiTop();
        int contentX = panelX + PAD;
        int contentY = panelY + PAD;
        int listTop = contentY + TITLE_H + 1;
        int listH = Math.max(rowH, panelY + panelH - PAD - listTop);
        int visibleRows = Math.max(1, listH / rowH);
        boolean needScroll = scrollCount > visibleRows;
        int maxScroll = Math.max(0, scrollCount - visibleRows);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        int listAreaW = listW + (needScroll ? SB_GAP + SB_W : 0);
        int contentW = Math.max(listAreaW, GEAR_SZ);   // title bar must fit the gear
        int panelW = PAD * 2 + contentW;
        int sbX = contentX + listW + SB_GAP;
        int gearX = contentX + contentW - GEAR_SZ;

        return new Layout(panelX, panelY, panelW, panelH, contentX, contentY, listW, listTop,
                visibleRows, scrollCount, needScroll, maxScroll, sbX, gearX, contentY,
                rowH, iconCell, iconDraw, textScale);
    }

    private record Layout(int panelX, int panelY, int panelW, int panelH, int contentX, int contentY,
                          int listW, int listTop, int visibleRows, int scrollCount, boolean needScroll,
                          int maxScroll, int sbX, int gearX, int gearY,
                          int rowH, int iconCell, int iconDraw, float textScale) {}

    @Nullable
    private static Tab tabForEntry(int entry) {
        return entry == 0 ? null : TabManager.tabs().get(entry - 1);
    }

    private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---- Rendering ---------------------------------------------------------

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Layout l = layout();
        boolean showLabels = labels();
        String activeId = TabManager.activeTabId();
        var font = Minecraft.getInstance().font;

        Ae2Style.panel(graphics, l.panelX, l.panelY, l.panelW, l.panelH);

        if (showLabels) {
            Ae2Style.scaledText(graphics, font, Component.translatable("ae2organizer.panel.title").getString(),
                    l.contentX, l.contentY + 4, Ae2Style.textColor(), l.textScale);
        }
        boolean gearHover = inRect(mouseX, mouseY, l.gearX, l.gearY, GEAR_SZ, GEAR_SZ);
        if (gearHover) {
            graphics.fill(l.gearX, l.gearY, l.gearX + GEAR_SZ, l.gearY + GEAR_SZ, 0x33FFFFFF);
        }
        Icon.COG.getBlitter().dest(l.gearX, l.gearY, GEAR_SZ, GEAR_SZ)
                .colorArgb(Ae2Style.textColor()).blit(graphics);
        graphics.fill(l.contentX, l.contentY + TITLE_H, l.contentX + l.listW, l.contentY + TITLE_H + 1, 0x40000000);

        Component hoverTip = gearHover ? Component.translatable("ae2organizer.editor.title") : null;
        int rows = Math.min(l.visibleRows, l.scrollCount - scroll);
        for (int i = 0; i < rows; i++) {
            int entry = scroll + i;
            int y = l.listTop + i * l.rowH;
            Tab tab = tabForEntry(entry);
            boolean active = (tab == null) ? activeId == null : tab.id().equals(activeId);
            ItemStack icon = (tab == null) ? new ItemStack(Items.COMPASS) : iconStack(tab.icon());
            Component label = (tab == null) ? Component.translatable("ae2organizer.tab.all")
                    : Component.literal(tab.name());
            boolean hovered = inRect(mouseX, mouseY, l.contentX, y, l.listW, l.rowH);

            drawButton(graphics, l.contentX, y, l.listW, l.rowH, active, hovered);
            int off = active ? 1 : 0;
            if (!icon.isEmpty()) {
                Ae2Style.scaledItem(graphics, icon, l.contentX + 2 + off,
                        y + (l.rowH - l.iconDraw) / 2 + off, l.iconDraw);
            }
            if (showLabels) {
                int textW = l.listW - l.iconCell - 4;
                String text = font.plainSubstrByWidth(label.getString(), (int) (textW / l.textScale));
                Ae2Style.scaledText(graphics, font, text, l.contentX + l.iconCell + 2 + off,
                        y + (l.rowH - Math.round(8 * l.textScale)) / 2 + off, Ae2Style.textColor(), l.textScale);
            } else if (hovered) {
                hoverTip = label;
            }
        }

        if (l.needScroll) {
            drawScrollbar(graphics, l);
        }
        if (hoverTip != null) {
            graphics.renderTooltip(font, hoverTip, mouseX, mouseY);
        }
    }

    private static void drawButton(GuiGraphics graphics, int x, int y, int w, int h, boolean active, boolean hovered) {
        Ae2Style.bevelButton(graphics, x, y, w, h, active, hovered);
    }

    private void drawScrollbar(GuiGraphics graphics, Layout l) {
        int sbTop = l.listTop;
        int sbH = l.visibleRows * l.rowH;
        graphics.fill(l.sbX, sbTop, l.sbX + SB_W, sbTop + sbH, 0x66000000);
        int thumbH = Math.max(12, sbH * l.visibleRows / l.scrollCount);
        int travel = sbH - thumbH;
        int thumbY = sbTop + (l.maxScroll == 0 ? 0 : travel * scroll / l.maxScroll);
        drawButton(graphics, l.sbX, thumbY, SB_W, thumbH, false, draggingScrollbar);
    }

    private static ItemStack iconStack(ResourceLocation id) {
        return new ItemStack(BuiltInRegistries.ITEM.getOptional(id).orElse(Items.CHEST));
    }

    // ---- Input (called from ClientEvents) ----------------------------------

    public boolean handleMouseDown(double mouseX, double mouseY) {
        Layout l = layout();
        if (!inRect(mouseX, mouseY, l.panelX, l.panelY, l.panelW, l.panelH)) {
            return false;
        }
        if (inRect(mouseX, mouseY, l.gearX, l.gearY, GEAR_SZ, GEAR_SZ)) {
            playClick();
            Minecraft.getInstance().setScreen(new TabEditorScreen(terminal));
            return true;
        }
        int listBottom = l.listTop + l.visibleRows * l.rowH;
        if (l.needScroll && inRect(mouseX, mouseY, l.sbX, l.listTop, SB_W, l.visibleRows * l.rowH)) {
            draggingScrollbar = true;
            scrollTo(mouseY, l);
            return true;
        }
        if (mouseX >= l.contentX && mouseX < l.contentX + l.listW && mouseY >= l.listTop && mouseY < listBottom) {
            int row = (int) ((mouseY - l.listTop) / l.rowH);
            int entry = scroll + row;
            if (row < Math.min(l.visibleRows, l.scrollCount - scroll) && entry >= 0 && entry < l.scrollCount) {
                Tab tab = tabForEntry(entry);
                TabManager.setActive(tab == null ? null : tab.id());
                if (TabManager.getSettings().clearSearchOnTabSelect()) {
                    MEStorageScreenAccessor acc = (MEStorageScreenAccessor) terminal;
                    acc.ae2organizer$getSearchField().setValue("");
                    acc.ae2organizer$getRepo().setSearchString("");
                }
                ClientEvents.applyFilter(terminal, TabManager.activePredicate());
                playClick();
            }
        }
        return true;
    }

    public boolean handleMouseDrag(double mouseX, double mouseY) {
        if (draggingScrollbar) {
            scrollTo(mouseY, layout());
            return true;
        }
        return false;
    }

    public void handleMouseUp() {
        draggingScrollbar = false;
    }

    public boolean handleScroll(double mouseX, double mouseY, double deltaY) {
        Layout l = layout();
        if (!inRect(mouseX, mouseY, l.panelX, l.panelY, l.panelW, l.panelH)) {
            return false;
        }
        if (l.needScroll) {
            scroll = Math.max(0, Math.min(scroll + (deltaY < 0 ? 1 : -1), l.maxScroll));
        }
        return true;
    }

    private void scrollTo(double mouseY, Layout l) {
        if (l.maxScroll == 0) {
            scroll = 0;
            return;
        }
        double fraction = (mouseY - l.listTop) / Math.max(1, l.visibleRows * l.rowH);
        scroll = Math.max(0, Math.min((int) Math.round(fraction * l.maxScroll), l.maxScroll));
    }

    private void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        Layout l = layout();
        return inRect(mouseX, mouseY, l.panelX, l.panelY, l.panelW, l.panelH);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
    }
}
