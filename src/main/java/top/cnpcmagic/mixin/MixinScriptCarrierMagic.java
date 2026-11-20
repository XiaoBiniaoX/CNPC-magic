package top.cnpcmagic.mixin;

import noppes.npcs.api.wrapper.BlockWrapper;
import noppes.npcs.api.wrapper.ItemScriptedWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import top.cnpcmagic.server.ScriptMagic;

/** Both scripted block types inherit BlockWrapper. Carriers specify the living caster explicitly. */
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

    @Unique public top.cnpcmagic.casting.SpellResult castSpellResult(Object caster, String id, int level, Object target) { return ScriptMagic.castResult(caster, id, level, target); }
    @Unique public top.cnpcmagic.casting.SpellResult castSpellAt(Object caster, String id, int level, double x, double y, double z) { return ScriptMagic.castAt(caster, id, level, x, y, z); }
    @Unique public top.cnpcmagic.casting.SpellResult castSpellDirection(Object caster, String id, int level, double x, double y, double z) { return ScriptMagic.castDirection(caster, id, level, x, y, z); }
    @Unique public top.cnpcmagic.casting.SpellResult castSpellSelf(Object caster, String id, int level) { return ScriptMagic.castSelf(caster, id, level); }
    @Unique public top.cnpcmagic.casting.SpellResult castSpellWithoutTarget(Object caster, String id, int level) { return ScriptMagic.castResult(caster, id, level, top.cnpcmagic.casting.SpellTarget.none()); }
    @Unique public String[] findSpells(Object caster, String filter) { return ScriptMagic.query(caster, filter); }
    @Unique public String getSpellInfo(Object caster, String id) { return ScriptMagic.info(caster, id); }
    @Unique public int getSpellCooldown(Object caster, String id) { return ScriptMagic.cooldown(caster, id); }
    @Unique public top.cnpcmagic.casting.SpellResult getSpellInstance(String id) { return top.cnpcmagic.casting.CastingService.result(id); }
}
