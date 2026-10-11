package dev.auxy.warbcraft.plugin.command;

import dev.auxy.warbcraft.core.faction.Diplomacy;
import dev.auxy.warbcraft.core.faction.Kingdom;
import dev.auxy.warbcraft.core.faction.Settlement;
import dev.auxy.warbcraft.plugin.Game;
import dev.auxy.warbcraft.plugin.Msg;
import dev.auxy.warbcraft.plugin.WarbcraftPlugin;
import dev.auxy.warbcraft.plugin.field.Retinue;
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
            List.of("gun", "kaydet", "dinar", "asker", "durum", "un", "konum", "haydut", "savas", "baris");

    private final WarbcraftPlugin plugin;
    private final Bandits bandits;
    private final Retinue retinue;

    public AdminCommand(Game game, WarbcraftPlugin plugin, Bandits bandits, Retinue retinue) {
        super(game);
        this.plugin = plugin;
        this.bandits = bandits;
        this.retinue = retinue;
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
            case "asker" -> {
                String usage = "/" + label + " asker <oyuncu> <birlik> [adet]";
                Player target = online(arg(args, 1, usage));
                String troopId = arg(args, 2, usage);
                if (game.tree().find(troopId).isEmpty()) {
                    throw new CommandError("Bilinmeyen birlik: " + Msg.esc(troopId));
                }
                int amount = args.length > 3 ? positiveInt(args[3], "Adet") : 1;
                int added = game.profile(target).party().addTroops(troopId, amount);
                Msg.ok(sender, Msg.esc(target.getName()) + " partisine " + added + " " + Msg.esc(game.tree().get(troopId).name())
                        + " eklendi" + (added < amount ? " (parti sınırı doldu)." : "."));
            }
            case "durum" -> {
                Player target = args.length > 1 ? online(args[1]) : player(sender);
                Msg.info(sender, "<gold>" + Msg.esc(target.getName()) + "</gold> · sahada " + retinue.fieldCount(target)
                        + " asker · emir " + retinue.order(target).displayName()
                        + " · parti " + game.profile(target).party().totalCount()
                        + " · ortalama uzaklık " + String.format(Locale.ROOT, "%.1f", retinue.averageDistance(target)));
                List<String> gangs = bandits.describeNear(target.getLocation(), 128);
                if (gangs.isEmpty()) {
                    Msg.line(sender, "  Yakında haydut çetesi yok.");
                }
                gangs.forEach(line -> Msg.line(sender, "  " + line));
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
                String kind = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : null;
                if (kind != null && !bandits.kindIds().contains(kind)) {
                    throw new CommandError("Haydut türleri: " + String.join(", ", bandits.kindIds()));
                }
                int spawned = bandits.spawnNear(player.getLocation(), kind);
                Msg.ok(sender, spawned + " kişilik bir haydut çetesi yakında belirdi.");
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
        if (args.length == 3 && sub.equals("asker")) {
            return complete(game.tree().all().stream().map(t -> t.id()).toList(), args[2]);
        }
        if (args.length == 2 && (sub.equals("dinar") || sub.equals("un") || sub.equals("asker") || sub.equals("durum"))) {
            return complete(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 2 && sub.equals("haydut")) {
            return complete(bandits.kindIds(), args[1]);
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
