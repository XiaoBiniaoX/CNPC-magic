package top.cnpcmagic.wizardry;

import electroblob.wizardry.util.SpellModifiers;
import electroblob.wizardry.entity.EntityShield;

/** Internal state bridge added to CustomNPCs entities by Mixin. */
public interface MagicCasterAccess {
    void cnpcmagic$setModifiers(SpellModifiers modifiers);
    int cnpcmagic$getSpellTargetId();
    void cnpcmagic$setSpellTargetId(int targetId);
    EntityShield cnpcmagic$getShield();
    void cnpcmagic$setShield(EntityShield shield);
}
