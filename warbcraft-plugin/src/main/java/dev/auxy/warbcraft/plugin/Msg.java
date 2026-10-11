package dev.auxy.warbcraft.plugin;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

/** MiniMessage bicimli sohbet mesajlari. Oyuncu girdisi her zaman {@link #esc} ile kacirilmalidir. */
public final class Msg {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String PREFIX = "<dark_gray>[<gold>Warbcraft</gold>]</dark_gray> ";

    private Msg() {
    }

    public static void info(CommandSender to, String message) {
        to.sendMessage(MM.deserialize(PREFIX + "<gray>" + message));
    }

    public static void ok(CommandSender to, String message) {
        to.sendMessage(MM.deserialize(PREFIX + "<green>" + message));
    }

    public static void error(CommandSender to, String message) {
        to.sendMessage(MM.deserialize(PREFIX + "<red>" + message));
    }

    /** Onek olmadan, liste satirlari icin. */
    public static void line(CommandSender to, String message) {
        to.sendMessage(MM.deserialize("<gray>" + message));
    }

    public static String esc(String text) {
        return MM.escapeTags(text);
    }
}
