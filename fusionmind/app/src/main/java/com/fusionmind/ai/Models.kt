package com.fusionmind.ai

enum class EngineKind { BRAIN, VISION, IMAGE, VOICE }

data class AiModel(
    val id: String,
    val title: String,
    val subtitle: String,
    val kind: EngineKind,
    val sizeMb: Int,
    val fileName: String,
    val url: String,
    val companionFileName: String? = null,
    val companionUrl: String? = null,
    val companionSizeMb: Int = 0
)

object ModelCatalog {
    val brainFast = AiModel(
        "qwen3_1_7b_fast",
        "Qwen3 1.7B • Rápido",
        "Padrão recomendado • conversa e código com menor uso de RAM",
        EngineKind.BRAIN,
        1280,
        "Qwen3-1.7B-Q4_K_M.gguf",
        "https://huggingface.co/ggml-org/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf?download=true"
    )

    val brainQuality = AiModel(
        "qwen3_4b_quality",
        "Qwen3 4B • Qualidade",
        "Mais inteligente • raciocínio e código, porém mais pesado",
        EngineKind.BRAIN,
        2500,
        "Qwen3-4B-Q4_K_M.gguf",
        "https://huggingface.co/ggml-org/Qwen3-4B-GGUF/resolve/main/Qwen3-4B-Q4_K_M.gguf?download=true"
    )

    // Compatibilidade com a primeira versão.
    val brain = brainFast

    val vision = AiModel(
        "gemma3_4b_vision",
        "Gemma 3 4B Vision",
        "Olhos • entende fotos e imagens",
        EngineKind.VISION,
        2490,
        "gemma-3-4b-it-Q4_K_M.gguf",
        "https://huggingface.co/ggml-org/gemma-3-4b-it-GGUF/resolve/main/gemma-3-4b-it-Q4_K_M.gguf?download=true",
        "mmproj-model-f16.gguf",
        "https://huggingface.co/ggml-org/gemma-3-4b-it-GGUF/resolve/main/mmproj-model-f16.gguf?download=true",
        851
    )

    val image = AiModel(
        "sd15_q4",
        "Stable Diffusion 1.5 Q4",
        "Artista • gera imagens localmente",
        EngineKind.IMAGE,
        1750,
        "stable-diffusion-v1-5-Q4_0.gguf",
        "https://huggingface.co/gpustack/stable-diffusion-v1-5-GGUF/resolve/main/stable-diffusion-v1-5-Q4_0.gguf?download=true"
    )

    val voice = AiModel(
        "whisper_base_q8",
        "Whisper Base Q8",
        "Ouvidos • voz para texto offline",
        EngineKind.VOICE,
        82,
        "ggml-base-q8_0.bin",
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q8_0.bin?download=true"
    )

    val all = listOf(brainFast, brainQuality, vision, image, voice)
}
