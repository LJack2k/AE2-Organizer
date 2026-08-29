package nl.ljack2k.ae2organizer.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import nl.ljack2k.ae2organizer.backend.ScreenAdapter;
import nl.ljack2k.ae2organizer.backend.Theme;
import nl.ljack2k.ae2organizer.client.ClientEvents;
import nl.ljack2k.ae2organizer.client.TabManager;
import nl.ljack2k.ae2organizer.filter.Condition;
import nl.ljack2k.ae2organizer.filter.ModCondition;
import nl.ljack2k.ae2organizer.filter.Tab;
import nl.ljack2k.ae2organizer.filter.TextCondition;

import java.util.ArrayList;
import java.util.List;

/**
 * In-place modal shown when an item carried on the cursor is dropped onto an
 * existing tab in a {@link TabBarWidget}: asks whether to add the item to that
 * tab's conditions by name or by mod, or cancel. Not a {@code Screen} — it is
 * rendered and click-driven by {@code ClientEvents} over the live terminal (same
 * pattern as the move banner), so the carried item stays on the cursor.
 */
public final class AddToTabDialog {
    private static final int PAD = 10;
    private static final int BTN_W = 62;
    private static final int BTN_H = 18;
    private static final int GAP = 6;
    /** Modal dim: lighter than {@link RsStyle#DIM} so the terminal stays visible. */
    private static final int MODAL_DIM = 0x60101018;

    private final ScreenAdapter adapter;
    private final TabManager.Store store;
    private final Theme theme;
    private final Tab tab;
    private final ItemStack stack;
    private final String itemName;
    private final String modId;

    public AddToTabDialog(ScreenAdapter adapter, TabManager.Store store, Theme theme, Tab tab, ItemStack stack) {
        this.adapter = adapter;
        this.store = store;
        this.theme = theme;
        this.tab = tab;
        this.stack = stack;
        this.itemName = stack.getHoverName().getString();
        this.modId = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
    }

    private static int screenW() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    private static int screenH() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }

    /** Panel rect {x, y, w, h}, recomputed per call so a window resize recenters it. */
    private int[] panelRect() {
        var font = Minecraft.getInstance().font;
        int line1W = 20 + font.width("Add \"" + itemName + "\"");
        int line2W = 20 + font.width("to \"" + tab.name() + "\"");
        int btnsW = 3 * BTN_W + 2 * GAP;
        int w = PAD * 2 + Math.max(btnsW, Math.max(line1W, line2W));
        int h = PAD + 34 + BTN_H + PAD;
        return new int[]{(screenW() - w) / 2, (screenH() - h) / 2, w, h};
    }

    /** Button rects in order: by-name, by-mod, cancel. */
    private int[][] buttonRects(int[] p) {
        int bx = p[0] + (p[2] - (3 * BTN_W + 2 * GAP)) / 2;
        int by = p[1] + PAD + 34;
        return new int[][]{
                {bx, by, BTN_W, BTN_H},
                {bx + BTN_W + GAP, by, BTN_W, BTN_H},
                {bx + 2 * (BTN_W + GAP), by, BTN_W, BTN_H}};
    }

    /**
     * Drawing later is not enough to be on top — depth testing is on, so this modal
     * must also be <em>nearer</em> than everything the container screen already
     * drew, or it loses the depth test. The ceiling to clear (all verified against
     * 1.20.1 bytecode): slot items z≈150, their count/damage decorations z=200,
     * vanilla tooltips z=400, and the highest of all — the carried stack, which
     * {@code AbstractContainerScreen#renderFloatingItem} draws at z=232 with its
     * decorations nested inside that pose, i.e. z=432. Hence 500.
     */
    private static final int Z_ABOVE_SCREEN = 500;

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        // Publish this backend's palette to the theme-neutral widget helpers.
        RsStyle.useTheme(theme);
        var font = Minecraft.getInstance().font;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, Z_ABOVE_SCREEN);
        renderContents(graphics, font, mouseX, mouseY);
        graphics.pose().popPose();
    }

    private void renderContents(GuiGraphics graphics, net.minecraft.client.gui.Font font, int mouseX, int mouseY) {
        graphics.fill(0, 0, screenW(), screenH(), MODAL_DIM);
        int[] p = panelRect();
        theme.panel(graphics, p[0], p[1], p[2], p[3]);
        int tc = theme.textColor();
        int tx = p[0] + PAD;
        int ty = p[1] + PAD;
        graphics.renderItem(stack, tx, ty);
        graphics.drawString(font, "Add \"" + itemName + "\"", tx + 20, ty + 4, tc, false);
        graphics.drawString(font, "to \"" + tab.name() + "\"", tx + 20, ty + 20, tc, false);

        int[][] btns = buttonRects(p);
        String[] labels = {"By name", "By mod", "Cancel"};
        Component tip = null;
        for (int i = 0; i < 3; i++) {
            int[] b = btns[i];
            boolean hovered = inRect(mouseX, mouseY, b[0], b[1], b[2], b[3]);
            RsStyle.labelButton(graphics, font, Component.literal(labels[i]), b[0], b[1], b[2], b[3], hovered, false);
            if (hovered && i == 0) {
                tip = Component.literal("Adds name filter: " + itemName);
            } else if (hovered && i == 1) {
                tip = Component.literal("Adds mod filter: " + modId);
            }
        }
        if (tip != null) {
            graphics.renderTooltip(font, tip, mouseX, mouseY);
        }
    }

    /**
     * Handles a left-click while the dialog is open.
     *
     * @return {@code true} when the dialog should close (a button was pressed or
     *         the click landed outside the panel — treated as cancel).
     */
    public boolean handleClick(double mouseX, double mouseY) {
        int[] p = panelRect();
        if (!inRect(mouseX, mouseY, p[0], p[1], p[2], p[3])) {
            return true;
        }
        int[][] btns = buttonRects(p);
        if (inRect(mouseX, mouseY, btns[0][0], btns[0][1], btns[0][2], btns[0][3])) {
            playClick();
            apply(new TextCondition(itemName.trim(), false));
            return true;
        }
        if (inRect(mouseX, mouseY, btns[1][0], btns[1][1], btns[1][2], btns[1][3])) {
            playClick();
            apply(new ModCondition(modId, false));
            return true;
        }
        if (inRect(mouseX, mouseY, btns[2][0], btns[2][1], btns[2][2], btns[2][3])) {
            playClick();
            return true;
        }
        return false;
    }

    private void apply(Condition condition) {
        List<Condition> conditions = new ArrayList<>(tab.conditions());
        if (!conditions.contains(condition)) {
            conditions.add(condition);
            store.updateTab(new Tab(tab.id(), tab.name(), tab.icon(), tab.mode(), conditions, tab.window()));
            ClientEvents.applyFilter(adapter, store);
        }
    }

    private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
