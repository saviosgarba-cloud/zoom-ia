# Zoom IA 0.2 — Android

Base Android editável da Zoom IA.

## O que funciona agora
- App Android em Kotlin + Jetpack Compose.
- Configuração de endpoint, modelo e chave dentro do próprio app.
- Chat real com APIs compatíveis com o formato `POST /v1/chat/completions` da OpenAI.
- Configuração salva localmente no aparelho.
- Módulos visuais preparados para imagem, remoção de objetos, troca de roupa e vídeo.
- GitHub Actions gera um APK debug automaticamente.

> Para uso pessoal/teste, a chave pode ser salva no aparelho. Para publicar o app, o recomendado é usar seu próprio backend e não distribuir uma chave secreta dentro do APK.

## Gerar APK no GitHub
Abra a aba Actions e execute `Build Zoom IA APK`.
Depois baixe o artifact `Zoom-IA-APK`.
Dentro dele estará `Zoom-IA.apk`.

## Configurar o chat
No app, informe:
1. Endpoint completo, por exemplo `https://seu-servidor/v1/chat/completions`.
2. Nome do modelo aceito pelo seu servidor.
3. Chave da API, se o servidor exigir.

## Código principal
`app/src/main/java/com/zoomia/app/MainActivity.kt`

## Observação
Os módulos de imagem, inpainting, troca de roupa e vídeo ainda precisam ser conectados aos endpoints/modelos escolhidos por você.
