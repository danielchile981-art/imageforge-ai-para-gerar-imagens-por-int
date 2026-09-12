# FusionMind AI

App Android offline-first que combina **Qwen3 4B**, **Gemma 3 4B Vision**, **Stable Diffusion 1.5** e **Whisper** numa única interface.

Base: Llamatik 1.7.0 (llama.cpp + whisper.cpp + stable-diffusion.cpp). Os modelos são baixados separadamente dentro do app; não ficam dentro do APK.

## Recursos
- Chat e código com Qwen
- Análise de fotos com Gemma Vision
- Geração de imagens 512×512 com Stable Diffusion
- Voz para texto com Whisper
- Gerenciador de modelos com progresso
- Tema claro/escuro e ícone próprio

## Limitações
- Download inicial exige internet.
- Instalar tudo exige aproximadamente 7,7 GB só de pesos, mais espaço temporário/cache.
- Modelos 4B usam muita RAM e podem ficar lentos em aparelhos antigos.
- Não há notícias/preços atuais no modo offline.
- Vídeo multimodal ainda não está exposto nesta primeira interface.
