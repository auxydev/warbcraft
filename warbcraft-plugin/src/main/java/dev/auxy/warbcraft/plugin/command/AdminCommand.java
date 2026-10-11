package dev.auxy.warbcraft.plugin.command;

import dev.auxy.warbcraft.core.faction.Diplomacy;
import dev.auxy.warbcraft.core.faction.Kingdom;
import dev.auxy.warbcraft.core.faction.Settlement;
import dev.auxy.warbcraft.plugin.Game;
import dev.auxy.warbcraft.plugin.Msg;
import dev.auxy.warbcraft.plugin.WarbcraftPlugin;
import dev.auxy.warbcraft.plugin.listener.Bandits;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** /wbadmin — sunucu yonetimi. */
public final class AdminCommand extends BaseCommand {

    private static final List<String> SUBCOMMANDS =
            List.of("gun", "kaydet", "dinar", "un", "konum", "haydut", "savas", "baris");

    private final WarbcraftPlugin plugin;
    private final Bandits bandits;

    public AdminCommand(Game game, WarbcraftPlugin plugin, Bandits bandits) {
        super(game);
        this.plugin = plugin;
        this.bandits = bandits;
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        String sub = arg(args, 0, "/" + label + " <" + String.join("|", SUBCOMMANDS) + ">").toLowerCase(Locale.ROOT);
        switch (sub) {
            case "gun" -> {
                game.dailyTick();
                Msg.ok(sender, "Bir sefer günü ilerletildi.");
            }
            case "kaydet" -> {
                plugin.saveAll();
                Msg.ok(sender, "Kaydedildi.");
            }
            case "dinar" -> {
                String usage = "/" + label + " dinar <oyuncu> <miktar>";
                Player target = online(arg(args, 1, usage));
                int amount = positiveInt(arg(args, 2, usage), "Miktar");
                game.purse(target).deposit(amount);
                Msg.ok(sender, Msg.esc(target.getName()) + " +" + amount + " dinar.");
            }
            case "un" -> {
                String usage = "/" + label + " un <oyuncu> <miktar>";
                Player target = online(arg(args, 1, usage));
                if (game.clanOf(target.getUniqueId()).isEmpty()) {
                    throw new CommandError("Oyuncunun klanı yok.");
                }
                game.addRenown(target.getUniqueId(), positiveInt(arg(args, 2, usage), "Miktar"));
                Msg.ok(sender, "Ün eklendi.");
            }
            case "konum" -> {
                Player player = player(sender);
                String usage = "/" + label + " konum <yerlesim> [yaricap]";
                Settlement settlement = game.realm().findSettlement(arg(args, 1, usage))
                        .orElseThrow(() -> new CommandError("Böyle bir yerleşim yok."));
                int radius = args.length > 2 ? positiveInt(args[2], "Yarıçap") : 50;
                plugin.setSite(settlement.id(), player.getLocation(), radius);
                Msg.ok(sender, Msg.esc(settlement.name()) + " merkezi buraya ayarlandı (yarıçap " + radius + ").");
            }
            case "haydut" -> {
                Player player = player(sender);
                int amount = args.length > 1 ? positiveInt(args[1], "Adet") : 5;
                Msg.ok(sender, bandits.spawnGroup(player.getLocation(), Math.min(amount, 30)) + " haydut doğdu.");
            }
            case "savas", "baris" -> {
                String usage = "/" + label + " " + sub + " <krallik> <krallik>";
                Kingdom a = kingdom(arg(args, 1, usage));
                Kingdom b = kingdom(arg(args, 2, usage));
                Diplomacy.Outcome outcome = sub.equals("savas")
                        ? game.diplomacy().declareWar(a.id(), b.id(), game.now())
                        : game.diplomacy().makePeace(a.id(), b.id());
                if (outcome != Diplomacy.Outcome.OK) {
                    throw new CommandError(outcome.message());
                }
                game.refreshBoards();
                Msg.ok(sender, outcome.message() + ".");
            }
            default -> throw new CommandError("Alt komutlar: " + String.join(", ", SUBCOMMANDS));
        }
    }

    private Player online(String name) {
        Player player = Bukkit.getPlayerExact(name);
        if (player == null) {
            throw new CommandError("Oyuncu çevrimiçi değil: " + Msg.esc(name));
        }
        return player;
    }

    private Kingdom kingdom(String id) {
        return game.realm().findKingdom(id).orElseThrow(() -> new CommandError("Böyle bir krallık yok: " + Msg.esc(id)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return complete(SUBCOMMANDS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && (sub.equals("dinar") || sub.equals("un"))) {
            return complete(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 2 && sub.equals("konum")) {
            return complete(game.realm().settlements().stream().map(Settlement::id).toList(), args[1]);
        }
        if ((args.length == 2 || args.length == 3) && (sub.equals("savas") || sub.equals("baris"))) {
            return complete(game.realm().kingdoms().stream().map(Kingdom::id).toList(), args[args.length - 1]);
        }
        return List.of();
    }
}
