package top.cnpcmagic.compat.ebwizardry;

import com.binaris.wizardry.content.spell.DefaultProperties;
import com.binaris.wizardry.content.spell.abstr.AreaEffectSpell;
import com.binaris.wizardry.content.spell.abstr.ConstructRangedSpell;
import com.binaris.wizardry.content.spell.abstr.ConstructSpell;
import com.binaris.wizardry.content.spell.abstr.RaySpell;
import com.binaris.wizardry.setup.registries.EBMobEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayer;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import noppes.npcs.entity.EntityNPCInterface;

/** Changes only the isolated preview actors/environment; native spell limits stay intact. */
public final class EbWizardryPreview {
    private EbWizardryPreview() {}

    public static double targetDistance(String id) {
        var spell = EbWizardryAdapter.getSpell(id);
        double limit = 0;
        if (spell instanceof RaySpell || spell instanceof ConstructRangedSpell) limit = spell.property(DefaultProperties.RANGE);
        else if (spell instanceof AreaEffectSpell || spell instanceof ConstructSpell) limit = spell.property(DefaultProperties.EFFECT_RADIUS);
        return limit > 0 ? Math.min(6, limit * 0.65) : 6;
    }

    public static void resetArena(ServerLevel level, BlockPos origin) {
        for (int x = -20; x <= 40; x++) for (int z = -20; z <= 20; z++) for (int y = -1; y <= 5; y++)
            level.setBlock(origin.offset(x, y, z), (y == -1 ? Blocks.SMOOTH_STONE : Blocks.AIR).defaultBlockState(), 2);
    }

    public static void prepare(String id, EntityNPCInterface npc, Mob target) {
        // CNPC's no-AI tick skips physics; derive grounded state from the real support collision.
        npc.setOnGround(!npc.level().noCollision(npc, npc.getBoundingBox().move(0, -0.05, 0)));
        switch (id) {
            case "ebwizardry:heal", "ebwizardry:greater_heal", "ebwizardry:group_heal", "ebwizardry:healing_aura",
                 "ebwizardry:font_of_vitality", "ebwizardry:life_drain" -> npc.setHealth(npc.getMaxHealth() / 2);
            case "ebwizardry:heal_ally" -> target.setHealth(target.getMaxHealth() / 2);
            case "ebwizardry:cure_effects", "ebwizardry:reversal" -> npc.addEffect(new MobEffectInstance(MobEffects.POISON, 600));
            case "ebwizardry:remove_curse" -> npc.addEffect(new MobEffectInstance(EBMobEffects.CURSE_OF_UNDEATH.get(), 600));
            case "ebwizardry:arcane_lock" -> {
                BlockPos chest = target.blockPosition();
                npc.level().setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
                target.setPos(chest.getX() + 0.5, chest.getY() + 0.875, chest.getZ() + 0.5);
            }
            case "ebwizardry:enrage" -> {
                target.setNoAi(false);
                target.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
            }
        }
    }

    /** Native Font of Mana only affects players; this actor is a recipient, never the caster. */
    public static FakePlayer manaRecipient(EntityNPCInterface caster) {
        ServerLevel level = (ServerLevel) caster.level();
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "ManaRecipient"));
        noppes.npcs.controllers.data.PlayerData.get(player).factionData.factionData
            .put(caster.faction.id, caster.faction.friendlyPoints);
        player.moveTo(caster.getX() + 2, caster.getY(), caster.getZ() + 2, 0, 0);
        level.addNewPlayer(player);
        return player;
    }

    /** Invalidate the native client lock cache before removing this preview's chest. */
    public static void clear(EntityNPCInterface caster, Mob target) {
        if (caster.level().getBlockEntity(BlockPos.containing(EbWizardryAdapter.targetSurface(target)))
            instanceof net.minecraft.world.level.block.entity.BaseContainerBlockEntity container) {
            var lock = com.binaris.wizardry.core.platform.Services.OBJECT_DATA.getArcaneLockData(container);
            if (caster.getUUID().equals(lock.getArcaneLockOwnerUUID())) lock.clearArcaneLockOwner();
        }
    }
}
