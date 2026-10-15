#!/usr/bin/env python3
"""
Pemeriksa kebersihan teks untuk sumber Kotlin.

Mendeteksi sisa-sisa kesalahan penulisan yang sering muncul saat kode
dihasilkan otomatis: karakter CJK/Thailand yang menyelinap, dan potongan kata
asing yang menempel pada kata Indonesia.

Pakai:
    python3 tools/ceksumber.py app/src/main/java/com/arsipsenja/util
"""
from __future__ import annotations

import io
import re
import sys
import unicodedata
from pathlib import Path

# Rentang blokNon-Latin yang tidak pernah muncul dalam komentar Indonesia:
# CJK, Hiragana/Katakana, Hangul, Thai, Cyrillic, Arab, Hebrew, Devanagari.
RENTANG_AWAL = [
    (0x1100, 0x11FF),    # Hangul Jamo
    (0x2E80, 0x2EFF),    # CJK Radikal
    (0x3000, 0x303F),    # CJK Simbol & Hiragana/Katakana
    (0x3040, 0x30FF),    # Hiragana & Katakana
    (0x3100, 0x312F),    # Bopomofo
    (0x3130, 0x318F),    # Hangul Kompatibilitas
    (0x31F0, 0x31FF),    # Katakana Ekstensi
    (0x4E00, 0x9FFF),    # CJK Unified
    (0xA960, 0xA97F),    # Hangul Jamo Lama
    (0xAC00, 0xD7AF),    # Hangul Silaba
    (0xF900, 0xFAFF),    # CJK Kompatibilitas
    (0xFE30, 0xFE4F),    # CJK Bentuk Kompatibilitas
    (0xFF00, 0xFF60),    # Bentuk Lebar Penuh
    (0xFFE0, 0xFFE6),    # Simbol Bentuk Lebar Penuh
    (0x0400, 0x04FF),    # Cyrillic
    (0x0590, 0x05FF),    # Hebrew
    (0x0600, 0x06FF),    # Arab
    (0x0E00, 0x0E7F),    # Thai
    (0x0900, 0x097F),    # Devanagari
    (0x0370, 0x03FF),    # Yunani
]

# Kata asing/aneh yang pernah muncul akibat kesalahan penulisan.
KATA_AWAM = [
    "zachastanov", "LOGIKA", "feng", "shu", "间的", "前的", "尾巴",
    "骨干", "读完", "Typically", "adegan", "Fotos", "chuj", "kesme",
]


def dalam_rentang_asing(kode: int) -> bool:
    return any(awal <= kode <= akhir for awal, akhir in RENTANG_AWAL)


def pindai_teks(teks: str, nama_berkas: str) -> list[str]:
    masalah: list[str] = []
    for nomor, baris in enumerate(teks.splitlines(), start=1):
        for indeks, karakter in enumerate(baris):
            kode = ord(karakter)
            if dalam_rentang_asing(kode):
                nama_karakter = unicodedata.name(karakter, "TIDAK DIKENAL")
                masalah.append(
                    f"{nama_berkas}:{nomor}: karakter asing "
                    f"U+{kode:04X} {karakter!r} ({nama_karakter})"
                )
    for nomor, baris in enumerate(teks.splitlines(), start=1):
        for kata in KATA_AWAM:
            if kata in baris:
                masalah.append(f"{nama_berkas}:{nomor}: kata mencurigakan {kata!r}")
    return masalah


def main(argv: list[str]) -> int:
    if len(argv) < 2:
        print("Pakai: ceksumber.py <folder> [folder...]", file=sys.stderr)
        return 2
    semua: list[str] = []
    for argumen in argv[1:]:
        jalur = Path(argumen)
        berkas = sorted(jalur.rglob("*.kt")) if jalur.is_dir() else [jalur]
        for berkas_kotlin in berkas:
            isi = io.open(berkas_kotlin, encoding="utf-8").read()
            semua.extend(pindai_teks(isi, str(berkas_kotlin)))

    if semua:
        print(f"DITEMUKAN {len(semua)} MASALAH:")
        for pesan in semua:
            print("  " + pesan)
        return 1
    print("BERSIH: tidak ada karakter asing atau kata mencurigakan.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))