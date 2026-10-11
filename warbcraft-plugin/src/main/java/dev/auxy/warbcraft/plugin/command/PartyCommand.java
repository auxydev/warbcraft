package dev.auxy.warbcraft.plugin.command;

import dev.auxy.warbcraft.core.party.Party;
import dev.auxy.warbcraft.core.party.TroopStack;
import dev.auxy.warbcraft.core.troop.TroopType;
import dev.auxy.warbcraft.plugin.Game;
import dev.auxy.warbcraft.plugin.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** /parti [terfi <birlik> <hedef> [adet] | terhis <birlik> <adet>] */
public final class PartyCommand extends BaseCommand {

    public PartyCommand(Game game) {
        super(game);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        Party party = game.profile(player).party();
        if (args.length == 0) {
            show(player, party);
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "terfi" -> {
                String usage = "/" + label + " terfi <birlik> <hedef> [adet]";
                TroopType from = troop(arg(args, 1, usage));
                TroopType to = troop(arg(args, 2, usage));
                int amount = args.length > 3 ? positiveInt(args[3], "Adet") : Integer.MAX_VALUE;
                if (!from.upgradesTo().contains(to.id())) {
                    throw new CommandError(Msg.esc(from.name()) + " → " + Msg.esc(to.name()) + " terfi yolu yok.");
                }
                int upgraded = party.upgrade(game.tree(), from.id(), to.id(), amount);
                if (upgraded == 0) {
                    throw new CommandError("Terfi için yeterli deneyim yok (asker başına " + from.upgradeXp() + " XP).");
                }
                Msg.ok(player, upgraded + " " + Msg.esc(from.name()) + " → <gold>" + Msg.esc(to.name()) + "</gold> terfi etti.");
            }
            case "terhis" -> {
                String usage = "/" + label + " terhis <birlik> <adet>";
                TroopType troop = troop(arg(args, 1, usage));
                int removed = party.removeTroops(troop.id(), positiveInt(arg(args, 2, usage), "Adet"));
                if (removed == 0) {
                    throw new CommandError("Partinde " + Msg.esc(troop.name()) + " yok.");
                }
                Msg.ok(player, removed + " " + Msg.esc(troop.name()) + " terhis edildi.");
            }
            default -> throw new CommandError("Kullanım: /" + label + " [terfi|terhis]");
        }
    }

    private void show(Player player, Party party) {
        Msg.info(player, "<gold>Partin</gold> · " + party.totalCount() + "/" + party.sizeLimit()
                + " asker · moral " + party.morale() + " · günlük maaş <yellow>" + party.dailyWage(game.tree()) + "</yellow>");
        if (party.isEmpty()) {
            Msg.line(player, "  Partinde asker yok. Bir köy ya da şehirde <white>/topla</white> ile asker toplayabilirsin.");
            return;
        }
        for (Map.Entry<String, TroopStack> entry : party.roster().entrySet()) {
            TroopType type = game.tree().get(entry.getKey());
            TroopStack stack = entry.getValue();
            String upgrade = type.canUpgrade()
                    ? " · XP " + stack.xp() + " (terfi: " + party.upgradeableCount(game.tree(), type.id()) + ")"
                    : "";
            String wounded = stack.wounded() > 0 ? " <red>(" + stack.wounded() + " yaralı)</red>" : "";
            Msg.line(player, "  <white>" + stack.total() + "×</white> " + Msg.esc(type.name())
                    + " <dark_gray>[K" + type.tier() + " " + type.id() + "]</dark_gray>" + wounded + upgrade);
        }
    }

    private TroopType troop(String id) {
        return game.tree().find(id).orElseThrow(() -> new CommandError("Bilinmeyen birlik: " + Msg.esc(id)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        Party party = game.profile(player).party();
        if (args.length == 1) {
            return complete(List.of("terfi", "terhis"), args[0]);
        }
        if (args.length == 2) {
            return complete(party.roster().keySet(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("terfi")) {
            return game.tree().find(args[1]).map(t -> complete(t.upgradesTo(), args[2])).orElse(List.of());
        }
        return List.of();
    }
}
