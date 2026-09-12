# FusionMind AI 1.1

Assistente Android offline-first com interface de chat única. Combina Qwen, Gemma Vision, Stable Diffusion e Whisper usando Llamatik 1.7.0.

## Novidades 1.1
- Interface principal no estilo de um assistente de chat: uma conversa, anexo, microfone e respostas em streaming.
- Histórico local das últimas conversas.
- Modo **Rápido** com Qwen3 1.7B Q4_K_M (~1,28 GB) e `/no_think`.
- Modo **Qualidade** com Qwen3 4B Q4_K_M (~2,5 GB).
- 4 threads de inferência, `mmap` ligado e Flash Attention desligado por estabilidade no Android.
- Apenas um motor pesado fica carregado por vez para evitar falta de RAM.
- Geração de imagem automática quando o pedido é reconhecido no próprio chat.
- Download retomável e até 3 tentativas automáticas em falhas de rede.
- Botão parar/cancelar geração.

## Motores
- Qwen3 1.7B: chat rápido, texto e código.
- Qwen3 4B: maior qualidade e raciocínio.
- Gemma 3 4B Vision: análise de fotos.
- Stable Diffusion 1.5 Q4: geração de imagens local.
- Whisper Base Q8: voz para texto.

## Limitações
- Modelos precisam ser baixados uma vez pela internet.
- Modelos 4B continuam exigindo bastante RAM; trocar de motor leva alguns segundos porque o anterior é descarregado.
- Stable Diffusion local é mais lento que serviços em nuvem; modo Rápido usa 384×384/10 passos e Qualidade 512×512/16 passos.
- Pesquisa web e informações em tempo real não funcionam offline.
- PDFs/DOCX e análise de vídeo ainda não estão integrados nesta versão.
