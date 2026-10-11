package dev.auxy.warbcraft.core.field;

/**
 * Takip ve bekleme sirasinda askerlerin dizilisi. Asker {@code i}, {@link #slot(int)} ile liderin
 * arkasinda bir yere yerlesir; boylece kalabalik bir parti ust uste binmez.
 */
public enum FieldFormation {
    LINE("Sıra", 5, 1.6),
    COLUMN("Kol", 2, 1.4),
    LOOSE("Dağınık", 4, 3.0);

    /** Liderle ilk sira arasindaki mesafe (blok). */
    public static final double LEADER_GAP = 2.5;

    private final String displayName;
    private final int width;
    private final double spacing;

    FieldFormation(String displayName, int width, double spacing) {
        this.displayName = displayName;
        this.width = width;
        this.spacing = spacing;
    }

    public String displayName() {
        return displayName;
    }

    public int width() {
        return width;
    }

    public double spacing() {
        return spacing;
    }

    /** Sonraki formasyon (menude tiklayinca sirayla degisir). */
    public FieldFormation next() {
        FieldFormation[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    /**
     * Bir askerin lidere gore yeri.
     *
     * @param index 0'dan baslayan asker sirasi
     * @return {@code back}: liderin baktigi yonun tersine uzaklik, {@code side}: saga (+) / sola (-) kayma
     */
    public Slot slot(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("Sira negatif olamaz: " + index);
        }
        int row = index / width;
        int col = index % width;
        double center = (width - 1) / 2.0;
        return new Slot(LEADER_GAP + row * spacing, (col - center) * spacing);
    }

    public record Slot(double back, double side) {
    }
}
