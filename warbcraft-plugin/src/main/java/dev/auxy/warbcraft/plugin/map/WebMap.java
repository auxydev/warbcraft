package dev.auxy.warbcraft.plugin.map;

import dev.auxy.warbcraft.core.faction.Kingdom;
import dev.auxy.warbcraft.core.faction.Settlement;
import dev.auxy.warbcraft.core.party.Party;
import dev.auxy.warbcraft.plugin.Game;
import dev.auxy.warbcraft.plugin.data.SettlementSite;
import dev.auxy.warbcraft.plugin.field.SoldierKit;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import xyz.jpenilla.squaremap.api.BukkitAdapter;
import xyz.jpenilla.squaremap.api.Key;
import xyz.jpenilla.squaremap.api.MapWorld;
import xyz.jpenilla.squaremap.api.Point;
import xyz.jpenilla.squaremap.api.SimpleLayerProvider;
import xyz.jpenilla.squaremap.api.SquaremapProvider;
import xyz.jpenilla.squaremap.api.marker.Marker;
import xyz.jpenilla.squaremap.api.marker.MarkerOptions;

import java.awt.Color;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * squaremap web haritasina Warbcraft katmanlari: yerlesimler (kralligin renginde daire) ve
 * NPC'ler (krallar, lord ordulari; {@link #setPin}). Yalnizca squaremap yukluyse olusturulur.
 */
public final class WebMap {

    /** Haritada konumu degisen isaretler icin katman kimlikleri. */
    public enum Layer {
        SETTLEMENTS("warbcraft_yerlesimler", "Yerleşimler", 10, 200),
        KINGS("warbcraft_krallar", "Krallar", 11, 300),
        LORDS("warbcraft_lordlar", "Lord orduları", 12, 310);

        private final String key;
        private final String label;
        private final int priority;
        private final int zIndex;

        Layer(String key, String label, int priority, int zIndex) {
            this.key = key;
            this.label = label;
            this.priority = priority;
            this.zIndex = zIndex;
        }
    }

    private static final long REFRESH_TICKS = 20L * 15;

    private final Plugin plugin;
    private final Game game;
    /** dunya adi -> katman -> saglayici */
    private final Map<String, Map<Layer, SimpleLayerProvider>> providers = new HashMap<>();

    public WebMap(Plugin plugin, Game game) {
        this.plugin = plugin;
        this.game = game;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshSettlements, 40L, REFRESH_TICKS);
    }

    public void shutdown() {
        for (Map.Entry<String, Map<Layer, SimpleLayerProvider>> entry : providers.entrySet()) {
            World world = Bukkit.getWorld(entry.getKey());
            if (world == null) {
                continue;
            }
            mapWorld(world).ifPresent(mapWorld -> entry.getValue().keySet()
                    .forEach(layer -> mapWorld.layerRegistry().unregister(Key.of(layer.key))));
        }
        providers.clear();
    }

    /** Hareketli bir NPC'nin (kral, lord) isaretini koyar ya da tasir. */
    public void setPin(Layer layer, String id, Location at, double radius, Color color, String tooltipHtml) {
        provider(at.getWorld(), layer).ifPresent(provider -> provider.addMarker(Key.of(layer.key + "_" + keyPart(id)),
                Marker.circle(BukkitAdapter.point(at), radius).markerOptions(MarkerOptions.builder()
                        .strokeColor(color).strokeWeight(2).fillColor(color).fillOpacity(0.8)
                        .hoverTooltip(tooltipHtml).clickTooltip(tooltipHtml))));
    }

    public void removePin(Layer layer, String id) {
        Key key = Key.of(layer.key + "_" + keyPart(id));
        for (Map<Layer, SimpleLayerProvider> byLayer : providers.values()) {
            SimpleLayerProvider provider = byLayer.get(layer);
            if (provider != null) {
                provider.removeMarker(key);
            }
        }
    }

    /** Yerlesimler sahip degistirebilir; katman belli araliklarla bastan cizilir. */
    public void refreshSettlements() {
        for (World world : Bukkit.getWorlds()) {
            provider(world, Layer.SETTLEMENTS).ifPresent(SimpleLayerProvider::clearMarkers);
        }
        for (SettlementSite site : game.data().sites().values()) {
            World world = Bukkit.getWorld(site.world());
            Optional<Settlement> found = game.realm().findSettlement(site.settlementId());
            if (world == null || found.isEmpty()) {
                continue;
            }
            Settlement settlement = found.get();
            Optional<Kingdom> kingdom = game.realm().kingdomOf(settlement);
            Color color = kingdom.map(WebMap::awt).orElse(Color.GRAY);
            String html = settlementHtml(settlement, kingdom);
            provider(world, Layer.SETTLEMENTS).ifPresent(provider -> provider.addMarker(
                    Key.of("y_" + keyPart(settlement.id())),
                    Marker.circle(Point.of(site.x(), site.z()), site.radius()).markerOptions(MarkerOptions.builder()
                            .strokeColor(color).strokeWeight(settlement.type().isFortified() ? 3 : 1)
                            .fillColor(color).fillOpacity(0.2)
                            .hoverTooltip(html).clickTooltip(html))));
        }
    }

    private String settlementHtml(Settlement settlement, Optional<Kingdom> kingdom) {
        String type = switch (settlement.type()) {
            case TOWN -> "şehir";
            case CASTLE -> "kale";
            case VILLAGE -> "köy";
        };
        StringBuilder html = new StringBuilder("<b>").append(esc(settlement.name())).append("</b> · ").append(type);
        html.append("<br>").append(kingdom.map(k -> esc(k.name())).orElse("bağımsız"));
        game.realm().findClan(settlement.ownerClanId()).ifPresent(clan -> html.append("<br>Sahibi: ").append(esc(clan.name())));
        html.append("<br>Refah: ").append(settlement.prosperity());
        Party garrison = game.garrisons().get(settlement.id());
        if (garrison != null) {
            html.append("<br>Garnizon: ").append(garrison.totalCount());
        }
        if (settlement.boundTo() != null) {
            game.realm().findSettlement(settlement.boundTo())
                    .ifPresent(town -> html.append("<br>Bağlı: ").append(esc(town.name())));
        }
        return html.toString();
    }

    /** Kralligin harita rengi: NPC krallikta kultur rengi, oyuncu kralliginda altin. */
    public static Color awt(Kingdom kingdom) {
        if (!kingdom.npc()) {
            return new Color(0xE0B000);
        }
        org.bukkit.Color color = SoldierKit.color(kingdom.culture());
        return new Color(color.getRed(), color.getGreen(), color.getBlue());
    }

    public static String esc(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private Optional<SimpleLayerProvider> provider(World world, Layer layer) {
        if (world == null) {
            return Optional.empty();
        }
        Map<Layer, SimpleLayerProvider> byLayer = providers.computeIfAbsent(world.getName(), name -> new HashMap<>());
        SimpleLayerProvider existing = byLayer.get(layer);
        if (existing != null) {
            return Optional.of(existing);
        }
        Optional<MapWorld> mapWorld = mapWorld(world);
        if (mapWorld.isEmpty()) {
            return Optional.empty();
        }
        SimpleLayerProvider provider = SimpleLayerProvider.builder(layer.label)
                .showControls(true).defaultHidden(false).layerPriority(layer.priority).zIndex(layer.zIndex).build();
        mapWorld.get().layerRegistry().register(Key.of(layer.key), provider);
        byLayer.put(layer, provider);
        return Optional.of(provider);
    }

    private static Optional<MapWorld> mapWorld(World world) {
        return SquaremapProvider.get().getWorldIfEnabled(BukkitAdapter.worldIdentifier(world));
    }

    /** Kucuk harflerle, harita anahtarina uygun kimlik. */
    public static String keyPart(String id) {
        return id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
    }

}
