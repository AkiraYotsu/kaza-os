#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <dirent.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <time.h>
#include <unistd.h>

#define COLOR_RESET   "\033[0m"
#define COLOR_WHITE   "\033[1;37m"
#define COLOR_GREEN   "\033[1;32m"
#define COLOR_BLUE    "\033[1;34m"
#define COLOR_RED     "\033[1;31m"
#define COLOR_GRAY    "\033[1;30m"

static char current_dir[1024];

void print_header() {
    printf("%s====================================================%s\n", COLOR_GREEN, COLOR_RESET);
    printf("%s     KAZA OS v1.1 — Standalone POSIX Terminal Engine %s\n", COLOR_GREEN, COLOR_RESET);
    printf("%s====================================================%s\n", COLOR_GREEN, COLOR_RESET);
    printf("%sType 'help' to view all available commands.%s\n\n", COLOR_WHITE, COLOR_RESET);
}

void get_prompt_path(char *out, size_t maxlen) {
    const char *home = getenv("HOME");
    if (home && strncmp(current_dir, home, strlen(home)) == 0) {
        snprintf(out, maxlen, "~%s", current_dir + strlen(home));
    } else {
        snprintf(out, maxlen, "%s", current_dir);
    }
}

void cmd_pwd() {
    printf("%s%s%s\n\n", COLOR_WHITE, current_dir, COLOR_RESET);
}

void cmd_cd(const char *arg) {
    if (strlen(arg) == 0 || strcmp(arg, "~") == 0) {
        const char *home = getenv("HOME");
        if (!home) home = "/sdcard/TrKZ";
        if (chdir(home) == 0) {
            getcwd(current_dir, sizeof(current_dir));
            printf("%s%s%s\n\n", COLOR_GRAY, current_dir, COLOR_RESET);
        } else {
            printf("%sERROR: Cannot navigate to home directory.%s\n\n", COLOR_RED, COLOR_RESET);
        }
        return;
    }

    if (chdir(arg) == 0) {
        getcwd(current_dir, sizeof(current_dir));
        printf("%s%s%s\n\n", COLOR_GRAY, current_dir, COLOR_RESET);
    } else {
        printf("%sERROR: Directory '%s' not found or inaccessible.%s\n\n", COLOR_RED, arg, COLOR_RESET);
    }
}

void cmd_li(const char *arg) {
    const char *target = (strlen(arg) == 0) ? current_dir : arg;
    DIR *d = opendir(target);
    if (!d) {
        printf("%sERROR: Directory '%s' not found or inaccessible.%s\n\n", COLOR_RED, target, COLOR_RESET);
        return;
    }

    printf("%sDirectory listing for: %s%s\n", COLOR_GREEN, target, COLOR_RESET);
    struct dirent *dir;
    int count = 0;
    while ((dir = readdir(d)) != NULL) {
        if (strcmp(dir->d_name, ".") == 0 || strcmp(dir->d_name, "..") == 0) continue;
        count++;
        char fullpath[2048];
        snprintf(fullpath, sizeof(fullpath), "%s/%s", target, dir->d_name);
        struct stat st;
        if (stat(fullpath, &st) == 0) {
            if (S_ISDIR(st.st_mode)) {
                printf("%s[DIR]  %s/%s\n", COLOR_BLUE, dir->d_name, COLOR_RESET);
            } else if (st.st_mode & S_IXUSR) {
                printf("%s[EXEC] %s%s\n", COLOR_GREEN, dir->d_name, COLOR_RESET);
            } else {
                printf("%s[FILE] %s %s(%off_t B)%s\n", COLOR_WHITE, dir->d_name, COLOR_GRAY, st.st_size, COLOR_RESET);
            }
        } else {
            printf("%s[FILE] %s%s\n", COLOR_WHITE, dir->d_name, COLOR_RESET);
        }
    }
    closedir(d);
    printf("%sTotal entries: %d%s\n\n", COLOR_GRAY, count, COLOR_RESET);
}

void cmd_read(const char *arg) {
    if (strlen(arg) == 0) {
        printf("%sERROR: Usage: read <filepath>%s\n\n", COLOR_RED, COLOR_RESET);
        return;
    }
    FILE *f = fopen(arg, "r");
    if (!f) {
        printf("%sERROR: File '%s' not found or cannot be opened.%s\n\n", COLOR_RED, arg, COLOR_RESET);
        return;
    }
    printf("%s--- Content of %s ---%s\n", COLOR_GREEN, arg, COLOR_RESET);
    char line[1024];
    while (fgets(line, sizeof(line), f)) {
        printf("%s%s%s", COLOR_WHITE, line, COLOR_RESET);
    }
    fclose(f);
    printf("\n%s--- End of file ---%s\n\n", COLOR_GREEN, COLOR_RESET);
}

void cmd_write(const char *arg) {
    char filepath[512] = {0};
    char text[1024] = {0};
    if (sscanf(arg, "%511s %[^\n]", filepath, text) < 1) {
        printf("%sERROR: Usage: write <filepath> <content>%s\n\n", COLOR_RED, COLOR_RESET);
        return;
    }
    FILE *f = fopen(filepath, "a");
    if (!f) {
        printf("%sERROR: Cannot write to file '%s'.%s\n\n", COLOR_RED, filepath, COLOR_RESET);
        return;
    }
    fprintf(f, "%s\n", text);
    fclose(f);
    printf("%sSUCCESS: Data written to '%s'%s\n\n", COLOR_GREEN, filepath, COLOR_RESET);
}

void cmd_mkdir(const char *arg) {
    if (strlen(arg) == 0) {
        printf("%sERROR: Usage: mkdir <dirpath>%s\n\n", COLOR_RED, COLOR_RESET);
        return;
    }
    if (mkdir(arg, 0755) == 0) {
        printf("%sSUCCESS: Directory '%s' created.%s\n\n", COLOR_GREEN, arg, COLOR_RESET);
    } else {
        printf("%sERROR: Could not create directory '%s'.%s\n\n", COLOR_RED, arg, COLOR_RESET);
    }
}

void cmd_rm(const char *arg) {
    if (strlen(arg) == 0) {
        printf("%sERROR: Usage: rm <filepath>%s\n\n", COLOR_RED, COLOR_RESET);
        return;
    }
    if (remove(arg) == 0) {
        printf("%sSUCCESS: File/Directory '%s' removed.%s\n\n", COLOR_GREEN, arg, COLOR_RESET);
    } else {
        printf("%sERROR: Could not remove '%s'.%s\n\n", COLOR_RED, arg, COLOR_RESET);
    }
}

void cmd_calc(const char *arg) {
    double a, b;
    char op;
    if (sscanf(arg, "%lf %c %lf", &a, &op, &b) == 3) {
        double res = 0;
        if (op == '+') res = a + b;
        else if (op == '-') res = a - b;
        else if (op == '*') res = a * b;
        else if (op == '/') {
            if (b == 0) { printf("%sERROR: Division by zero.%s\n\n", COLOR_RED, COLOR_RESET); return; }
            res = a / b;
        }
        printf("%sRESULT: %.2f %c %.2f = %.2f%s\n\n", COLOR_GREEN, a, op, b, res, COLOR_RESET);
    } else {
        printf("%sERROR: Invalid expression. Example: calc 25 * 4%s\n\n", COLOR_RED, COLOR_RESET);
    }
}

void cmd_time() {
    time_t t = time(NULL);
    struct tm tm = *localtime(&t);
    printf("%sSystem Time: %04d-%02d-%02d %02d:%02d:%02d%s\n\n", COLOR_GREEN,
           tm.tm_year + 1900, tm.tm_mon + 1, tm.tm_mday,
           tm.tm_hour, tm.tm_min, tm.tm_sec, COLOR_RESET);
}

void cmd_trkz() {
    const char *path = "/sdcard/TrKZ";
    mkdir(path, 0755);
    if (chdir(path) == 0) {
        getcwd(current_dir, sizeof(current_dir));
        printf("%s[TrKZ FIELD] Workspace set to '%s'%s\n", COLOR_GREEN, current_dir, COLOR_RESET);
        cmd_li("");
    } else {
        printf("%sERROR: Inaccessible TrKZ Field path '%s'.%s\n\n", COLOR_RED, path, COLOR_RESET);
    }
}

void cmd_help() {
    printf("%sAvailable Commands (Kaza OS v1.1):%s\n", COLOR_GREEN, COLOR_RESET);
    printf("  pwd              - Print current working directory\n");
    printf("  cd <path>        - Change directory\n");
    printf("  li / ls [path]   - List directory contents (Folders in BLUE)\n");
    printf("  read / cat <file>- Read file contents\n");
    printf("  write <file> <t> - Write text to file\n");
    printf("  mkdir <dir>      - Create directory\n");
    printf("  rm <file>        - Remove file/directory\n");
    printf("  calc <expression>- System calculator\n");
    printf("  time / date      - System clock\n");
    printf("  trkz             - Switch to /sdcard/TrKZ workspace\n");
    printf("  clear            - Clear terminal screen\n");
    printf("  exit / halt      - Terminate shell session\n\n");
}

int main() {
    getcwd(current_dir, sizeof(current_dir));
    print_header();

    char input[1024];
    while (1) {
        char prompt_path[512];
        get_prompt_path(prompt_path, sizeof(prompt_path));
        printf("%skaza@kernel:%s$ %s", COLOR_GREEN, prompt_path, COLOR_RESET);
        if (!fgets(input, sizeof(input), stdin)) break;

        input[strcspn(input, "\r\n")] = 0;
        char *clean = input;
        while (*clean == ' ') clean++;
        if (*clean == '/') clean++;
        if (strlen(clean) == 0) continue;

        char cmd[128] = {0};
        char args[896] = {0};
        sscanf(clean, "%127s %[^\n]", cmd, args);

        if (strcmp(cmd, "pwd") == 0) cmd_pwd();
        else if (strcmp(cmd, "cd") == 0) cmd_cd(args);
        else if (strcmp(cmd, "li") == 0 || strcmp(cmd, "ls") == 0) cmd_li(args);
        else if (strcmp(cmd, "read") == 0 || strcmp(cmd, "cat") == 0) cmd_read(args);
        else if (strcmp(cmd, "write") == 0) cmd_write(args);
        else if (strcmp(cmd, "mkdir") == 0) cmd_mkdir(args);
        else if (strcmp(cmd, "rm") == 0) cmd_rm(args);
        else if (strcmp(cmd, "calc") == 0) cmd_calc(args);
        else if (strcmp(cmd, "time") == 0 || strcmp(cmd, "date") == 0) cmd_time();
        else if (strcmp(cmd, "trkz") == 0) cmd_trkz();
        else if (strcmp(cmd, "clear") == 0) printf("\033[H\033[J");
        else if (strcmp(cmd, "exit") == 0 || strcmp(cmd, "halt") == 0) {
            printf("%s[KAZA OS] Session terminated.%s\n", COLOR_RED, COLOR_RESET);
            break;
        }
        else if (strcmp(cmd, "help") == 0) cmd_help();
        else {
            printf("%sERROR: Command '%s' not found. Type 'help' for command list.%s\n\n", COLOR_RED, clean, COLOR_RESET);
        }
    }
    return 0;
}
