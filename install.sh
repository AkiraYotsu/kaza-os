#!/bin/bash
# Kaza OS v1.1 — Termux Installer & Launcher Script

echo -e "\033[1;32m====================================================\033[0m"
echo -e "\033[1;32m   Installing Kaza OS v1.1 (TrKZ Field Edition)...  \033[0m"
echo -e "\033[1;32m====================================================\033[0m"

# Setup Android storage permissions
if command -v termux-setup-storage >/dev/null 2>&1; then
    echo -e "\033[1;33m[1/3] Requesting Android Storage Permissions...\033[0m"
    termux-setup-storage
fi

echo -e "\033[1;33m[2/3] Installing build dependencies (gcc, make)...\033[0m"
pkg install -y gcc make 2>/dev/null

mkdir -p ~/.kaza_os
cd ~/.kaza_os

# Fetch latest kaza_cli.c directly with cache busting
curl -sSL "https://raw.githubusercontent.com/AkiraYotsu/kaza-os/main/kaza_cli.c?$(date +%s)" -o kaza.c

echo -e "\033[1;33m[3/3] Compiling Kaza OS Binary...\033[0m"
gcc -O2 kaza.c -o kaza

# Overwrite old binary completely
if [ -d "$PREFIX/bin" ]; then
    cp -f kaza $PREFIX/bin/kaza
    chmod +x $PREFIX/bin/kaza
fi

mkdir -p /sdcard/TrKZ 2>/dev/null

echo -e "\033[1;32m====================================================\033[0m"
echo -e "\033[1;32m SUCCESS: Kaza OS v1.1 (TrKZ Field) Installed!     \033[0m"
echo -e "\033[1;32m Type 'kaza' in Termux to launch Kaza OS!          \033[0m"
echo -e "\033[1;32m====================================================\033[0m"
