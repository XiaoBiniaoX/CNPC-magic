package top.cnpcmagic.mixin;

import noppes.npcs.api.wrapper.BlockWrapper;
import noppes.npcs.api.wrapper.ItemScriptedWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import top.cnpcmagic.server.ScriptMagic;

/** Scripted blocks and items specify their living caster explicitly. */
@Mixin(value = {BlockWrapper.class, ItemScriptedWrapper.class}, remap = false)
public abstract class MixinScriptCarrierMagic {
    @Unique
    public boolean castSpell(Object caster, String spellId, int level, Object target) {
        return ScriptMagic.cast(caster, spellId, level, target);
    }

    @Unique
    public boolean isCastingSpell(Object caster) {
        return ScriptMagic.isCasting(caster);
    }

    @Unique
    public boolean cancelSpell(Object caster) {
        return ScriptMagic.cancel(caster);
    }
}
