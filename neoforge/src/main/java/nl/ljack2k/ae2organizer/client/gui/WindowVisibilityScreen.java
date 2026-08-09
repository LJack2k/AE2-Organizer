package nl.ljack2k.ae2organizer.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import nl.ljack2k.ae2organizer.backend.Theme;
import nl.ljack2k.ae2organizer.client.TabManager;

import java.util.List;
import java.util.Set;

/**
 * Per-window visibility editor: a small list of the grid types the window
 * already has coordinates for, each a toggle to show/hide the window there.
 * Edits the passed {@code hidden} set in place; the editor persists it on Save.
 */
public final class WindowVisibilityScreen extends Screen {

    private static final int BTN_H = 18;

    private final Screen parent;
    private final List<String> terminals;
    private final Set<String> hidden;
    private final String currentKey;
    private final TabManager.Store store;
    private final Theme theme;

    private int left, top, panelW, panelH;

    public WindowVisibilityScreen(Screen parent, List<String> terminals, Set<String> hidden, String currentKey,
                                  TabManager.Store store, Theme theme) {
        super(Component.literal("Show on grids"));
        this.parent = parent;
        this.terminals = terminals;
        this.hidden = hidden;
        this.currentKey = currentKey;
        this.store = store;
        this.theme = theme;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        int rows = Math.max(1, terminals.size());
        panelW = Math.min(320, this.width - 20);
        panelH = Math.min(this.height - 20, 44 + rows * 22 + 30);
        left = (this.width - panelW) / 2;
        top = (this.height - panelH) / 2;

        int x = left + 10;
        int w = panelW - 20;
        int y = top + 28;
        for (String key : terminals) {
            final String k = key;
            boolean vis = !hidden.contains(k);
            String label = displayName(k) + (k.equals(currentKey) ? " (this one)" : "") + " — " + (vis ? "Shown" : "Hidden");
            addRenderableWidget(new RsButton(x, y, w, BTN_H, Component.literal(label), b -> {
                if (hidden.contains(k)) {
                    hidden.remove(k);
                } else {
                    hidden.add(k);
                }
                rebuildWidgets();
            }));
            y += 22;
        }

        addRenderableWidget(new RsButton(left + panelW - 68, top + panelH - 26, 58, 20,
                Component.literal("Done"), b -> onClose()));
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        graphics.fill(0, 0, this.width, this.height, RsStyle.DIM);
        theme.panel(graphics, left, top, panelW, panelH);
        int tc = theme.textColor();
        graphics.drawString(this.font, getTitle(), left + 10, top + 9, tc, false);
    }

    /** A captured screen title if there was one, else a known Refined Storage name, else prettified id. */
    String displayName(String key) {
        String remembered = store.terminalName(key);
        if (remembered != null && !remembered.isBlank()) {
            return remembered;
        }
        return switch (key) {
            case "refinedstorage:grid" -> "Grid";
            case "refinedstorage:crafting_grid" -> "Crafting Grid";
            case "refinedstorage:pattern_grid" -> "Pattern Grid";
            case "refinedstorage:wireless_grid" -> "Wireless Grid";
            default -> pretty(key);
        };
    }

    /** "refinedstorage:crafting_grid" → "Crafting Grid". */
    static String pretty(String key) {
        int colon = key.indexOf(':');
        String ns = colon >= 0 ? key.substring(0, colon) : "";
        String path = colon >= 0 ? key.substring(colon + 1) : key;
        StringBuilder sb = new StringBuilder();
        for (String part : path.split("_")) {
            if (part.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
        }
        String name = sb.toString().trim();
        if (name.isEmpty()) {
            name = key;
        }
        if (!ns.isEmpty() && !ns.equals("refinedstorage") && !ns.equals("minecraft")) {
            name += " (" + ns + ")";
        }
        return name;
    }

    // 1.20.1's Screen.render does NOT call renderBackground for us.
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
