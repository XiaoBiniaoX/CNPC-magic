package top.cnpcmagic.compat.ars;

import com.hollingsworth.arsnouveau.api.ArsNouveauAPI;
import com.hollingsworth.arsnouveau.api.registry.GlyphRegistry;
import com.hollingsworth.arsnouveau.api.spell.AbstractCastMethod;
import com.hollingsworth.arsnouveau.api.spell.AbstractEffect;
import com.hollingsworth.arsnouveau.api.spell.AbstractSpellPart;
import com.hollingsworth.arsnouveau.api.spell.EntitySpellResolver;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentAmplify;
import com.hollingsworth.arsnouveau.common.spell.method.MethodProjectile;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.hollingsworth.arsnouveau.common.spell.method.MethodTouch;
import com.hollingsworth.arsnouveau.common.spell.method.MethodUnderfoot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.cnpcmagic.provider.MagicCast;
import top.cnpcmagic.server.NpcAnimationSync;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Only loaded when Ars Nouveau is installed; mana is waived by EntitySpellResolver. */
public final class ArsAdapter {
    /** Amplify is the only way to scale an effect, and each effect caps it separately. */
    private static final int AMPLIFY_LIMIT = 3;
    private static final String SEPARATOR = "+";

    private ArsAdapter() {
    }

    private static AbstractCastMethod[] castMethods() {
        return new AbstractCastMethod[]{
            MethodProjectile.INSTANCE, MethodSelf.INSTANCE, MethodTouch.INSTANCE, MethodUnderfoot.INSTANCE
        };
    }

    public static List<AbstractSpellPart> getEffects() {
        List<AbstractSpellPart> effects = new ArrayList<>();
        for (AbstractSpellPart part : GlyphRegistry.getSpellpartMap().values()) {
            if (part instanceof AbstractEffect && part.isEnabled() && part.getRegistryName() != null) {
                effects.add(part);
            }
        }
        return effects;
    }

    public static String getId(AbstractSpellPart method, AbstractSpellPart effect) {
        return method.getRegistryName() + SEPARATOR + effect.getRegistryName();
    }

    public static List<String> getSpellIds() {
        List<String> ids = new ArrayList<>();
        for (AbstractCastMethod method : castMethods()) {
            if (method == null || !method.isEnabled()) {
                continue;
            }
            for (AbstractSpellPart effect : getEffects()) {
                ids.add(getId(method, effect));
            }
        }
        return ids;
    }

    private static AbstractSpellPart effect(String id) {
        int split = id == null ? -1 : id.indexOf(SEPARATOR);
        if (split <= 0) {
            return null;
        }
        ResourceLocation location = ResourceLocation.tryParse(id.substring(split + 1));
        AbstractSpellPart part = location == null ? null : GlyphRegistry.getSpellpartMap().get(location);
        return part instanceof AbstractEffect && part.isEnabled() ? part : null;
    }

    /** Level buys Amplify stacks, clamped to whatever this effect actually allows at cast time. */
    private static int amplify(AbstractSpellPart effect, int level) {
        int cap = effect.getAugmentLimit(AugmentAmplify.INSTANCE.getRegistryName());
        return Math.max(0, Math.min(cap, Math.min(AMPLIFY_LIMIT, level - 1)));
    }

    public static Spell getSpell(String id, int level) {
        int split = id == null ? -1 : id.indexOf(SEPARATOR);
        if (split <= 0) {
            return null;
        }
        ResourceLocation location = ResourceLocation.tryParse(id.substring(0, split));
        AbstractSpellPart method = location == null ? null : GlyphRegistry.getSpellpartMap().get(location);
        AbstractSpellPart effect = effect(id);
        if (!(method instanceof AbstractCastMethod) || !method.isEnabled() || effect == null) {
            return null;
        }
        // Augments attach to the phrase they follow, so Amplify must come after the effect glyph.
        List<AbstractSpellPart> recipe = new ArrayList<>();
        recipe.add(method);
        recipe.add(effect);
        for (int i = amplify(effect, level); i > 0; i--) {
            recipe.add(AugmentAmplify.INSTANCE);
        }
        Spell spell = new Spell(recipe);
        // Reject anything the casting validator would refuse, so a bad pick fails at selection, not mid-fight.
        return ArsNouveauAPI.getInstance().getSpellCastingSpellValidator().validate(recipe).isEmpty() ? spell : null;
    }

    public static int getMaxLevel(String id) {
        AbstractSpellPart effect = effect(id);
        if (effect == null) {
            return 1;
        }
        int cap = effect.getAugmentLimit(AugmentAmplify.INSTANCE.getRegistryName());
        return 1 + Math.max(0, Math.min(cap, AMPLIFY_LIMIT));
    }

    public static MagicCast startCast(LivingEntity caster, String id, int level, LivingEntity target) {
        Spell spell = getSpell(id, level);
        if (spell == null || target == null) {
            return null;
        }
        // EntitySpellResolver waives mana, but onCastOnEntity() reports nothing, so gate on validation first.
        EntitySpellResolver resolver = new EntitySpellResolver(
            SpellContext.fromEntity(spell, caster, ItemStack.EMPTY));
        if (!resolver.canCast(caster)) {
            return null;
        }
        resolver.onCastOnEntity(target);
        NpcAnimationSync.play(caster, id, 10);
        return new Session(id);
    }

    private static final class Session implements MagicCast {
        private final String animation;
        private boolean finished;

        private Session(String animation) {
            this.animation = animation;
        }

        @Override
        public boolean tick(LivingEntity caster, LivingEntity target) {
            return true;
        }

        @Override
        public boolean wasSuccessful() {
            return true;
        }

        @Override
        public void cancel(LivingEntity caster) {
            if (!finished) {
                finished = true;
                NpcAnimationSync.stop(caster);
            }
        }
    }
}
