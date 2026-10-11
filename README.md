# Warbcraft

Mount & Blade: Warband ve Bannerlord'dan esinlenen, **sezonluk ortaçağ Minecraft sunucusu**:
asker topla, ün kazan, klanını kur, bir krala bağlan ya da kendi krallığını kur.

- Ana sürüm **Paper 1.21.1**. ViaVersion ailesiyle 1.7'den en yeni sürüme kadar herkes bağlanabilir.
- NPC krallar dünyayı canlı tutar ve görev verir. Lordları bilerek zayıftır, oyunu oyuncular domine eder.
- Her sezon sıfırlanır. Kuşatmalar yalnızca ilan edilen savaş pencerelerinde yapılır.

Ayrıntılar: [docs/TASARIM.md](docs/TASARIM.md) · Sunucu kurulumu: [docs/SUNUCU.md](docs/SUNUCU.md)

## Yapı

| Klasör | İçerik |
|---|---|
| `warbcraft-core/` | Saf Java oyun kuralları (ordu, ekonomi, krallıklar, görevler, savaş, sezon) ve testleri |
| `warbcraft-plugin/` | Paper eklentisi: komutlar, olaylar, kayıt, YAML verisi (`config.yml`, `birlikler.yml`, `dunya.yml`) |
| `skript/` | Skript içerik betikleri (karşılama, rehber) |
| `docs/` | Tasarım ve kurulum belgeleri |

## Derleme

Java 21 gerekir.

```bash
./gradlew build                      # testler + eklenti jar'ı
./gradlew :warbcraft-core:test       # yalnızca çekirdek testleri (sunucu API'si gerekmez)
./gradlew :warbcraft-plugin:runServer  # eklentiyle yerel Paper 1.21.1 test sunucusu (run/ klasörü)
```

Eklenti dosyası: `warbcraft-plugin/build/libs/Warbcraft-<sürüm>.jar` (çekirdek içine gömülüdür).
GitHub Actions her push'ta derler ve jar'ı "Warbcraft" adıyla yükler.

## Oyuncu komutları

| Komut | Ne yapar |
|---|---|
| `/parti` | Ordunu gösterir. `terfi <birlik> <hedef> [adet]`, `terhis <birlik> <adet>` |
| `/topla [adet]` | Bulunduğun köy ya da şehirden gönüllü toplar |
| `/pazar` | Şehir pazarı: `al <mal> <adet>`, `sat <mal> <adet>` |
| `/gorev` | Görevlerin. Başkentte: `pano`, `al <no>`, `teslim <no>`; her yerde: `birak <no>` |
| `/klan` | `bilgi`, `kur <ad>`, `davet <oyuncu>`, `katil <klan>`, `ayril` |
| `/krallik` | `liste`, `bilgi <id>`, `kur <kültür> <ad>`, `katil <id>`, `davet <klan>`, `ayril`, `savas <id>`, `baris <id>` |
| `/sezon` | Evre, sonraki savaş penceresi, sıralama |
| `/dinar` | Kesen; `gonder <oyuncu> <miktar>` |

Yönetici: `/wbadmin gun | kaydet | dinar | un | konum <yerleşim> [yarıçap] | haydut [adet] | savas | baris`
