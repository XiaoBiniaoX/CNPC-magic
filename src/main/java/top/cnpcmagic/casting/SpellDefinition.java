package top.cnpcmagic.casting;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;

/** Versioned world definitions; NPCs retain only a stable spell ID in their existing rule list. */
public final class SpellDefinition {
    public enum Delivery { INSTANT, PROJECTILE, RAY, AREA, GROUND, SELF, CHANNEL, SUMMON }
    public enum EffectType { DAMAGE, HEALING, STATUS, MOTION, VISUAL, AUDIO, SUMMON }
    public enum Filter { ENEMIES, ALLIES, ALL, SELF }
    public static final class Effect {
        public EffectType type = EffectType.DAMAGE;
        public String id = "";
        public double amount = 4;
        public int duration = 100, delay;
        public boolean enabled = true;
        public CompoundTag save() {
            CompoundTag t = new CompoundTag(); t.putString("type", type.name()); t.putString("id", id);
            t.putDouble("amount", amount); t.putInt("duration", duration); t.putInt("delay", delay); t.putBoolean("enabled", enabled); return t;
        }
        public static Effect load(CompoundTag t) {
            Effect e = new Effect(); e.type = EffectType.valueOf(t.getString("type")); e.id = t.getString("id");
            e.amount = t.getDouble("amount"); e.duration = t.getInt("duration"); e.delay = t.getInt("delay"); e.enabled = t.getBoolean("enabled"); return e;
        }
    }
    public String id = "cnpcmagic:custom", name = "Custom spell", description = "", school = "arcane", tags = "";
    public Delivery delivery = Delivery.PROJECTILE;
    public Filter filter = Filter.ENEMIES;
    public String resource = "health";
    public double range = 32, radius = 3, speed = 0.8, gravity, power = 1, healthCost;
    public int castTicks = 10, cooldownTicks = 20, duration = 100, interval = 10, maxTargets = 16;
    public boolean enabled = true, lineOfSight = true, homing;
    public final List<Effect> effects = new ArrayList<>();
    public CompoundTag save() {
        CompoundTag t = new CompoundTag(); t.putInt("schema", 1); t.putString("id", id); t.putString("name", name);
        t.putString("description", description); t.putString("school", school); t.putString("tags", tags);
        t.putString("delivery", delivery.name()); t.putString("filter", filter.name());
        t.putDouble("range", range); t.putDouble("radius", radius); t.putDouble("speed", speed); t.putDouble("gravity", gravity);
        t.putString("resource", resource); t.putDouble("power", power); t.putDouble("healthCost", healthCost); t.putInt("castTicks", castTicks);
        t.putInt("cooldown", cooldownTicks); t.putInt("duration", duration); t.putInt("interval", interval); t.putInt("maxTargets", maxTargets);
        t.putBoolean("enabled", enabled); t.putBoolean("lineOfSight", lineOfSight); t.putBoolean("homing", homing);
        ListTag list = new ListTag(); effects.forEach(e -> list.add(e.save())); t.put("effects", list); return t;
    }
    public static SpellDefinition load(CompoundTag t) {
        if (t.getInt("schema") != 1) throw new IllegalArgumentException("Unsupported spell schema");
        SpellDefinition d = new SpellDefinition(); d.id = t.getString("id"); d.name = t.getString("name");
        d.description = t.getString("description"); d.school = t.getString("school"); d.tags = t.getString("tags");
        d.delivery = Delivery.valueOf(t.getString("delivery")); d.filter = Filter.valueOf(t.getString("filter"));
        d.range = t.getDouble("range"); d.radius = t.getDouble("radius"); d.speed = t.getDouble("speed"); d.gravity = t.getDouble("gravity");
        d.resource = t.contains("resource") ? t.getString("resource") : "health";
        d.power = t.getDouble("power"); d.healthCost = t.getDouble("healthCost"); d.castTicks = t.getInt("castTicks");
        d.cooldownTicks = t.getInt("cooldown"); d.duration = t.getInt("duration"); d.interval = t.getInt("interval"); d.maxTargets = t.getInt("maxTargets");
        d.enabled = t.getBoolean("enabled"); d.lineOfSight = t.getBoolean("lineOfSight"); d.homing = t.getBoolean("homing");
        for (var raw : t.getList("effects", 10)) d.effects.add(Effect.load((CompoundTag) raw));
        String error = d.validate(); if (!error.isEmpty()) throw new IllegalArgumentException(error); return d;
    }
    public String validate() {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !key.getNamespace().equals("cnpcmagic")) return "id: use cnpcmagic:name";
        if (name.isBlank() || name.length() > 120) return "name: 1–120 characters";
        if (description.length() > 2048 || tags.length() > 512 || school.length() > 80 || resource.length() > 80) return "description, tags, school or resource is too long";
        if (!finite(range, 1, 64) || !finite(radius, 0.1, 16)) return "range: 1–64; radius: 0.1–16 blocks";
        if (!finite(speed, 0.05, 4) || !finite(gravity, 0, 0.2) || !finite(power, 0.01, 100)) return "speed: 0.05–4; gravity: 0–0.2; power: 0.01–100";
        if (!finite(healthCost, 0, 1000)) return "healthCost: 0–1000";
        if (castTicks < 0 || castTicks > 1200 || cooldownTicks < 0 || cooldownTicks > 72000) return "castTicks: 0–1200; cooldown: 0–72000 ticks";
        if (duration < 1 || duration > 1200 || interval < 1 || interval > 1200 || maxTargets < 1 || maxTargets > 64) return "duration/interval: 1–1200; maxTargets: 1–64";
        if (effects.isEmpty() || effects.size() > 32) return "effects: 1–32 entries";
        for (int i = 0; i < effects.size(); i++) {
            Effect e = effects.get(i);
            if (!finite(e.amount, 0, 1000) || e.duration < 1 || e.duration > 24000 || e.delay < 0 || e.delay > 1200) return "effects[" + i + "]: invalid amount, duration or delay";
            if (e.type == EffectType.STATUS || e.type == EffectType.SUMMON || e.type == EffectType.AUDIO)
                if (ResourceLocation.tryParse(e.id) == null) return "effects[" + i + "].id: namespaced ID required";
        }
        return "";
    }
    private static boolean finite(double n, double min, double max) { return Double.isFinite(n) && n >= min && n <= max; }
    public static SpellDefinition template(String id, Delivery delivery, EffectType type) {
        SpellDefinition d = new SpellDefinition(); d.id = "cnpcmagic:" + id; d.name = id.replace('_', ' '); d.delivery = delivery;
        Effect e = new Effect(); e.type = type;
        if (type == EffectType.SUMMON) { e.id = "minecraft:allay"; e.amount = 1; e.duration = 200; }
        if (type == EffectType.STATUS) { e.id = "minecraft:resistance"; e.amount = 1; }
        if (type == EffectType.HEALING || delivery == Delivery.SELF) d.filter = Filter.ALLIES;
        d.effects.add(e); return d;
    }
}
