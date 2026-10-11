package dev.auxy.warbcraft.plugin.command;

import dev.auxy.warbcraft.plugin.Game;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

/** /menu — Sefer Defteri (ana menu). */
public final class MenuCommand extends BaseCommand {

    private final Consumer<Player> openMain;

    public MenuCommand(Game game, Consumer<Player> openMain) {
        super(game);
        this.openMain = openMain;
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        openMain.accept(player(sender));
    }
}
