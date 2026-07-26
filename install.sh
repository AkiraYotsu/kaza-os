#!/bin/bash
# Kaza OS — Termux Installer & Launcher Script

echo -e "\033[1;32m====================================================\033[0m"
echo -e "\033[1;32m       Installing Kaza OS for Termux...            \033[0m"
echo -e "\033[1;32m====================================================\033[0m"

pkg install -y gcc make 2>/dev/null

mkdir -p ~/.kaza_os
cd ~/.kaza_os

cat << 'EOF' > kaza.c
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <dirent.h>
#include <sys/stat.h>
#include <unistd.h>

#define COLOR_RESET   "\033[0m"
#define COLOR_WHITE   "\033[1;37m"
#define COLOR_GREEN   "\033[1;32m"
#define COLOR_RED     "\033[1;31m"
#define COLOR_MUTED   "\033[0;37m"

void print_banner(void) {
    printf("%s====================================================\n", COLOR_GREEN);
    printf("     KAZA OS v1.0 — Minimalist System Shell         \n");
    printf("====================================================%s\n", COLOR_RESET);
    printf("%sType 'help' or 'li <path>' to inspect directories.%s\n\n", COLOR_WHITE, COLOR_RESET);
}

void cmd_li(const char *path) {
    if (!path || strlen(path) == 0) path = ".";

    DIR *dir = opendir(path);
    if (!dir) {
        printf("%sERROR: Directory '%s' not found or inaccessible.%s\n\n", COLOR_RED, path, COLOR_RESET);
        return;
    }

    printf("%sDirectory listing for: %s%s\n", COLOR_GREEN, path, COLOR_RESET);
    struct dirent *entry;
    struct stat statbuf;
    char fullpath[1024];

    while ((entry = readdir(dir)) != NULL) {
        if (strcmp(entry->d_name, ".") == 0 || strcmp(entry->d_name, "..") == 0) continue;
        snprintf(fullpath, sizeof(fullpath), "%s/%s", path, entry->d_name);
        if (stat(fullpath, &statbuf) == 0) {
            if (S_ISDIR(statbuf.st_mode)) {
                printf("%s[DIR]  %s/%s\n", COLOR_GREEN, entry->d_name, COLOR_RESET);
            } else {
                printf("%s[FILE] %s (%ld bytes)%s\n", COLOR_WHITE, entry->d_name, statbuf.st_size, COLOR_RESET);
            }
        }
    }
    closedir(dir);
    printf("\n");
}

void cmd_read(const char *filepath) {
    if (!filepath || strlen(filepath) == 0) {
        printf("%sERROR: Usage: read <filepath>%s\n\n", COLOR_RED, COLOR_RESET);
        return;
    }

    FILE *f = fopen(filepath, "r");
    if (!f) {
        printf("%sERROR: File '%s' not found or cannot be opened.%s\n\n", COLOR_RED, filepath, COLOR_RESET);
        return;
    }

    printf("%s--- Content of %s ---%s\n", COLOR_GREEN, filepath, COLOR_RESET);
    char buf[512];
    while (fgets(buf, sizeof(buf), f)) printf("%s%s", COLOR_WHITE, buf);
    fclose(f);
    printf("\n%s--- End of file ---%s\n\n", COLOR_GREEN, COLOR_RESET);
}

void cmd_write(const char *filepath, const char *text) {
    if (!filepath || strlen(filepath) == 0) {
        printf("%sERROR: Usage: write <filepath> <text>%s\n\n", COLOR_RED, COLOR_RESET);
        return;
    }

    FILE *f = fopen(filepath, "a");
    if (!f) {
        printf("%sERROR: Cannot write to '%s'. Permission denied or invalid path.%s\n\n", COLOR_RED, filepath, COLOR_RESET);
        return;
    }

    fprintf(f, "%s\n", text ? text : "");
    fclose(f);
    printf("%sSUCCESS: Data written to '%s'%s\n\n", COLOR_GREEN, filepath, COLOR_RESET);
}

int main(void) {
    print_banner();
    char input[1024];

    while (1) {
        printf("%skaza%s@%skernel%s:~$ %s", COLOR_GREEN, COLOR_MUTED, COLOR_WHITE, COLOR_MUTED, COLOR_WHITE);
        fflush(stdout);

        if (!fgets(input, sizeof(input), stdin)) break;
        input[strcspn(input, "\n")] = 0;
        if (strlen(input) == 0) continue;

        if (strcmp(input, "exit") == 0 || strcmp(input, "halt") == 0) {
            printf("%s[KAZA OS] System halted.%s\n", COLOR_RED, COLOR_RESET);
            break;
        } else if (strcmp(input, "help") == 0) {
            printf("%sAvailable Commands:%s\n", COLOR_GREEN, COLOR_RESET);
            printf("  li <path>         - List directory contents (e.g. li /sdcard/Download)\n");
            printf("  read <file>       - Read file content\n");
            printf("  write <file> <txt>- Write/append text to file\n");
            printf("  clear             - Clear screen\n");
            printf("  exit / halt       - Exit Kaza OS shell\n\n");
        } else if (strncmp(input, "li ", 3) == 0 || strcmp(input, "li") == 0) {
            char *path = (strlen(input) > 3) ? input + 3 : ".";
            cmd_li(path);
        } else if (strncmp(input, "read ", 5) == 0) {
            cmd_read(input + 5);
        } else if (strncmp(input, "write ", 6) == 0) {
            char *args = input + 6;
            char *space = strchr(args, ' ');
            if (space) { *space = 0; cmd_write(args, space + 1); }
            else { cmd_write(args, ""); }
        } else if (strcmp(input, "clear") == 0) {
            printf("\033[H\033[J");
        } else {
            printf("%sERROR: Command '%s' not found. Type 'help' for command list.%s\n\n", COLOR_RED, input, COLOR_RESET);
        }
    }
    return 0;
}
EOF

gcc -O2 kaza.c -o kaza
mkdir -p $PREFIX/bin
cp kaza $PREFIX/bin/kaza
chmod +x $PREFIX/bin/kaza

echo -e "\033[1;32m====================================================\033[0m"
echo -e "\033[1;32m SUCCESS: Kaza OS installed in Termux!             \033[0m"
echo -e "\033[1;32m Type 'kaza' anytime in Termux to launch Kaza OS!   \033[0m"
echo -e "\033[1;32m====================================================\033[0m"
