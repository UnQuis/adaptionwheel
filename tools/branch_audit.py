#!/usr/bin/env python3
"""Deeper main<->26.3 parity audit (run from anywhere: python3 tools/branch_audit.py): the checks a method-name diff cannot do.

A method-name diff found seven gaps, then missed an eighth that broke the feature the
user reported next (Instabreak): `FistMastery.breakSpeed` was present on both
branches and simply never *called* on 26.3, because the `PlayerEvent.BreakSpeed`
handler there was still the pre-fist version. So:

  1. CALL SITES  - a mod method with N references on main and 0 on 26.3 is a
                   behaviour that never got wired up. This is what caught breakSpeed.
  2. CONFIG      - every `define*` key with its default, compared. A silently
                   different default is a balance change nobody asked for.
  3. LANG        - key -> value, not just key presence. Same key, different text.
  4. TAGS        - full JSON contents of the block tags, which drive FistTiers.

Renames produce known false positives; they are listed at the end so they can be
judged rather than blindly "fixed".
"""
import io, json, os, re, subprocess, sys

REPO = os.environ.get('AW_REPO') or os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if not os.path.isdir(os.path.join(REPO, '.git')):
    sys.exit('AW_REPO does not point at the repo: ' + REPO)
JAVA = 'src/main/java'
RES = 'src/main/resources'


def show(tree, path):
    r = subprocess.run(['git', 'show', f'{tree}:{path}'], cwd=REPO,
                       capture_output=True, text=True)
    return r.stdout if r.returncode == 0 else None


def paths(tree, sub):
    out = subprocess.run(['git', 'ls-tree', '-r', '--name-only', tree, '--', sub],
                         cwd=REPO, capture_output=True, text=True).stdout.split()
    return [p for p in out if p.endswith('.java')]


def blob(tree, path):
    if tree == LOCAL:
        fp = os.path.join(REPO, path)
        return open(fp, 'rb').read() if os.path.isfile(fp) else None
    r = subprocess.run(['git', 'show', f'{tree}:{path}'], cwd=REPO, capture_output=True)
    return r.stdout if r.returncode == 0 else None


MAIN, W = 'main', '26.3'
# The branch you are standing on is read from the working tree, not from its commit, so an
# uncommitted fix shows up instead of being reported as still missing.
_cur = subprocess.run(['git', 'rev-parse', '--abbrev-ref', 'HEAD'], cwd=REPO,
                      capture_output=True, text=True).stdout.strip()
LOCAL = _cur if _cur in (MAIN, W) else W
problems = 0


def read(tree, path):
    if tree == LOCAL:
        fp = os.path.join(REPO, path)
        return io.open(fp, encoding='utf-8', errors='replace').read() if os.path.isfile(fp) else None
    return show(tree, path)

# ---------------------------------------------------------------- 1. call sites
print('=' * 72)
print('1. CALL SITES  (declared on main, referenced 0 times on 26.3)')
print('=' * 72)
src = {t: {p: (read(t, p) or '') for p in paths(t, JAVA)} for t in (MAIN, W)}
decl = re.compile(r'public static [\w.<>\[\],? ]+? (\w+)\s*\(')
refs = {}
for t in (MAIN, W):
    blob_all = '\n'.join(src[t].values())
    refs[t] = {}
    for m in decl.finditer(blob_all):
        name = m.group(1)
        # own-body call + external Class.name( references
        n = len(re.findall(r'\b' + re.escape(name) + r'\s*\(', blob_all))
        refs[t][name] = n

main_names = set(refs[MAIN])
w_names = set(refs[W])
only_main = sorted(main_names - w_names)
for name in only_main:
    a, b = refs[MAIN][name], refs[W].get(name, 0)
    if a >= 2 and b == 0:
        print(f'  {name:34} main {a:3} refs  |  26.3  0   <-- never called on 26.3')
        problems += 1
if not any(refs[MAIN][n] >= 2 and refs[W].get(n, 0) == 0 for n in only_main):
    print('  (none)')

# ---------------------------------------------------------------- 2. config
print()
print('=' * 72)
print('2. CONFIG DEFAULTS  (same key, different default value)')
print('=' * 72)
DEF = re.compile(r'\.define(?:InRange|List)?\(\s*"([^"]+)"\s*,\s*([^,\)]+)')
cfg = {}
for t in (MAIN, W):
    body = src[t].get(f'{JAVA}/ru/adaptionwheel/config/AdaptionConfig.java', '')
    d = {}
    for m in DEF.finditer(body):
        d[m.group(1)] = m.group(2).strip()
    cfg[t] = d
    print(f'  {t}: {len(d)} define* calls' + ('  (working tree)' if t == LOCAL else '  (commit)'))
diff = [(k, cfg[MAIN][k], cfg[W].get(k, 'MISSING'))
        for k in sorted(cfg[MAIN]) if cfg[W].get(k) != cfg[MAIN][k]]
for k, a, b in diff:
    print(f'  {k:34} main={a:22} 26.3={b}')
    problems += 1
if not diff:
    print('  (identical)')

# ---------------------------------------------------------------- 3. lang
print()
print('=' * 72)
print('3. LANG VALUES  (same key, different text)')
print('=' * 72)
for loc in ('en_us', 'ru_ru'):
    p = f'{RES}/assets/adaptionwheel/lang/{loc}.json'
    a, b = blob(MAIN, p), blob(W, p)
    if not a or not b:
        print(f'  {loc}: missing on one side')
        continue
    ja, jb = json.loads(a), json.loads(b)
    d = [(k, ja[k], jb[k]) for k in sorted(ja) if k in jb and ja[k] != jb[k]]
    only_a = [k for k in ja if k not in jb]
    print(f'  {loc}: {len(ja)} keys main / {len(jb)} keys 26.3'
          + (f'   |  only on main: {only_a}' if only_a else ''))
    for k, x, y in d:
        print(f'    {k}\n      main: {x!r}\n      26.3: {y!r}')
        problems += 1

# ---------------------------------------------------------------- 4. tags
print()
print('=' * 72)
print('4. BLOCK TAGS  (drives FistTiers material classes)')
print('=' * 72)
tag_paths = [p for p in subprocess.run(
    ['git', 'ls-tree', '-r', '--name-only', MAIN, '--', f'{RES}/data'],
    cwd=REPO, capture_output=True, text=True).stdout.split()
    if '/tags/' in p and p.endswith('.json')]
for p in sorted(tag_paths):
    a, b = blob(MAIN, p), blob(W, p)
    if a == b:
        continue
    name = p.split('/tags/')[-1]
    if not b:
        print(f'  {name}: MISSING on 26.3')
        problems += 1
        continue
    # Only the list values may be sorted. A tag file also carries scalars -- "replace": false --
    # and sorted(False) raises, which used to report every tag with a replace flag as
    # "unparseable on one side" the moment the two sides stopped being byte-identical. A check that
    # cannot read the file it is checking is worse than no check.
    def normalise(text):
        out = {}
        for k, v in json.loads(text).items():
            out[k] = sorted(v) if isinstance(v, list) else v
        return out

    try:
        ta, tb = normalise(a), normalise(b)
    except Exception:
        print(f'  {name}: unparseable on one side')
        problems += 1
        continue
    if ta != tb:
        print(f'  {name}: DIFFERS')
        for key in sorted(set(ta) | set(tb)):
            if ta.get(key) != tb.get(key):
                print(f'    {key}:\n      main {ta.get(key)}\n      26.3 {tb.get(key)}')
        problems += 1
if problems == 0:
    print('  (all identical)')

print()
print(f'flagged: {problems}')
