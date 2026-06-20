package nl.ljack2k.ae2organizer.client.gui;

import appeng.client.gui.widgets.AE2Button;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import nl.ljack2k.ae2organizer.client.TabManager;
import nl.ljack2k.ae2organizer.filter.ComponentCondition;
import nl.ljack2k.ae2organizer.filter.ComponentMatch;
import nl.ljack2k.ae2organizer.filter.Condition;
import nl.ljack2k.ae2organizer.filter.ConditionType;
import nl.ljack2k.ae2organizer.filter.MatchMode;
import nl.ljack2k.ae2organizer.filter.ModCondition;
import nl.ljack2k.ae2organizer.filter.Tab;
import nl.ljack2k.ae2organizer.filter.TagCondition;
import nl.ljack2k.ae2organizer.filter.TextCondition;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Windowed, client-only tab editor, drawn with AE2's themed panel and widgets
 * ({@link Ae2Style}, {@link AE2Button}, AE2 text fields) so it matches AE2 and
 * inherits AE2 dark-mode packs. Items are assigned via the built-in
 * {@link ItemPickerScreen}, by dragging from JEI (the JEI plugin registers this
 * screen so its overlay appears), or by dragging from the player inventory shown
 * at the bottom — all resolving to the same {@link GhostTarget} areas.
 */
public final class TabEditorScreen extends Screen {

    /** Neutral drop target consumed by the JEI ghost handler and inventory drag. */
    public record GhostTarget(Rect2i area, Consumer<ItemStack> accept) {}

    private final Screen parent;
    private final List<TabDraft> drafts = new ArrayList<>();
    private final List<GhostTarget> ghostTargets = new ArrayList<>();
    private int selected = -1;

    private int left;
    private int top;
    private int panelW;
    private int panelH;
    private int contentTop;
    private int iconX;
    private int iconY;
    private int invX;
    private int invY;

    @Nullable
    private ItemStack draggingStack;

    public TabEditorScreen(Screen parent) {
        super(Component.translatable("ae2organizer.editor.title"));
        this.parent = parent;
        for (Tab tab : TabManager.tabs()) {
            drafts.add(TabDraft.from(tab));
        }
        if (!drafts.isEmpty()) {
            selected = 0;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public List<GhostTarget> ghostTargets() {
        return ghostTargets;
    }

    // Panel bounds — used by the JEI screen handler to place its overlay.
    public int panelLeft() {
        return left;
    }

    public int panelTop() {
        return top;
    }

    public int panelWidth() {
        return panelW;
    }

    public int panelHeight() {
        return panelH;
    }

    @Override
    protected void init() {
        ghostTargets.clear();
        panelW = Math.min(460, this.width - 20);
        panelH = Math.min(330, this.height - 20);
        left = (this.width - panelW) / 2;
        top = (this.height - panelH) / 2;
        contentTop = top + 22;

        int invBlockH = 3 * 18 + 4 + 18;
        invX = left + (panelW - 9 * 18) / 2;
        invY = top + panelH - 8 - invBlockH;
        int actionY = invY - 24;

        int listX = left + 10;
        int listW = 116;
        for (int i = 0; i < drafts.size(); i++) {
            final int idx = i;
            TabDraft draft = drafts.get(i);
            String label = (i == selected ? "▶ " : "") + (draft.name.isBlank() ? draft.id : draft.name);
            addRenderableWidget(new AE2Button(listX, contentTop + i * 20, listW, 18,
                    Component.literal(label), b -> select(idx)));
        }

        addRenderableWidget(new AE2Button(listX, actionY, 38, 18, Component.literal("Add"), b -> addTab()));
        addRenderableWidget(new AE2Button(listX + 40, actionY, 38, 18, Component.literal("Del"), b -> deleteTab()));
        addRenderableWidget(new AE2Button(listX + 80, actionY, 16, 18, Component.literal("▲"), b -> moveTab(-1)));
        addRenderableWidget(new AE2Button(listX + 98, actionY, 16, 18, Component.literal("▼"), b -> moveTab(1)));

        int rightX = left + 134;
        int rightW = panelW - 144;
        if (selected >= 0 && selected < drafts.size()) {
            buildRightPanel(rightX, rightW, drafts.get(selected));
        } else {
            addRenderableWidget(new StringWidget(rightX, contentTop + 16, rightW, 12,
                    Component.literal("Select a tab, or click Add."), this.font).alignLeft()
                    .setColor(Ae2Style.textColor()));
        }

        addRenderableWidget(new AE2Button(left + panelW - 202, actionY, 72, 18,
                Component.literal("Settings…"), b -> this.minecraft.setScreen(new SettingsScreen(this))));
        addRenderableWidget(new AE2Button(left + panelW - 128, actionY, 58, 18,
                Component.literal("Done"), b -> commitAndClose()));
        addRenderableWidget(new AE2Button(left + panelW - 68, actionY, 58, 18,
                Component.literal("Cancel"), b -> onClose()));
    }

    private void buildRightPanel(int rightX, int rightW, TabDraft draft) {
        addRenderableWidget(new StringWidget(rightX, contentTop + 5, 36, 10, Component.literal("Name"), this.font)
                .alignLeft().setColor(Ae2Style.textColor()));
        EditBox name = Ae2Style.textField(this.font, rightX + 40, contentTop + 2, rightW - 40, 16, Component.literal("Name"));
        name.setMaxLength(64);
        name.setValue(draft.name);
        name.setResponder(s -> draft.name = s);
        addRenderableWidget(name);

        addRenderableWidget(new StringWidget(rightX, contentTop + 29, 34, 10, Component.literal("Icon"), this.font)
                .alignLeft().setColor(Ae2Style.textColor()));
        iconX = rightX + 40;
        iconY = contentTop + 24;
        addRenderableWidget(new AE2Button(rightX + 62, contentTop + 24, 50, 18,
                Component.literal("Pick…"), b -> openIconPicker(draft)));
        ghostTargets.add(new GhostTarget(new Rect2i(iconX, iconY, 18, 18),
                stack -> draft.icon = idOf(stack)));

        addRenderableWidget(new AE2Button(rightX, contentTop + 48, 120, 18,
                Component.literal("Mode: Match " + (draft.mode == MatchMode.ALL ? "ALL" : "ANY")),
                b -> {
                    draft.mode = draft.mode == MatchMode.ALL ? MatchMode.ANY : MatchMode.ALL;
                    rebuildWidgets();
                }));

        addRenderableWidget(new StringWidget(rightX, contentTop + 74, rightW, 10,
                Component.literal("Conditions:"), this.font).alignLeft().setColor(Ae2Style.textColor()));

        int rowTop = contentTop + 86;
        int typeW = 96;
        for (int j = 0; j < draft.conditions.size(); j++) {
            final CondDraft cond = draft.conditions.get(j);
            final int condIndex = j;
            int rowY = rowTop + j * 22;
            int removeX = rightX + rightW - 18;
            int fieldX = rightX + typeW + 2;

            addRenderableWidget(new AE2Button(rightX, rowY, typeW, 18,
                    Component.literal("Type: " + cond.type.getSerializedName()),
                    b -> {
                        cond.type = cycle(cond.type);
                        rebuildWidgets();
                    }));

            if (cond.type == ConditionType.COMPONENT) {
                if (cond.componentMatch.usesArg()) {
                    int matchW = 96;
                    addRenderableWidget(matchButton(cond, fieldX, rowY, matchW));
                    EditBox arg = Ae2Style.textField(this.font, fieldX + matchW + 2, rowY + 1,
                            removeX - (fieldX + matchW + 2) - 2, 16, Component.literal("Arg"));
                    arg.setMaxLength(128);
                    arg.setValue(cond.value);
                    arg.setResponder(s -> cond.value = s);
                    addRenderableWidget(arg);
                } else {
                    addRenderableWidget(matchButton(cond, fieldX, rowY, removeX - fieldX - 2));
                }
            } else {
                int pickX = removeX - 20;
                int boxW = pickX - fieldX - 2;
                EditBox value = Ae2Style.textField(this.font, fieldX, rowY + 1, boxW, 16, Component.literal("Value"));
                value.setMaxLength(128);
                value.setValue(cond.value);
                value.setResponder(s -> cond.value = s);
                addRenderableWidget(value);
                addRenderableWidget(new AE2Button(pickX, rowY, 18, 18,
                        Component.literal("…"), b -> openConditionPicker(cond)));
                ghostTargets.add(new GhostTarget(new Rect2i(fieldX, rowY, boxW, 18),
                        stack -> applyDroppedToCondition(cond, stack)));
            }

            addRenderableWidget(new AE2Button(removeX, rowY, 18, 18, Component.literal("✖"), b -> {
                draft.conditions.remove(condIndex);
                rebuildWidgets();
            }));
        }

        int addCondY = rowTop + draft.conditions.size() * 22;
        addRenderableWidget(new AE2Button(rightX, addCondY, 120, 18,
                Component.literal("+ Add condition"), b -> {
            draft.conditions.add(CondDraft.fresh());
            rebuildWidgets();
        }));
    }

    private AE2Button matchButton(CondDraft cond, int x, int y, int width) {
        return new AE2Button(x, y, width, 18, Component.literal(cond.componentMatch.getSerializedName()), b -> {
            cond.componentMatch = cycle(cond.componentMatch);
            rebuildWidgets();
        });
    }

    private static <E extends Enum<E>> E cycle(E value) {
        E[] values = value.getDeclaringClass().getEnumConstants();
        return values[(value.ordinal() + 1) % values.length];
    }

    // ---- Item-picking ------------------------------------------------------

    private void openIconPicker(TabDraft draft) {
        this.minecraft.setScreen(new ItemPickerScreen(this, Component.literal("Pick tab icon"), item -> {
            draft.icon = BuiltInRegistries.ITEM.getKey(item).toString();
            this.minecraft.setScreen(this);
        }));
    }

    private void openConditionPicker(CondDraft cond) {
        switch (cond.type) {
            case MOD -> this.minecraft.setScreen(new ItemPickerScreen(this,
                    Component.literal("Pick item — uses its mod"), item -> {
                cond.value = BuiltInRegistries.ITEM.getKey(item).getNamespace();
                this.minecraft.setScreen(this);
            }));
            case TEXT -> this.minecraft.setScreen(new ItemPickerScreen(this,
                    Component.literal("Pick item — uses its name"), item -> {
                cond.value = new ItemStack(item).getHoverName().getString();
                this.minecraft.setScreen(this);
            }));
            case TAG -> this.minecraft.setScreen(new ItemPickerScreen(this,
                    Component.literal("Pick item — choose its tag"), item ->
                    this.minecraft.setScreen(new TagChooserScreen(this, item, tag -> {
                        cond.value = tag;
                        this.minecraft.setScreen(this);
                    }))));
            case COMPONENT -> { /* no item picker for component conditions */ }
        }
    }

    private void applyDroppedToCondition(CondDraft cond, ItemStack stack) {
        switch (cond.type) {
            case MOD -> {
                cond.value = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
                rebuildWidgets();
            }
            case TEXT -> {
                cond.value = stack.getHoverName().getString();
                rebuildWidgets();
            }
            case TAG -> this.minecraft.setScreen(new TagChooserScreen(this, stack.getItem(), tag -> {
                cond.value = tag;
                this.minecraft.setScreen(this);
            }));
            case COMPONENT -> { /* component conditions have no ghost target */ }
        }
    }

    private static String idOf(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static ItemStack iconStack(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id.trim());
        Item item = rl == null ? Items.CHEST : BuiltInRegistries.ITEM.getOptional(rl).orElse(Items.CHEST);
        return new ItemStack(item);
    }

    // ---- Inventory rendering + drag ---------------------------------------

    private int[] invSlotPos(int index) {
        if (index < 9) { // hotbar row, below the main grid
            return new int[]{invX + index * 18, invY + 3 * 18 + 4};
        }
        int main = index - 9;
        return new int[]{invX + (main % 9) * 18, invY + (main / 9) * 18};
    }

    private int invSlotAt(double mouseX, double mouseY) {
        for (int i = 0; i < 36; i++) {
            int[] p = invSlotPos(i);
            if (mouseX >= p[0] && mouseX < p[0] + 18 && mouseY >= p[1] && mouseY < p[1] + 18) {
                return i;
            }
        }
        return -1;
    }

    private void drawInventory(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        ItemStack hovered = ItemStack.EMPTY;
        for (int i = 0; i < 36; i++) {
            int[] p = invSlotPos(i);
            Ae2Style.slot(graphics, p[0], p[1]);
            ItemStack stack = this.minecraft.player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, p[0] + 1, p[1] + 1);
                graphics.renderItemDecorations(this.font, stack, p[0] + 1, p[1] + 1);
                if (mouseX >= p[0] && mouseX < p[0] + 18 && mouseY >= p[1] && mouseY < p[1] + 18) {
                    hovered = stack;
                }
            }
        }
        if (!hovered.isEmpty() && draggingStack == null) {
            graphics.renderTooltip(this.font, hovered, mouseX, mouseY);
        }
    }

    // ---- Tab list actions --------------------------------------------------

    private void select(int index) {
        selected = index;
        rebuildWidgets();
    }

    private void addTab() {
        drafts.add(new TabDraft("tab-" + UUID.randomUUID().toString().substring(0, 8),
                "New Tab", "minecraft:chest", MatchMode.ANY));
        selected = drafts.size() - 1;
        rebuildWidgets();
    }

    private void deleteTab() {
        if (selected < 0 || selected >= drafts.size()) {
            return;
        }
        drafts.remove(selected);
        selected = drafts.isEmpty() ? -1 : Math.min(selected, drafts.size() - 1);
        rebuildWidgets();
    }

    private void moveTab(int delta) {
        int target = selected + delta;
        if (selected < 0 || target < 0 || target >= drafts.size()) {
            return;
        }
        Collections.swap(drafts, selected, target);
        selected = target;
        rebuildWidgets();
    }

    private void commitAndClose() {
        List<Tab> tabs = new ArrayList<>(drafts.size());
        for (TabDraft draft : drafts) {
            tabs.add(draft.toTab());
        }
        TabManager.replaceAll(tabs);
        onClose();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    // ---- Input -------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int invIndex = invSlotAt(mouseX, mouseY);
            if (invIndex >= 0 && this.minecraft != null && this.minecraft.player != null) {
                ItemStack stack = this.minecraft.player.getInventory().getItem(invIndex);
                if (!stack.isEmpty()) {
                    draggingStack = stack.copy();
                    return true;
                }
            }
            if (selected >= 0 && selected < drafts.size()
                    && mouseX >= iconX && mouseX < iconX + 18 && mouseY >= iconY && mouseY < iconY + 18) {
                openIconPicker(drafts.get(selected));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingStack != null) {
            ItemStack dropped = draggingStack;
            draggingStack = null;
            for (GhostTarget target : ghostTargets) {
                Rect2i area = target.area();
                if (mouseX >= area.getX() && mouseX < area.getX() + area.getWidth()
                        && mouseY >= area.getY() && mouseY < area.getY() + area.getHeight()) {
                    target.accept().accept(dropped);
                    break;
                }
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, Ae2Style.DIM);
        Ae2Style.panel(graphics, left, top, panelW, panelH);
        graphics.drawString(this.font, getTitle(), left + 10, top + 7, Ae2Style.textColor(), false);
        super.render(graphics, mouseX, mouseY, partialTick);

        if (selected >= 0 && selected < drafts.size()) {
            Ae2Style.slot(graphics, iconX, iconY);
            ItemStack icon = iconStack(drafts.get(selected).icon);
            if (!icon.isEmpty()) {
                graphics.renderItem(icon, iconX + 1, iconY + 1);
            }
            if (mouseX >= iconX && mouseX < iconX + 18 && mouseY >= iconY && mouseY < iconY + 18
                    && !icon.isEmpty() && draggingStack == null) {
                graphics.renderTooltip(this.font, icon, mouseX, mouseY);
            }
        }

        drawInventory(graphics, mouseX, mouseY);

        if (draggingStack != null && !draggingStack.isEmpty()) {
            graphics.renderItem(draggingStack, mouseX - 8, mouseY - 8);
        }
    }

    // ---- Mutable working copies -------------------------------------------

    private static final class TabDraft {
        String id;
        String name;
        String icon;
        MatchMode mode;
        final List<CondDraft> conditions = new ArrayList<>();

        TabDraft(String id, String name, String icon, MatchMode mode) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.mode = mode;
        }

        static TabDraft from(Tab tab) {
            TabDraft draft = new TabDraft(tab.id(), tab.name(), tab.icon().toString(), tab.mode());
            for (Condition condition : tab.conditions()) {
                draft.conditions.add(CondDraft.from(condition));
            }
            return draft;
        }

        Tab toTab() {
            List<Condition> built = new ArrayList<>();
            for (CondDraft cond : conditions) {
                Condition condition = cond.build();
                if (condition != null) {
                    built.add(condition);
                }
            }
            ResourceLocation iconId = ResourceLocation.tryParse(icon.trim());
            String finalName = name.isBlank() ? id : name;
            return new Tab(id, finalName, iconId != null ? iconId : Tab.DEFAULT_ICON, mode, built);
        }
    }

    private static final class CondDraft {
        ConditionType type;
        String value;
        ComponentMatch componentMatch;

        CondDraft(ConditionType type, String value, ComponentMatch componentMatch) {
            this.type = type;
            this.value = value;
            this.componentMatch = componentMatch;
        }

        static CondDraft fresh() {
            return new CondDraft(ConditionType.MOD, "", ComponentMatch.ENCHANTED);
        }

        static CondDraft from(Condition condition) {
            if (condition instanceof ModCondition c) {
                return new CondDraft(ConditionType.MOD, c.modId(), ComponentMatch.ENCHANTED);
            }
            if (condition instanceof TagCondition c) {
                return new CondDraft(ConditionType.TAG, c.tagId().toString(), ComponentMatch.ENCHANTED);
            }
            if (condition instanceof TextCondition c) {
                return new CondDraft(ConditionType.TEXT, c.text(), ComponentMatch.ENCHANTED);
            }
            if (condition instanceof ComponentCondition c) {
                return new CondDraft(ConditionType.COMPONENT, c.arg(), c.match());
            }
            return fresh();
        }

        @Nullable
        Condition build() {
            return switch (type) {
                case MOD -> new ModCondition(value.trim());
                case TAG -> {
                    ResourceLocation rl = ResourceLocation.tryParse(value.trim());
                    yield rl == null ? null : new TagCondition(rl);
                }
                case TEXT -> new TextCondition(value);
                case COMPONENT -> new ComponentCondition(componentMatch, value);
            };
        }
    }
}
