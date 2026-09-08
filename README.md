# ImagemLivre AI

Aplicativo Android para usar um gerador de imagens com **Stable Diffusion 2.1-base**, mantendo o **safety checker ativo**.

## O que há no projeto

- `python-generator/gerador_imagens.py` — núcleo Stable Diffusion 2.1-base + filtro de segurança.
- `python-generator/app.py` — interface Gradio redesenhada.
- `android-wrapper/` — aplicativo Android nativo com visual moderno.
- `.github/workflows/build-apk.yml` — gera o APK automaticamente.

## Visual Android

A versão 1.1 traz:

- tela inicial escura com gradiente roxo;
- nome **ImagemLivre AI**;
- ícone próprio;
- cartão para configurar a URL do gerador;
- URL salva no aparelho;
- botão **Abrir gerador**;
- WebView integrada;
- botão de voltar ao início e recarregar;
- splash e Material 3.

## Rodar o gerador

```bash
cd python-generator
python -m pip install --upgrade -r requirements.txt
python app.py
```

O Gradio escuta em `0.0.0.0:7860`, então um celular na mesma rede pode acessar usando o IP do computador, por exemplo:

```text
http://192.168.0.10:7860
```

Digite essa URL na tela inicial do APK.

## Gerar o APK

Abra a aba **Actions** deste repositório e selecione **Build Android APK**. O workflow gera o artefato:

```text
ImagemLivreAI-debug-apk
```

Dentro dele fica o arquivo `app-debug.apk`.

## Segurança

O código mantém `StableDiffusionSafetyChecker` e `CLIPImageProcessor`. O pipeline cancela a execução se o filtro não estiver carregado corretamente e não salva imagens sinalizadas pelo safety checker.

## Custo

O código não exige API paga. A geração local depende apenas do hardware onde o backend Python estiver rodando.
