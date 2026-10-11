# Skript betikleri

Bu klasördeki `.sk` dosyaları sunucudaki `plugins/Skript/scripts/` klasörüne kopyalanır.

**İş bölümü:**

- **Warbcraft eklentisi (Java):** Ordu, ekonomi, krallıklar, savaş, sezon gibi birbirine bağlı ve veri tutan
  bütün kurallar. Test edilir, hızlıdır, sürümlenir.
- **Skript:** Sık değişen ve kurala dokunmayan içerik: karşılama mesajları, rehberler, duyurular,
  etkinlikler, NPC diyalog metinleri. Bir yetkili Java bilmeden düzenleyebilir.

Skript betikleri oyun durumunu değiştirmek için Warbcraft komutlarını çağırmalıdır
(örn. `make console execute command "wbadmin dinar %player% 100"`). Böylece kurallar tek yerde kalır.
