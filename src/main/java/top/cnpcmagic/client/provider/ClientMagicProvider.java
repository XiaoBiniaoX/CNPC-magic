package top.cnpcmagic.client.provider;

import java.util.List;

public interface ClientMagicProvider {
    String getId();

    List<String> getSpellIds();

    String getDisplayName(String spellId);

    int getMaxLevel(String spellId);
}
