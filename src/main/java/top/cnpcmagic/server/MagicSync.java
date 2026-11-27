package top.cnpcmagic.server;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import noppes.npcs.CustomNpcsPermissions;
import noppes.npcs.NoppesUtilServer;
import noppes.npcs.entity.EntityNPCInterface;
import top.cnpcmagic.data.MagicDataStore;
import top.cnpcmagic.data.MagicRule;
import top.cnpcmagic.provider.MagicProvider;
import top.cnpcmagic.provider.MagicProviders;
import top.cnpcmagic.casting.SpellDefinition;
import top.cnpcmagic.casting.SpellLibrary;
import top.cnpcmagic.network.NetworkHandler;
import top.cnpcmagic.network.S2CMagicEditor;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MagicSync {
    private MagicSync() {
    }

    public static void sendFull(ServerPlayer player) {
        EntityNPCInterface npc = NoppesUtilServer.getEditingNpc(player);
        if (npc == null || !CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.NPC_GUI)) {
            error(player, "No permission to edit this NPC"); return;
        }
        MagicDataStore store = MagicDataStore.get(player.server);
        CompoundTag providers = new CompoundTag();
        for (MagicProvider provider : MagicProviders.all()) {
            providers.put(provider.getId(), MagicDataStore.saveRules(store.getRules(npc, provider.getId())));
        }
        CompoundTag tag = new CompoundTag();
        tag.put("providers", providers);
        tag.put("library", SpellLibrary.get(player.server).save(new CompoundTag()).getCompound("spells"));
        tag.putUUID("npc", npc.getUUID());
        tag.putString("action", "settings");
        send(player, tag);
    }

    public static void save(ServerPlayer player, CompoundTag tag) {
        EntityNPCInterface npc = NoppesUtilServer.getEditingNpc(player);
        if (npc == null || !CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.NPC_GUI)) { error(player, "No permission to edit this NPC"); return; }
        if (!tag.hasUUID("npc") || !tag.getUUID("npc").equals(npc.getUUID())) { error(player, "The edited NPC has changed; reopen the editor"); return; }
        MagicDataStore store = MagicDataStore.get(player.server);
        CompoundTag providers = tag.getCompound("providers");
        int count = 0;
        Map<String, List<MagicRule>> updated = new HashMap<>();
        for (MagicProvider provider : MagicProviders.all()) {
            if (!providers.contains(provider.getId())) {
                continue;
            }
            List<MagicRule> rules = MagicDataStore.loadRules(providers.get(provider.getId()));
            count += rules.size();
            if (count > 256) { error(player, "Maximum 256 NPC spell entries"); return; }
            rules.forEach(rule -> rule.sanitize(provider));
            for (MagicRule rule : rules) {
                String invalid = rule.conditions.validate(rule.range);
                if (!invalid.isEmpty()) { error(player, invalid); return; }
                for (var type : List.of(top.cnpcmagic.data.RuleConditions.Type.CASTER_EFFECT, top.cnpcmagic.data.RuleConditions.Type.TARGET_EFFECT)) {
                    if (!rule.conditions.enabled.contains(type)) continue;
                    String id = type == top.cnpcmagic.data.RuleConditions.Type.CASTER_EFFECT ? rule.conditions.casterEffect : rule.conditions.targetEffect;
                    if (!net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.containsKey(net.minecraft.resources.ResourceLocation.parse(id))) { error(player, "Unknown condition effect: " + id); return; }
                }
            }
            if (provider.getId().equals("cnpcmagic")) for (MagicRule rule : rules) {
                if (!SpellLibrary.get(player.server).spells.containsKey(rule.spellId)) { rule.spellId = ""; rule.enabled = false; }
            }
            updated.put(provider.getId(), rules);
        }
        try { store.apply(npc, updated); }
        catch (IllegalStateException e) { error(player, e.getMessage()); return; }
        MagicCombatHandler.reset(npc);
        CompoundTag ack = new CompoundTag(); ack.putString("action", "saved"); ack.putUUID("npc", npc.getUUID());
        CompoundTag accepted = new CompoundTag();
        for (MagicProvider provider : MagicProviders.all()) accepted.put(provider.getId(), MagicDataStore.saveRules(store.getRules(npc, provider.getId())));
        ack.put("providers", accepted); send(player, ack);
    }

    public static void send(ServerPlayer player, CompoundTag data) { PacketDistributor.sendToPlayer(player, new S2CMagicEditor(data)); }
    public static void error(ServerPlayer player, String message) { CompoundTag t = new CompoundTag(); t.putString("action", "error"); t.putString("message", message); send(player, t); }
    public static boolean canEdit(ServerPlayer player) { return NoppesUtilServer.getEditingNpc(player) != null && CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.NPC_GUI); }
    public static void saveDefinition(ServerPlayer player, CompoundTag packet) {
        if (!canEdit(player)) { error(player, "No permission to edit spells"); return; }
        top.cnpcmagic.api.MagicApi.decodeDefinitionAsync(packet.getCompound("definition")).whenCompleteAsync((definition, failure) -> {
        if (player.hasDisconnected()) return;
        if (failure != null) {
            Throwable cause = failure instanceof java.util.concurrent.CompletionException ? failure.getCause() : failure;
            error(player, cause.getMessage()); return;
        }
        try {
            for (var e : definition.effects) {
                var key = net.minecraft.resources.ResourceLocation.tryParse(e.id);
                if (e.type == SpellDefinition.EffectType.STATUS && !net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.containsKey(key)
                    || e.type == SpellDefinition.EffectType.SUMMON && !net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(key)
                    || e.type == SpellDefinition.EffectType.AUDIO && !net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.containsKey(key))
                    throw new IllegalArgumentException("Unknown effect registry ID: " + e.id);
            }
            SpellLibrary library = SpellLibrary.get(player.server);
            if (library.spells.size() >= 512 && !library.spells.containsKey(definition.id)) throw new IllegalArgumentException("Maximum 512 spell definitions");
            SpellDefinition previous = library.spells.put(definition.id, definition); library.setDirty();
            try { library.flush(); }
            catch (IllegalStateException e) {
                if (previous == null) library.spells.remove(definition.id); else library.spells.put(definition.id, previous);
                throw e;
            }
            CompoundTag ack = new CompoundTag(); ack.putString("action", "definitionSaved"); ack.put("definition", definition.save()); send(player, ack);
        } catch (IllegalArgumentException | IllegalStateException e) { error(player, e.getMessage()); }
        }, player.server);
    }
    public static void deleteDefinition(ServerPlayer player, String id) {
        if (!canEdit(player)) { error(player, "No permission to edit spells"); return; }
        SpellLibrary library = SpellLibrary.get(player.server);
        SpellDefinition previous = library.spells.remove(id);
        if (previous != null) {
            library.setDirty();
            try { library.flush(); }
            catch (IllegalStateException e) { library.spells.put(id, previous); error(player, e.getMessage()); return; }
        }
        CompoundTag ack = new CompoundTag(); ack.putString("action", "definitionDeleted"); ack.putString("id", id); send(player, ack);
    }
}
