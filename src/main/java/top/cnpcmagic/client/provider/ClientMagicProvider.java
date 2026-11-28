package top.cnpcmagic.client.provider;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import java.util.List;

public interface ClientMagicProvider {
    String getId();

    List<String> getSpellIds();

    String getDisplayName(String spellId);

    int getMaxLevel(String spellId);

    default boolean requiresMeleeTarget(String spellId) { return false; }

    default java.util.Map<String, Component> getSchools() { return java.util.Map.of(); }

    default String getSchoolId(String spellId) { return ""; }

    List<? extends Component> describe(String spellId, int level, LivingEntity caster);
}
