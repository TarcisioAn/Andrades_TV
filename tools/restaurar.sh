#!/usr/bin/env bash
# Repõe a TV como estava antes do instalar.sh: reativa o ecrã inicial da fábrica, o protetor de ecrã e a VPN.
#
#   ./tools/restaurar.sh <IP da TV> [--desinstalar]
#
#   --desinstalar  remove também o Andrades TV da TV
set -euo pipefail
cd "$(dirname "$0")/.."

IP="${1:-}"
[ -n "$IP" ] || { sed -n '4,6p' "$0" | sed 's/^# \{0,1\}//'; exit 1; }
UNINSTALL=false; [ "${2:-}" = "--desinstalar" ] && UNINSTALL=true

PKG=pt.andrades.tv
TARGET="$IP"; [[ "$IP" == *:* ]] || TARGET="$IP:5555"
STATE="tools/estado/$(echo "$IP" | tr ':' '_').txt"
[ -f "$STATE" ] || { echo "Não há estado guardado para $IP (o instalar.sh não correu a partir deste computador)."; exit 1; }

adb connect "$TARGET" >/dev/null
adb -s "$TARGET" wait-for-device
sh() { adb -s "$TARGET" shell "$@"; }
val() { grep "^$1=" "$STATE" | cut -d= -f2-; }

for p in $(val disabled); do
  sh pm enable --user 0 "$p" >/dev/null && echo "→ Reativado: $p"
done

HOME_ACT="$(val home)"
if [ -n "$HOME_ACT" ] && [[ "$HOME_ACT" == */* ]]; then
  sh cmd package set-home-activity --user 0 "$HOME_ACT" >/dev/null 2>&1 \
    || sh cmd package set-home-activity "$HOME_ACT" >/dev/null 2>&1 || true
  echo "→ Ecrã inicial: $HOME_ACT"
fi

restore() {  # restore <chave em settings secure> <valor guardado>
  if [ -z "$2" ] || [ "$2" = "null" ]; then sh settings delete secure "$1" >/dev/null; else sh settings put secure "$1" "$2"; fi
}
restore screensaver_components "$(val saver)"
restore always_on_vpn_app "$(val vpn)"

REMOVED="$(val removed || true)"
[ -z "$REMOVED" ] || echo "→ Desinstalados na limpeza (reinstale pela Play Store se precisar): $REMOVED"

if $UNINSTALL; then
  adb -s "$TARGET" uninstall "$PKG" >/dev/null && echo "→ Andrades TV removido."
fi
rm -f "$STATE"
sh am start -W -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null 2>&1 || true
echo "Pronto: a TV voltou ao original."
