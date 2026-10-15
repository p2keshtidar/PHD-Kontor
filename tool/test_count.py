#!/usr/bin/env python3
"""Zählt die Testergebnisse und gibt sie als Annotation aus (auch bei Erfolg sichtbar).

Aufruf:
  python3 tool/test_count.py junit backend/build/test-results/test
  python3 tool/test_count.py flutter /tmp/ci.log
"""
import glob
import os
import re
import sys
import xml.etree.ElementTree as ET


def junit(folder):
    tests = failures = skipped = 0
    for f in glob.glob(os.path.join(folder, '*.xml')):
        root = ET.parse(f).getroot()
        tests += int(root.get('tests', 0))
        failures += int(root.get('failures', 0)) + int(root.get('errors', 0))
        skipped += int(root.get('skipped', 0))
    return tests - failures - skipped, failures, skipped


def flutter(log):
    text = open(log, encoding='utf-8', errors='replace').read().replace('\r', '\n')
    # Reporter "github" (Standard in GitHub Actions): "N tests passed, M failed." bzw. "🎉 N tests passed."
    gh = list(re.finditer(r'(\d+) tests? passed(?:, (\d+) failed)?(?:, (\d+) skipped)?', text))
    if gh:
        m = gh[-1]
        return int(m.group(1)), int(m.group(2) or 0), int(m.group(3) or 0)
    last = None
    for m in re.finditer(r'\+(\d+)(?: ~(\d+))?(?: -(\d+))?: ', text):
        last = m
    if last is None:
        return 0, 0, 0
    return int(last.group(1)), int(last.group(3) or 0), int(last.group(2) or 0)


def main():
    kind, path = sys.argv[1], sys.argv[2]
    passed, failed, skipped = junit(path) if kind == 'junit' else flutter(path)
    msg = f'{passed} bestanden, {failed} fehlgeschlagen, {skipped} übersprungen'
    level = 'error' if failed else 'notice'
    print(f'::{level} title=Tests ({kind})::{msg}')
    summary = os.environ.get('GITHUB_STEP_SUMMARY')
    if summary:
        with open(summary, 'a', encoding='utf-8') as f:
            f.write(f'### Tests ({kind})\n\n{msg}\n\n')


if __name__ == '__main__':
    main()
