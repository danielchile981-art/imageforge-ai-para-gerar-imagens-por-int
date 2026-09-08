"""Gerador de imagens com Stable Diffusion 2.1-base – versão detalhada e segura.

Este script carrega o modelo oficial stabilityai/stable-diffusion-2-1-base,
aplica o filtro de segurança padrão da Hugging Face e gera imagens a partir
de texto digitado pelo usuário. Todo o processo é local e gratuito.
"""

# ================================================================
# Instalação (execute no terminal apenas uma vez):
# python -m pip install --upgrade torch diffusers transformers accelerate pillow gradio
#
# Autenticação (se necessário):
# 1. Acesse https://huggingface.co/stabilityai/stable-diffusion-2-1-base
# 2. Aceite a licença
# 3. Execute: hf auth login
# Nunca coloque o token neste arquivo.
# ================================================================

import sys
from pathlib import Path
from typing import Optional

import torch
from diffusers import StableDiffusionPipeline
from diffusers.pipelines.stable_diffusion import StableDiffusionSafetyChecker
from PIL import Image
from transformers import CLIPImageProcessor

MODEL_ID = "stabilityai/stable-diffusion-2-1-base"
SAFETY_CHECKER_ID = "CompVis/stable-diffusion-safety-checker"
ARQUIVO_PADRAO = "imagem_gerada.png"

DEVICE = torch.device("cuda" if torch.cuda.is_available() else "cpu")
DTYPE = torch.float16 if DEVICE.type == "cuda" else torch.float32

print(f"Dispositivo selecionado: {DEVICE}")
print(f"Precisão numérica: {DTYPE}")

if DEVICE.type == "cpu":
    print(
        "⚠️ Aviso: nenhuma GPU CUDA detectada. A geração em CPU será "
        "significativamente mais lenta e consumirá bastante memória RAM."
    )

pipeline: Optional[StableDiffusionPipeline] = None


class ConteudoBloqueadoError(RuntimeError):
    """Exceção lançada quando o filtro de segurança bloqueia a imagem."""


def carregar_pipeline() -> StableDiffusionPipeline:
    """Carrega o modelo Stable Diffusion 2.1-base com filtro de segurança obrigatório.

    O checkpoint SD 2.1-base não inclui o safety checker no model_index.json.
    Por isso o verificador padrão CompVis/stable-diffusion-safety-checker é
    carregado explicitamente junto com o CLIPImageProcessor. Se qualquer um
    dos dois componentes falhar, a execução é abortada (política de falha segura).
    """
    print("\nCarregando o filtro de segurança (StableDiffusionSafetyChecker)...")
    safety_checker = StableDiffusionSafetyChecker.from_pretrained(
        SAFETY_CHECKER_ID,
        torch_dtype=DTYPE,
    )
    feature_extractor = CLIPImageProcessor.from_pretrained(SAFETY_CHECKER_ID)

    print("Carregando o modelo Stable Diffusion 2.1-base...")
    pipe = StableDiffusionPipeline.from_pretrained(
        MODEL_ID,
        torch_dtype=DTYPE,
        safety_checker=safety_checker,
        feature_extractor=feature_extractor,
        requires_safety_checker=True,
    )

    if pipe.safety_checker is None or pipe.feature_extractor is None:
        raise RuntimeError(
            "Filtro de segurança não carregado corretamente. Execução cancelada."
        )

    pipe = pipe.to(DEVICE)
    print("✅ Modelo e filtro de segurança carregados com sucesso.\n")
    return pipe


def normalizar_nome_png(nome_arquivo: str) -> Path:
    """Garante que o arquivo de saída termine exatamente com a extensão .png."""
    nome_limpo = nome_arquivo.strip()
    if not nome_limpo:
        raise ValueError("O nome do arquivo não pode estar vazio.")

    caminho = Path(nome_limpo).expanduser()

    if not caminho.name or caminho.name in {".", ".."}:
        raise ValueError("Informe um nome de arquivo válido.")

    if caminho.suffix:
        caminho = caminho.with_suffix(".png")
    else:
        caminho = Path(f"{caminho}.png")

    return caminho


def gerar_imagem(prompt: str, nome_arquivo: str = ARQUIVO_PADRAO) -> Path:
    """Gera uma imagem a partir do texto e salva em PNG com verificação de segurança."""
    if pipeline is None:
        raise RuntimeError("O pipeline ainda não foi carregado.")

    prompt_limpo = prompt.strip()
    if not prompt_limpo:
        raise ValueError("O prompt não pode estar vazio.")

    caminho_saida = normalizar_nome_png(nome_arquivo)
    print(f"Gerando imagem para o prompt: {prompt_limpo!r}...")

    with torch.no_grad():
        resultado = pipeline(
            prompt=prompt_limpo,
            guidance_scale=7.5,
            num_inference_steps=30,
        )

    deteccoes = resultado.nsfw_content_detected
    if deteccoes is None or len(deteccoes) == 0:
        raise RuntimeError(
            "O filtro de segurança não retornou resultado. A imagem não será salva."
        )

    if bool(deteccoes[0]):
        raise ConteudoBloqueadoError(
            "Conteúdo bloqueado pelo filtro de segurança. A imagem não foi salva."
        )

    if not resultado.images:
        raise RuntimeError("O pipeline não retornou nenhuma imagem.")

    imagem = resultado.images[0]
    if not isinstance(imagem, Image.Image):
        raise RuntimeError("O resultado não é uma imagem PIL válida.")

    caminho_saida.parent.mkdir(parents=True, exist_ok=True)
    imagem.save(caminho_saida, format="PNG")

    caminho_absoluto = caminho_saida.resolve()
    print(f"✅ Imagem salva com sucesso em: {caminho_absoluto}\n")
    return caminho_absoluto


def main() -> int:
    """Loop interativo principal do gerador."""
    global pipeline

    print("=== Gerador de Imagens – Stable Diffusion 2.1-base (gratuito) ===")

    try:
        pipeline = carregar_pipeline()
    except KeyboardInterrupt:
        print("\nCarregamento cancelado pelo usuário.")
        return 130
    except Exception as erro:
        print(f"\n❌ Falha ao carregar o modelo: {erro}")
        print(
            "Se aparecer erro 401/403, aceite a licença no Hugging Face "
            "e execute 'hf auth login'."
        )
        return 1

    while True:
        try:
            texto = input("Digite o prompt (ou 'sair' para encerrar): ").strip()

            if texto.casefold() in {"sair", "exit", "quit"}:
                print("Encerrando o programa...")
                return 0

            if not texto:
                print("⚠️ O prompt não pode estar vazio. Tente novamente.\n")
                continue

            nome = input(
                f"Nome do arquivo de saída [padrão: {ARQUIVO_PADRAO}]: "
            ).strip()
            if not nome:
                nome = ARQUIVO_PADRAO

            gerar_imagem(texto, nome)

        except ConteudoBloqueadoError as erro:
            print(f"⚠️ {erro}\n")
        except ValueError as erro:
            print(f"⚠️ Entrada inválida: {erro}\n")
        except torch.cuda.OutOfMemoryError:
            print(
                "❌ Memória da GPU insuficiente. Feche outros programas ou "
                "use uma GPU com mais VRAM.\n"
            )
            torch.cuda.empty_cache()
        except (EOFError, KeyboardInterrupt):
            print("\nEncerrando o programa...")
            return 0
        except OSError as erro:
            print(f"❌ Erro de arquivo: {erro}\n")
        except Exception as erro:
            print(f"❌ Falha durante a geração: {erro}\n")


if __name__ == "__main__":
    sys.exit(main())
