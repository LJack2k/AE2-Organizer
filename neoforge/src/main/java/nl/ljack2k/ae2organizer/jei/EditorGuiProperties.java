package nl.ljack2k.ae2organizer.jei;

import mezz.jei.api.gui.handlers.IGuiProperties;
import net.minecraft.client.gui.screens.Screen;
import nl.ljack2k.ae2organizer.client.gui.TabEditorScreen;

/**
 * Tells JEI the editor's panel bounds so JEI draws its ingredient-list overlay
 * in the free space beside the panel (and thus allows dragging items out of it
 * onto the editor's ghost targets). Without this, JEI shows nothing on a plain
 * (non-container) screen.
 */
public record EditorGuiProperties(TabEditorScreen screen) implements IGuiProperties {

    @Override
    public Class<? extends Screen> screenClass() {
        return TabEditorScreen.class;
    }

    @Override
    public int guiLeft() {
        return screen.panelLeft();
    }

    @Override
    public int guiTop() {
        return screen.panelTop();
    }

    @Override
    public int guiXSize() {
        return screen.panelWidth();
    }

    @Override
    public int guiYSize() {
        return screen.panelHeight();
    }

    @Override
    public int screenWidth() {
        return screen.width;
    }

    @Override
    public int screenHeight() {
        return screen.height;
    }
}
