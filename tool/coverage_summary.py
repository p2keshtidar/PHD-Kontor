#!/usr/bin/env python3
"""Fasst die Testabdeckung zusammen (JaCoCo-XML fürs Backend, lcov für die App).

Aufruf:
  python3 tool/coverage_summary.py jacoco backend/build/reports/jacoco/test/jacocoTestReport.xml
  python3 tool/coverage_summary.py lcov app/coverage/lcov.info
Gibt eine Tabelle aus und schreibt sie (falls vorhanden) in $GITHUB_STEP_SUMMARY.
"""
import os
import sys
import xml.etree.ElementTree as ET


def pct(c, m):
    return 100.0 * c / (c + m) if c + m else 100.0


def jacoco(path):
    root = ET.parse(path).getroot()
    rows = []
    for pkg in root.findall('package'):
        for cls in pkg.findall('class'):
            line = next((c for c in cls.findall('counter') if c.get('type') == 'LINE'), None)
            if line is None:
                continue
            cov, mis = int(line.get('covered')), int(line.get('missed'))
            rows.append((cls.get('name').split('/')[-1], cov, mis))
    total = next(c for c in root.findall('counter') if c.get('type') == 'LINE')
    branch = next((c for c in root.findall('counter') if c.get('type') == 'BRANCH'), None)
    return rows, int(total.get('covered')), int(total.get('missed')), branch


def lcov(path):
    rows, cur, hit, found = [], None, 0, 0
    for line in open(path):
        line = line.strip()
        if line.startswith('SF:'):
            cur, hit, found = line[3:].split('lib/')[-1], 0, 0
        elif line.startswith('LH:'):
            hit = int(line[3:])
        elif line.startswith('LF:'):
            found = int(line[3:])
        elif line == 'end_of_record' and cur:
            rows.append((cur, hit, found - hit))
    return rows, sum(r[1] for r in rows), sum(r[2] for r in rows), None


def main():
    kind, path = sys.argv[1], sys.argv[2]
    rows, cov, mis, branch = jacoco(path) if kind == 'jacoco' else lcov(path)
    out = [f"### Testabdeckung {'Backend' if kind == 'jacoco' else 'App'}: "
           f"**{pct(cov, mis):.1f} %** der Zeilen ({cov} von {cov + mis})"]
    if branch is not None:
        bc, bm = int(branch.get('covered')), int(branch.get('missed'))
        out.append(f"Verzweigungen: {pct(bc, bm):.1f} %")
    out.append('')
    out.append('| Datei/Klasse (am wenigsten getestet zuerst) | Zeilen | Abdeckung |')
    out.append('|---|---|---|')
    for name, c, m in sorted(rows, key=lambda r: (pct(r[1], r[2]), -(r[1] + r[2])))[:25]:
        if c + m:
            out.append(f'| {name} | {c + m} | {pct(c, m):.0f} % |')
    text = '\n'.join(out)
    print(text)
    summary = os.environ.get('GITHUB_STEP_SUMMARY')
    if summary:
        with open(summary, 'a') as f:
            f.write(text + '\n\n')
    print(f"::notice title=Testabdeckung {kind}::{pct(cov, mis):.1f} % der Zeilen")
    # Liste auch als Hinweis (in GitHub ohne Log-Download lesbar)
    lines = [f"{n}: {pct(c, m):.0f} % von {c + m}" for n, c, m in
             sorted(rows, key=lambda r: (pct(r[1], r[2]), -(r[1] + r[2])))[:40] if c + m]
    print(f"::notice title=Am wenigsten getestet ({kind})::" + '%0A'.join(lines))


if __name__ == '__main__':
    main()
