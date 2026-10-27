#!/usr/bin/env bash
# Instala o Andrades TV na TV e torna-o o ecrã inicial. Tudo é reversível com tools/restaurar.sh.
#
#   ./tools/instalar.sh <IP da TV> [--smarttube] [--vpn-sempre] [--protetor]
#
#   --smarttube   descarrega o SmartTube (YouTube sem anúncios) do GitHub oficial e instala-o
#   --vpn-sempre  liga a NordVPN como "VPN sempre ativa" do Android (precisa da NordVPN já instalada)
#   --protetor    usa o relógio do Andrades TV como protetor de ecrã
#
# Antes: na TV, ative as Opções de programador e a Depuração USB / ADB por rede.
set -euo pipefail
cd "$(dirname "$0")/.."

IP="${1:-}"
[ -n "$IP" ] || { sed -n '4,10p' "$0" | sed 's/^# \{0,1\}//'; exit 1; }
shift
SMARTTUBE=false; VPN=false; SAVER=false
for a in "$@"; do
  case "$a" in
    --smarttube) SMARTTUBE=true ;;
    --vpn-sempre) VPN=true ;;
    --protetor) SAVER=true ;;
    *) echo "Opção desconhecida: $a"; exit 1 ;;
  esac
done

PKG=pt.andrades.tv
HOME_ACT="$PKG/.HomeActivity"
TARGET="$IP"; [[ "$IP" == *:* ]] || TARGET="$IP:5555"
STATE="tools/estado/$(echo "$IP" | tr ':' '_').txt"
mkdir -p tools/estado

echo "→ A ligar à TV em $TARGET (aceite o pedido de depuração que aparece na TV)…"
adb connect "$TARGET" >/dev/null
for _ in $(seq 1 30); do
  st="$(adb -s "$TARGET" get-state 2>&1 || true)"
  [ "$st" = "device" ] && break
  sleep 2
done
[ "$(adb -s "$TARGET" get-state 2>/dev/null || true)" = "device" ] \
  || { echo "A TV não autorizou a ligação. Aceite o pedido na TV e tente outra vez."; exit 1; }
sh() { adb -s "$TARGET" shell "$@"; }

[ -f AndradesTV.apk ] || ./build.sh
echo "→ A instalar o Andrades TV…"
adb -s "$TARGET" install -r AndradesTV.apk >/dev/null

if $SMARTTUBE; then
  ABI="$(sh getprop ro.product.cpu.abi | tr -d '\r')"
  echo "→ A procurar o SmartTube mais recente para $ABI…"
  URL="$(curl -fsSL https://api.github.com/repos/yuliskov/SmartTube/releases/latest \
    | grep -o '"browser_download_url": *"[^"]*\.apk"' | cut -d'"' -f4 \
    | grep -i stable | { grep -i "$ABI" || true; } | head -1)"
  [ -n "$URL" ] || URL="$(curl -fsSL https://api.github.com/repos/yuliskov/SmartTube/releases/latest \
    | grep -o '"browser_download_url": *"[^"]*\.apk"' | cut -d'"' -f4 | grep -i stable | head -1)"
  [ -n "$URL" ] || { echo "Não encontrei o APK do SmartTube no GitHub."; exit 1; }
  TMP="$(mktemp -d)"
  curl -fsSL -o "$TMP/smarttube.apk" "$URL"
  adb -s "$TARGET" install -r "$TMP/smarttube.apk" >/dev/null
  rm -rf "$TMP"
  echo "  SmartTube instalado ($(basename "$URL"))."
fi

# Guardar o estado original uma única vez, para o restaurar.sh repor tudo como veio de fábrica.
if [ ! -f "$STATE" ]; then
  OLD_HOME="$(sh cmd package resolve-activity --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME | tr -d '\r' | tail -1)"
  OLD_SAVER="$(sh settings get secure screensaver_components | tr -d '\r')"
  OLD_VPN="$(sh settings get secure always_on_vpn_app | tr -d '\r')"
  {
    echo "home=$OLD_HOME"
    echo "saver=$OLD_SAVER"
    echo "vpn=$OLD_VPN"
    echo "disabled="
  } > "$STATE"
fi
OLD_HOME="$(grep '^home=' "$STATE" | cut -d= -f2-)"

echo "→ A tornar o Andrades TV o ecrã inicial…"
sh cmd package set-home-activity --user 0 "$HOME_ACT" >/dev/null 2>&1 \
  || sh cmd package set-home-activity "$HOME_ACT" >/dev/null 2>&1 || true

# Em muitas TVs (PatchWall da Xiaomi, Google TV) o botão Home ignora a escolha acima enquanto o ecrã
# da fábrica estiver ativo. Desativamos só esse app (continua instalado; o restaurar.sh reativa-o).
OLD_PKG="${OLD_HOME%%/*}"
if [ -n "$OLD_PKG" ] && [ "$OLD_PKG" != "$PKG" ] && [ "$OLD_PKG" != "android" ] && [[ "$OLD_PKG" == *.* ]]; then
  if sh pm disable-user --user 0 "$OLD_PKG" 2>&1 | grep -q disabled; then
    grep '^disabled=' "$STATE" | cut -d= -f2- | tr ' ' '\n' | grep -qx "$OLD_PKG" \
      || sed -i '' "s|^disabled=\(.*\)|disabled=\1 $OLD_PKG|" "$STATE"
    echo "  Ecrã inicial da fábrica ($OLD_PKG) desativado."
  fi
fi

if $VPN; then
  if sh pm path com.nordvpn.android >/dev/null 2>&1; then
    sh settings put secure always_on_vpn_app com.nordvpn.android
    sh settings put secure always_on_vpn_lockdown 0
    echo "→ NordVPN definida como VPN sempre ativa. Abra-a uma vez na TV e inicie sessão."
  else
    echo "→ A NordVPN não está instalada: instale-a pela Play Store da TV e corra outra vez com --vpn-sempre."
  fi
fi

if $SAVER; then
  sh settings put secure screensaver_components "$PKG/.ClockDream"
  echo "→ Protetor de ecrã: relógio do Andrades TV."
fi

sh am start -W -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null 2>&1 || true
echo "Pronto. Para voltar ao original: ./tools/restaurar.sh $IP"
