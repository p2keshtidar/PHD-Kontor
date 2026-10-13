#!/usr/bin/env bash
# Setzt die Bildschirmfotos aus dem Testprotokoll (Zeilen "SCREENSHOT:<name>:<base64>") zusammen
# und legt sie im Branch ci-shots-<Name> ab. Aufruf: bildschirmfotos.sh <Name> <Protokoll>
set -u
NAME="$1"; LOG="$2"
OUT=$(mktemp -d)
python3 - "$LOG" "$OUT" <<'PY'
import base64, re, sys, collections
log, out = sys.argv[1], sys.argv[2]
parts = collections.OrderedDict()
for line in open(log, errors='ignore'):
    m = re.search(r'SCREENSHOT:([\w-]+):([A-Za-z0-9+/=]+)\s*$', line)
    if m:
        parts.setdefault(m.group(1), []).append(m.group(2))
for name, chunks in parts.items():
    try:
        open(f'{out}/{name}.png', 'wb').write(base64.b64decode(''.join(chunks)))
        print('Bild', name)
    except Exception as e:
        print('Bild', name, 'kaputt:', e)
PY
ls "$OUT"/*.png > /dev/null 2>&1 || { echo "Keine Bildschirmfotos im Protokoll."; exit 0; }
cd "$OUT" && git init -q && git add . && git -c user.name=ci -c user.email=ci@example.org commit -qm "Bildschirmfotos $NAME"
git push -qf "https://x-access-token:${GITHUB_TOKEN}@github.com/${GITHUB_REPOSITORY}" HEAD:refs/heads/ci-shots-"$NAME" && echo "Abgelegt in Branch ci-shots-$NAME"
