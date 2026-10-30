package top.cnpcmagic;

import java.util.Collections;
import java.util.List;
import zone.rong.mixinbooter.ILateMixinLoader;

/** Lets the required MixinBooter instance apply mixins to CustomNPCs and Wizardry. */
public final class LateMixinLoader implements ILateMixinLoader {
    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList("mixins.cnpcmagic.json");
    }
}
