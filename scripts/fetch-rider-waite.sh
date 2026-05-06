#!/bin/bash
# Downloads all 78 Rider-Waite-Smith (1909) tarot card scans from Wikimedia
# Commons (public domain). Pulls 600px-wide JPEG thumbnails to keep the bundled
# APK small.
#
# Usage: bash scripts/fetch-rider-waite.sh

set -u

DEST="C:/TarotApp/app/src/main/assets/decks/rider-waite"
WIDTH=600
SLEEP="0.05"  # tiny delay between requests to be polite

mkdir -p "$DEST"

OK=0
FAIL=0
FAILURES=""

download() {
    local wm_name="$1"
    local out_name="$2"
    local url="https://commons.wikimedia.org/wiki/Special:FilePath/${wm_name}?width=${WIDTH}"
    local out="$DEST/$out_name"
    if curl -sSL --max-time 60 -A "ArcanaApp/1.0 (https://github.com/local; downloading public-domain RWS scans)" -o "$out" "$url"; then
        local size
        size=$(wc -c < "$out" 2>/dev/null || echo 0)
        if [ "$size" -lt 1000 ]; then
            FAIL=$((FAIL + 1))
            FAILURES="$FAILURES $out_name(too-small:$size)"
            rm -f "$out"
            echo "FAIL  $wm_name -> $out_name ($size bytes)"
        else
            OK=$((OK + 1))
            echo "OK    $wm_name -> $out_name ($size bytes)"
        fi
    else
        FAIL=$((FAIL + 1))
        FAILURES="$FAILURES $out_name(curl-error)"
        echo "FAIL  $wm_name -> $out_name (curl error)"
    fi
    sleep "$SLEEP"
}

# ---- Major Arcana ----
download "RWS_Tarot_00_Fool.jpg"             "major_00_fool.jpg"
download "RWS_Tarot_01_Magician.jpg"         "major_01_magician.jpg"
download "RWS_Tarot_02_High_Priestess.jpg"   "major_02_high_priestess.jpg"
download "RWS_Tarot_03_Empress.jpg"          "major_03_empress.jpg"
download "RWS_Tarot_04_Emperor.jpg"          "major_04_emperor.jpg"
download "RWS_Tarot_05_Hierophant.jpg"       "major_05_hierophant.jpg"
download "RWS_Tarot_06_Lovers.jpg"           "major_06_lovers.jpg"
download "RWS_Tarot_07_Chariot.jpg"          "major_07_chariot.jpg"
download "RWS_Tarot_08_Strength.jpg"         "major_08_strength.jpg"
download "RWS_Tarot_09_Hermit.jpg"           "major_09_hermit.jpg"
download "RWS_Tarot_10_Wheel_of_Fortune.jpg" "major_10_wheel.jpg"
download "RWS_Tarot_11_Justice.jpg"          "major_11_justice.jpg"
download "RWS_Tarot_12_Hanged_Man.jpg"       "major_12_hanged_man.jpg"
download "RWS_Tarot_13_Death.jpg"            "major_13_death.jpg"
download "RWS_Tarot_14_Temperance.jpg"       "major_14_temperance.jpg"
download "RWS_Tarot_15_Devil.jpg"            "major_15_devil.jpg"
download "RWS_Tarot_16_Tower.jpg"            "major_16_tower.jpg"
download "RWS_Tarot_17_Star.jpg"             "major_17_star.jpg"
download "RWS_Tarot_18_Moon.jpg"             "major_18_moon.jpg"
download "RWS_Tarot_19_Sun.jpg"              "major_19_sun.jpg"
download "RWS_Tarot_20_Judgement.jpg"        "major_20_judgement.jpg"
download "RWS_Tarot_21_World.jpg"            "major_21_world.jpg"

# ---- Minor Arcana ----
# Wikimedia uses Wands/Cups/Swords/Pents with 01-14 ranks
# (01=Ace, 02-10=Two-Ten, 11=Page, 12=Knight, 13=Queen, 14=King)

# Wands
download "Wands01.jpg" "wands_01_ace.jpg"
for i in 02 03 04 05 06 07 08 09 10; do download "Wands${i}.jpg" "wands_${i}.jpg"; done
download "Wands11.jpg" "wands_page.jpg"
download "Wands12.jpg" "wands_knight.jpg"
download "Wands13.jpg" "wands_queen.jpg"
download "Wands14.jpg" "wands_king.jpg"

# Cups
download "Cups01.jpg" "cups_01_ace.jpg"
for i in 02 03 04 05 06 07 08 09 10; do download "Cups${i}.jpg" "cups_${i}.jpg"; done
download "Cups11.jpg" "cups_page.jpg"
download "Cups12.jpg" "cups_knight.jpg"
download "Cups13.jpg" "cups_queen.jpg"
download "Cups14.jpg" "cups_king.jpg"

# Swords
download "Swords01.jpg" "swords_01_ace.jpg"
for i in 02 03 04 05 06 07 08 09 10; do download "Swords${i}.jpg" "swords_${i}.jpg"; done
download "Swords11.jpg" "swords_page.jpg"
download "Swords12.jpg" "swords_knight.jpg"
download "Swords13.jpg" "swords_queen.jpg"
download "Swords14.jpg" "swords_king.jpg"

# Pentacles (Wikimedia abbreviates as "Pents")
download "Pents01.jpg" "pentacles_01_ace.jpg"
for i in 02 03 04 05 06 07 08 09 10; do download "Pents${i}.jpg" "pentacles_${i}.jpg"; done
download "Pents11.jpg" "pentacles_page.jpg"
download "Pents12.jpg" "pentacles_knight.jpg"
download "Pents13.jpg" "pentacles_queen.jpg"
download "Pents14.jpg" "pentacles_king.jpg"

echo ""
echo "=========================================="
echo "DONE — succeeded: $OK, failed: $FAIL"
if [ -n "$FAILURES" ]; then
    echo "Failed downloads:$FAILURES"
fi
echo "Saved to: $DEST"
ls -la "$DEST" | head -5
echo ""
ls "$DEST" | wc -l
echo "files in deck folder"
