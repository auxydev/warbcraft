package dev.auxy.warbcraft.plugin.command;

import dev.auxy.warbcraft.core.economy.Good;
import dev.auxy.warbcraft.core.economy.Market;
import dev.auxy.warbcraft.core.economy.Purse;
import dev.auxy.warbcraft.core.faction.Settlement;
import dev.auxy.warbcraft.plugin.Game;
import dev.auxy.warbcraft.plugin.Msg;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** /pazar [al|sat <mal> <adet>] — bulunulan sehrin pazari. */
public final class MarketCommand extends BaseCommand {

    public MarketCommand(Game game) {
        super(game);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        Settlement town = game.settlementAt(player.getLocation())
                .filter(s -> game.markets().containsKey(s.id()))
                .orElseThrow(() -> new CommandError("Pazar yalnızca şehirlerde kurulur."));
        Market market = game.markets().get(town.id());
        if (args.length == 0) {
            Msg.info(player, "<gold>" + Msg.esc(town.name()) + " pazarı</gold> <dark_gray>(alış / satış · stok)</dark_gray>");
            for (Good good : market.goods().values()) {
                Msg.line(player, "  " + Msg.esc(good.name()) + " <dark_gray>[" + good.id() + "]</dark_gray> · <yellow>"
                        + market.buyPrice(good.id()) + "</yellow> / <green>" + market.sellPrice(good.id())
                        + "</green> · " + market.stock(good.id()));
            }
            Msg.line(player, "  <white>/" + label + " al <mal> <adet></white>, <white>/" + label + " sat <mal> <adet></white>");
            return;
        }
        String usage = "/" + label + " " + args[0] + " <mal> <adet>";
        String goodId = arg(args, 1, usage);
        Good good = market.goods().get(goodId);
        if (good == null) {
            throw new CommandError("Bu pazarda öyle bir mal yok: " + Msg.esc(goodId));
        }
        int amount = positiveInt(arg(args, 2, usage), "Adet");
        Material item = game.data().goodItems().get(good.id());
        Purse purse = game.purse(player);
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "al" -> {
                long cost = market.buy(good.id(), amount, purse);
                if (cost < 0) {
                    throw new CommandError("Yeterli stok ya da dinar yok.");
                }
                Items.give(player, item, amount);
                Msg.ok(player, amount + " " + Msg.esc(good.name()) + " aldın (-" + cost + " dinar).");
            }
            case "sat" -> {
                int have = Items.count(player, item);
                if (have < amount) {
                    throw new CommandError("Envanterinde yalnızca " + have + " " + Msg.esc(good.name()) + " var.");
                }
                Items.take(player, item, amount);
                long income = market.sell(good.id(), amount, purse);
                Msg.ok(player, amount + " " + Msg.esc(good.name()) + " sattın (+" + income + " dinar).");
            }
            default -> throw new CommandError("Kullanım: /" + label + " [al|sat] <mal> <adet>");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return complete(List.of("al", "sat"), args[0]);
        }
        if (args.length == 2) {
            return complete(game.data().goods().keySet(), args[1]);
        }
        return List.of();
    }
}
