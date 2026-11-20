package top.cnpcmagic.client.gui;

import top.cnpcmagic.casting.SpellDefinition;
import java.util.*;

final class EffectEditor extends EditorScreen {
    final DefinitionEditor definition;
    final SpellDefinition.Effect effect;
    final Map<String, String> values = new HashMap<>();
    int page;
    EffectEditor(DefinitionEditor definition, SpellDefinition.Effect effect) {
        super(definition, "effect"); this.definition = definition; this.effect = effect;
        values.put("id", effect.id); values.put("amount", "" + effect.amount); values.put("duration", "" + effect.duration); values.put("delay", "" + effect.delay);
    }
    @Override protected void init() {
        super.init(); button(width - 100, 7, 92, "done", this::onClose);
        int x = Math.max(14, (width - 400) / 2), w = Math.min(400, width - 28);
        segments(x, 43, w, "page", new String[]{"settings", "timing"}, page, i -> page = i);
        if (page == 0) {
            buttonText(x, 72, w, net.minecraft.network.chat.Component.literal(text("effect." + effect.type.name())), () -> minecraft.setScreen(new DefinitionEditor.ChoiceScreen(this, "effect", Arrays.stream(SpellDefinition.EffectType.values()).map(e -> "effect." + e.name()).toArray(String[]::new), i -> effect.type = SpellDefinition.EffectType.values()[i])));
            field(x, 100, w, "registryId", values.get("id"), s -> values.put("id", s));
            field(x, 126, w, "amount", values.get("amount"), s -> values.put("amount", s));
            segments(x, 152, w, "enableEffect", new String[]{"cnpcmagic.gui.off", "cnpcmagic.gui.on"}, effect.enabled ? 1 : 0, i -> effect.enabled = i == 1);
        } else {
            field(x, 72, w, "duration", values.get("duration"), s -> values.put("duration", s));
            field(x, 98, w, "delay", values.get("delay"), s -> values.put("delay", s));
            button(x, 126, (w - 6) / 3, "up", () -> move(-1));
            button(x + (w - 6) / 3 + 3, 126, (w - 6) / 3, "down", () -> move(1));
            button(x + 2 * ((w - 6) / 3 + 3), 126, (w - 6) / 3, "duplicate", () -> { if (apply() && definition.draft.effects.size() < 32) { definition.draft.effects.add(SpellDefinition.Effect.load(effect.save())); minecraft.setScreen(definition); } });
        }
        button(x, height - 54, w, "cnpcmagic.gui.remove", () -> { definition.draft.effects.remove(effect); minecraft.setScreen(definition); });
    }
    private void move(int offset) { var list = definition.draft.effects; int from = list.indexOf(effect), to = from + offset; if (to >= 0 && to < list.size()) Collections.swap(list, from, to); }
    private boolean apply() {
        try {
            double amount = Double.parseDouble(values.get("amount")); int duration = Integer.parseInt(values.get("duration")), delay = Integer.parseInt(values.get("delay"));
            if (!Double.isFinite(amount) || amount < 0 || amount > 1000 || duration < 1 || duration > 24000 || delay < 0 || delay > 1200) throw new IllegalArgumentException();
            if ((effect.type == SpellDefinition.EffectType.STATUS || effect.type == SpellDefinition.EffectType.AUDIO || effect.type == SpellDefinition.EffectType.SUMMON) && net.minecraft.resources.ResourceLocation.tryParse(values.get("id")) == null) throw new IllegalArgumentException();
            effect.amount = amount; effect.duration = duration; effect.delay = delay; effect.id = values.get("id"); return true;
        } catch (IllegalArgumentException e) { status = text("invalidEffect"); return false; }
    }
    @Override public void onClose() { if (apply()) minecraft.setScreen(definition); }
}
