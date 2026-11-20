package top.cnpcmagic.client.gui;

import net.minecraft.nbt.TagParser;
import java.util.ArrayList;

final class LibraryScreen extends EditorScreen {
    final GuiNpcMagic editor;
    LibraryScreen(GuiNpcMagic editor) { super(editor, "library"); this.editor = editor; }
    @Override protected void init() {
        super.init();
        var definitions = new ArrayList<>(editor.library.values());
        addRenderableWidget(new Rows(14, 43, width - 28, height - 110, definitions.stream().map(d -> editor.name(d.id) + " · " + d.id).toList(), -1,
            i -> minecraft.setScreen(new DefinitionEditor(editor, definitions.get(i)))));
        button(14, height - 56, (width - 34) / 2, "newSpell", () -> minecraft.setScreen(new DefinitionEditor(editor, null)));
        button(width / 2 + 3, height - 56, width / 2 - 17, "import", () -> {
            try { minecraft.setScreen(new DefinitionEditor(editor, top.cnpcmagic.casting.SpellDefinition.load(TagParser.parseTag(minecraft.keyboardHandler.getClipboard())))); }
            catch (com.mojang.brigadier.exceptions.CommandSyntaxException | IllegalArgumentException e) { status = text("invalidImport") + ": " + e.getMessage(); }
        });
    }
}
