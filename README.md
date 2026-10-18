# Nexo Mobile

App nativo do Nexo para Android e iOS, um fork do [Nuvio Mobile](https://github.com/NuvioMedia/NuvioMobile) (GPLv3). Compatível com addons do Stremio, com debrid (TorBox, Real-Debrid, Premiumize) e P2P.

O que o fork acrescenta:

- **Aba Animes**: temporada atual, em exibição, populares, mais bem avaliados, filmes e próximos lançamentos. Usa os rankings e as notas do **MyAnimeList** quando há um Client ID (engrenagem na aba; crie um em https://myanimelist.net/apiconfig) e o **Kitsu** quando não há. Os títulos abrem pelo addon **Anime Kitsu**, que a aba oferece para instalar.
- **Aba Downloads**: a tela de downloads virou uma aba. Torrents são baixados pelo debrid ativo (por exemplo, TorBox), que entrega um link direto.
- **Tags automáticas nas fontes**: em cache no debrid, resolução, Dublado/Dual áudio/Multi, HDR/DV, origem (REMUX, WEB-DL, CAM…), codec, áudio, tamanho e seeders.
- Marca e ícone do Nexo, `applicationId` `app.nexo.mobile` (instala ao lado do Nuvio) e atualizador apontando para as releases deste repositório.

## Build from source

```bash
git clone https://github.com/NuvioMedia/NuvioMobile.git
cd NuvioMobile
```

### Android

Android development requires Android Studio and the Android SDK.

```bash
./gradlew :androidApp:assembleFullDebug
```

### iOS

iOS development requires macOS and Xcode.

```bash
env NUVIO_IOS_DISTRIBUTION=full xcodebuild \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Debug \
  -sdk iphonesimulator \
  -derivedDataPath build/ios-derived-full-simulator \
  CODE_SIGNING_ALLOWED=NO \
  build
```

The shared app is built with Kotlin Multiplatform and Compose Multiplatform.

## License

[GNU General Public License v3.0](./LICENSE)
