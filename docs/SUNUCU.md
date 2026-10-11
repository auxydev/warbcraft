# Sunucu kurulumu

## 1. Paper 1.21.1

1. Java 21 kur.
2. [papermc.io](https://papermc.io/downloads/paper) üzerinden **1.21.1** sürümünü indir ve bir kez
   çalıştırıp `eula.txt` dosyasını onayla.
3. `server.properties` için önerilenler: `difficulty=normal`, `spawn-protection=0` (koruma
   WorldGuard ile yapılır), `view-distance=8`, `simulation-distance=6`. Büyük savaşlar için
   simülasyon mesafesini düşük tut.

## 2. Çok sürüm (eski ve yeni istemciler)

`plugins/` klasörüne şunları koy:

| Eklenti | Hangi istemciler |
|---|---|
| ViaVersion | 1.21.1'den yeni sürümler |
| ViaBackwards | 1.10 – 1.21.0 |
| ViaRewind (+ ViaRewind-Legacy-Support) | 1.7 – 1.9 |

ViaVersion ailesinin her zaman en güncel sürümünü kullan. Yeni Minecraft sürümleri çıktıkça sunucuyu
değil ViaVersion'ı güncellemek yeterlidir.

## 3. Warbcraft

1. `./gradlew build` çalıştır ya da GitHub Actions'tan "Warbcraft" jar'ını indir.
2. `Warbcraft-<sürüm>.jar` dosyasını `plugins/` klasörüne koy ve sunucuyu başlat.
3. `plugins/Warbcraft/` altında şu dosyalar oluşur:
   - `config.yml`: sezon tarihleri, savaş pencereleri, NPC güç sınırları, ücretler
   - `birlikler.yml`: kültürler ve birlik ağaçları
   - `dunya.yml`: mallar, NPC krallıklar, klanlar, yerleşimler
   - `durum.yml`: oyun sırasında değişen dünya durumu (otomatik)
   - `oyuncular/`: oyuncu keseleri ve partileri (otomatik)
4. Haritayı kurduktan sonra her yerleşimin ortasında dur ve
   `/wbadmin konum <yerleşim> [yarıçap]` yaz. Asker toplama, pazar ve görevler bu bölgelere göre
   çalışır.

## 4. Yeni sezon

1. Sunucuyu kapat.
2. Haritayı yeni sezon için hazırla ya da sıfırla.
3. `config.yml` içinde `sezon.numara` değerini artır ve `sezon.baslangic` tarihini güncelle.
4. Sunucuyu aç. Önceki sezonun `durum.yml` dosyası ve `oyuncular/` klasörü
   `arsiv/sezon-N/` altına taşınır.

## 5. Önerilen diğer eklentiler

**Zorunlu:** Citizens 2.0.35 (derleme 3598): askerler ve haydutlar skinli insan NPC'leridir.
Daha yeni Citizens sürümleri 1.21.1'i desteklemez. Deneme sunucusu (`baslat.bat`) onu kendisi indirir:
<https://ci.citizensnpcs.co/job/Citizens2/3598/>

**Eski kurulumu güncellerken:** `plugins/Warbcraft/birlikler.yml` dosyası kendiliğinden güncellenmez.
Yeni haydut birlikleri (`haydut_dag`, `haydut_col` vb.) yoksa eklenti açılışta hangi birliğin eksik
olduğunu söyler; dosyayı depodaki sürümle değiştir ya da eksik satırları ekle.

LuckPerms (izinler), WorldGuard + WorldEdit (koruma ve harita), Skript (`skript/` klasöründeki
betikler), CoreProtect (kayıt). ModelEngine sonraki aşamalarda gerekecek, ayrıntılar
[TASARIM.md](TASARIM.md) içinde.
