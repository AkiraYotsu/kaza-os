# Kaza OS v1.1 — Operating System Architecture & Standalone TrKZ Terminal

Kaza OS adalah proyek sistem operasi minimalis yang mencakup kernel bare-metal x86 (multiboot ELF), simulator konsol berbasis web, dan aplikasi terminal Android mandiri bernama TrKZ (Terminal Kaza OS).

Proyek ini dirancang dengan prinsip desain ultra-minimalis (kombinasi warna Hitam, Putih, Hijau Matriks, Biru Direktori, dan Merah Error) tanpa menggunakan elemen emoji sama sekali.

---

## Spesifikasi & Fitur Utama

- Kernel Bare-Metal: Agos Kernel x86 (18 KB) dengan VGA Video Driver (0xB8000), IDT 256 Gates, dan Keyboard Handler.
- Aplikasi Standalone Android: TrKZ Terminal (`com.kazaos.trkz`) berbasis POSIX Process Subsystem.
- Sistem Navigasi File: Perintah `cd`, `pwd`, `li`/`ls` dengan pewarnaan folder berwarna Biru dan status berkas terstruktur secara vertikal.
- Pengolahan Berkas: Perintah `read`/`cat`, `write`, `mkdir`, `rm`, `find`.
- Perkakas Sistem: Kalkulator matematika (`calc`), Jam & Tanggal (`time`/`date`), dan Switcher Workspace (`trkz`).
- Dukungan Multi-Sesi: Pengelolaan beberapa sesi konsol terminal secara bersamaan.
- Antarmuka Terminal: Pengodean font monospace condensed, seleksi teks output, serta deretan tombol pintasan khusus (CTRL, Slash, TAB, ESC, HOME, Panah Navigasi).

---

## Metode Instalasi & Penggunaan

### Metode 1: Instalasi Aplikasi Standalone Android (TrKZ APK)

Aplikasi TrKZ dapat diunduh dan dipasang pada perangkat Android sebagai aplikasi terminal mandiri tanpa memerlukan aplikasi pihak ketiga.

1. Buka halaman GitHub Actions Workflow Kaza OS:
   https://github.com/AkiraYotsu/kaza-os/actions
2. Pilih alur kerja (workflow run) terbaru yang berstatus berhasil (Centang Hijau).
3. Gulir ke bagian bawah halaman hingga menemukan bagian **Artifacts**.
4. Unduh berkas **TrKZ-Terminal-v1.1-debug** dan ekstrak berkas APK tersebut.
5. Pasang berkas `TrKZ.apk` pada perangkat Android Anda.
6. Saat pertama kali dijalankan, berikan izin akses penyimpanan (Storage Permission) agar workspace `/sdcard/TrKZ` otomatis terinisialisasi.

---

### Metode 2: Instalasi via Script Termux (1-Line Command)

Bagi pengguna aplikasi Termux pada Android, Kaza OS dapat dipasang dan dijalankan menggunakan skrip instalasi otomatis berikut:

```bash
curl -sSL https://raw.githubusercontent.com/AkiraYotsu/kaza-os/main/install.sh | bash
```

Setelah proses pengompilasi selesai, jalankan Kaza OS kapan saja melalui Termux dengan mengetikkan perintah:

```bash
kaza
```

---

### Metode 3: Pengujian Kernel Bare-Metal x86 via QEMU

Bagi pengembang yang ingin menguji kernel bare-metal x86 (`agos.elf`) pada emulator hardware:

```bash
# Kompilasi kernel x86 dari kode sumber
nasm -f elf32 kernel/boot.asm -o boot.o
gcc -m32 -c kernel/kernel.c -o kernel.o -ffreestanding -O2 -wall -Wextra
ld -m elf_i386 -T kernel/linker.ld -o agos.elf boot.o kernel.o

# Dijalankan menggunakan QEMU
qemu-system-i386 -kernel agos.elf
```

---

## Daftar Perintah Konsol Kaza OS

| Perintah | Deskripsi Fungsi |
| :--- | :--- |
| `pwd` | Menampilkan direktori kerja saat ini. |
| `cd <path>` | Pindah direktori (contoh: `cd /sdcard/Download` atau `cd ~`). |
| `li` / `ls` | Menampilkan daftar berkas & folder secara vertikal (Folder berwarna Biru). |
| `read` / `cat <file>` | Membaca dan menampilkan isi berkas teks. |
| `write <file> <teks>` | Menulis atau menambahkan teks ke dalam berkas. |
| `mkdir <folder>` | Membuat direktori baru. |
| `rm <berkas>` | Menghapus berkas atau direktori. |
| `find <kata_kunci>` | Mencari berkas dalam direktori berdasarkan kata kunci. |
| `calc <angka1 op angka2>` | Kalkulator matematika bawaan sistem (contoh: `calc 25 * 4`). |
| `time` / `date` | Menampilkan jam dan tanggal sistem saat ini. |
| `trkz` | Berpindah otomatis ke direktori workspace `/sdcard/TrKZ`. |
| `clear` | Membersihkan tampilan layar konsol terminal. |
| `exit` / `halt` | Menghentikan sesi terminal. |

---

## Lisensi & Pengembang

Dikembangkan oleh **AkiraYotsu** sebagai proyek sistem operasi dan terminal standalone untuk Kaza OS v1.1.
