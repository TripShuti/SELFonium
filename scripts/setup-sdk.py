"""Download pinned Android command-line tools, verified against Google's repository metadata."""
from pathlib import Path
import hashlib
import json
import shutil
import stat
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile
from urllib.parse import urljoin, urlparse

BASE = 'https://dl.google.com/android/repository/'
PACKAGE = 'cmdline-tools;12.0'
ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / '.tooling/android-sdk/cmdline-tools/12.0'


def child(element, name):
    return next((e for e in element if e.tag.split('}')[-1] == name), None)


def value(element, name):
    entry = child(element, name)
    if entry is None or not entry.text:
        raise ValueError(f'Missing repository field: {name}')
    return entry.text.strip()


def select_archive(data):
    root = ET.fromstring(data)
    package = next((e for e in root if e.tag.split('}')[-1] == 'remotePackage' and e.get('path') == PACKAGE), None)
    if package is None:
        raise ValueError(f'{PACKAGE} is unavailable in the official repository. Install SDK through Android Studio instead.')
    for archive in child(package, 'archives'):
        host = child(archive, 'host-os')
        if host is None or host.text.strip() != 'linux':
            continue
        complete = child(archive, 'complete')
        checksum = child(complete, 'checksum')
        algorithm = checksum.get('type', 'sha1').lower().replace('-', '')
        if algorithm not in ('sha1', 'sha256'):
            raise ValueError('Unsupported repository checksum algorithm')
        url = urljoin(BASE, value(complete, 'url'))
        parsed = urlparse(url)
        if parsed.scheme != 'https' or parsed.netloc != 'dl.google.com' or not parsed.path.startswith('/android/repository/'):
            raise ValueError('Unexpected download origin')
        return {'url': url, 'size': int(value(complete, 'size')), 'algorithm': algorithm, 'digest': checksum.text.strip().lower()}
    raise ValueError('Linux command-line tools not found')


def fetch(url, destination):
    subprocess.run(['curl', '--fail', '--location', '--proto', '=https', '--connect-timeout', '20', '--max-time', '900', '--output', str(destination), url], check=True)


def install():
    if (DEST / 'bin/sdkmanager').is_file():
        print(f'Using existing SDK manager: {DEST}', flush=True)
        return
    if DEST.exists():
        raise RuntimeError(f'Incomplete installation at {DEST}; inspect it before retrying.')
    (ROOT / '.tooling').mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='sdk-bootstrap-', dir=ROOT / '.tooling') as temporary:
        tmp = Path(temporary)
        fetch(BASE + 'repository2-1.xml', tmp / 'repository.xml')
        info = select_archive((tmp / 'repository.xml').read_bytes())
        print(f'Downloading {PACKAGE} for Linux', flush=True)
        archive = tmp / 'tools.zip'
        fetch(info['url'], archive)
        if archive.stat().st_size != info['size']:
            raise ValueError('Android tools archive size mismatch')
        with archive.open('rb') as stream:
            digest = hashlib.file_digest(stream, info['algorithm']).hexdigest()
        if digest != info['digest']:
            raise ValueError('Android tools checksum mismatch')
        unpack = tmp / 'unpacked'
        with zipfile.ZipFile(archive) as bundle:
            for member in bundle.infolist():
                target = unpack / member.filename
                if not target.resolve().is_relative_to(unpack.resolve()) or stat.S_ISLNK(member.external_attr >> 16):
                    raise ValueError('Unsafe archive member')
            bundle.extractall(unpack)
        source = unpack / 'cmdline-tools'
        if not (source / 'bin/sdkmanager').is_file():
            raise ValueError('Unexpected command-line tools layout')
        for executable in (source / 'bin').iterdir():
            if executable.is_file():
                executable.chmod(0o755)
        DEST.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(str(source), str(DEST))
        (DEST / 'selfonium-download.json').write_text(json.dumps(info, indent=2) + '\n')
        print(f'Installed verified command-line tools at {DEST}', flush=True)


if __name__ == '__main__':
    install()
