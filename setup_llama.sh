#!/bin/bash
# Script d'initialisation : clone llama.cpp pour la compilation native

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LLAMA_DIR="$SCRIPT_DIR/llama.cpp"

if [ -d "$LLAMA_DIR" ]; then
    echo "llama.cpp déjà présent, mise à jour..."
    cd "$LLAMA_DIR" && git pull
else
    echo "Clonage de llama.cpp..."
    git clone --depth 1 https://github.com/ggerganov/llama.cpp.git "$LLAMA_DIR"
fi

echo ""
echo "=== Setup terminé ==="
echo "Vous pouvez maintenant builder le projet avec : ./gradlew assembleDebug"
echo ""
echo "Pour le modèle Qwen3-0.6B, deux options :"
echo "  1. Téléchargement in-app (nécessite WiFi, ~400 Mo)"
echo "  2. Sideload via ADB :"
echo "     wget https://huggingface.co/Qwen/Qwen3-0.6B-GGUF/resolve/main/qwen3-0.6b-q4_k_m.gguf"
echo "     adb push qwen3-0.6b-q4_k_m.gguf /data/local/tmp/"
echo "     adb shell run-as com.identifiant.ordoread mkdir -p files/models"
echo "     adb shell run-as com.identifiant.ordoread cp /data/local/tmp/qwen3-0.6b-q4_k_m.gguf files/models/"
