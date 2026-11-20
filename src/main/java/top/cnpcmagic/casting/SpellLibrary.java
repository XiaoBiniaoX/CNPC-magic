package top.cnpcmagic.casting;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import top.cnpcmagic.data.CnpcPlusData;
import top.cnpcmagic.CNPCMagic;
import java.util.LinkedHashMap;
import java.util.Map;
import static top.cnpcmagic.casting.SpellDefinition.*;

public final class SpellLibrary extends CnpcPlusData {
    public final Map<String, SpellDefinition> spells = new LinkedHashMap<>();
    public SpellLibrary() {
        super("magic_library");
        for (SpellDefinition d : new SpellDefinition[]{
            template("magic_missile", Delivery.PROJECTILE, EffectType.DAMAGE),
            template("magic_ray", Delivery.RAY, EffectType.DAMAGE), template("ground_burst", Delivery.GROUND, EffectType.DAMAGE),
            template("healing", Delivery.INSTANT, EffectType.HEALING), template("resistance", Delivery.SELF, EffectType.STATUS),
            template("push_wave", Delivery.AREA, EffectType.MOTION), template("channel_beam", Delivery.CHANNEL, EffectType.DAMAGE),
            template("temporary_allay", Delivery.SUMMON, EffectType.SUMMON)}) spells.put(d.id, d);
        setDirty();
    }
    private SpellLibrary(CompoundTag t) {
        super("magic_library");
        CompoundTag entries = t.getCompound("spells");
        for (String key : entries.getAllKeys()) {
            try { SpellDefinition d = SpellDefinition.load(entries.getCompound(key)); spells.put(d.id, d); }
            catch (IllegalArgumentException e) { CNPCMagic.LOGGER.error("Invalid saved spell {}: {}", key, e.getMessage()); }
        }
    }
    public static SpellLibrary get(MinecraftServer server) {
        return CnpcPlusData.get(server, "cnpcmagic_spell_library", "magic_library", SpellLibrary::new, SpellLibrary::new);
    }
    @Override public CompoundTag save(CompoundTag t) {
        CompoundTag entries = new CompoundTag(); spells.forEach((id, d) -> entries.put(id, d.save())); t.put("spells", entries); t.putInt("schema", 1); return t;
    }
}
