package nl.ljack2k.ae2organizer.jei;

import mezz.jei.api.gui.handlers.IGuiProperties;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import nl.ljack2k.ae2organizer.client.gui.TabEditorScreen;

/**
 * Tells JEI the editor's panel bounds so JEI draws its ingredient-list overlay
 * in the free space beside the panel (and thus allows dragging items out of it
 * onto the editor's ghost targets). Without this, JEI shows nothing on a plain
 * (non-container) screen.
 * <p>
 * (JEI 15.x — the 1.20.1 line — names these getters {@code getGuiLeft()} etc.,
 * where JEI 19.x dropped the {@code get} prefix.)
 */
public record EditorGuiProperties(TabEditorScreen screen) implements IGuiProperties {

    @Override
    public Class<? extends Screen> getScreenClass() {
        return TabEditorScreen.class;
    }

    @Override
    public int getGuiLeft() {
        return screen.panelLeft();
    }

    @Override
    public int getGuiTop() {
        return screen.panelTop();
    }

    @Override
    public int getGuiXSize() {
        return screen.panelWidth();
    }

    @Override
    public int getGuiYSize() {
        return screen.panelHeight();
    }

    // The live gui-scaled window size — never 0, unlike screen.width/height which
    // JEI may read during the screen-open transition before Minecraft sizes it.
    @Override
    public int getScreenWidth() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    @Override
    public int getScreenHeight() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }
}
