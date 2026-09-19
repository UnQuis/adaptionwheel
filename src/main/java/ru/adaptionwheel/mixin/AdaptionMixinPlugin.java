package ru.adaptionwheel.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Gates optional integration mixins on mod presence.
 * {@link GuardianLaserMixin} targets a Draconic Evolution class; applying it
 * without that mod would produce missing-target errors, so it is skipped unless
 * {@code draconicevolution} is on the mod list.
 */
public class AdaptionMixinPlugin implements IMixinConfigPlugin {

    private static final String CHAOS_MIXIN = "GuardianLaserMixin";

    private static boolean isDraconicEvolutionLoaded() {
        try {
            net.neoforged.fml.loading.LoadingModList list = net.neoforged.fml.loading.FMLLoader.getCurrent().getLoadingModList();
            return list != null && list.getModFileById("draconicevolution") != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith(CHAOS_MIXIN)) {
            return isDraconicEvolutionLoaded();
        }
        return true;
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
