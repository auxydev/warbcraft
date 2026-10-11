package dev.auxy.warbcraft.plugin.gui;

import dev.auxy.warbcraft.plugin.Game;
import dev.auxy.warbcraft.plugin.Msg;
import dev.auxy.warbcraft.plugin.command.ClanCommand;
import dev.auxy.warbcraft.plugin.field.Retinue;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Menulerin ortak servisi: acma, komut calistirip yenileme, sohbetten metin isteme ve
 * "Sefer Defteri" esyasi (sag tikla ana menu).
 */
public final class Menus implements Listener {

    private static final int JOURNAL_SLOT = 8;

    private final Plugin plugin;
    private final Game game;
    private final Retinue retinue;
    private final ClanCommand clans;
    private final NamespacedKey journalKey;
    private final Map<UUID, Consumer<String>> prompts = new ConcurrentHashMap<>();

    public Menus(Plugin plugin, Game game, Retinue retinue, ClanCommand clans) {
        this.plugin = plugin;
        this.game = game;
        this.retinue = retinue;
        this.clans = clans;
        this.journalKey = new NamespacedKey(plugin, "sefer_defteri");
    }

    public Game game() {
        return game;
    }

    public Retinue retinue() {
        return retinue;
    }

    public ClanCommand clans() {
        return clans;
    }

    public void openMain(Player player) {
        open(new MainMenu(this, player));
    }

    public void openParty(Player player) {
        open(new PartyMenu(this, player));
    }

    /** Bir sonraki tikte acar (tiklama olayinin icinden envanter degistirmek guvenli degildir). */
    public void open(Menu menu) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            menu.redraw();
            menu.viewer.openInventory(menu.getInventory());
        });
    }

    /** Komutu oyuncu adina calistirir (mesajlar sohbete duser) ve acik menuyu yeniler. */
    void run(Player player, Menu current, String command) {
        player.performCommand(command);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.getOpenInventory().getTopInventory().getHolder() == current) {
                current.redraw();
            }
        });
    }

    /** Menuyu kapatir ve oyuncudan sohbete bir metin yazmasini ister. */
    void prompt(Player player, String question, Consumer<String> answer) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.closeInventory();
            prompts.put(player.getUniqueId(), answer);
            Msg.info(player, question + " <dark_gray>(vazgeçmek için <white>iptal</white> yaz)</dark_gray>");
        });
    }

    // ---------------------------------------------------------------- olaylar

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Menu menu)) {
            return;
        }
        event.setCancelled(true);
        if (top.equals(event.getClickedInventory()) && event.getWhoClicked() instanceof Player) {
            menu.click(event.getSlot(), event.getClick());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Consumer<String> answer = prompts.remove(event.getPlayer().getUniqueId());
        if (answer == null) {
            return;
        }
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (text.isEmpty() || text.equalsIgnoreCase("iptal")) {
                Msg.info(player, "Vazgeçildi.");
            } else {
                answer.accept(text);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        prompts.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        giveJournal(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        giveJournal(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::isJournal);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (isJournal(event.getItem())) {
            event.setCancelled(true);
            openMain(event.getPlayer());
        }
    }

    // ---------------------------------------------------------------- sefer defteri

    public void giveJournal(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isJournal(item)) {
                return;
            }
        }
        ItemStack journal = Icons.icon(Material.BOOK, "<gold>Sefer Defteri",
                "Ordun, pazar, görevler, klan ve krallıklar.", "", "<yellow>Sağ tık: menüyü aç");
        journal.editMeta(meta -> meta.getPersistentDataContainer().set(journalKey, PersistentDataType.BYTE, (byte) 1));
        if (player.getInventory().getItem(JOURNAL_SLOT) == null) {
            player.getInventory().setItem(JOURNAL_SLOT, journal);
        } else {
            player.getInventory().addItem(journal);
        }
    }

    private boolean isJournal(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(journalKey, PersistentDataType.BYTE);
    }
}
