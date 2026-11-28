package top.cnpcmagic.mixin;

import io.redspace.ironsspellbooks.api.entity.NoopMagicEntity;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
import io.redspace.ironsspellbooks.capabilities.magic.SyncedSpellData;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import top.cnpcmagic.compat.iron.IronAdapter;

/** Native Iron packets and addon handlers can address the actual NPC, without a player proxy. */
@Mixin(value = EntityNPCInterface.class, remap = false)
public abstract class MixinIronNpc implements NoopMagicEntity {
    @Unique private MagicData cnpcmagic$ironClientData;

    @Override public MagicData getMagicData() {
        LivingEntity npc = (LivingEntity)(Object)this;
        MagicData scoped = IronAdapter.getCastData(npc);
        if (scoped != null) return scoped;
        if (cnpcmagic$ironClientData == null) {
            cnpcmagic$ironClientData = new MagicData(true);
            cnpcmagic$ironClientData.setSyncedData(new SyncedSpellData(npc));
        }
        return cnpcmagic$ironClientData;
    }

    @Override public boolean isCasting() { return getMagicData().isCasting(); }

    @Override public void setSyncedSpellData(SyncedSpellData synced) {
        LivingEntity npc = (LivingEntity)(Object)this;
        if (!npc.level().isClientSide) return;
        MagicData data = getMagicData();
        String previous = data.getCastingSpellId();
        data.setSyncedData(synced);
        if (!synced.isCasting()) {
            data.resetCastingState();
        } else if (!previous.equals(synced.getCastingSpellId())) {
            data.resetAdditionalCastData();
            var spell = SpellRegistry.getSpell(synced.getCastingSpellId());
            data.initiateCast(spell, synced.getCastingSpellLevel(), spell.getEffectiveCastTime(synced.getCastingSpellLevel(), npc),
                CastSource.MOB, SpellSelectionManager.MAINHAND);
            data.setAdditionalCastData(spell.getEmptyCastData());
        }
    }
}
