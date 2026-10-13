#!/usr/bin/env python3
"""Check staged repository files before committing and in CI.

Reads Git's index, including staged content, without inspecting ignored local
files. This is a basic publication check, not a comprehensive secret scanner.
"""
import posixpath
import re
import subprocess
from pathlib import Path, PurePosixPath
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[1]


def git(*args):
    return subprocess.check_output(['git', '-C', str(ROOT), *args])


def main():
    entries = {}
    for record in git('ls-files', '--stage', '-z').split(b'\0'):
        if record:
            metadata, name = record.split(b'\t', 1)
            mode, blob, stage = metadata.decode().split()
            entries[name.decode()] = (mode, blob, stage)
    errors = []
    forbidden_dirs = {'.tooling', '.gradle', '.idea', '.vscode', '.agents',
                      '.codex', 'build', 'captures', '__pycache__'}
    forbidden_names = {'local.properties', 'key.properties', 'keystore.properties',
                       'secrets.properties', 'google-services.json'}
    forbidden_suffixes = {'.apk', '.aab', '.apks', '.apkm', '.dex', '.jar', '.class',
                          '.zip', '.tar', '.gz', '.xz', '.jks', '.keystore', '.pem',
                          '.p12', '.pfx', '.db', '.sqlite', '.sqlite3', '.log', '.pyc'}
    secrets = [
        re.compile(r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'),
        re.compile(r'\bgh[pousr]_[A-Za-z0-9]{30,}\b'),
        re.compile(r'\bgithub_pat_[A-Za-z0-9_]{40,}\b'),
        re.compile(r'\bAKIA[0-9A-Z]{16}\b'),
    ]
    machine_path = re.compile(r'/home/[A-Za-z0-9_.-]+/|/Users/[A-Za-z0-9_.-]+/|[A-Za-z]:\\Users\\')
    for name, (mode, blob, stage) in entries.items():
        path = PurePosixPath(name)
        if mode not in {'100644', '100755'} or stage != '0':
            errors.append(f'{name}: unsupported file mode or unresolved merge')
            continue
        if (forbidden_dirs.intersection(path.parts) or path.name in forbidden_names
                or path.suffix.lower() in forbidden_suffixes
                or (path.name.startswith('.env') and path.name != '.env.example')
                or path.name.startswith('Screenshot_')
                or path.name.endswith(('.db-wal', '.db-shm'))):
            errors.append(f'{name}: local/generated/sensitive file must stay outside Git')
        content = git('cat-file', 'blob', blob)
        if len(content) > 2 * 1024 * 1024:
            errors.append(f'{name}: large file requires review')
        try:
            text = content.decode('utf-8')
        except UnicodeDecodeError:
            continue
        if any(pattern.search(text) for pattern in secrets):
            errors.append(f'{name}: possible secret (value omitted)')
        if machine_path.search(text):
            errors.append(f'{name}: machine-specific home path')
        if path.suffix == '.md':
            for link in re.findall(r'\]\(([^\s)]+)\)', text):
                if link.startswith(('#', 'https://', 'http://', 'mailto:')):
                    continue
                target = unquote(link.split('#', 1)[0].split('?', 1)[0])
                if not target:
                    continue
                resolved = posixpath.normpath(posixpath.join(str(path.parent), target))
                if resolved not in entries and not any(p.startswith(resolved.rstrip('/') + '/') for p in entries):
                    errors.append(f'{name}: link target is not tracked: {target}')
    if not entries:
        errors.append('No staged/tracked files; stage the intended files first.')
    if errors:
        raise SystemExit('\n'.join('FAIL: ' + error for error in errors))
    print(f'PASS: {len(entries)} indexed files; paths, common secrets and documentation links checked')


if __name__ == '__main__':
    main()
