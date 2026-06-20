package nl.ljack2k.ae2organizer.client.gui;

import appeng.client.gui.widgets.AE2Button;
import net.minecraft.client.Minecraft;
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
 * Windowed, client-only tab editor, themed via {@link Ae2Style}.
 *
 * <p>The screen is built from one repeated motif — a header label above a
 * recessed {@link Ae2Style#inset} panel — so every region reads as a deliberate
 * group rather than scattered controls:
 * <ul>
 *   <li><b>Tabs</b> (left): the list and its Add/Del/reorder toolbar share a
 *       single inset, split by a divider.</li>
 *   <li><b>Properties</b> (right-top): Name / Icon / Mode on an aligned
 *       label+field grid.</li>
 *   <li><b>Conditions</b> (right-bottom): a scrollable row list with the
 *       "+ Add condition" button attached as a footer.</li>
 *   <li><b>Inventory</b> (bottom): the drag-to-pick tray, framed with a hint.</li>
 * </ul>
 * Items can be dragged from the inventory tray (or from JEI) onto the icon slot
 * or a condition's value field.
 */
public final class TabEditorScreen extends Screen {

    public record GhostTarget(Rect2i area, Consumer<ItemStack> accept) {}

    private static final int PAD = 8;
    private static final int GAP = 5;
    private static final int HEADER_H = 11;
    private static final int BTN_H = 18;
    private static final int ROW_HE = 18;
    private static final int COND_ROW_H = 20;
    private static final int SBW = 8;
    private static final int TYPE_W = 86;

    private final Screen parent;
    private final List<TabDraft> drafts = new ArrayList<>();
    private final List<GhostTarget> ghostTargets = new ArrayList<>();
    private int selected = -1;

    @Nullable
    private ItemStack draggingStack;
    private int listScroll = 0;
    private int condScroll = 0;
    private boolean draggingListScrollbar = false;
    private boolean draggingCondScrollbar = false;

    // Window
    private int left, top, panelW, panelH, innerX, innerR, contentTop, dividerY, footerY;
    // Inventory tray
    private int invPanelX, invPanelY, invPanelW, invPanelH, invX, invY;
    // Tabs panel (list + attached toolbar)
    private int tabsX, tabsW, tabsHeaderY, tabsInsetY, tabsInsetH;
    private int listX, listY, listW, listRowW, listVisible, listSbX, toolbarY, toolbarDivY;
    private boolean listNeedScroll;
    private int listMaxScroll;
    // Right column: Properties + Conditions
    private int rightX, rightW, propsRight, labelX, fieldX;
    private int propsHeaderY, propsInsetY, propsInsetH, nameRowY, iconRowY, iconX, iconY;
    private int condHeaderY, condInsetY, condInsetH, condRowsTop, condVisible, condSbX, condContentR;
    private int ctrlRowY, ctrlDivY, modeBtnX, modeBtnW, addBtnX, addBtnW;
    private boolean condNeedScroll;
    private int condMaxScroll;

    public TabEditorScreen(Screen parent) {
        super(Component.translatable("ae2organizer.editor.title"));
        this.parent = parent;
        String activeId = TabManager.activeTabId();
        for (Tab tab : TabManager.tabs()) {
            drafts.add(TabDraft.from(tab));
        }
        // Open on the tab that's active in the terminal; fall back to the first.
        if (!drafts.isEmpty()) {
            selected = 0;
            if (activeId != null) {
                for (int i = 0; i < drafts.size(); i++) {
                    if (drafts.get(i).id.equals(activeId)) {
                        selected = i;
                        break;
                    }
                }
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public List<GhostTarget> ghostTargets() {
        return ghostTargets;
    }

    // Panel bounds derive from the live gui-scaled WINDOW size, not this.width/
    // this.height: JEI reads our IGuiProperties during the screen-open transition
    // while the screen's own width/height are still 0, rejects them as "invalid
    // gui properties", and then refuses to draw its ingredient-list overlay. The
    // window is always sized, so these stay valid no matter when JEI asks.
    public int panelWidth() {
        return Math.max(1, Math.min(480, frameWidth() - 20));
    }

    public int panelHeight() {
        return Math.max(1, Math.min(400, frameHeight() - 20));
    }

    public int panelLeft() {
        return (frameWidth() - panelWidth()) / 2;
    }

    public int panelTop() {
        return (frameHeight() - panelHeight()) / 2;
    }

    static int frameWidth() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    static int frameHeight() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }

    @Override
    protected void init() {
        ghostTargets.clear();
        panelW = panelWidth();
        panelH = panelHeight();
        left = panelLeft();
        top = panelTop();
        innerX = left + PAD;
        innerR = left + panelW - PAD;
        contentTop = top + 22;

        // Footer (Settings | Cancel | Done) pinned to the bottom, divider above.
        footerY = top + panelH - PAD - BTN_H;
        dividerY = footerY - 6;

        int mainTop = contentTop;
        int mainBottom = dividerY - 6;              // shared bottom for both columns

        // ---- Left: Tabs panel, full height (list + toolbar in one inset) ----
        tabsX = innerX;
        tabsW = 128;
        tabsHeaderY = mainTop;
        tabsInsetY = mainTop + HEADER_H;
        tabsInsetH = Math.max(48, mainBottom - tabsInsetY);
        toolbarY = tabsInsetY + tabsInsetH - 3 - BTN_H;
        toolbarDivY = toolbarY - 4;
        listX = tabsX + 3;
        listY = tabsInsetY + 3;
        int listAreaH = Math.max(ROW_HE, (toolbarDivY - 3) - listY);
        listVisible = Math.max(1, listAreaH / ROW_HE);
        listNeedScroll = drafts.size() > listVisible;
        listMaxScroll = Math.max(0, drafts.size() - listVisible);
        listScroll = Math.max(0, Math.min(listScroll, listMaxScroll));
        listW = tabsW - 6;
        listRowW = listW - (listNeedScroll ? SBW + 2 : 0);
        listSbX = listX + listW - SBW;

        addRenderableWidget(new AE2Button(tabsX + 4, toolbarY, 39, BTN_H, Component.literal("Add"), b -> addTab()));
        addRenderableWidget(new AE2Button(tabsX + 45, toolbarY, 39, BTN_H, Component.literal("Del"), b -> deleteTab()));
        addRenderableWidget(new AE2Button(tabsX + 86, toolbarY, 18, BTN_H, Component.literal("▲"), b -> moveTab(-1)));
        addRenderableWidget(new AE2Button(tabsX + 106, toolbarY, 18, BTN_H, Component.literal("▼"), b -> moveTab(1)));

        // ---- Right column: Properties / Conditions / Inventory stacked ----
        rightX = tabsX + tabsW + 8;
        rightW = innerR - rightX;
        propsRight = rightX + rightW - 4;
        labelX = rightX + 4;
        fieldX = labelX + 34;

        // Properties (fixed height): Name + Icon. Mode moved to the Conditions
        // panel since it governs how the conditions combine.
        propsHeaderY = mainTop;
        propsInsetY = mainTop + HEADER_H;
        propsInsetH = 4 + 2 * BTN_H + 4 + 4;        // pad + 2 rows + gap + pad
        nameRowY = propsInsetY + 4;
        iconRowY = nameRowY + 22;
        iconX = fieldX;
        iconY = iconRowY;

        // Inventory dimensions (placed directly beneath the Conditions inset).
        int invGridW = 9 * 18;
        int invBlockH = 3 * 18 + 4 + 18;            // 3 rows + gap + hotbar
        invPanelX = rightX;
        invPanelW = rightW;
        invPanelH = 3 + invBlockH + 3;              // inset: pad + grid + pad

        // Conditions panel: control row (Mode + Add) over a divider, then the
        // condition rows. Sized to exactly the rows shown — never taller — so
        // there is no dead strip; the Inventory sits directly beneath it. Rows
        // are capped so the Inventory still fits above the footer.
        condHeaderY = propsInsetY + propsInsetH + GAP;
        condInsetY = condHeaderY + HEADER_H;
        ctrlRowY = condInsetY + 4;
        ctrlDivY = ctrlRowY + BTN_H + 2;
        condRowsTop = ctrlRowY + BTN_H + 6;
        int ctrlW = rightW - 8;
        int half = (ctrlW - 4) / 2;
        modeBtnX = rightX + 4;
        modeBtnW = half;
        addBtnX = rightX + 4 + half + 4;
        addBtnW = ctrlW - half - 4;
        condContentR = rightX + rightW - 4;
        condSbX = rightX + rightW - 3 - SBW;

        int condCount = hasSelection() ? drafts.get(selected).conditions.size() : 0;
        int rowsRoom = mainBottom - condRowsTop - 4 - GAP - HEADER_H - invPanelH;
        int capacity = Math.max(1, rowsRoom / COND_ROW_H);
        condVisible = Math.min(capacity, Math.max(1, condCount));
        condInsetH = (condRowsTop - condInsetY) + condVisible * COND_ROW_H + 4;
        condNeedScroll = condCount > condVisible;
        condMaxScroll = Math.max(0, condCount - condVisible);
        condScroll = Math.max(0, Math.min(condScroll, condMaxScroll));

        // Inventory follows directly beneath the Conditions inset.
        invPanelY = condInsetY + condInsetH + GAP;
        invX = invPanelX + (invPanelW - invGridW) / 2;
        invY = invPanelY + HEADER_H + 3;

        if (hasSelection()) {
            buildRightPanel(drafts.get(selected));
        }

        addRenderableWidget(new AE2Button(innerX, footerY, 72, BTN_H,
                Component.literal("Settings…"), b -> this.minecraft.setScreen(new SettingsScreen(this))));
        addRenderableWidget(new AE2Button(innerR - 120, footerY, 58, BTN_H,
                Component.literal("Cancel"), b -> onClose()));
        addRenderableWidget(new AE2Button(innerR - 58, footerY, 58, BTN_H,
                Component.literal("Done"), b -> commitAndClose()));
    }

    private void buildRightPanel(TabDraft draft) {
        // Name
        EditBox name = Ae2Style.textField(this.font, fieldX, nameRowY + 1, propsRight - fieldX, 16, Component.literal("Name"));
        name.setMaxLength(64);
        name.setValue(draft.name);
        name.setResponder(s -> draft.name = s);
        addRenderableWidget(name);

        // Icon: recessed slot (drawn in render) + Pick button; slot is a ghost target
        addRenderableWidget(new AE2Button(fieldX + 22, iconRowY, propsRight - (fieldX + 22), BTN_H,
                Component.literal("Pick…"), b -> openIconPicker(draft)));
        ghostTargets.add(new GhostTarget(new Rect2i(iconX, iconY, 18, 18), stack -> draft.icon = idOf(stack)));

        // Conditions control row: Mode (how the conditions combine) + Add.
        addRenderableWidget(new AE2Button(modeBtnX, ctrlRowY, modeBtnW, BTN_H,
                Component.literal(draft.mode == MatchMode.ALL ? "Match ALL" : "Match ANY"),
                b -> {
                    draft.mode = draft.mode == MatchMode.ALL ? MatchMode.ANY : MatchMode.ALL;
                    rebuildWidgets();
                }));
        addRenderableWidget(new AE2Button(addBtnX, ctrlRowY, addBtnW, BTN_H,
                Component.literal("+ Add condition"), b -> {
            draft.conditions.add(CondDraft.fresh());
            condScroll = Integer.MAX_VALUE / 2;     // jump to the new last row; init clamps
            rebuildWidgets();
        }));

        // Condition rows — only those within the scroll window get widgets.
        int rowsShown = Math.min(condVisible, draft.conditions.size() - condScroll);
        for (int k = 0; k < rowsShown; k++) {
            final int condIndex = condScroll + k;
            final CondDraft cond = draft.conditions.get(condIndex);
            int rowY = condRowsTop + k * COND_ROW_H;
            int contentR = condNeedScroll ? condSbX - 2 : condContentR;
            int removeX = contentR - 18;
            int fieldStart = rightX + 4 + TYPE_W + 2;

            addRenderableWidget(new AE2Button(rightX + 4, rowY, TYPE_W, BTN_H,
                    Component.literal("Type: " + cond.type.getSerializedName()),
                    b -> {
                        cond.type = cycle(cond.type);
                        rebuildWidgets();
                    }));

            if (cond.type == ConditionType.COMPONENT) {
                if (cond.componentMatch.usesArg()) {
                    int cycleW = 80;
                    addRenderableWidget(matchButton(cond, fieldStart, rowY, cycleW));
                    EditBox arg = Ae2Style.textField(this.font, fieldStart + cycleW + 2, rowY + 1,
                            Math.max(20, removeX - (fieldStart + cycleW + 2) - 2), 16, Component.literal("Arg"));
                    arg.setMaxLength(128);
                    arg.setValue(cond.value);
                    arg.setResponder(s -> cond.value = s);
                    addRenderableWidget(arg);
                } else {
                    addRenderableWidget(matchButton(cond, fieldStart, rowY, Math.max(20, removeX - fieldStart - 2)));
                }
            } else {
                int pickX = removeX - 20;
                int boxW = Math.max(20, pickX - fieldStart - 2);
                EditBox value = Ae2Style.textField(this.font, fieldStart, rowY + 1, boxW, 16, Component.literal("Value"));
                value.setMaxLength(128);
                value.setValue(cond.value);
                value.setResponder(s -> cond.value = s);
                addRenderableWidget(value);
                addRenderableWidget(new AE2Button(pickX, rowY, 18, BTN_H,
                        Component.literal("…"), b -> openConditionPicker(cond)));
                ghostTargets.add(new GhostTarget(new Rect2i(fieldStart, rowY, boxW, 18),
                        stack -> applyDroppedToCondition(cond, stack)));
            }

            addRenderableWidget(new AE2Button(removeX, rowY, 18, BTN_H, Component.literal("✖"), b -> {
                draft.conditions.remove(condIndex);
                rebuildWidgets();
            }));
        }
    }

    private AE2Button matchButton(CondDraft cond, int x, int y, int width) {
        return new AE2Button(x, y, width, BTN_H, Component.literal(cond.componentMatch.getSerializedName()), b -> {
            cond.componentMatch = cycle(cond.componentMatch);
            rebuildWidgets();
        });
    }

    private static <E extends Enum<E>> E cycle(E value) {
        E[] values = value.getDeclaringClass().getEnumConstants();
        return values[(value.ordinal() + 1) % values.length];
    }

    private boolean hasSelection() {
        return selected >= 0 && selected < drafts.size();
    }

    // ---- Rendering ---------------------------------------------------------

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, Ae2Style.DIM);
        Ae2Style.panel(graphics, left, top, panelW, panelH);
        int tc = Ae2Style.textColor();
        graphics.drawString(this.font, getTitle(), left + PAD, top + 8, tc, false);

        // Left: Tabs panel (full height) — list + toolbar share one inset.
        graphics.drawString(this.font, "Tabs", tabsX, tabsHeaderY, tc, false);
        Ae2Style.inset(graphics, tabsX, tabsInsetY, tabsW, tabsInsetH);
        Ae2Style.divider(graphics, tabsX + 3, toolbarDivY, tabsW - 6);

        // Right column, stacked: Properties / Conditions / Inventory.
        graphics.drawString(this.font, "Properties", rightX, propsHeaderY, tc, false);
        Ae2Style.inset(graphics, rightX, propsInsetY, rightW, propsInsetH);

        graphics.drawString(this.font, "Conditions", rightX, condHeaderY, tc, false);
        Ae2Style.inset(graphics, rightX, condInsetY, rightW, condInsetH);
        Ae2Style.divider(graphics, rightX + 4, ctrlDivY, rightW - 8);

        graphics.drawString(this.font, "Inventory — drag onto the icon or a condition", invPanelX, invPanelY, tc, false);
        Ae2Style.inset(graphics, invPanelX, invPanelY + HEADER_H, invPanelW, invPanelH);

        // Footer separator.
        Ae2Style.divider(graphics, innerX, dividerY, innerR - innerX);

        if (hasSelection()) {
            graphics.drawString(this.font, "Name", labelX, nameRowY + 5, tc, false);
            graphics.drawString(this.font, "Icon", labelX, iconRowY + 5, tc, false);
        } else {
            graphics.drawString(this.font, "Select a tab, or click Add.", rightX + 6, propsInsetY + 7, tc, false);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        drawTabList(graphics, mouseX, mouseY);

        if (hasSelection()) {
            Ae2Style.slot(graphics, iconX, iconY);
            ItemStack icon = iconStack(drafts.get(selected).icon);
            if (!icon.isEmpty()) {
                graphics.renderItem(icon, iconX + 1, iconY + 1);
                if (inRect(mouseX, mouseY, iconX, iconY, 18, 18) && draggingStack == null) {
                    graphics.renderTooltip(this.font, icon, mouseX, mouseY);
                }
            }
            drawCondScrollbar(graphics);
        }

        drawInventory(graphics, mouseX, mouseY);

        if (draggingStack != null && !draggingStack.isEmpty()) {
            graphics.renderItem(draggingStack, mouseX - 8, mouseY - 8);
        }
    }

    private void drawTabList(GuiGraphics graphics, int mouseX, int mouseY) {
        int rows = Math.min(listVisible, drafts.size() - listScroll);
        for (int i = 0; i < rows; i++) {
            int idx = listScroll + i;
            int y = listY + i * ROW_HE;
            TabDraft draft = drafts.get(idx);
            boolean active = idx == selected;
            boolean hovered = inRect(mouseX, mouseY, listX, y, listRowW, ROW_HE - 1);
            Ae2Style.bevelButton(graphics, listX, y, listRowW, ROW_HE - 1, active, hovered);
            int off = active ? 1 : 0;
            Ae2Style.scaledItem(graphics, iconStack(draft.icon), listX + 2 + off, y + 1 + off, 14);
            String label = draft.name.isBlank() ? draft.id : draft.name;
            String text = this.font.plainSubstrByWidth(label, listRowW - 22);
            graphics.drawString(this.font, text, listX + 19 + off, y + 5 + off, Ae2Style.textColor(), false);
        }
        if (listNeedScroll) {
            int sbH = listVisible * ROW_HE;
            graphics.fill(listSbX, listY, listSbX + SBW, listY + sbH, 0x66000000);
            int thumbH = Math.max(12, sbH * listVisible / drafts.size());
            int travel = sbH - thumbH;
            int thumbY = listY + (listMaxScroll == 0 ? 0 : travel * listScroll / listMaxScroll);
            Ae2Style.bevelButton(graphics, listSbX, thumbY, SBW, thumbH, false, draggingListScrollbar);
        }
    }

    private void drawCondScrollbar(GuiGraphics graphics) {
        if (!condNeedScroll) {
            return;
        }
        int size = drafts.get(selected).conditions.size();
        int sbH = condVisible * COND_ROW_H;
        graphics.fill(condSbX, condRowsTop, condSbX + SBW, condRowsTop + sbH, 0x66000000);
        int thumbH = Math.max(12, sbH * condVisible / size);
        int travel = sbH - thumbH;
        int thumbY = condRowsTop + (condMaxScroll == 0 ? 0 : travel * condScroll / condMaxScroll);
        Ae2Style.bevelButton(graphics, condSbX, thumbY, SBW, thumbH, false, draggingCondScrollbar);
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
        double fraction = (mouseY - listY) / Math.max(1, listVisible * ROW_HE);
        listScroll = Math.max(0, Math.min((int) Math.round(fraction * listMaxScroll), listMaxScroll));
    }

    private void condScrollTo(double mouseY) {
        if (condMaxScroll == 0) {
            return;
        }
        double fraction = (mouseY - condRowsTop) / Math.max(1, condVisible * COND_ROW_H);
        int next = Math.max(0, Math.min((int) Math.round(fraction * condMaxScroll), condMaxScroll));
        if (next != condScroll) {
            condScroll = next;
            rebuildWidgets();
        }
    }

    // ---- Tab actions -------------------------------------------------------

    private void select(int index) {
        selected = index;
        condScroll = 0;
        rebuildWidgets();
    }

    private void addTab() {
        drafts.add(new TabDraft("tab-" + UUID.randomUUID().toString().substring(0, 8),
                "New Tab", "minecraft:chest", MatchMode.ANY));
        selected = drafts.size() - 1;
        condScroll = 0;
        rebuildWidgets();
    }

    private void deleteTab() {
        if (!hasSelection()) {
            return;
        }
        drafts.remove(selected);
        selected = drafts.isEmpty() ? -1 : Math.min(selected, drafts.size() - 1);
        condScroll = 0;
        rebuildWidgets();
    }

    private void moveTab(int delta) {
        int target = selected + delta;
        if (!hasSelection() || target < 0 || target >= drafts.size()) {
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
            if (hasSelection() && inRect(mouseX, mouseY, iconX, iconY, 18, 18)) {
                openIconPicker(drafts.get(selected));
                return true;
            }
            if (listNeedScroll && inRect(mouseX, mouseY, listSbX, listY, SBW, listVisible * ROW_HE)) {
                draggingListScrollbar = true;
                listScrollTo(mouseY);
                return true;
            }
            if (condNeedScroll && inRect(mouseX, mouseY, condSbX, condRowsTop, SBW, condVisible * COND_ROW_H)) {
                draggingCondScrollbar = true;
                condScrollTo(mouseY);
                return true;
            }
            int rows = Math.min(listVisible, drafts.size() - listScroll);
            if (inRect(mouseX, mouseY, listX, listY, listRowW, rows * ROW_HE)) {
                int row = (int) ((mouseY - listY) / ROW_HE);
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
        if (draggingCondScrollbar) {
            condScrollTo(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingListScrollbar = false;
        draggingCondScrollbar = false;
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
        if (listNeedScroll && inRect(mouseX, mouseY, listX, listY, listW, listVisible * ROW_HE)) {
            listScroll = Math.max(0, Math.min(listScroll + (scrollY < 0 ? 1 : -1), listMaxScroll));
            return true;
        }
        if (condNeedScroll && inRect(mouseX, mouseY, rightX, condRowsTop, rightW, condVisible * COND_ROW_H)) {
            int next = Math.max(0, Math.min(condScroll + (scrollY < 0 ? 1 : -1), condMaxScroll));
            if (next != condScroll) {
                condScroll = next;
                rebuildWidgets();
            }
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
                // Skip empty rows (null) and duplicates (Conditions are records,
                // so equality is by value).
                if (condition != null && !built.contains(condition)) {
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

        /** Returns {@code null} for an empty/unfilled row so it isn't persisted. */
        @Nullable
        Condition build() {
            return switch (type) {
                case MOD -> value.isBlank() ? null : new ModCondition(value.trim());
                case TAG -> {
                    ResourceLocation rl = ResourceLocation.tryParse(value.trim());
                    yield rl == null ? null : new TagCondition(rl);
                }
                case TEXT -> value.isBlank() ? null : new TextCondition(value.trim());
                case COMPONENT -> componentMatch.usesArg()
                        ? (value.isBlank() ? null : new ComponentCondition(componentMatch, value.trim()))
                        : new ComponentCondition(componentMatch, "");
            };
        }
    }
}
