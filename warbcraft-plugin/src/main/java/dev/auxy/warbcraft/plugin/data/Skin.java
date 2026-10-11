package dev.auxy.warbcraft.plugin.data;

import org.bukkit.configuration.ConfigurationSection;

/**
 * NPC skini. Ya bir Minecraft kullanici adi (Citizens skini Mojang'dan ceker) ya da mineskin.org
 * gibi bir yerden alinmis imzali doku. Ikisi de yoksa varsayilan Steve/Alex gorunur.
 */
public record Skin(String playerName, String texture, String signature) {

    public static final Skin NONE = new Skin(null, null, null);

    public boolean isSigned() {
        return texture != null && signature != null;
    }

    public boolean isPlayerName() {
        return playerName != null;
    }

    /** {@code skin: {oyuncu: ...}} ya da {@code skin: {doku: ..., imza: ...}}; bolum yoksa {@link #NONE}. */
    static Skin parse(ConfigurationSection section, String path) {
        if (section == null) {
            return NONE;
        }
        String texture = blankToNull(section.getString("doku"));
        String signature = blankToNull(section.getString("imza"));
        if ((texture == null) != (signature == null)) {
            throw new IllegalArgumentException(path + ": doku ve imza birlikte verilmeli");
        }
        return new Skin(blankToNull(section.getString("oyuncu")), texture, signature);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
