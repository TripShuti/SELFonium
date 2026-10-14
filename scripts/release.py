#!/usr/bin/env python3
"""Local release packaging and signing-key backup; never uploads or changes Git."""
import argparse
from dataclasses import dataclass
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
# Public signing certificate of the existing SELFonium release series, not a secret.
SIGNER_SHA256 = '8688ea8fbc70d6732080fc24764618d073874c874f74f7e36931429d1ea1009c'
PACKAGE = 'app.selfonium'


class ReleaseError(Exception):
    pass


def sha256(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def outside_repository(path):
    resolved = path.resolve()
    if resolved == ROOT or ROOT in resolved.parents:
        raise ReleaseError('Signing files and backups must be outside the repository.')
    return resolved


def private_file(path):
    path = outside_repository(path)
    if not path.is_file() or path.stat().st_uid != os.getuid() or path.stat().st_mode & 0o077:
        raise ReleaseError('Signing files must exist, belong to this user and have mode 600 or 400.')
    return path


@dataclass(frozen=True)
class Signing:
    keystore: Path
    password: Path
    alias: str


def load_signing(config):
    config = private_file(config)
    try:
        data = json.loads(config.read_text())
        def location(name):
            value = Path(data[name]).expanduser()
            return private_file(value if value.is_absolute() else config.parent / value)
        signing = Signing(location('keystore'), location('password_file'), data['alias'])
        if not isinstance(signing.alias, str) or not signing.alias:
            raise ValueError()
        lines = signing.password.read_bytes().splitlines()
        if len(lines) != 1 or not lines[0]:
            raise ValueError()
        return signing
    except (KeyError, ValueError, TypeError):
        raise ReleaseError('Invalid signing configuration or password file (one nonempty line required).') from None


def capture(command):
    result = subprocess.run(list(map(str, command)), cwd=ROOT, stdin=subprocess.DEVNULL,
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode:
        # Do not echo tool exceptions or configuration content into release logs.
        raise ReleaseError(f'{Path(str(command[0])).name} failed (exit {result.returncode}); no artifact published.')
    return result.stdout


def jdk_tool(name):
    home = os.environ.get('JAVA_HOME')
    candidate = Path(home) / 'bin' / name if home else None
    found = str(candidate) if candidate and candidate.is_file() else shutil.which(name)
    if not found:
        raise ReleaseError(f'{name} is missing; install JDK 17 or 21.')
    return found


def certificate(signing):
    der = capture([jdk_tool('keytool'), '-exportcert', '-alias', signing.alias,
                   '-keystore', signing.keystore, '-storepass:file', signing.password])
    digest = hashlib.sha256(der).hexdigest()
    if digest != SIGNER_SHA256:
        raise ReleaseError('Signing certificate differs from existing SELFonium releases. Key rotation is not automatic.')
    return digest


def sdk_tools():
    candidates = [os.environ.get('ANDROID_HOME'), os.environ.get('ANDROID_SDK_ROOT'),
                  ROOT / '.tooling/android-sdk', Path.home() / 'Android/Sdk', '/opt/android-sdk']
    for candidate in candidates:
        if not candidate:
            continue
        sdk = Path(candidate)
        tools = sdk / 'build-tools/35.0.0'
        if (sdk / 'platforms/android-35/android.jar').is_file() and all((tools / name).is_file() for name in ('aapt', 'zipalign', 'apksigner')):
            return sdk, tools
    raise ReleaseError('Android SDK Platform 35 and Build Tools 35.0.0 are required.')


def parse_version(source):
    name = re.search(r"versionName\s+'([^']+)'", source)
    code = re.search(r'versionCode\s+(\d+)', source)
    if not name or not code or not re.fullmatch(r'\d+\.\d+\.\d+(?:-[A-Za-z0-9.-]+)?', name[1]):
        raise ReleaseError('Unsupported source version; use a semantic version safe for an APK filename.')
    return name[1], int(code[1])


def source_version():
    return parse_version((ROOT / 'app/build.gradle').read_text())


def tag_context(tag):
    if not re.fullmatch(r'v(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)', tag):
        raise ReleaseError('Release tags must have the exact form vX.Y.Z.')
    commit = capture(['git', 'rev-parse', '--verify', f'refs/tags/{tag}^{{commit}}']).decode().strip()
    result = subprocess.run(['git', 'merge-base', '--is-ancestor', commit, 'origin/main'], cwd=ROOT,
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode:
        raise ReleaseError('Release commit must be reachable from origin/main.')
    version, code = parse_version(capture(['git', 'show', f'{commit}:app/build.gradle']).decode())
    if tag != 'v' + version or code <= 0:
        raise ReleaseError('Tag/versionName mismatch or invalid versionCode.')
    current = tuple(map(int, version.split('.')))
    previous = []
    for candidate in capture(['git', 'tag', '--list', 'v*']).decode().splitlines():
        if re.fullmatch(r'v[0-9]+\.[0-9]+\.[0-9]+', candidate):
            numbers = tuple(map(int, candidate[1:].split('.')))
            if numbers < current:previous.append((numbers, candidate))
    if previous:
        previous_tag = max(previous)[1]
        _, old_code = parse_version(capture(['git', 'show', f'{previous_tag}:app/build.gradle']).decode())
        if code <= old_code:
            raise ReleaseError('versionCode must increase relative to the previous version tag.')
    return {'tag':tag, 'commit':commit, 'version':version, 'version_code':code}


def github_outputs(values):
    output = os.environ.get('GITHUB_OUTPUT')
    if output:
        with open(output, 'a') as stream:
            for name, value in values.items():
                if '\n' in str(value):raise ReleaseError('Invalid multiline job output.')
                stream.write(f'{name}={value}\n')


def run_checks():
    sdk, _ = sdk_tools()
    env = os.environ.copy(); env['ANDROID_HOME'] = str(sdk)
    for command in (['python3', 'scripts/test-release.py'],
                    ['python3', 'scripts/test-ci-signing.py'],
                    ['bash', 'scripts/test-core.sh'],
                    ['bash', 'gradlew', '--no-daemon', ':app:assembleRelease', ':app:lintRelease'],
                    ['python3', 'scripts/test-providers.py']):
        subprocess.run(command, cwd=ROOT, env=env, stdin=subprocess.DEVNULL, check=True)


def unsigned_build(output, tag):
    context = tag_context(tag)
    if capture(['git','rev-parse','HEAD']).decode().strip() != context['commit']:
        raise ReleaseError('Checkout the validated tag commit before building.')
    run_checks()
    _, tools = sdk_tools()
    apk = ROOT / 'app/build/outputs/apk/release/app-release-unsigned.apk'
    validate_badging(capture([tools/'aapt','dump','badging',apk]).decode(), context['version'], context['version_code'])
    if capture(['git','status','--porcelain']).strip():raise ReleaseError('Unsigned release source tree must be clean.')
    output.mkdir(parents=True, exist_ok=True)
    release_notes(context['version'], output/'release-notes.md')
    shutil.copyfile(apk,output/'app-release-unsigned.apk')
    context['unsignedSha256'] = sha256(apk)
    (output/'context.json').write_text(json.dumps(context,indent=2)+'\n')
    print('Unsigned release verified; no signing credentials used.')


def verify_unsigned(directory, tag, commit):
    context = tag_context(tag)
    if context['commit'] != commit or capture(['git','rev-parse','HEAD']).decode().strip() != commit:
        raise ReleaseError('Unsigned artifact commit does not match the reviewed checkout.')
    metadata = json.loads((directory/'context.json').read_text())
    if any(metadata.get(name)!=value for name,value in context.items()):
        raise ReleaseError('Unsigned artifact metadata does not match the validated tag.')
    apk=directory/'app-release-unsigned.apk'
    if metadata.get('unsignedSha256') != sha256(apk):raise ReleaseError('Unsigned APK checksum mismatch.')
    _,tools=sdk_tools()
    validate_badging(capture([tools/'aapt','dump','badging',apk]).decode(),context['version'],context['version_code'])
    print('Unsigned artifact commit, checksum and manifest verified.')


def release_notes(version, output):
    changelog=(ROOT/'CHANGELOG.md').read_text()
    match=re.search(r'^## '+re.escape(version)+r'\s*\n(.*?)(?=^## |\Z)',changelog,re.M|re.S)
    if not match or not match[1].strip():raise ReleaseError('Add a changelog entry for the release version.')
    output.parent.mkdir(parents=True,exist_ok=True)
    output.write_text('SELFonium '+version+'\n\nAndroid 8.0+ (API 26). Download the signed APK below.\n\n'+match[1].strip()+'\n\nSee README and docs/FEATURES.md at this tag for supported features and limitations.\n')


def validate_badging(badging, version, code):
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging, re.M)
    if not package or package[1] != PACKAGE or int(package[2]) != code or package[3] != version:
        raise ReleaseError('APK package/version does not match the source configuration.')
    if 'application-debuggable' in badging:
        raise ReleaseError('Refusing a debuggable APK.')
    if "sdkVersion:'26'" not in badging or "targetSdkVersion:'35'" not in badging:
        raise ReleaseError('Unexpected minimum or target Android SDK.')


def verify_apk(apk, tools, version, code):
    validate_badging(capture([tools / 'aapt', 'dump', 'badging', apk]).decode(), version, code)
    output = capture([tools / 'apksigner', 'verify', '--verbose', '--print-certs', apk]).decode()
    signers = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})', output)
    if [value.lower() for value in signers] != [SIGNER_SHA256]:
        raise ReleaseError('APK signer does not match the existing SELFonium release series.')
    capture([tools / 'zipalign', '-c', '-P', '16', '4', apk])
    return sha256(apk)


def build(signing, output):
    certificate(signing)
    source_version()
    run_checks()
    package_apk(signing, output)


def package_apk(signing, output, unsigned=None):
    certificate(signing)
    _, tools = sdk_tools()
    version, code = source_version()
    unsigned = unsigned or ROOT / 'app/build/outputs/apk/release/app-release-unsigned.apk'
    validate_badging(capture([tools / 'aapt', 'dump', 'badging', unsigned]).decode(), version, code)
    output.mkdir(parents=True, exist_ok=True)
    apk = output / f'SELFonium-v{version}.apk'
    with tempfile.TemporaryDirectory(prefix='.sign-', dir=output) as temporary:
        work = Path(temporary); aligned = work / 'aligned.apk'; signed = work / apk.name
        capture([tools / 'zipalign', '-f', '-P', '16', '4', unsigned, aligned])
        # PKCS12 uses the same password for store and private key. Password is never an argument.
        capture([tools / 'apksigner', 'sign', '--ks', signing.keystore,
                 '--ks-key-alias', signing.alias, '--ks-pass', 'file:' + str(signing.password),
                 '--out', signed, aligned])
        digest = verify_apk(signed, tools, version, code)
        if apk.exists():
            if sha256(apk) != digest:
                raise ReleaseError('A different APK already exists for this version. Choose a new output directory or version.')
        else:
            os.replace(signed, apk)
        checksum = output / (apk.name + '.sha256')
        checksum.write_text(f'{digest}  {apk.name}\n')
        commit = capture(['git', 'rev-parse', 'HEAD']).decode().strip()
        dirty = bool(capture(['git', 'status', '--porcelain']).strip())
        (output / (apk.stem + '.json')).write_text(json.dumps({
            'package': PACKAGE, 'version': version, 'versionCode': code,
            'sha256': digest, 'signerSha256': SIGNER_SHA256,
            'gitCommit': commit, 'workingTreeDirty': dirty,
            'unsignedSha256': sha256(unsigned), 'debuggable': False,
        }, indent=2) + '\n')
    print(f'APK: {apk}\nSHA-256: {digest}\nChecksum: {checksum}')
    github_outputs({'sha256':digest, 'apk_name':apk.name})
    if dirty:
        print('NOTICE: built from a working tree with uncommitted changes; recorded in the JSON manifest.')


def backup(signing, destination):
    certificate(signing)
    destination = outside_repository(destination)
    destination.mkdir(parents=True, mode=0o700, exist_ok=False)
    os.chmod(destination, 0o700)
    copied = destination / 'selfonium-signing.p12'
    shutil.copyfile(signing.keystore, copied); os.chmod(copied, 0o600)
    if sha256(copied) != sha256(signing.keystore):
        raise ReleaseError('Backup checksum mismatch.')
    certificate(Signing(copied, signing.password, signing.alias))
    (destination / 'manifest.json').write_text(json.dumps({
        'file': copied.name, 'keystoreSha256': sha256(copied),
        'certificateSha256': SIGNER_SHA256, 'alias': signing.alias,
        'createdUtc': datetime.now(timezone.utc).isoformat(),
    }, indent=2) + '\n')
    (destination / 'RESTORE.txt').write_text(
        'This encrypted PKCS12 contains the CURRENT SELFonium signing key, not a new key.\n'
        'Keep the password separately in a password manager or another protected location.\n'
        'The password is deliberately not included in this backup directory.\n'
        'Restore the .p12 and password file outside the Git repository with mode 600.\n'
        'Configure keystore, password_file and alias in the local signing.json.\n'
        'Run bash scripts/build-release.sh and verify the signer before publishing.\n'
        'A copy on the same disk does not protect against disk loss.\n')
    print(f'Encrypted backup: {destination}\nVerified certificate: {SIGNER_SHA256}\nPassword is NOT included; keep it separately.')


def main():
    os.umask(0o077)
    if not os.environ.get('JAVA_HOME') and Path('/usr/lib/jvm/java-21-openjdk').is_dir():
        os.environ['JAVA_HOME'] = '/usr/lib/jvm/java-21-openjdk'
    # Android tools may prefer java on PATH over JAVA_HOME.
    if os.environ.get('JAVA_HOME'):
        os.environ['PATH'] = str(Path(os.environ['JAVA_HOME']) / 'bin') + os.pathsep + os.environ.get('PATH', '')
    config_home = Path(os.environ.get('XDG_CONFIG_HOME', Path.home() / '.config'))
    state_home = Path(os.environ.get('XDG_STATE_HOME', Path.home() / '.local/state'))
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['build', 'backup', 'preflight', 'unsigned', 'verify-unsigned', 'sign', 'verify', 'notes'])
    parser.add_argument('--config', type=Path, default=config_home / 'selfonium/signing.json')
    parser.add_argument('--output-dir', type=Path, default=ROOT / 'build/releases')
    parser.add_argument('--destination', type=Path, default=state_home / 'selfonium/signing-backups' / datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ'))
    parser.add_argument('--tag')
    parser.add_argument('--commit')
    parser.add_argument('--unsigned', type=Path)
    parser.add_argument('--apk', type=Path)
    parser.add_argument('--input-dir', type=Path)
    args = parser.parse_args()
    try:
        if args.action=='preflight':
            if not args.tag:raise ReleaseError('--tag is required.')
            context=tag_context(args.tag);github_outputs(context);print(json.dumps(context))
        elif args.action=='unsigned':
            if not args.tag:raise ReleaseError('--tag is required.')
            unsigned_build(args.output_dir.resolve(),args.tag)
        elif args.action=='verify-unsigned':
            if not all((args.input_dir,args.tag,args.commit)):raise ReleaseError('--input-dir, --tag and --commit are required.')
            verify_unsigned(args.input_dir,args.tag,args.commit)
        elif args.action=='verify':
            if not args.apk:raise ReleaseError('--apk is required.')
            _,tools=sdk_tools();version,code=source_version()
            digest=verify_apk(args.apk,tools,version,code);github_outputs({'sha256':digest});print('Verified APK SHA-256: '+digest)
        elif args.action=='notes':
            version,_=source_version();release_notes(version,args.output_dir/'release-notes.md')
        else:
            signing = load_signing(args.config)
            if args.action=='build':build(signing,args.output_dir.resolve())
            elif args.action=='sign':
                if not args.unsigned:raise ReleaseError('--unsigned is required.')
                package_apk(signing,args.output_dir.resolve(),args.unsigned)
            else:backup(signing,args.destination)
    except (ReleaseError, OSError, ValueError, subprocess.CalledProcessError) as error:
        print(f'Release failed: {error}', file=sys.stderr); return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
