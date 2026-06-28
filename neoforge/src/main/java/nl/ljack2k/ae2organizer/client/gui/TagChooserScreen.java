package nl.ljack2k.ae2organizer.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Lists the item tags of a chosen item so the user picks one for a TAG
 * condition without having to know tag ids. Scrollable when an item has many
 * tags. The callback receives the tag id string and is responsible for
 * navigating back.
 */
public final class TagChooserScreen extends Screen {
    private final Screen parent;
    private final Consumer<String> onPick;
    private final List<TagKey<Item>> tags;
    private int offset = 0;

    private int left;
    private int top;
    private int panelW;
    private int panelH;
    private int listTop;
    private int visible;

    public TagChooserScreen(Screen parent, Item item, Consumer<String> onPick) {
        super(Component.literal("Choose a tag"));
        this.parent = parent;
        this.onPick = onPick;
        this.tags = new ItemStack(item).getTags()
                .sorted(Comparator.comparing(tag -> tag.location().toString()))
                .toList();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        panelW = Math.min(320, this.width - 20);
        panelH = Math.min(220, this.height - 20);
        left = (this.width - panelW) / 2;
        top = (this.height - panelH) / 2;
        listTop = top + 26;
        visible = Math.max(1, (panelH - 26 - 28) / 20);

        if (tags.isEmpty()) {
            addRenderableWidget(new StringWidget(left + 10, top + 44, panelW - 20, 12,
                    Component.literal("This item has no tags."), this.font).alignLeft()
                    .setColor(Ae2Style.textColor()));
        } else {
            int count = Math.min(visible, tags.size() - offset);
            for (int i = 0; i < count; i++) {
                final TagKey<Item> tag = tags.get(offset + i);
                addRenderableWidget(new Ae2Button(left + 10, listTop + i * 20, panelW - 20, 18,
                        Component.literal(tag.location().toString()),
                        b -> onPick.accept(tag.location().toString())));
            }
        }
        addRenderableWidget(new Ae2Button(left + panelW - 66, top + panelH - 24, 56, 18,
                Component.literal("Cancel"), b -> onClose()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!tags.isEmpty()) {
            int max = Math.max(0, tags.size() - visible);
            offset = Math.max(0, Math.min(offset - (int) Math.signum(delta), max));
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        graphics.fill(0, 0, this.width, this.height, Ae2Style.DIM);
        Ae2Style.panel(graphics, left, top, panelW, panelH);
        graphics.drawString(this.font, getTitle(), left + 10, top + 9, Ae2Style.textColor(), false);
    }

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
