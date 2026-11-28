package top.cnpcmagic.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import top.cnpcmagic.casting.SpellDefinition;
import top.cnpcmagic.network.C2MMagicEditor;
import top.cnpcmagic.network.NetworkHandler;

/** Editor drafts and the detached preview scene share one editing session. */
public final class MagicEditor {
    static GuiNpcMagic editor;
    static DefinitionEditor definition;
    /** Local detached-preview state only; it never drives a Minecraft dimension transition. */
    private static boolean previewActive;

    public static boolean isPreviewActive() { return previewActive; }

    public static void send(String action) { CompoundTag t = new CompoundTag(); t.putString("action", action); send(t); }
    public static void send(CompoundTag data) { NetworkHandler.CHANNEL.sendToServer(new C2MMagicEditor(data)); }
    public static void receive(CompoundTag data) {
        SpellPicker picker = editor == null ? null : editor.picker;
        switch (data.getString("action")) {
            case "settings" -> { if (editor != null) editor.setGuiData(data); }
            case "saved" -> { if (editor != null) editor.saved(data); }
            case "error" -> {
                String message = data.getString("message");
                if (editor != null) { editor.saving = false; editor.leaveAfterSave = false; editor.status = message; }
                if (definition != null) { definition.saving = false; definition.status = message; }
                if (Minecraft.getInstance().screen instanceof EditorScreen screen) screen.rebuild();
            }
            case "definitionSaved" -> {
                SpellDefinition d = SpellDefinition.load(data.getCompound("definition"));
                if (editor != null) editor.library.put(d.id, d);
                if (definition != null) definition.saved();
            }
            case "definitionDeleted" -> { if (editor != null) { editor.library.remove(data.getString("id")); if (Minecraft.getInstance().screen == editor) editor.rebuild(); } }
            case "previewBegin" -> {
                previewActive = true;
                top.cnpcmagic.client.PreviewScene.open(net.minecraft.core.BlockPos.of(data.getLong("origin")));
                if (picker != null) picker.begin();
            }
            case "previewFrame" -> top.cnpcmagic.client.PreviewScene.receive(data);
            case "previewStatus" -> { if (picker != null && !picker.closing && picker.request == data.getInt("request")) picker.status = data.getString("message"); }
            case "previewError" -> { if (picker != null && !picker.closing && picker.request == data.getInt("request")) picker.error(data.getString("message")); }
            case "previewEnd" -> {
                top.cnpcmagic.client.PreviewScene.dispose();
                if (picker != null) picker.end();
                previewActive = false;
            }
        }
    }
    public static void registerBoltRenderer(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(top.cnpcmagic.casting.BuiltinSpell.BOLT.get(), net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
    }
}
