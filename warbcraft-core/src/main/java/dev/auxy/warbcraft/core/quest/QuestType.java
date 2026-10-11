package dev.auxy.warbcraft.core.quest;

public enum QuestType {
    /** Kralligin topraklarindaki haydutlari temizle. Hedef: bos. */
    HUNT_BANDITS("Haydut Avi"),
    /** Baskente mal getir. Hedef: mal kimligi. */
    DELIVER_GOODS("Erzak Teslimi"),
    /** Garnizona belirli kademede asker teslim et. Hedef: en dusuk kademe (sayi olarak). */
    RAISE_TROOPS("Asker Toplama"),
    /** Kralligin dusmanlarina karsi savas kazan. Hedef: dusman krallik kimligi. */
    WAR_SERVICE("Sefer Hizmeti");

    private final String displayName;

    QuestType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
