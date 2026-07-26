#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <dirent.h>
#include <sys/stat.h>
#include <unistd.h>
#include <time.h>

#define COLOR_RESET   "\033[0m"
#define COLOR_WHITE   "\033[1;37m"
#define COLOR_GREEN   "\033[1;32m"
#define COLOR_RED     "\033[1;31m"
#define COLOR_MUTED   "\033[0;37m"
#define COLOR_YELLOW  "\033[1;33m"

void print_banner(void) {
    printf("%s====================================================\n", COLOR_GREEN);
    printf("     KAZA OS v1.1 — Expanded System Shell           \n");
    printf("====================================================%s\n", COLOR_RESET);
    printf("%sType 'help' or '/' for all available system commands.%s\n\n", COLOR_WHITE, COLOR_RESET);
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
    int count = 0;

    while ((entry = readdir(dir)) != NULL) {
        if (strcmp(entry->d_name, ".") == 0 || strcmp(entry->d_name, "..") == 0) continue;
        snprintf(fullpath, sizeof(fullpath), "%s/%s", path, entry->d_name);
        if (stat(fullpath, &statbuf) == 0) {
            count++;
            if (S_ISDIR(statbuf.st_mode)) {
                printf("%s[DIR]  %s/%s\n", COLOR_GREEN, entry->d_name, COLOR_RESET);
            } else {
                printf("%s[FILE] %s (%ld bytes)%s\n", COLOR_WHITE, entry->d_name, (long)statbuf.st_size, COLOR_RESET);
            }
        }
    }
    closedir(dir);
    printf("%sTotal entries: %d%s\n\n", COLOR_MUTED, count, COLOR_RESET);
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

void cmd_mkdir(const char *path) {
    if (!path || strlen(path) == 0) {
        printf("%sERROR: Usage: mkdir <dirpath>%s\n\n", COLOR_RED, COLOR_RESET);
        return;
    }

    if (mkdir(path, 0755) == 0) {
        printf("%sSUCCESS: Directory '%s' created.%s\n\n", COLOR_GREEN, path, COLOR_RESET);
    } else {
        printf("%sERROR: Could not create directory '%s'. Already exists or permission denied.%s\n\n", COLOR_RED, path, COLOR_RESET);
    }
}

void cmd_rm(const char *path) {
    if (!path || strlen(path) == 0) {
        printf("%sERROR: Usage: rm <filepath>%s\n\n", COLOR_RED, COLOR_RESET);
        return;
    }

    if (remove(path) == 0) {
        printf("%sSUCCESS: File/Directory '%s' removed.%s\n\n", COLOR_GREEN, path, COLOR_RESET);
    } else {
        printf("%sERROR: Could not remove '%s'. File not found or permission denied.%s\n\n", COLOR_RED, path, COLOR_RESET);
    }
}

void cmd_edit(const char *filepath) {
    if (!filepath || strlen(filepath) == 0) {
        printf("%sERROR: Usage: edit <filepath>%s\n\n", COLOR_RED, COLOR_RESET);
        return;
    }

    printf("%s--- KAZA MINI TEXT EDITOR ---%s\n", COLOR_GREEN, COLOR_RESET);
    printf("%sEditing '%s'. Type lines below. Type ':w' on a new line to save & exit.%s\n\n", COLOR_YELLOW, filepath, COLOR_RESET);

    FILE *f = fopen(filepath, "w");
    if (!f) {
        printf("%sERROR: Cannot create/edit file '%s'.%s\n\n", COLOR_RED, filepath, COLOR_RESET);
        return;
    }

    char line[512];
    int line_count = 0;
    while (1) {
        printf("%s%3d | %s", COLOR_MUTED, ++line_count, COLOR_WHITE);
        fflush(stdout);
        if (!fgets(line, sizeof(line), stdin)) break;
        line[strcspn(line, "\n")] = 0;

        if (strcmp(line, ":w") == 0 || strcmp(line, ":wq") == 0) {
            break;
        }

        fprintf(f, "%s\n", line);
    }

    fclose(f);
    printf("\n%sSUCCESS: File '%s' saved successfully.%s\n\n", COLOR_GREEN, filepath, COLOR_RESET);
}

void cmd_find(const char *keyword, const char *base_path) {
    if (!keyword || strlen(keyword) == 0) {
        printf("%sERROR: Usage: find <keyword> [path]%s\n\n", COLOR_RED, COLOR_RESET);
        return;
    }

    if (!base_path || strlen(base_path) == 0) base_path = ".";

    DIR *dir = opendir(base_path);
    if (!dir) {
        printf("%sERROR: Path '%s' inaccessible.%s\n\n", COLOR_RED, base_path, COLOR_RESET);
        return;
    }

    printf("%sSearching for '%s' in '%s'...%s\n", COLOR_GREEN, keyword, base_path, COLOR_RESET);
    struct dirent *entry;
    int matches = 0;

    while ((entry = readdir(dir)) != NULL) {
        if (strstr(entry->d_name, keyword)) {
            matches++;
            printf("%s  -> Found: %s/%s%s\n", COLOR_WHITE, base_path, entry->d_name, COLOR_RESET);
        }
    }
    closedir(dir);
    printf("%sSearch finished. %d match(es) found.%s\n\n", COLOR_MUTED, matches, COLOR_RESET);
}

void cmd_calc(const char *expr) {
    if (!expr || strlen(expr) == 0) {
        printf("%sERROR: Usage: calc <num1> <op> <num2> (e.g. calc 25 * 4)%s\n\n", COLOR_RED, COLOR_RESET);
        return;
    }

    double a, b;
    char op;
    if (sscanf(expr, "%lf %c %lf", &a, &op, &b) == 3) {
        double res = 0;
        if (op == '+') res = a + b;
        else if (op == '-') res = a - b;
        else if (op == '*') res = a * b;
        else if (op == '/') {
            if (b == 0) { printf("%sERROR: Division by zero.%s\n\n", COLOR_RED, COLOR_RESET); return; }
            res = a / b;
        } else {
            printf("%sERROR: Unknown operator '%c'. Supported: +, -, *, /%s\n\n", COLOR_RED, op, COLOR_RESET);
            return;
        }
        printf("%sRESULT: %.2lf %c %.2lf = %.2lf%s\n\n", COLOR_GREEN, a, op, b, res, COLOR_RESET);
    } else {
        printf("%sERROR: Invalid expression format. Example: calc 100 / 4%s\n\n", COLOR_RED, COLOR_RESET);
    }
}

void cmd_time(void) {
    time_t t = time(NULL);
    struct tm *tm_info = localtime(&t);
    char buf[64];
    strftime(buf, sizeof(buf), "%Y-%m-%d %H:%M:%S", tm_info);
    printf("%sSystem Time: %s%s\n\n", COLOR_GREEN, buf, COLOR_RESET);
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
            printf("  li [path]           - List directory contents\n");
            printf("  read / cat <file>   - Read text file content\n");
            printf("  write <file> <txt>  - Append text to file\n");
            printf("  edit <file>         - Launch Kaza mini text editor (:w to save)\n");
            printf("  mkdir <dirpath>     - Create new directory\n");
            printf("  rm <filepath>       - Remove file or directory\n");
            printf("  find <keyword>      - Search files in directory\n");
            printf("  calc <a op b>       - Built-in math calculator (e.g. calc 50 * 4)\n");
            printf("  time / date         - Display system clock\n");
            printf("  clear               - Clear terminal screen\n");
            printf("  exit / halt         - Exit Kaza OS shell\n\n");
        } else if (strncmp(input, "li ", 3) == 0 || strcmp(input, "li") == 0) {
            char *path = (strlen(input) > 3) ? input + 3 : ".";
            cmd_li(path);
        } else if (strncmp(input, "read ", 5) == 0 || strncmp(input, "cat ", 4) == 0) {
            char *path = strncmp(input, "read ", 5) == 0 ? input + 5 : input + 4;
            cmd_read(path);
        } else if (strncmp(input, "write ", 6) == 0) {
            char *args = input + 6;
            char *space = strchr(args, ' ');
            if (space) { *space = 0; cmd_write(args, space + 1); }
            else { cmd_write(args, ""); }
        } else if (strncmp(input, "edit ", 5) == 0) {
            cmd_edit(input + 5);
        } else if (strncmp(input, "mkdir ", 6) == 0) {
            cmd_mkdir(input + 6);
        } else if (strncmp(input, "rm ", 3) == 0) {
            cmd_rm(input + 3);
        } else if (strncmp(input, "find ", 5) == 0) {
            char *args = input + 5;
            char *space = strchr(args, ' ');
            if (space) { *space = 0; cmd_find(args, space + 1); }
            else { cmd_find(args, "."); }
        } else if (strncmp(input, "calc ", 5) == 0) {
            cmd_calc(input + 5);
        } else if (strcmp(input, "time") == 0 || strcmp(input, "date") == 0) {
            cmd_time();
        } else if (strcmp(input, "clear") == 0) {
            printf("\033[H\033[J");
        } else {
            printf("%sERROR: Command '%s' not found. Type 'help' for command list.%s\n\n", COLOR_RED, input, COLOR_RESET);
        }
    }
    return 0;
}
