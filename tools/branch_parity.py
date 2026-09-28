#!/usr/bin/env python3
"""Method-level parity check between the 1.21.1 branch (main) and the 26.3 branch.

Usage:  python3 tools/branch_parity.py   (override the repo with AW_REPO)

It exists because the port was signed off as "synchronised" while seven main-side
fixes had never landed -- including a helper method that HAD been copied but
never called, which reads as present in every grep. Method-name differences on
shared files are the cheap signal; each one still has to be judged, since API
renames (syncAdaption -> sync, render -> extractRenderState) are expected.

Original sweep description follows.

Two porting omissions already turned up (grantAllAdaptations missing the fist block,
taskProgress calling the pre-fix helper while the helper itself had been copied), so
this walks every shared file and lists methods present on one side only. Import and
API-renaming differences are expected; a *method* present on main and absent on 26.3 is
either a missing port or a deliberate 26.3-only design, and each one has to be looked at.
"""
import io, re, subprocess, sys

import os
REPO = os.environ.get('AW_REPO') or os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if not os.path.isdir(os.path.join(REPO, '.git')):
    sys.exit('not a git repository: ' + REPO)

def ls(tree, sub):
    out = subprocess.run(['git', 'ls-tree', '-r', '--name-only', tree, '--', sub],
                         cwd=REPO, capture_output=True, text=True).stdout.split()
    return {p for p in out if p.endswith('.java')}

MAIN, W = ls('main', 'src/main/java'), ls('26.3', 'src/main/java')
shared = sorted(MAIN & W)
print(f'shared java files: {len(shared)}   main-only: {len(MAIN - W)}   26.3-only: {len(W - MAIN)}')

def methods(text):
    """Method names declared at any depth: `<mods> <ret> name(` on its own line."""
    found = set()
    for line in text.splitlines():
        m = re.match(r'\s{4,}(?:@\w+\s+)*(?:public|private|protected|static|final|synchronized|\s)*'
                     r'[\w.<>\[\],? ]+?\s(\w+)\s*\(', line)
        if m and m.group(1) not in ('if', 'for', 'while', 'switch', 'catch', 'return',
                                    'new', 'super', 'this'):
            found.add(m.group(1))
    return found

interesting = []
for path in shared:
    a = subprocess.run(['git', 'show', f'main:{path}'], cwd=REPO,
                       capture_output=True, text=True).stdout
    b = subprocess.run(['git', 'show', f'26.3:{path}'], cwd=REPO,
                       capture_output=True, text=True).stdout
    ma, mb = methods(a), methods(b)
    only_main, only_263 = ma - mb, mb - ma
    if only_main or only_263:
        interesting.append((path, only_main, only_263, len(a.splitlines()), len(b.splitlines())))

if not interesting:
    print('\nno method-level differences in shared files')
for path, om, o2, la, lb in interesting:
    short = path.split('ru/adaptionwheel/')[-1]
    print(f'\n{short}  (main {la}L / 26.3 {lb}L)')
    if om:
        print('   only on MAIN :', ', '.join(sorted(om)))
    if o2:
        print('   only on 26.3 :', ', '.join(sorted(o2)))
