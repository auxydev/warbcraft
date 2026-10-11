# Warbcraft: geliştirici notları

Sezonluk, Mount & Blade esintili Minecraft sunucusu. Paper 1.21.1, Java 21, Gradle (Kotlin DSL).
Belgeler, komut ve oyuncu mesajları Türkçedir. Kod tanımlayıcıları İngilizcedir, kod yorumları
ASCII Türkçedir.

## Kurallar

- **Oyun kuralı `warbcraft-core` içine yazılır** ve JUnit testiyle gelir. Çekirdek Bukkit/Paper
  sınıflarını import etmez.
- `warbcraft-plugin` yalnızca bağlar: komut, olay, kayıt, YAML okuma. Burada iş kuralı biriktirme;
  gerekiyorsa çekirdeğe taşı.
- Çekirdekte `-Xlint:all -Werror` açıktır; uyarı bırakma.
- Oyuncu girdisini MiniMessage içine koymadan önce `Msg.esc(...)` ile kaçır.
- Büyük-küçük harf dönüşümünde her zaman `Locale.ROOT` kullan (Türkçe yerel ayarda `I`/`ı` sorunu).
- NPC'leri güçlendiren her değişiklik `NpcBalance` sınırlarına uymalıdır. Tasarım gereği NPC
  lordlar oyunculardan zayıftır.
- Veri dosyaları (`birlikler.yml`, `dunya.yml`) değişince `DataLoader` doğrulamalarına uymalıdır
  (terfi bir kademe yukarı ve aynı kültürde, köy bir şehre ya da kaleye bağlı vb.).

## Komutlar

```bash
./gradlew :warbcraft-core:test      # hızlı; sunucu API'si gerekmez
./gradlew build                     # eklenti dahil (repo.papermc.io erişimi gerekir)
./gradlew :warbcraft-plugin:runServer
```

Tasarım ve yol haritası: `docs/TASARIM.md`.
