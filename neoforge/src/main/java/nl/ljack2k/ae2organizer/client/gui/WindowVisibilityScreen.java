package nl.ljack2k.ae2organizer.client.gui;

import appeng.client.gui.widgets.AE2Button;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import nl.ljack2k.ae2organizer.client.TabManager;

import java.util.List;
import java.util.Set;

/**
 * Per-window visibility editor: a small list of the terminal types the window
 * already has coordinates for, each a toggle to show/hide the window there.
 * Edits the passed {@code hidden} set in place; the editor persists it on Save.
 */
public final class WindowVisibilityScreen extends Screen {

    private static final int BTN_H = 18;

    private final Screen parent;
    private final List<String> terminals;
    private final Set<String> hidden;
    private final String currentKey;

    private int left, top, panelW, panelH;

    public WindowVisibilityScreen(Screen parent, List<String> terminals, Set<String> hidden, String currentKey) {
        super(Component.literal("Show on terminals"));
        this.parent = parent;
        this.terminals = terminals;
        this.hidden = hidden;
        this.currentKey = currentKey;
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
            addRenderableWidget(new AE2Button(x, y, w, BTN_H, Component.literal(label), b -> {
                if (hidden.contains(k)) {
                    hidden.remove(k);
                } else {
                    hidden.add(k);
                }
                rebuildWidgets();
            }));
            y += 22;
        }

        addRenderableWidget(new AE2Button(left + panelW - 68, top + panelH - 26, 58, 20,
                Component.literal("Done"), b -> onClose()));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, Ae2Style.DIM);
        Ae2Style.panel(graphics, left, top, panelW, panelH);
        int tc = Ae2Style.textColor();
        graphics.text(this.font, getTitle(), left + 10, top + 9, tc, false);
    }

    /** A captured screen title if there was one, else a known AE2 name, else prettified id. */
    static String displayName(String key) {
        String remembered = TabManager.terminalName(key);
        if (remembered != null && !remembered.isBlank()) {
            return remembered;
        }
        return switch (key) {
            case "ae2:item_terminal" -> "ME Terminal";
            case "ae2:craftingterm" -> "Crafting Terminal";
            case "ae2:patternterm" -> "Pattern Encoding Terminal";
            case "ae2:patternaccessterm" -> "Pattern Access Terminal";
            case "ae2:wirelessterm" -> "Wireless Terminal";
            case "ae2:wirelesscraftingterm" -> "Wireless Crafting Terminal";
            default -> pretty(key);
        };
    }

    /** "ae2:crafting_terminal" → "Crafting Terminal"; also turns a trailing "term" into " Terminal". */
    static String pretty(String key) {
        int colon = key.indexOf(':');
        String ns = colon >= 0 ? key.substring(0, colon) : "";
        String path = colon >= 0 ? key.substring(colon + 1) : key;
        // A trailing abbreviated "term" reads better as a full "terminal" word.
        if (path.endsWith("term") && !path.endsWith("_term")) {
            path = path.substring(0, path.length() - 4) + "_terminal";
        }
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
        if (!ns.isEmpty() && !ns.equals("ae2") && !ns.equals("minecraft")) {
            name += " (" + ns + ")";
        }
        return name;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
