package nl.ljack2k.ae2organizer.jei;

import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import nl.ljack2k.ae2organizer.client.ClientEvents;
import nl.ljack2k.ae2organizer.client.gui.TabBarWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JEI ghost-ingredient handler for the storage screens themselves: lets items be
 * dragged straight from JEI's list onto a filter window's tabs (add-to-tab
 * dialog) or its "+" cell (new tab seeded from the item). Registered once per
 * backend screen class; JEI <em>combines</em> all handlers matching a screen's
 * hierarchy, so AE2's own ghost targets (e.g. pattern encoding slots) keep
 * working alongside ours.
 * <p>
 * Typed over plain {@link Screen} — target discovery goes through
 * {@link ClientEvents#ghostTargetsFor}, which returns nothing unless our bars
 * are live on that exact screen, so the handler is inert everywhere else.
 */
public class BarGhostHandler implements IGhostIngredientHandler<Screen> {

    @Override
    public <I> List<Target<I>> getTargetsTyped(Screen gui, ITypedIngredient<I> ingredient, boolean doStart) {
        // doStart == false is JEI's hover-hint query (it highlights targets while
        // merely hovering an ingredient, before any drag). Our bars only enter
        // drop mode during a real drag, so hinting would paint green boxes on the
        // tabs — and on a "+" cell that isn't even rendered yet. Only answer real
        // drag starts; JEI keeps drawing those targets from its drag-start
        // snapshot for the whole drag.
        if (!doStart) {
            return List.of();
        }
        Optional<ItemStack> maybe = ingredient.getItemStack();
        if (maybe.isEmpty()) {
            return List.of();
        }
        List<TabBarWidget.DropTarget> drops = ClientEvents.ghostTargetsFor(gui, maybe.get(), doStart);
        List<Target<I>> targets = new ArrayList<>(drops.size());
        for (TabBarWidget.DropTarget drop : drops) {
            targets.add(new Target<I>() {
                @Override
                public Rect2i getArea() {
                    return drop.area();
                }

                @Override
                public void accept(I ingredient) {
                    drop.action().run();
                }
            });
        }
        return targets;
    }

    @Override
    public void onComplete() {
        ClientEvents.clearExternalDrag();
    }
}
