package top.cnpcmagic.mixin;

import noppes.npcs.api.wrapper.EntityLivingBaseWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import top.cnpcmagic.server.ScriptMagic;

/** PlayerWrapper and NPCWrapper inherit these methods directly in scripts. */
@Mixin(value = EntityLivingBaseWrapper.class, remap = false)
public abstract class MixinLivingScriptMagic {
    @Unique
    public boolean castSpell(String spellId, int level, Object target) {
        return ScriptMagic.cast(this, spellId, level, target);
    }

    @Unique
    public boolean isCastingSpell() {
        return ScriptMagic.isCasting(this);
    }

    @Unique
    public boolean cancelSpell() {
        return ScriptMagic.cancel(this);
    }
}
