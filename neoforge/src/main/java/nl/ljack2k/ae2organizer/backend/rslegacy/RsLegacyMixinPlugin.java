package nl.ljack2k.ae2organizer.backend.rslegacy;

import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Gates the legacy-RS mixin config so its mixins only apply when Refined Storage is
 * actually installed. Without this, the grid mixin's missing target class would crash
 * an install that has AE2 (or neither) but not RS.
 * <p>
 * Referenced by {@code ae2organizer.rslegacy.mixins.json}'s {@code "plugin"} field and
 * classloaded during early mod loading — so it must reference only loader APIs, never
 * RS classes.
 */
public final class RsLegacyMixinPlugin implements IMixinConfigPlugin {

    private boolean rsPresent;

    @Override
    public void onLoad(String mixinPackage) {
        this.rsPresent = LoadingModList.get().getModFileById("refinedstorage") != null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return rsPresent;
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
