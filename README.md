# KUGO AUTO CLICKER

Auto clicker para Android/emuladores controlado por **tecla global + botão esquerdo do mouse**.

- A tecla configurada só **ativa/desativa** o recurso.
- Com o recurso ativado, os cliques só acontecem **enquanto o botão esquerdo estiver pressionado**; soltou, parou.
- CPS mínimo e máximo (1–100) com variação aleatória dentro do intervalo.
- Funciona em segundo plano (Foreground Service), sem janelas sobre o jogo.

## Como funciona (e o que é necessário)

| Função | Como é feita | Requisito |
|---|---|---|
| Tecla global | `AccessibilityService.onKeyEvent` | Ativar o serviço de acessibilidade do KUGO |
| Gerar cliques | `dispatchGesture` (toque curto) | Mesmo serviço de acessibilidade (Android 7.0+) |
| Mouse pressionado/solto | Android 14+: `onMotionEvent` (sem root). Qualquer versão: `su -c getevent -l` (BTN_LEFT / BTN_TOUCH) | Android 14+ com mouse real, **ou** root |
| Segundo plano | Foreground Service com notificação discreta | Permitir notificações (Android 13+) |

### Limitações importantes (leia)

- O `AccessibilityService` sozinho **não** recebe de forma confiável o botão do mouse. Por isso o app lê `/dev/input` com root. **Sem root, o KUGO não detecta o mouse** e o indicador mostra "SEM ROOT". Não há simulação falsa.
- Muitos emuladores (BlueStacks, LDPlayer, MEmu, etc.) têm opção de habilitar root nas configurações; o nome e a disponibilidade variam por emulador/versão.
- Onde o clique acontece: na **última posição do ponteiro** lida de `/dev/input`. Se o dispositivo de mouse não reportar posição absoluta, o toque cai no **centro da tela**.
- Um dedo/toque real também gera `BTN_TOUCH`, então em tela touch o app também reconhece "pressionado".
- O Android limita a frequência de gestos; CPS muito altos podem não ser atingidos em todos os aparelhos.
- O app não contorna mecanismos de segurança do Android nem sistemas antitrapaça. Verifique as regras do jogo/servidor antes de usar.
- O projeto **ainda não foi testado em dispositivo/emulador**.

## Relatório de diagnóstico do Shizuku (v1.1.1)

O cartão SHIZUKU mostra um relatório com cada etapa: Shizuku instalado, sinal (binder) recebido, permissão, serviço do KUGO, conexão do serviço de shell, leitura do `getevent` e injeção de cliques. **COPIAR RELATÓRIO** copia o texto; **TESTAR CLIQUE (5 s)** injeta um clique de mouse no centro da tela, independente da detecção do botão. O KUGO tenta reconectar sozinho a cada 2 s.

## Shizuku: clique esquerdo real e mouse capturado (v1.1.0)

Com o **Shizuku** instalado e iniciado, o KUGO liga um serviço no processo de shell que (1) lê o botão esquerdo em `/dev/input` mesmo com o cursor capturado e (2) injeta **cliques de mouse reais (botão esquerdo)**. Fluxo: instalar o Shizuku → iniciá-lo (o próprio Shizuku guia o pareamento da Depuração sem fio; ou root) → no KUGO tocar em **CONECTAR SHIZUKU**, conceder a permissão e tocar em **INICIAR**. O botão **MODO DE CLIQUE** alterna: 1 mouse absoluto (shell), 2 mouse capturado (shell), 3 toque (acessibilidade). Se um modo não funcionar no jogo, teste o outro. Não foi testado em jogo pelo autor. O Shizuku precisa ser reiniciado após reiniciar o aparelho (modo sem root).

## Limitação: botão esquerdo x direito no Pojav/Zalith (v1.0.5)

O app gera **toques** (`dispatchGesture`). O Pojav/Zalith interpreta um toque curto como **botão direito** (usar/colocar) e só um toque longo como botão esquerdo (atacar/quebrar). Por isso os cliques automáticos saem no botão direito dentro desses launchers. Gerar **cliques de mouse esquerdo reais** exige injeção de eventos de mouse com permissão de shell (via ADB), não só acessibilidade.

## Ponte Termux/ADB para mouse capturado (v1.0.4)

Dentro do Pojav/Zalith o jogo captura o mouse. Pedir esses eventos ao `AccessibilityService` (v1.0.3) **travou o movimento do mouse** no jogo, então a v1.0.4 voltou a pedir só o mouse comum. Para detectar o botão esquerdo com o cursor capturado **sem root**, use `termux/kugo-mouse.sh`: ele lê `adb shell getevent -l` (via Depuração sem fio, no próprio aparelho) e avisa o app por broadcast. Enquanto o botão está pressionado ele reenvia o aviso a cada ~1 s; se os avisos pararem por 3 s, o app solta o mouse sozinho. O broadcast é aceito de qualquer app, mas só tem efeito com o Auto Clicker ativado pela tecla.

## Minecraft (PojavLauncher / Zalith) — v1.0.3

Dentro do jogo, o Minecraft **captura o cursor** e o Android passa a enviar eventos *relativos* do mouse. A v1.0.3 também escuta esses eventos (`SOURCE_MOUSE_RELATIVE`) e, nesse modo, toca no **centro da tela** (a mira) em vez de usar uma posição que não existe. O toque dura de 5 a 25 ms conforme o CPS. A tela principal mostra "Cursor capturado: SIM/NÃO" e a contagem de toques enviados, concluídos, cancelados e recusados, para diagnosticar se o jogo está recebendo os toques. Não foi testado dentro do jogo pelo autor.

## Mouse sem root (v1.0.2)

No **Android 14+ (API 34)** o serviço de acessibilidade pede os eventos de mouse (`onMotionEvent`) e acompanha o botão esquerdo sem root. Isso depende de o Android entregar eventos com origem `SOURCE_MOUSE` (mouse físico USB/Bluetooth no aparelho). Em Android anterior ao 14, ou se o emulador converter o mouse em toque, esse caminho não funciona e o app só detecta o mouse com root. A tela principal mostra a contagem de eventos de cada caminho.

## Notificação de status e diagnóstico (v1.0.1)

Com o serviço iniciado, uma notificação discreta mostra **ATIVADO/DESATIVADO**, o estado do mouse, a tecla configurada, a última tecla recebida, a acessibilidade e a leitura do mouse (root). A mesma informação aparece na tela principal, junto com a contagem de eventos de mouse/toque lidos. Se pressionar a tecla não muda nada, "Última tecla recebida" mostra se o Android entregou a tecla ao serviço; se segurar o mouse não muda nada, a contagem de eventos mostra se a leitura com root está recebendo algo.

## Instalação e permissões

1. Instale o APK de **Releases**.
2. Abra o KUGO AUTO CLICKER → **ABRIR AJUSTES DE ACESSIBILIDADE** → ative **KUGO AUTO CLICKER**.
   - Se o Android bloquear (app fora da Play Store), em Ajustes → Apps → KUGO → menu ⋮ → **Permitir configurações restritas**, e tente de novo.
3. Conceda a permissão de **notificações** (Android 13+).
4. Conceda **root** ao KUGO quando o gerenciador de root pedir.
5. Defina CPS mínimo/máximo e toque em **SELECIONAR KEY**; pressione a tecla desejada.
6. Toque em **INICIAR**, abra o jogo, pressione a tecla para ativar e segure o botão esquerdo.

Em alguns aparelhos/emuladores, desative a otimização de bateria para o KUGO.

## Compilação (somente GitHub Actions)

O APK é compilado no GitHub Actions (`.github/workflows/build.yml`) a cada push em `main` ou manualmente (Actions → *Build e Release do APK* → Run workflow). Não compile no Termux.

- O workflow usa Java 17, Android SDK e Gradle 8.9 (sem Gradle Wrapper no repositório).
- O APK sai como artefato e em **Releases** (`Kugo-Auto-Clicker-vX.Y.Z.apk`). Versões existentes **não** são substituídas: aumente `versionName`/`versionCode` em `app/build.gradle.kts` para uma nova versão.

### Assinatura (opcional, recomendado)

Sem secrets, o workflow assina com uma chave de debug temporária (cada build tem assinatura diferente, então atualizar por cima pode falhar). Para assinatura estável, crie estes **GitHub Secrets** no repositório (nunca coloque a chave no código):

`KUGO_KEYSTORE_B64` (keystore em base64), `KUGO_STORE_PASSWORD`, `KUGO_KEY_ALIAS`, `KUGO_KEY_PASSWORD`.

## Estrutura

```
app/                      código Kotlin, layout e recursos
.github/workflows/build.yml
build.gradle.kts  settings.gradle.kts  gradle.properties
```
