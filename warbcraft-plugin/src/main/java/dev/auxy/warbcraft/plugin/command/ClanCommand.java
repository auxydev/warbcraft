package dev.auxy.warbcraft.plugin.command;

import dev.auxy.warbcraft.core.faction.Clan;
import dev.auxy.warbcraft.core.faction.Settlement;
import dev.auxy.warbcraft.plugin.Game;
import dev.auxy.warbcraft.plugin.Msg;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** /klan [kur <ad> | davet <oyuncu> | katil <klan> | ayril] */
public final class ClanCommand extends BaseCommand {

    /** oyuncu -> davet edildigi klanlar (sunucu yeniden baslayinca silinir). */
    private final Map<UUID, Set<String>> invites = new HashMap<>();

    public ClanCommand(Game game) {
        super(game);
    }

    /** Oyuncunun bekleyen klan davetleri (menude gosterilir). */
    public Set<String> invitesFor(UUID playerId) {
        return Set.copyOf(invites.getOrDefault(playerId, Set.of()));
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = player(sender);
        String me = player.getUniqueId().toString();
        if (args.length == 0 || args[0].equalsIgnoreCase("bilgi")) {
            Clan clan = game.clanOf(player.getUniqueId())
                    .orElseThrow(() -> new CommandError("Bir klanın yok. <white>/" + label + " kur <ad></white> ile kurabilirsin ("
                            + game.data().settings().clanCost() + " dinar)."));
            show(player, clan);
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "kur" -> {
                String name = joinFrom(args, 1, "/" + label + " kur <ad>");
                String id = "k_" + Game.slug(name);
                if (game.clanOf(player.getUniqueId()).isPresent()) {
                    throw new CommandError("Zaten bir klandasın.");
                }
                if (name.length() > 32 || id.length() <= 2) {
                    throw new CommandError("Klan adı 1-32 karakter olmalı ve harf/rakam içermeli.");
                }
                if (game.realm().findClan(id).isPresent()) {
                    throw new CommandError("Bu adda bir klan zaten var.");
                }
                if (!game.purse(player).tryWithdraw(game.data().settings().clanCost())) {
                    throw new CommandError("Klan kurmak " + game.data().settings().clanCost() + " dinar tutar.");
                }
                game.realm().addClan(new Clan(id, name, me, false));
                game.profile(player);
                Msg.ok(player, "<gold>" + Msg.esc(name) + "</gold> klanı kuruldu! Ün kazandıkça kademen ve parti sınırın artar.");
            }
            case "davet" -> {
                Clan clan = ledClan(player);
                Player target = Bukkit.getPlayerExact(arg(args, 1, "/" + label + " davet <oyuncu>"));
                if (target == null) {
                    throw new CommandError("Oyuncu çevrimiçi değil.");
                }
                invites.computeIfAbsent(target.getUniqueId(), k -> new HashSet<>()).add(clan.id());
                Msg.ok(player, Msg.esc(target.getName()) + " davet edildi.");
                Msg.info(target, "<gold>" + Msg.esc(clan.name()) + "</gold> klanına davet edildin: <white>/klan katil "
                        + clan.id() + "</white>");
            }
            case "katil" -> {
                String clanId = arg(args, 1, "/" + label + " katil <klan>");
                if (game.clanOf(player.getUniqueId()).isPresent()) {
                    throw new CommandError("Önce mevcut klanından ayrılmalısın.");
                }
                if (!invites.getOrDefault(player.getUniqueId(), Set.of()).contains(clanId)) {
                    throw new CommandError("Bu klandan davetin yok.");
                }
                Clan clan = game.realm().findClan(clanId).orElseThrow(() -> new CommandError("Klan artık yok."));
                clan.addMember(me);
                invites.remove(player.getUniqueId());
                Msg.ok(player, "<gold>" + Msg.esc(clan.name()) + "</gold> klanına katıldın.");
            }
            case "ayril" -> {
                Clan clan = game.clanOf(player.getUniqueId()).orElseThrow(() -> new CommandError("Bir klanda değilsin."));
                if (clan.leaderId().equals(me)) {
                    throw new CommandError("Lider klandan ayrılamaz.");
                }
                clan.removeMember(me);
                Msg.ok(player, Msg.esc(clan.name()) + " klanından ayrıldın.");
            }
            default -> throw new CommandError("Kullanım: /" + label + " [bilgi|kur|davet|katil|ayril]");
        }
    }

    private void show(Player player, Clan clan) {
        int tier = clan.tier();
        String next = tier < Clan.MAX_TIER ? " (sonraki kademe: " + Clan.renownForTier(tier + 1) + ")" : "";
        String kingdom = clan.kingdomId() == null ? "bağımsız"
                : Msg.esc(game.realm().kingdom(clan.kingdomId()).name());
        Msg.info(player, "<gold>" + Msg.esc(clan.name()) + "</gold> · kademe " + tier + " · ün " + clan.renown() + next
                + " · " + kingdom);
        StringBuilder members = new StringBuilder();
        for (String member : clan.members()) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(UUID.fromString(member));
            members.append(members.isEmpty() ? "" : ", ").append(Msg.esc(String.valueOf(offline.getName())));
        }
        Msg.line(player, "  Üyeler: " + members);
        StringBuilder fiefs = new StringBuilder();
        for (Settlement settlement : game.realm().settlements()) {
            if (settlement.ownerClanId().equals(clan.id())) {
                fiefs.append(fiefs.isEmpty() ? "" : ", ").append(Msg.esc(settlement.name()));
            }
        }
        Msg.line(player, "  Topraklar: " + (fiefs.isEmpty() ? "yok" : fiefs));
    }

    private Clan ledClan(Player player) {
        Clan clan = game.clanOf(player.getUniqueId()).orElseThrow(() -> new CommandError("Bir klanda değilsin."));
        if (!clan.leaderId().equals(player.getUniqueId().toString())) {
            throw new CommandError("Bunu yalnızca klan lideri yapabilir.");
        }
        return clan;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return complete(List.of("bilgi", "kur", "davet", "katil", "ayril"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("davet")) {
            return complete(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("katil") && sender instanceof Player player) {
            return complete(invites.getOrDefault(player.getUniqueId(), Set.of()), args[1]);
        }
        return List.of();
    }
}
