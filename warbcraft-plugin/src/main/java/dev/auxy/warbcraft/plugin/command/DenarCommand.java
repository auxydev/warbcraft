package dev.auxy.warbcraft.plugin.command;

import dev.auxy.warbcraft.plugin.Game;
import dev.auxy.warbcraft.plugin.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** /dinar [gonder <oyuncu> <miktar>] */
public final class DenarCommand extends BaseCommand {

    public DenarCommand(Game game) {
        super(game);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        if (args.length == 0) {
            Msg.info(player, "Keseinde <yellow>" + game.purse(player).balance() + " dinar</yellow> var.");
            return;
        }
        if (!args[0].equalsIgnoreCase("gonder")) {
            throw new CommandError("Kullanım: /" + label + " [gonder <oyuncu> <miktar>]");
        }
        String usage = "/" + label + " gonder <oyuncu> <miktar>";
        Player target = Bukkit.getPlayerExact(arg(args, 1, usage));
        if (target == null || target.equals(player)) {
            throw new CommandError("Geçerli, çevrimiçi bir oyuncu belirt.");
        }
        int amount = positiveInt(arg(args, 2, usage), "Miktar");
        if (!game.purse(player).tryWithdraw(amount)) {
            throw new CommandError("Yeterli dinarın yok.");
        }
        game.purse(target).deposit(amount);
        Msg.ok(player, Msg.esc(target.getName()) + " adlı oyuncuya " + amount + " dinar gönderdin.");
        Msg.info(target, Msg.esc(player.getName()) + " sana <yellow>" + amount + " dinar</yellow> gönderdi.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return complete(List.of("gonder"), args[0]);
        }
        if (args.length == 2) {
            return complete(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        return List.of();
    }
}
