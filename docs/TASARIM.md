# Warbcraft: Tasarım Belgesi

Mount & Blade: Warband ve Bannerlord'dan esinlenen, sezonluk, ortaçağ temalı bir Minecraft sunucusu.
Oyuncu sıradan bir maceracı olarak başlar. Asker toplar, ün kazanır, klan kurar, bir krala bağlanır
ya da kendi krallığını kurar. Sezon sonunda en çok toprağı ve ünü olan oyuncu krallığı kazanır.

## 1. Temel ilkeler

1. **Oyunu oyuncular domine eder, NPC'ler değil.** NPC krallar dünyayı canlı tutar ve görev verir.
   Lordları bilerek zayıftır; orta seviye bir oyuncu klanı bir NPC lordunu yenebilmelidir.
2. **Sezonluk dünya.** Her sezon sıfırdan başlar. Büyükler kalıcı tekel kuramaz, yeni gelen geride
   kalmaz.
3. **Çevrimdışı oyuncu cezalandırılmaz.** Kuşatmalar yalnızca ilan edilen savaş pencerelerinde ve ihbar
   süresinden sonra yapılır. Çevrimdışı oyuncunun ordusu maaş yemez.
4. **Her sürümden giriş.** Ana sürüm 1.21.1'dir. Eski ve yeni istemciler ViaVersion ailesiyle bağlanır.
   Oynanış özel görsellere bağımlı olmaz.
5. **Kurallar tek yerde.** Bütün oyun mantığı test edilen Java çekirdeğindedir. Skript yalnızca
   içerik içindir.

## 2. Sezon yapısı

| Evre | Varsayılan süre | Ne olur |
|---|---|---|
| Hazırlık | 7 gün | Savaş ilan edilemez. Ticaret, asker toplama, görevler, klan kurma. |
| Sefer | 42 gün | Savaşlar serbest. Kuşatmalar yalnızca savaş pencerelerinde. |
| Son Muharebeler | 7 gün | Savaş devam eder, sıralama kesinleşir. |
| Bitti | | Kazananlar ilan edilir. Yeni sezon numarasıyla dünya sıfırlanır. |

- **Savaş pencereleri:** Varsayılan olarak Cuma, Cumartesi ve Pazar 20:00–23:00 ile Çarşamba
  21:00–23:00 (İstanbul saati). Ayarlar `config.yml` dosyasındadır.
- **Kuşatma ihbarı:** Kuşatma ilan edildikten en az 24 saat sonra, açık olan ilk savaş penceresinde
  başlar. Savunanlar hazırlanabilir.
- **Sezon puanı:** Şehir 30, kale 20, köy 5 puan. Buna klanların her 50 ün için 1 puanı eklenir.
  Yalnızca oyuncu krallıkları sıralanır.
- **Sıfırlama:** `config.yml` içindeki `sezon.numara` artırılınca önceki sezonun durumu ve oyuncu
  verileri `arsiv/sezon-N/` klasörüne taşınır.

## 3. Sistemler ve durumları

Durum işaretleri: ✅ yapıldı (çekirdek + eklenti) · 🧪 çekirdekte hazır, oyuna bağlanması sırada ·
🔜 sıradaki aşama · 📋 planlı

### Ordu (parti)
- ✅ Birlik ağaçları: 4 kültür, kültür başına 7–10 birlik, kademe 1–6, iki dallı terfi yolları.
  Kaynak dosya: `birlikler.yml`.
- ✅ Parti sınırı klan kademesiyle büyür: `20 + 15 × kademe (+ liderlik/5)`.
- ✅ Bannerlord tarzı yığın deneyimi ve terfi (`/parti terfi`).
- ✅ Günlük maaş, moral ve firar. Maaş ödenmezse moral düşer, moral 20'nin altına inince en düşük
  kademeli askerler kaçar.
- ✅ Yaralılar ve günlük iyileşme.
- ✅ Köy ve şehirlerden günlük gönüllü havuzuyla asker toplama (`/topla`). Düşman topraklarında
  toplanamaz.
- ✅ **Askerler dünyada oyuncuyu takip eder.** Ek eklenti yok; kendi hafif NPC'lerimiz (Husk,
  kültür rengi ve kademeye göre zırh, süvari atlı, okçular ok atar). Sahada en fazla
  `oyun.sahada-asker` asker görünür (en yüksek kademe önce), gerisi yedektir. Düşen asker %30
  olasılıkla ölür, yoksa yaralanır; yerine yedekten biri çıkar. Öldürdükleri yaratık yığına
  deneyim yazar. Emirler: takip et, burada bekle, hücum; formasyon: sıra, kol, dağınık
  (`/parti emir|formasyon|saha`).
- ✅ **Menüler:** Tüm sistemler sandık menüsüyle oynanır (`/menu` ya da "Sefer Defteri" kitabına sağ
  tık). Menüler kuralları yeniden yazmaz, mevcut komutları çağırır; komutlar da çalışmaya devam eder.
- 📋 Yoldaşlar: tavernadan kiralanan, isimli ve yetenekli NPC'ler.

### Savaş
- 🧪 Otomatik savaş çözümü (`AutoResolve`): NPC'ler arası savaşlar, haydut baskınları ve "otomatik
  çöz" seçeneği. Sınıf bonusları uygulanır: süvari açık arazide güçlü, surlarda zayıf; surda okçu ve
  savunan avantajlı.
- 🔜 **Saha savaşı:** Oyuncu ile düşman parti karşılaştığında bir savaş alanı açılır. Gerçek
  varlıklarla savaşılır ve sonuç partiye yazılır. Performans sınırı: taraf başına aynı anda 50–100
  varlık. Fazlası dalga dalga takviye olarak gelir (Bannerlord'un savaş boyutu ayarı gibi).
- 🔜 **Yönlü dövüş:** Bakış yönüne göre blok, kalkan dayanıklılığı, atlı mızrak hücumu (hızla artan
  hasar), zırh türüne göre hasar azaltma. Minecraft'ın saldırı bekleme süresine dayanmaz, bu yüzden
  1.8 ve 1.21 istemcilerinde aynı hissettirir.
- 📋 Kuşatma: merdiven, koçbaşı, mancınık, kuşatma kulesi (ModelEngine modelleri). Surlar
  kuşatmadan sonra otomatik onarılır.
- 📋 Turnuva ve arena.

### Krallık, klan ve diplomasi
- ✅ 4 NPC krallık, 12 NPC klan, 20 yerleşim (4 şehir, 4 kale, 12 köy). Kaynak dosya: `dunya.yml`.
- ✅ Oyuncu klanı: kurma, davet, katılma, ayrılma, ün ve kademe (`/klan`).
- ✅ Oyuncu krallığı kurma (klan kademesi ≥ 2), davetle klan katma (`/krallik`).
- ✅ NPC krala vasal olma: kralla ilişkin en az 10 olmalı. İlişki görevlerle kazanılır.
- ✅ Savaş ve barış ilanı. Oyuncular arası barışı iki hükümdar da onaylamalı. NPC kral, ilişkin
  negatif değilse barışır.
- ✅ Sezon kuralları: hazırlıkta savaş yok.
- ✅ NPC saldırganlık sınırları: bir NPC krallık oyunculara en fazla haftada bir savaş ilan eder ve
  aynı anda en fazla bir oyuncu krallığıyla savaşır.
- 🧪 Şehir ya da kale el değiştirince bağlı köyler de geçer.
- 🔜 NPC krallıkların kendi kararlarıyla savaş ilan etmesi (yukarıdaki sınırlar içinde).
- 📋 Toprak dağıtımı oylaması, krallık politikaları, etki puanı.

### NPC krallar ve lordlar
- ✅ NPC krallar başkentlerinde oturur ve günlük görev panosu sunar (`/gorev pano`).
- ✅ Görev türleri: haydut avı, mal teslimi, asker teslimi (garnizona katılır), sefer hizmeti
  (yalnızca kralın krallığı savaştayken çıkar).
- ✅ Ödüller: dinar, ilişki, ün. Süresi dolan ya da bırakılan görev ilişki kaybettirir.
- ✅ **Güç sınırları** (`npc-denge`):
  - NPC lord ordusu, aynı kademedeki oyuncu sınırının %60'ı kadar.
  - NPC lordlar en fazla kademe 4 asker taşır. Seçkin birlikler (5–6) oyunculara özeldir.
  - NPC garnizonları %70 boyutunda.
- 🔜 NPC lord partilerinin haritada dolaşması: haydut kovalama, köy yağmalama, kuşatma. Önce soyut
  simülasyon (otomatik çözüm), oyuncu yakındaysa gerçek varlık.

### Ekonomi
- ✅ Dinar kesesi. Para negatife düşmez.
- ✅ Şehir pazarları: fiyat arz ve talebe göre değişir. Stok azsa mal pahalıdır, çoksa ucuzdur.
  Alış ile satış arasında %15 fark olduğundan aynı pazarda al-sat ile para basılamaz.
- ✅ Köyler bağlı oldukları şehrin pazarına günlük mal üretir, şehirler tüketir. Bu, şehirler arası
  fiyat farkı ve ticaret fırsatı yaratır.
- ✅ Mallar gerçek Minecraft eşyalarıdır (buğday, demir…). Görev teslimi ve oyuncular arası ticaret
  de bu eşyalarla yapılır.
- 📋 Kervanlar, atölyeler, köy yağmalamanın pazara etkisi.

### Haydutlar
- ✅ Etiketli yağmacı ve eşkıyalar (şimdilik yönetici çağırır: `/wbadmin haydut`). Öldürünce ödül,
  ün ve görev ilerlemesi.
- 🔜 Haydut kampları ve dolaşan haydut partileri (birlik ağacındaki `haydut` kültürü).

## 4. Çok sürüm desteği

- **Sunucu:** Paper 1.21.1 (Java 21).
- **ViaVersion:** Daha yeni istemciler (1.21.2+). **ViaBackwards:** 1.10–1.21.0 istemcileri.
  **ViaRewind:** 1.7–1.9 istemcileri.
- **Dövüş:** 1.8 istemcisinde saldırı bekleme süresi ve ikinci el kalkan yoktur. Yönlü dövüş sistemi
  vanilya bekleme süresine dayanmayacak şekilde tasarlanır. Gerekirse OldCombatMechanics ile herkes
  aynı tabana çekilir.
- **Görseller:** Özel modeller 1.14+ istemcilerde görünen `CustomModelData` ile yapılır. Eski
  istemci vanilya eşyayı görür ama oynanış aynıdır.
- **Mesajlar:** Adventure ve MiniMessage. ViaVersion eski istemcilere uygun biçime çevirir.

## 5. Mimari

```
warbcraft-core     Saf Java 21. Bütün oyun kuralları. Sunucu API'sine bağımlı değildir, JUnit ile test edilir.
  troop            Birlik türleri ve ağaçları
  party            Parti, yığın deneyimi, terfi, maaş/moral/firar
  faction          Krallık, klan, yerleşim, Realm (kayıt defteri), Diplomacy (savaş/barış kuralları)
  npc              NpcBalance (güç sınırları), NpcLordFactory, NpcKing, RelationBook
  quest            Görev teklifleri, panolar, ilerleme, ödül/ceza
  economy          Purse, Good, Market (arz-talep fiyatlaması)
  battle           AutoResolve (otomatik savaş çözümü)
  season           Season (evreler, savaş pencereleri, kuşatma ihbarı), SeasonScoreboard

warbcraft-plugin   Paper 1.21.1 eklentisi. Çekirdeği oyuna bağlar: komutlar, olaylar, kayıt, YAML verisi.
  data             DataLoader (config/birlikler/dunya.yml), PlayerStore, StateStore (durum.yml)
  command          /parti /topla /pazar /gorev /klan /krallik /sezon /dinar /wbadmin
  listener         Giriş/çıkış, haydut ölümleri, sefer hizmeti

skript/            İçerik betikleri (karşılama, rehber, duyurular)
```

**Neden önce çekirdek:** Kurallar sunucu açmadan saniyeler içinde test edilir. Aynı mantık ileride
saha savaşı, NPC yapay zekâsı ve web paneli tarafından da kullanılabilir.

## 6. Önerilen eklenti yığını

| İhtiyaç | Eklenti | Not |
|---|---|---|
| Çok sürüm | ViaVersion, ViaBackwards, ViaRewind | Zorunlu |
| İzinler | LuckPerms | Rütbeler, VIP |
| Bölge koruma | WorldGuard + WorldEdit | Başkentler ve harita yapımı |
| Asker NPC'leri | Citizens (+ Sentinel) | 2. aşamada değerlendirilecek |
| Özel modeller | ModelEngine, ItemsAdder ya da Nexo | Kuşatma silahları, sancaklar, zırhlar |
| İçerik | Skript | Rehber, etkinlik, duyuru |
| Harita | WorldPainter ya da Terra | Kıta ölçeğinde özel harita |
| Kayıt ve geri alma | CoreProtect | Kötüye kullanım incelemesi |

## 7. Yol haritası

1. **Temel (bu sürüm):** Çekirdek kurallar, birlik ağaçları, parti, ekonomi, görevler, klan,
   krallık, diplomasi, sezon, kayıt.
2. **Saha:** Askerlerin dünyada görünmesi ve takibi, saha savaşları, haydut kampları, yönlü dövüş.
3. **Kuşatma:** Kuşatma ilanı (ihbar ve pencere kuralları hazır), kuşatma silahları, sur onarımı,
   toprak ele geçirme.
4. **Canlı dünya:** NPC lord partileri, kervanlar, köy yağmalama, NPC savaş kararları.
5. **Derinlik:** Yoldaşlar, turnuvalar, atölyeler, krallık politikaları, sezon ödülleri.

## 8. Telif notu

Kültür, krallık, birlik ve yer adları Bannerlord'dan **esinlenmiş ama özgündür** (Valdor, Nordhem,
Aurelya, Karahan). TaleWorlds'ün markalı adlarını (Calradia, Vlandia vb.) ve varlıklarını herkese
açık bir sunucuda kullanmak hukuki risk taşır.

## 9. Açık riskler

- **Varlık sayısı ve performans:** Büyük savaşlar en büyük teknik risk. Çözüm olarak dalga sistemi,
  basit yapay zekâ ve uzaktaki savaşlar için otomatik çözüm düşünülüyor.
- **Alt hesapla görev istismarı:** Sefer hizmeti görevi oyuncu öldürmeyle ilerliyor. Hesap ve IP
  kontrolü ya da aynı kişiyi tekrar öldürmeye bekleme süresi eklenmeli.
- **NPC savaş ilanı zaman damgaları** yeniden başlatmada sıfırlanır. Kaydedilmeli.
