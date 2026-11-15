#!/bin/sh
# Scarica il log raw di Shazam dal telefono in ./logs/
mkdir -p logs
adb pull /sdcard/Android/data/dev.deref.vinylkiosk/files/shazam_raw.jsonl ./logs/
