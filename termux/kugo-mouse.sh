#!/data/data/com.termux/files/usr/bin/bash
# KUGO - ponte de mouse via ADB (sem root).
# Lê o botão esquerdo com "adb shell getevent" e avisa o app KUGO AUTO CLICKER.
# Requisitos: pkg install android-tools  +  adb conectado ao próprio aparelho (Depuração sem fio).

PKG="com.kugo.autoclicker"

send() {
  am broadcast -n "$PKG/.MouseReceiver" -a "$PKG.MOUSE" --ez down "$1" >/dev/null 2>&1
}

if ! command -v adb >/dev/null 2>&1; then
  echo "adb não encontrado. Instale com: pkg install android-tools"
  exit 1
fi

if ! adb get-state >/dev/null 2>&1; then
  echo "ADB não está conectado. Use: adb connect IP:PORTA (Depuração sem fio)."
  exit 1
fi

trap 'send false' EXIT
trap 'exit 0' INT TERM

echo "KUGO mouse bridge ativa. Pressione Ctrl+C para parar."
down=0
adb shell getevent -l 2>/dev/null | while true; do
  if IFS= read -r -t 1 line; then
    case "$line" in
      *BTN_LEFT*DOWN*) down=1; send true ;;
      *BTN_LEFT*UP*)   down=0; send false ;;
    esac
  else
    rc=$?
    if [ "$rc" -gt 128 ]; then
      # sem eventos há 1 s: reenvia "pressionado" como sinal de vida
      [ "$down" = "1" ] && send true
    else
      break
    fi
  fi
done
send false
echo "Ponte encerrada."
