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
| Mouse pressionado/solto | Leitura de eventos brutos: `su -c getevent -l` (BTN_LEFT / BTN_TOUCH) | **Root dentro do Android/emulador** |
| Segundo plano | Foreground Service com notificação discreta | Permitir notificações (Android 13+) |

### Limitações importantes (leia)

- O `AccessibilityService` sozinho **não** recebe de forma confiável o botão do mouse. Por isso o app lê `/dev/input` com root. **Sem root, o KUGO não detecta o mouse** e o indicador mostra "SEM ROOT". Não há simulação falsa.
- Muitos emuladores (BlueStacks, LDPlayer, MEmu, etc.) têm opção de habilitar root nas configurações; o nome e a disponibilidade variam por emulador/versão.
- Onde o clique acontece: na **última posição do ponteiro** lida de `/dev/input`. Se o dispositivo de mouse não reportar posição absoluta, o toque cai no **centro da tela**.
- Um dedo/toque real também gera `BTN_TOUCH`, então em tela touch o app também reconhece "pressionado".
- O Android limita a frequência de gestos; CPS muito altos podem não ser atingidos em todos os aparelhos.
- O app não contorna mecanismos de segurança do Android nem sistemas antitrapaça. Verifique as regras do jogo/servidor antes de usar.
- O projeto **ainda não foi testado em dispositivo/emulador**.

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
