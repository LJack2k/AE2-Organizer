package nl.ljack2k.ae2organizer.client.gui;

import appeng.client.gui.widgets.AE2Button;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
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
 * Windowed, client-only tab editor, themed via {@link Ae2Style}. The tab list on
 * the left lives in its own scrollable region with the Add/Del/reorder buttons
 * beneath it; the right pane edits the selected tab; the player inventory along
 * the bottom can be dragged onto the icon/condition targets (as can JEI).
 */
public final class TabEditorScreen extends Screen {

    public record GhostTarget(Rect2i area, Consumer<ItemStack> accept) {}

    private static final int ROW_HE = 18;
    private static final int SBW = 8;
    private static final int ICON = 16;

    private final Screen parent;
    private final List<TabDraft> drafts = new ArrayList<>();
    private final List<GhostTarget> ghostTargets = new ArrayList<>();
    private int selected = -1;

    @Nullable
    private ItemStack draggingStack;
    private int listScroll = 0;
    private boolean draggingListScrollbar = false;

    private int left, top, panelW, panelH, contentTop;
    private int leftX, leftW, leftListTop, leftListVisible, listBtnW, listSbX, leftMgmtY;
    private boolean listNeedScroll;
    private int listMaxScroll;
    private int rightX, rightW, nameY, iconX, iconY, condLabelY;
    private int invX, invY;

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
        panelW = Math.min(480, this.width - 20);
        panelH = Math.min(346, this.height - 20);
        left = (this.width - panelW) / 2;
        top = (this.height - panelH) / 2;
        contentTop = top + 24;

        int invBlockH = 3 * 18 + 4 + 18;
        invX = left + (panelW - 9 * 18) / 2;
        invY = top + panelH - 8 - invBlockH;
        int bottomY = invY - 24;

        // Left column: scrollable tab list, then management buttons beneath it.
        leftX = left + 10;
        leftW = 118;
        leftMgmtY = bottomY;
        leftListTop = contentTop;
        int leftListH = Math.max(ROW_HE, (leftMgmtY - 6) - leftListTop);
        leftListVisible = Math.max(1, leftListH / ROW_HE);
        listNeedScroll = drafts.size() > leftListVisible;
        listMaxScroll = Math.max(0, drafts.size() - leftListVisible);
        listScroll = Math.max(0, Math.min(listScroll, listMaxScroll));
        listBtnW = leftW - (listNeedScroll ? SBW + 2 : 0);
        listSbX = leftX + leftW - SBW;

        addRenderableWidget(new AE2Button(leftX, leftMgmtY, 38, 18, Component.literal("Add"), b -> addTab()));
        addRenderableWidget(new AE2Button(leftX + 40, leftMgmtY, 38, 18, Component.literal("Del"), b -> deleteTab()));
        addRenderableWidget(new AE2Button(leftX + 80, leftMgmtY, 16, 18, Component.literal("▲"), b -> moveTab(-1)));
        addRenderableWidget(new AE2Button(leftX + 98, leftMgmtY, 16, 18, Component.literal("▼"), b -> moveTab(1)));

        rightX = leftX + leftW + 12;
        rightW = left + panelW - 10 - rightX;
        if (selected >= 0 && selected < drafts.size()) {
            buildRightPanel(drafts.get(selected));
        }

        addRenderableWidget(new AE2Button(left + panelW - 202, bottomY, 72, 18,
                Component.literal("Settings…"), b -> this.minecraft.setScreen(new SettingsScreen(this))));
        addRenderableWidget(new AE2Button(left + panelW - 128, bottomY, 58, 18,
                Component.literal("Done"), b -> commitAndClose()));
        addRenderableWidget(new AE2Button(left + panelW - 68, bottomY, 58, 18,
                Component.literal("Cancel"), b -> onClose()));
    }

    private void buildRightPanel(TabDraft draft) {
        nameY = contentTop;
        EditBox name = Ae2Style.textField(this.font, rightX + 40, nameY, rightW - 40, 16, Component.literal("Name"));
        name.setMaxLength(64);
        name.setValue(draft.name);
        name.setResponder(s -> draft.name = s);
        addRenderableWidget(name);

        iconX = rightX + 40;
        iconY = contentTop + 24;
        addRenderableWidget(new AE2Button(rightX + 62, iconY, 50, 18,
                Component.literal("Pick…"), b -> openIconPicker(draft)));
        ghostTargets.add(new GhostTarget(new Rect2i(iconX, iconY, 18, 18), stack -> draft.icon = idOf(stack)));

        int modeY = contentTop + 48;
        addRenderableWidget(new AE2Button(rightX, modeY, 130, 18,
                Component.literal("Mode: Match " + (draft.mode == MatchMode.ALL ? "ALL" : "ANY")),
                b -> {
                    draft.mode = draft.mode == MatchMode.ALL ? MatchMode.ANY : MatchMode.ALL;
                    rebuildWidgets();
                }));

        condLabelY = contentTop + 74;
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
                    int cycleW = 92;
                    addRenderableWidget(matchButton(cond, fieldX, rowY, cycleW));
                    EditBox arg = Ae2Style.textField(this.font, fieldX + cycleW + 2, rowY + 1,
                            removeX - (fieldX + cycleW + 2) - 2, 16, Component.literal("Arg"));
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

    // ---- Rendering ---------------------------------------------------------

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, Ae2Style.DIM);
        Ae2Style.panel(graphics, left, top, panelW, panelH);
        int tc = Ae2Style.textColor();
        graphics.drawString(this.font, getTitle(), left + 10, top + 8, tc, false);
        if (selected >= 0 && selected < drafts.size()) {
            graphics.drawString(this.font, "Name", rightX, nameY + 4, tc, false);
            graphics.drawString(this.font, "Icon", rightX, iconY + 5, tc, false);
            graphics.drawString(this.font, "Conditions:", rightX, condLabelY, tc, false);
        } else {
            graphics.drawString(this.font, "Select a tab, or click Add.", rightX, contentTop + 4, tc, false);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        drawLeftList(graphics, mouseX, mouseY);

        if (selected >= 0 && selected < drafts.size()) {
            Ae2Style.slot(graphics, iconX, iconY);
            ItemStack icon = iconStack(drafts.get(selected).icon);
            if (!icon.isEmpty()) {
                graphics.renderItem(icon, iconX + 1, iconY + 1);
                if (inRect(mouseX, mouseY, iconX, iconY, 18, 18) && draggingStack == null) {
                    graphics.renderTooltip(this.font, icon, mouseX, mouseY);
                }
            }
        }

        drawInventory(graphics, mouseX, mouseY);

        if (draggingStack != null && !draggingStack.isEmpty()) {
            graphics.renderItem(draggingStack, mouseX - 8, mouseY - 8);
        }
    }

    private void drawLeftList(GuiGraphics graphics, int mouseX, int mouseY) {
        int rows = Math.min(leftListVisible, drafts.size() - listScroll);
        for (int i = 0; i < rows; i++) {
            int idx = listScroll + i;
            int y = leftListTop + i * ROW_HE;
            TabDraft draft = drafts.get(idx);
            boolean active = idx == selected;
            boolean hovered = inRect(mouseX, mouseY, leftX, y, listBtnW, ROW_HE - 1);
            Ae2Style.bevelButton(graphics, leftX, y, listBtnW, ROW_HE - 1, active, hovered);
            int off = active ? 1 : 0;
            String label = draft.name.isBlank() ? draft.id : draft.name;
            String text = this.font.plainSubstrByWidth(label, listBtnW - 6);
            graphics.drawString(this.font, text, leftX + 3 + off, y + 4 + off, Ae2Style.textColor(), false);
        }
        if (listNeedScroll) {
            int sbH = leftListVisible * ROW_HE;
            graphics.fill(listSbX, leftListTop, listSbX + SBW, leftListTop + sbH, 0x66000000);
            int thumbH = Math.max(12, sbH * leftListVisible / drafts.size());
            int travel = sbH - thumbH;
            int thumbY = leftListTop + (listMaxScroll == 0 ? 0 : travel * listScroll / listMaxScroll);
            Ae2Style.bevelButton(graphics, listSbX, thumbY, SBW, thumbH, false, draggingListScrollbar);
        }
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
                if (inRect(mouseX, mouseY, p[0], p[1], 18, 18)) {
                    hovered = stack;
                }
            }
        }
        if (!hovered.isEmpty() && draggingStack == null) {
            graphics.renderTooltip(this.font, hovered, mouseX, mouseY);
        }
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
            case COMPONENT -> { /* no item picker */ }
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
            case COMPONENT -> { /* none */ }
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

    // ---- Inventory + list geometry ----------------------------------------

    private int[] invSlotPos(int index) {
        if (index < 9) {
            return new int[]{invX + index * 18, invY + 3 * 18 + 4};
        }
        int main = index - 9;
        return new int[]{invX + (main % 9) * 18, invY + (main / 9) * 18};
    }

    private int invSlotAt(double mouseX, double mouseY) {
        for (int i = 0; i < 36; i++) {
            int[] p = invSlotPos(i);
            if (inRect(mouseX, mouseY, p[0], p[1], 18, 18)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void listScrollTo(double mouseY) {
        if (listMaxScroll == 0) {
            listScroll = 0;
            return;
        }
        double fraction = (mouseY - leftListTop) / Math.max(1, leftListVisible * ROW_HE);
        listScroll = Math.max(0, Math.min((int) Math.round(fraction * listMaxScroll), listMaxScroll));
    }

    // ---- Tab actions -------------------------------------------------------

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
            if (selected >= 0 && selected < drafts.size() && inRect(mouseX, mouseY, iconX, iconY, 18, 18)) {
                openIconPicker(drafts.get(selected));
                return true;
            }
            if (listNeedScroll && inRect(mouseX, mouseY, listSbX, leftListTop, SBW, leftListVisible * ROW_HE)) {
                draggingListScrollbar = true;
                listScrollTo(mouseY);
                return true;
            }
            int rows = Math.min(leftListVisible, drafts.size() - listScroll);
            if (inRect(mouseX, mouseY, leftX, leftListTop, listBtnW, rows * ROW_HE)) {
                int row = (int) ((mouseY - leftListTop) / ROW_HE);
                if (row >= 0 && row < rows) {
                    select(listScroll + row);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (draggingListScrollbar) {
            listScrollTo(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingListScrollbar = false;
        if (draggingStack != null) {
            ItemStack dropped = draggingStack;
            draggingStack = null;
            for (GhostTarget target : ghostTargets) {
                Rect2i area = target.area();
                if (inRect(mouseX, mouseY, area.getX(), area.getY(), area.getWidth(), area.getHeight())) {
                    target.accept().accept(dropped);
                    break;
                }
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (listNeedScroll && inRect(mouseX, mouseY, leftX, leftListTop, leftW, leftListVisible * ROW_HE)) {
            listScroll = Math.max(0, Math.min(listScroll + (scrollY < 0 ? 1 : -1), listMaxScroll));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
