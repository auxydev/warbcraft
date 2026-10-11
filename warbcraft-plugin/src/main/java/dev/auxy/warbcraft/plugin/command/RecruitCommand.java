package dev.auxy.warbcraft.plugin.command;

import dev.auxy.warbcraft.core.faction.Clan;
import dev.auxy.warbcraft.core.faction.Kingdom;
import dev.auxy.warbcraft.core.faction.Settlement;
import dev.auxy.warbcraft.core.faction.SettlementType;
import dev.auxy.warbcraft.core.troop.TroopType;
import dev.auxy.warbcraft.plugin.Game;
import dev.auxy.warbcraft.plugin.Msg;
import dev.auxy.warbcraft.plugin.data.PlayerProfile;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/** /topla [adet] — bulunulan koy veya sehirden acemi asker toplar. */
public final class RecruitCommand extends BaseCommand {

    public RecruitCommand(Game game) {
        super(game);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        Settlement here = game.settlementAt(player.getLocation())
                .orElseThrow(() -> new CommandError("Asker toplamak için bir köy ya da şehirde olmalısın."));
        if (here.type() == SettlementType.CASTLE) {
            throw new CommandError("Kalelerde asker toplanmaz; bir köy ya da şehre git.");
        }
        Optional<Kingdom> owner = game.realm().kingdomOf(here);
        Optional<String> myKingdom = game.clanOf(player.getUniqueId()).map(Clan::kingdomId);
        if (owner.isPresent() && myKingdom.isPresent() && game.realm().atWar(owner.get().id(), myKingdom.get())) {
            throw new CommandError("Düşman topraklarında kimse sana katılmaz.");
        }
        List<TroopType> recruits = game.tree().recruits(here.culture());
        if (recruits.isEmpty()) {
            throw new CommandError("Burada toplanabilecek asker yok.");
        }
        PlayerProfile profile = game.profile(player);
        int pool = game.recruitPools().getOrDefault(here.id(), 0);
        int wanted = args.length > 0 ? positiveInt(args[0], "Adet") : pool;
        int cost = game.data().settings().recruitCost();
        int affordable = (int) Math.min(Integer.MAX_VALUE, profile.purse().balance() / Math.max(1, cost));
        int amount = Math.min(Math.min(wanted, pool), Math.min(profile.party().freeSlots(), affordable));
        if (pool == 0) {
            throw new CommandError(Msg.esc(here.name()) + " bugün başka gönüllü çıkaramaz; yarın tekrar gel.");
        }
        if (profile.party().freeSlots() == 0) {
            throw new CommandError("Partin dolu (" + profile.party().sizeLimit() + "). Klan kademen arttıkça sınır büyür.");
        }
        if (amount == 0) {
            throw new CommandError("Yeterli dinarın yok (asker başına " + cost + ").");
        }
        profile.purse().tryWithdraw((long) amount * cost);
        for (int i = 0; i < amount; i++) {
            TroopType troop = recruits.get(ThreadLocalRandom.current().nextInt(recruits.size()));
            profile.party().addTroops(troop.id(), 1);
        }
        game.recruitPools().put(here.id(), pool - amount);
        Msg.ok(player, Msg.esc(here.name()) + " köylülerinden <white>" + amount + "</white> gönüllü partine katıldı (-"
                + (long) amount * cost + " dinar).");
    }
}
