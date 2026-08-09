package nl.ljack2k.ae2organizer.backend.ae2;

import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Gates the AE2 mixin config so its mixins only apply when Applied Energistics 2
 * is actually installed. Without this, the AE2 {@code Repo} mixin's missing
 * target class would crash the game on an install that has RS (or neither) but
 * not AE2.
 * <p>
 * Referenced by {@code ae2organizer.ae2.mixins.json}'s {@code "plugin"} field,
 * and classloaded during early mod loading — so it must reference only loader
 * APIs, never AE2 classes.
 */
public final class Ae2MixinPlugin implements IMixinConfigPlugin {

    private boolean ae2Present;

    @Override
    public void onLoad(String mixinPackage) {
        this.ae2Present = LoadingModList.get().getModFileById("ae2") != null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return ae2Present;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
