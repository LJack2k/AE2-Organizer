package nl.ljack2k.ae2organizer.jei;

import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import nl.ljack2k.ae2organizer.client.gui.TabEditorScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Bridges JEI drag-and-drop to the editor's neutral {@link TabEditorScreen.GhostTarget}
 * list. Only item ingredients are accepted; the editor decides what each target
 * does with the dropped stack (set icon, fill a mod/name, open the tag chooser).
 */
public class EditorGhostHandler implements IGhostIngredientHandler<TabEditorScreen> {

    @Override
    public <I> List<Target<I>> getTargetsTyped(TabEditorScreen gui, ITypedIngredient<I> ingredient, boolean doStart) {
        Optional<ItemStack> maybe = ingredient.getItemStack();
        if (maybe.isEmpty()) {
            return List.of();
        }
        ItemStack stack = maybe.get();
        List<Target<I>> targets = new ArrayList<>();
        for (TabEditorScreen.GhostTarget ghostTarget : gui.ghostTargets()) {
            targets.add(new Target<I>() {
                @Override
                public Rect2i getArea() {
                    return ghostTarget.area();
                }

                @Override
                public void accept(I ingredient) {
                    ghostTarget.accept().accept(stack);
                }
            });
        }
        return targets;
    }

    @Override
    public void onComplete() {
    }
}
