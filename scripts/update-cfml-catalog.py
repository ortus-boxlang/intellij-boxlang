#!/usr/bin/env python3
"""Regenerate bundled CFML built-ins from a local checkout of foundeo/cfdocs."""
import json
from pathlib import Path
import subprocess
import sys

source = Path(sys.argv[1])
root = Path(__file__).resolve().parents[1]
output = root / 'src/main/resources/cfml'
functions = []
for path in sorted((source / 'data/en').glob('*.json')):
    entry = json.loads(path.read_text())
    if entry.get('type') != 'function':
        continue
    engines = entry.get('engines', {})
    if not any(engine in engines for engine in ('coldfusion', 'lucee')):
        continue
    functions.append({key: entry.get(key, default) for key, default in
                      [('name', ''), ('syntax', ''), ('returns', ''), ('description', ''), ('params', []), ('member', '')]})
output.mkdir(parents=True, exist_ok=True)
(output / 'functions.json').write_text(json.dumps(functions, indent=2, ensure_ascii=False) + '\n')
(output / 'LICENSE-CFDocs.txt').write_text((source / 'LICENSE').read_text().rstrip() + '\n')
revision = subprocess.check_output(['git', '-C', str(source), 'rev-parse', 'HEAD'], text=True).strip()
(output / 'SOURCE.txt').write_text(f'CFDocs: https://github.com/foundeo/cfdocs\nRevision: {revision}\n'
                                  'Subset: Adobe ColdFusion or Lucee functions, metadata and parameters.\n'
                                  'Regenerate: python3 scripts/update-cfml-catalog.py /path/to/cfdocs\n')
print(f'Bundled {len(functions)} functions from {revision}')
