#!/usr/bin/env sh
# Warbcraft yerel deneme sunucusu (macOS/Linux). Java 21 gerekir.
cd "$(dirname "$0")" || exit 1
if ! command -v java >/dev/null 2>&1; then
  echo "Java 21 bulunamadi. https://adoptium.net adresinden Temurin 21 kurun." >&2
  exit 1
fi
echo "Warbcraft hazirlaniyor; acilinca Minecraft'ta localhost adresine baglanin."
echo "Yonetici olmak icin konsola: op OYUNCU_ADIN"
exec ./gradlew :warbcraft-plugin:runServer
