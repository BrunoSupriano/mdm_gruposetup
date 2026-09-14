# Setup MDM — App de localização diária (Android, Kotlin)

App corporativo que envia **1x/dia** a localização do dispositivo + identificação
(ANDROID_ID, modelo, fabricante, versão, operadora, bateria, e IMEI quando o Android permite)
para um backend. Sem bloqueio, sem wipe. Especificação completa em `ESPECIFICACAO_APP_MDM.md`.

- **minSdk 21** (Android 5.0) · **targetSdk 34** · package `br.com.gruposetup.mdm`
- Fallback automático para `LocationManager` em aparelhos sem Google Play Services.

## 1. Antes de compilar: configure o backend

Edite `app/build.gradle.kts` → bloco `defaultConfig`:

```kotlin
buildConfigField("String", "API_BASE_URL", "\"https://SEU-BACKEND.exemplo.com\"")
buildConfigField("String", "API_KEY", "\"SUA_CHAVE_REAL\"")
```

O app faz `POST {API_BASE_URL}/api/v1/posicoes` com header `Authorization: Bearer {API_KEY}`.

## 2. Compilar o APK (sem Android Studio)

### Opção A — Docker (recomendada)
```bash
docker build -t setup-mdm-build .
docker run --rm -v "$PWD":/proj -w /proj setup-mdm-build ./gradlew :app:assembleDebug
```

### Opção B — WSL/Linux por linha de comando
Requer JDK 17+ e o Android SDK (cmdline-tools). Com `ANDROID_SDK_ROOT` apontando pro SDK:
```bash
echo "sdk.dir=$ANDROID_SDK_ROOT" > local.properties
./gradlew :app:assembleDebug
```

APK gerado em: `app/build/outputs/apk/debug/app-debug.apk`

> Para distribuição, gere um APK de release assinado com seu próprio keystore
> (`./gradlew :app:assembleRelease` + `apksigner`). Para uso interno via cabo/link, o debug já serve.

## 3. Instalar e preparar cada aparelho (~20s, sem digitar nada)

1. Instale o APK (cabo `adb install app-debug.apk`, link interno ou MMDM).
2. Abra o app **uma vez** (obrigatório — o Android não roda app recém-instalado sem o 1º open).
3. Toque em **Concluir configuração** e conceda, na sequência:
   - Localização → **Permitir o tempo todo**
   - Notificações (Android 13+)
   - Desativar otimização de bateria
4. A tela mostra **"Monitoramento ativo"** → entregue o aparelho.

A partir daí roda sozinho, todo dia, inclusive após reiniciar. Se o usuário desligar o GPS,
o app dispara uma notificação pedindo para reativar.

## 4. Estrutura

```
app/src/main/java/br/com/gruposetup/mdm/
  MainActivity.kt        # tela Home: status + fluxo de permissões
  DailyLocationWorker.kt # job diário (WorkManager): captura + envio
  LocationRepository.kt  # Fused (Play Services) + fallback LocationManager
  DeviceInfo.kt          # android_id, modelo, bateria, operadora, IMEI opcional, payload JSON
  ApiClient.kt           # POST OkHttp + TLS 1.2 (Android 5.x)
  Tls12SocketFactory.kt  # habilita TLS 1.2 em API 21-22
  Notifier.kt            # notificação "ative a localização"
  Scheduler.kt           # agenda o job diário + execução manual
  BootReceiver.kt        # reagenda após reiniciar
  Permissions.kt         # checagens de permissão/GPS
```

## Observações técnicas
- **IMEI**: só é lido em Android ≤ 9. No Android 10+ retorna `null` (restrição do SO para app comum).
- **ANDROID_ID** é a chave universal do aparelho (enrollment automático no 1º envio).
