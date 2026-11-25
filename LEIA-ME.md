# Rosé Launcher 0.2.0 — Classic desktop

Código-fonte Android nativo (Java) baseado no Rosé 0.1.0. Interface clássica rosa: atalhos no desktop, painel de relógio com barra de título, barra de tarefas quadrada, janelas de aplicações e terminal, sem papel de parede incluído. Favoritos e ajustes existentes permanecem compatíveis se a assinatura do APK for a mesma.

## Gerar APK via GitHub Actions
1. Extraia o ZIP. Faça upload de todos os arquivos da raiz para um repositório GitHub, inclusive `.github/workflows/android.yml` e `gradle/wrapper/` (arquivos ocultos devem ser incluídos).
2. Em Actions, abra **APK do Rose Launcher** e execute Run workflow (ou faça um push). Baixe o artefato **Rose-Launcher-APK** após o build concluir.
3. Extraia o APK e instale no Android. Em Aparência > Tela inicial padrão, selecione Rosé Launcher. Se houver conflito de assinatura com a versão anterior, a desinstalação da anterior é necessária e apaga configurações locais.

Alternativamente, abra no Android Studio ou rode `bash gradlew :app:assembleDebug` com JDK 17 e SDK Android 35. APK em `app/build/outputs/apk/debug/app-debug.apk`.

## Uso
- Menu lista os aplicativos; lupa abre busca; toque prolongado abre opções e permite fixar até quatro favoritos.
- Aparência permite alterar o rosa, ocultar relógio do painel e escolher o papel de parede do Android.
- Terminal usa o Termux instalado separadamente, via RUN_COMMAND. Abra Termux uma vez, defina `allow-external-apps=true` em `~/.termux/termux.properties`, execute `termux-reload-settings` e autorize a permissão pelo menu Configurar. Não é uma sessão PTY: o painel envia um comando por vez e recebe a saída ao concluir; Abrir Termux abre a sessão interativa real.

## Validação
Este pacote contém o projeto, não um APK compilado. A compilação e a medição de desempenho no Moto G75 ainda precisam ser realizadas. A versão anterior foi usada como base; não há fonte tipográfica externa incluída.
