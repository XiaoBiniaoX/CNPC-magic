package top.cnpcmagic;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;
import java.util.List;
import java.util.Set;

/** The NPC target exists even when Iron is absent; skip its optional interface bridge then. */
public final class CnpcMixinPlugin implements IMixinConfigPlugin {
    public void onLoad(String mixinPackage) { }
    public String getRefMapperConfig() { return null; }
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!mixinClassName.endsWith(".MixinIronNpc")) return true;
        try {
            MixinService.getService().getBytecodeProvider().getClassNode("io.redspace.ironsspellbooks.api.entity.NoopMagicEntity");
            return true;
        } catch (ClassNotFoundException | java.io.IOException e) {
            return false;
        }
    }
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    public List<String> getMixins() { return null; }
    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) { }
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) { }
}
