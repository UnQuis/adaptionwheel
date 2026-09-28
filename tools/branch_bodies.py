#!/usr/bin/env python3
"""Show statements that exist on main but not on 26.3, comments and formatting stripped.

The method-name diff and branch_audit.py both miss a whole class of porting gap: a
method present on both branches whose *body* is the pre-fix version. The BreakSpeed
handler on 26.3 was exactly that -- it existed, it was subscribed, and it simply
predated the fist. So compare the statement text itself.

Renames and API differences show up here too and have to be judged; the point is
that nothing is silently assumed equivalent.
"""
import io, os, re, subprocess, sys

REPO = os.environ.get('AW_REPO') or os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if not os.path.isdir(os.path.join(REPO, '.git')):
    sys.exit('AW_REPO does not point at the repo: ' + REPO)
LOCAL = subprocess.run(['git', 'rev-parse', '--abbrev-ref', 'HEAD'], cwd=REPO,
                       capture_output=True, text=True).stdout.strip()
if LOCAL not in ('main', '26.3'):
    sys.exit('run this on main or 26.3')
OTHER = 'main' if LOCAL == '26.3' else '26.3'


def read(tree, path):
    if tree == LOCAL:
        fp = os.path.join(REPO, path)
        return io.open(fp, encoding='utf-8', errors='replace').read() if os.path.isfile(fp) else None
    r = subprocess.run(['git', 'show', f'{tree}:{path}'], cwd=REPO, capture_output=True, text=True)
    return r.stdout if r.returncode == 0 else None


def statements(text):
    """Meaningful lines: no imports, no comments, whitespace-collapsed."""
    out, in_block = [], False
    for raw in text.split('\n'):
        line = raw.strip()
        if in_block:
            if '*/' in line:
                in_block = False
            continue
        if line.startswith('/*'):
            if '*/' not in line:
                in_block = True
            continue
        if line.startswith('//') or line.startswith('*') or not line:
            continue
        if line.startswith('import ') or line.startswith('package '):
            continue
        line = re.sub(r'\s+', ' ', line)
        line = re.sub(r'\s*([{}();,=<>+\-*/?:])\s*', r'\1', line)
        out.append(line)
    return out


TARGETS = [
    'FistMastery', 'FistTiers', 'FistLuck', 'DarknessLightmap', 'SurfaceAdaptations',
    'ClientAdaption', 'AdaptionHud', 'DomainTriggers', 'FistProgressPayload',
    'FistInstabreakPayload', 'AdaptionCommand', 'WheelData', 'PlayerAdaption',
    'AdaptionSyncPayload', 'AdaptionNetworking', 'AdaptationScreen',
]

JAVA = 'src/main/java/ru/adaptionwheel'
index = subprocess.run(['git', 'ls-tree', '-r', '--name-only', OTHER, '--', JAVA],
                       cwd=REPO, capture_output=True, text=True).stdout.split()
by_name = {p.split('/')[-1][:-5]: p for p in index if p.endswith('.java')}

total = 0
for cls in TARGETS:
    if cls not in by_name:
        continue
    path = by_name[cls]
    a, b = read(OTHER, path), read(LOCAL, path)
    if not a or not b:
        continue
    sa, sb = statements(a), statements(b)
    only_a = [l for l in sa if l not in set(sb)]
    if not only_a:
        continue
    print(f'\n=== {cls}.java   ({OTHER} has {len(only_a)} statements not on {LOCAL}) ===')
    for line in only_a:
        print('  - ' + line[:150])
    total += len(only_a)
print(f'\ntotal statements present on {OTHER} but not on {LOCAL}: {total}')
