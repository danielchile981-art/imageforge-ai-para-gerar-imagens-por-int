#!/data/data/com.termux/files/usr/bin/bash
set -e

REPO_URL="https://github.com/danielchile981-art/imageforge-ai-para-gerar-imagens-por-int.git"
UBUNTU_NAME="ubuntu"
APP_DIR="/root/imagemlivre-ai"

printf '\n=== ImagemLivre AI - instalador Termux ===\n'
printf '1/5 Atualizando Termux e instalando PRoot-Distro...\n'
pkg update -y
pkg install -y proot-distro git curl

if ! proot-distro list | grep -qi "ubuntu"; then
  printf '\n2/5 Instalando Ubuntu dentro do Termux...\n'
  proot-distro install ubuntu
else
  printf '\n2/5 Ubuntu ja instalado.\n'
fi

printf '\n3/5 Preparando Python e dependencias no Ubuntu...\n'
proot-distro login "$UBUNTU_NAME" -- bash -lc "
set -e
apt update
DEBIAN_FRONTEND=noninteractive apt install -y python3 python3-venv python3-pip git curl ca-certificates libgomp1 libopenblas0

if [ ! -d '$APP_DIR/.git' ]; then
  git clone '$REPO_URL' '$APP_DIR'
else
  cd '$APP_DIR'
  git pull --ff-only || true
fi

cd '$APP_DIR/python-generator'
python3 -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip setuptools wheel

printf '\nInstalando PyTorch CPU oficial para Linux ARM64...\n'
python -m pip install --index-url https://download.pytorch.org/whl/cpu torch

printf '\nInstalando Diffusers, Transformers, Gradio e demais dependencias...\n'
python -m pip install --upgrade diffusers transformers accelerate pillow gradio huggingface_hub safetensors

python - <<'PY'
import platform
import torch
print('Arquitetura:', platform.machine())
print('PyTorch:', torch.__version__)
print('CUDA disponivel:', torch.cuda.is_available())
PY
"

printf '\n4/5 Criando atalho para iniciar o servidor...\n'
cat > "$HOME/iniciar-imagemlivre.sh" <<'EOF'
#!/data/data/com.termux/files/usr/bin/bash
exec proot-distro login ubuntu -- bash -lc 'cd /root/imagemlivre-ai/python-generator && source .venv/bin/activate && python app.py'
EOF
chmod +x "$HOME/iniciar-imagemlivre.sh"

printf '\n5/5 Concluido.\n'
printf '\nPara iniciar depois, rode:\n  ~/iniciar-imagemlivre.sh\n'
printf '\nNo APK, mantenha a URL:\n  http://127.0.0.1:7860\n'
printf '\nNa primeira execucao o Stable Diffusion 2.1-base ainda precisara baixar varios GB.\n'
printf 'Se o Hugging Face pedir autenticacao, entre no Ubuntu e rode: hf auth login\n\n'
