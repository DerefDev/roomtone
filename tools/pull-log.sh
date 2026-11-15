#!/bin/sh
# Scarica il log raw di Shazam dal telefono in ./logs/
mkdir -p logs
adb pull /sdcard/Android/data/dev.deref.roomtone/files/shazam_raw.jsonl ./logs/
adb pull /sdcard/Android/data/dev.deref.roomtone/files/listens.jsonl ./logs/
