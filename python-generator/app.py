"""Interface web Gradio para o gerador Stable Diffusion 2.1-base.

Usa exatamente o mesmo pipeline e o mesmo safety checker de gerador_imagens.py.
Nenhuma imagem é exibida se o filtro de segurança bloquear o resultado.
"""

from pathlib import Path
import uuid

import gradio as gr
from PIL import Image

import gerador_imagens as core


PASTA_SAIDA = Path("imagens_geradas")
PASTA_SAIDA.mkdir(parents=True, exist_ok=True)


def inicializar_modelo():
    """Carrega o pipeline uma única vez, preservando o safety checker obrigatório."""
    if core.pipeline is None:
        core.pipeline = core.carregar_pipeline()


def gerar_no_gradio(prompt: str):
    """Gera uma imagem segura e retorna imagem + arquivo PNG para download."""
    prompt = (prompt or "").strip()
    if not prompt:
        raise gr.Error("Digite uma descrição para gerar a imagem.")

    try:
        inicializar_modelo()
        nome = PASTA_SAIDA / f"imagem_{uuid.uuid4().hex[:10]}.png"
        caminho = core.gerar_imagem(prompt, str(nome))
        imagem = Image.open(caminho).copy()
        return imagem, str(caminho), "✅ Imagem gerada e aprovada pelo filtro de segurança."
    except core.ConteudoBloqueadoError:
        raise gr.Error(
            "Conteúdo bloqueado pelo filtro de segurança. Nenhuma imagem foi exibida ou salva."
        )
    except Exception as erro:
        raise gr.Error(f"Falha ao gerar a imagem: {erro}")


CSS = """
:root { --bg:#070b14; --panel:#0f172a; --border:#283548; --text:#f8fafc; --muted:#94a3b8; }
body, .gradio-container { background:radial-gradient(circle at top right,#1f1947 0%,#0b1020 32%,#070b14 72%) !important; color:var(--text)!important; }
.gradio-container { max-width:1050px!important; margin:0 auto!important; padding:22px!important; }
#hero { background:linear-gradient(135deg,#111827 0%,#312e81 55%,#7c3aed 100%); border:1px solid rgba(255,255,255,.08); border-radius:28px; padding:28px 30px; box-shadow:0 24px 70px rgba(0,0,0,.30); margin-bottom:18px; }
#hero h1 { font-size:clamp(30px,5vw,54px); line-height:1.02; margin:8px 0 10px; letter-spacing:-.04em; }
#hero p { color:#e2e8f0; margin:0; } #hero .eyebrow { color:#ddd6fe; font-weight:800; letter-spacing:.12em; font-size:12px; }
.card { background:rgba(15,23,42,.92)!important; border:1px solid var(--border)!important; border-radius:22px!important; padding:18px!important; box-shadow:0 16px 50px rgba(0,0,0,.18); }
#generate-btn { min-height:52px; font-weight:800; border-radius:16px!important; background:linear-gradient(135deg,#6d28d9,#8b5cf6)!important; border:none!important; box-shadow:0 10px 28px rgba(124,58,237,.30); }
textarea,input { background:#0b1220!important; color:var(--text)!important; border-color:#334155!important; border-radius:14px!important; }
#result-image { border-radius:18px!important; overflow:hidden; min-height:380px; }
footer { display:none!important; }
"""

with gr.Blocks(title="ImagemLivre AI", css=CSS, theme=gr.themes.Base()) as demo:
    gr.HTML("""
    <section id="hero">
      <div class="eyebrow">✦ IMAGEMLIVRE AI</div>
      <h1>Transforme texto em imagem.</h1>
      <p>Stable Diffusion 2.1-base • geração local • safety checker sempre ativo</p>
    </section>
    """)

    with gr.Row(equal_height=False):
        with gr.Column(scale=5, elem_classes=["card"]):
            gr.Markdown("### Descreva sua ideia")
            prompt = gr.Textbox(
                label="Prompt",
                placeholder="Ex.: retrato cinematográfico de um astronauta em uma cidade futurista, luz neon, fotografia profissional, alto nível de detalhes",
                lines=7,
            )
            gerar = gr.Button("✦ Gerar imagem", variant="primary", elem_id="generate-btn")
            gr.Markdown("**Dica:** descreva assunto, ambiente, iluminação, estilo e nível de detalhe.")
            gr.Examples(
                examples=[
                    ["Uma cabana moderna no meio de uma floresta com neblina, amanhecer, fotografia arquitetônica, ultra detalhada"],
                    ["Carro esportivo vermelho em uma avenida futurista à noite, reflexos no asfalto molhado, iluminação cinematográfica"],
                    ["Ilustração de uma ilha flutuante sobre as nuvens, cachoeiras, fantasia épica, luz dourada, arte digital detalhada"],
                ],
                inputs=[prompt],
                label="Ideias rápidas",
            )

        with gr.Column(scale=6, elem_classes=["card"]):
            gr.Markdown("### Resultado")
            imagem = gr.Image(label="Imagem gerada", type="pil", elem_id="result-image")
            status = gr.Textbox(label="Status", interactive=False)
            arquivo = gr.File(label="Baixar PNG")

    gerar.click(fn=gerar_no_gradio, inputs=[prompt], outputs=[imagem, arquivo, status])


if __name__ == "__main__":
    demo.queue().launch(server_name="0.0.0.0", server_port=7860, share=False)
