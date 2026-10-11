package dev.auxy.warbcraft.plugin.command;

import dev.auxy.warbcraft.core.season.Season;
import dev.auxy.warbcraft.core.season.SeasonPhase;
import dev.auxy.warbcraft.core.season.SeasonScoreboard;
import dev.auxy.warbcraft.plugin.Game;
import dev.auxy.warbcraft.plugin.Msg;
import org.bukkit.command.CommandSender;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** /sezon — sezon evresi, savas penceresi ve siralama. */
public final class SeasonCommand extends BaseCommand {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("d MMMM EEEE HH:mm", Locale.forLanguageTag("tr"));

    public SeasonCommand(Game game) {
        super(game);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Season season = game.season();
        Instant now = game.now();
        SeasonPhase phase = season.phaseAt(now);
        Msg.info(sender, "<gold>Sezon " + season.number() + "</gold> · evre: <white>" + phase.displayName() + "</white>"
                + " · bitiş: " + FORMAT.format(season.end().atZone(season.zone())));
        if (phase == SeasonPhase.NOT_STARTED) {
            Msg.line(sender, "  Başlangıç: " + FORMAT.format(season.start().atZone(season.zone())));
        }
        if (season.isWarWindowOpen(now)) {
            Msg.line(sender, "  <red>Savaş penceresi AÇIK</red> · kuşatmalar yapılabilir.");
        } else {
            season.nextWarWindow(now).ifPresent(next ->
                    Msg.line(sender, "  Sonraki savaş penceresi: <white>" + FORMAT.format(next.atZone(season.zone())) + "</white>"));
        }
        List<SeasonScoreboard.Entry> ranking = SeasonScoreboard.rank(game.realm());
        if (!ranking.isEmpty()) {
            Msg.line(sender, "  <gold>Sıralama</gold>");
            for (int i = 0; i < Math.min(5, ranking.size()); i++) {
                SeasonScoreboard.Entry entry = ranking.get(i);
                Msg.line(sender, "  " + (i + 1) + ". " + Msg.esc(entry.kingdom().name()) + " · " + entry.points() + " puan");
            }
        }
    }
}
