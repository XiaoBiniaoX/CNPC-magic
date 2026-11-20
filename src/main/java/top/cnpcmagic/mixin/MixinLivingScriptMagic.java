package top.cnpcmagic.mixin;

import noppes.npcs.api.wrapper.EntityLivingBaseWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import top.cnpcmagic.server.ScriptMagic;

/** PlayerWrapper and NPCWrapper inherit these public methods; scripts need no Java.type. */
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

    @Unique public top.cnpcmagic.casting.SpellResult castSpellResult(String id, int level, Object target) { return ScriptMagic.castResult(this, id, level, target); }
    @Unique public top.cnpcmagic.casting.SpellResult castSpellAt(String id, int level, double x, double y, double z) { return ScriptMagic.castAt(this, id, level, x, y, z); }
    @Unique public top.cnpcmagic.casting.SpellResult castSpellDirection(String id, int level, double x, double y, double z) { return ScriptMagic.castDirection(this, id, level, x, y, z); }
    @Unique public top.cnpcmagic.casting.SpellResult castSpellSelf(String id, int level) { return ScriptMagic.castSelf(this, id, level); }
    @Unique public top.cnpcmagic.casting.SpellResult castSpellWithoutTarget(String id, int level) { return ScriptMagic.castResult(this, id, level, top.cnpcmagic.casting.SpellTarget.none()); }
    @Unique public String[] findSpells(String filter) { return ScriptMagic.query(this, filter); }
    @Unique public String getSpellInfo(String id) { return ScriptMagic.info(this, id); }
    @Unique public int getSpellCooldown(String id) { return ScriptMagic.cooldown(this, id); }
    @Unique public top.cnpcmagic.casting.SpellResult getSpellInstance(String id) { return top.cnpcmagic.casting.CastingService.result(id); }
}
