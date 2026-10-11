package dev.auxy.warbcraft.core.troop;

import java.util.List;
import java.util.Objects;

/**
 * Bir birlik turu (orn. "vlandia_recruit"). Degerler veri dosyasindan gelir.
 *
 * @param tier       1 (acemi) ile 6 (seckin) arasi kademe
 * @param power      otomatik savas cozumunde kullanilan guc puani
 * @param wage       gunluk maas (dinar)
 * @param upgradeXp  bir sonraki kademeye terfi icin gereken deneyim; terfi yoksa 0
 * @param upgradesTo terfi edilebilecek birlik kimlikleri (bos ise son kademe)
 */
public record TroopType(
        String id,
        String name,
        String culture,
        int tier,
        TroopCategory category,
        int power,
        int wage,
        int upgradeXp,
        List<String> upgradesTo) {

    public static final int MIN_TIER = 1;
    public static final int MAX_TIER = 6;

    public TroopType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(culture, "culture");
        Objects.requireNonNull(category, "category");
        upgradesTo = List.copyOf(upgradesTo);
        if (id.isBlank()) {
            throw new IllegalArgumentException("Birlik kimligi bos olamaz");
        }
        if (tier < MIN_TIER || tier > MAX_TIER) {
            throw new IllegalArgumentException(id + ": kademe " + MIN_TIER + "-" + MAX_TIER + " arasinda olmali");
        }
        if (power <= 0) {
            throw new IllegalArgumentException(id + ": guc pozitif olmali");
        }
        if (wage < 0 || upgradeXp < 0) {
            throw new IllegalArgumentException(id + ": maas ve terfi deneyimi negatif olamaz");
        }
        if (upgradesTo.isEmpty() != (upgradeXp == 0)) {
            throw new IllegalArgumentException(id + ": terfi hedefi varsa terfi deneyimi de olmali (ve tersi)");
        }
    }

    public boolean canUpgrade() {
        return !upgradesTo.isEmpty();
    }
}
