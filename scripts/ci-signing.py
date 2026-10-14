#!/usr/bin/env python3
"""Materialize environment-scoped CI signing secrets outside the checkout; never upload them."""
import base64
import json
import os
from pathlib import Path
import shutil
import sys


def location():
    temp = os.environ.get('RUNNER_TEMP')
    if not temp:raise ValueError('RUNNER_TEMP is required.')
    parent=Path(temp).resolve();repo=Path(__file__).resolve().parents[1]
    if parent==repo or repo in parent.parents:raise ValueError('Signing temp directory must be outside the checkout.')
    return parent/'selfonium-release-signing'


def prepare():
    encoded=os.environ.get('ANDROID_KEYSTORE_BASE64','')
    password=os.environ.get('ANDROID_KEYSTORE_PASSWORD','')
    alias=os.environ.get('ANDROID_KEY_ALIAS','')
    if not encoded or not password or not alias:raise ValueError('Required signing secrets are missing.')
    if '\n' in password or '\r' in password or '\n' in alias or '\r' in alias:raise ValueError('Signing secret format is invalid.')
    raw=base64.b64decode(''.join(encoded.split()),validate=True)
    if not raw:raise ValueError('Keystore secret is empty.')
    directory=location();directory.mkdir(mode=0o700,exist_ok=False)
    try:
        store=directory/'key.p12';secret=directory/'store-password';config=directory/'signing.json'
        for path,data in ((store,raw),(secret,(password+'\n').encode()),(config,json.dumps({'keystore':str(store),'password_file':str(secret),'alias':alias}).encode())):
            with path.open('xb') as stream:stream.write(data)
            path.chmod(0o600)
        output=os.environ.get('GITHUB_OUTPUT')
        if output:
            with open(output,'a') as stream:stream.write('config='+str(config)+'\n')
        return config
    except Exception:
        shutil.rmtree(directory);raise


def cleanup():
    directory=location()
    if directory.is_symlink():raise ValueError('Refusing a symlink signing directory.')
    if directory.exists():shutil.rmtree(directory)


def main():
    os.umask(0o077)
    try:
        if sys.argv[1:] == ['prepare']:prepare();print('Signing files prepared outside checkout.')
        elif sys.argv[1:] == ['cleanup']:cleanup();print('Temporary signing files removed.')
        else:raise ValueError('Use prepare or cleanup.')
    except Exception:
        print('CI signing setup failed; check secret configuration. Values are not logged.',file=sys.stderr);return 1
    return 0


if __name__=='__main__':sys.exit(main())
