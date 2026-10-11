package dev.auxy.warbcraft.plugin.command;

import dev.auxy.warbcraft.plugin.Game;
import dev.auxy.warbcraft.plugin.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** Alt komutlu oyuncu komutlari icin ortak yardimcilar. */
abstract class BaseCommand implements TabExecutor {

    protected final Game game;

    BaseCommand(Game game) {
        this.game = game;
    }

    @Override
    public final boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            execute(sender, label, args);
        } catch (CommandError e) {
            Msg.error(sender, e.getMessage());
        }
        return true;
    }

    protected abstract void execute(CommandSender sender, String label, String[] args);

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }

    protected static Player player(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        throw new CommandError("Bu komut yalnızca oyun içinde kullanılabilir.");
    }

    protected static int positiveInt(String value, String what) {
        try {
            int number = Integer.parseInt(value);
            if (number > 0) {
                return number;
            }
        } catch (NumberFormatException ignored) {
            // asagida hata verilir
        }
        throw new CommandError(what + " pozitif bir sayı olmalı: " + Msg.esc(value));
    }

    protected static String arg(String[] args, int index, String usage) {
        if (args.length <= index) {
            throw new CommandError("Kullanım: " + usage);
        }
        return args[index];
    }

    protected static String joinFrom(String[] args, int index, String usage) {
        if (args.length <= index) {
            throw new CommandError("Kullanım: " + usage);
        }
        return String.join(" ", List.of(args).subList(index, args.length));
    }

    protected static List<String> complete(Collection<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lower)).sorted().toList();
    }

    /** Kullaniciya gosterilecek hata; komut akisini keser. */
    static final class CommandError extends RuntimeException {

        private static final long serialVersionUID = 1L;

        CommandError(String message) {
            super(message, null, false, false);
        }
    }
}
