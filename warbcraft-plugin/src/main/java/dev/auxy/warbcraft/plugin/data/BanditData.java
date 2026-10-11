package dev.auxy.warbcraft.plugin.data;

import dev.auxy.warbcraft.core.bandit.BanditKind;
import dev.auxy.warbcraft.core.bandit.BanditSpawner;
import org.bukkit.Color;

import java.util.Map;

/**
 * haydutlar.yml.
 *
 * @param kinds tur kimligi -> tur (dosyadaki sirayla)
 * @param looks tur kimligi -> gorunus
 */
public record BanditData(BanditSpawner.Settings settings, Map<String, BanditKind> kinds, Map<String, Look> looks) {

    /** Bir haydut turunun kiyafet rengi ve skini. */
    public record Look(Color color, Skin skin) {
    }
}
