#!/usr/bin/env python3
"""Exercise CI secret handling using fake values and temporary directories."""
import base64
import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('ci_signing',Path(__file__).with_name('ci-signing.py'))
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)


class CiSigningTest(unittest.TestCase):
    def setUp(self):
        self.work=tempfile.TemporaryDirectory(prefix='selfonium-ci-secret-test-')
        self.root=Path(self.work.name)
        self.values={'RUNNER_TEMP':str(self.root),'ANDROID_KEYSTORE_BASE64':base64.b64encode(b'fake-encrypted-key').decode(),
                     'ANDROID_KEYSTORE_PASSWORD':'fixture-secret','ANDROID_KEY_ALIAS':'fixture','GITHUB_OUTPUT':str(self.root/'outputs')}

    def tearDown(self):self.work.cleanup()

    def test_prepare_permissions_outputs_and_cleanup(self):
        with patch.dict(os.environ,self.values,clear=True):
            config=module.prepare();data=json.loads(config.read_text())
            self.assertEqual(config.parent.stat().st_mode&0o777,0o700)
            for name in ('keystore','password_file'):
                path=Path(data[name]);self.assertEqual(path.stat().st_mode&0o777,0o600)
            self.assertEqual(config.stat().st_mode&0o777,0o600)
            self.assertNotIn('fixture-secret',(self.root/'outputs').read_text())
            module.cleanup();self.assertFalse(config.parent.exists())

    def test_missing_or_malformed_secrets_are_not_logged(self):
        for overrides in ({'ANDROID_KEYSTORE_PASSWORD':''},{'ANDROID_KEYSTORE_BASE64':'invalid%%%fixture-secret'},{'ANDROID_KEYSTORE_PASSWORD':'fixture-secret\nsecond'}):
            env=self.values|overrides
            with patch.dict(os.environ,env,clear=True),patch('sys.argv',['ci-signing.py','prepare']),contextlib.redirect_stderr(io.StringIO()) as errors:
                self.assertEqual(module.main(),1)
                self.assertNotIn('fixture-secret',errors.getvalue())
                self.assertFalse((self.root/'selfonium-release-signing').exists())

    def test_existing_directory_not_overwritten(self):
        directory=self.root/'selfonium-release-signing';directory.mkdir();(directory/'sentinel').write_text('keep')
        with patch.dict(os.environ,self.values,clear=True):
            with self.assertRaises(FileExistsError):module.prepare()
        self.assertEqual((directory/'sentinel').read_text(),'keep')

    def test_checkout_location_rejected(self):
        repo=Path(module.__file__).resolve().parents[1]
        with patch.dict(os.environ,self.values|{'RUNNER_TEMP':str(repo/'build')},clear=True):
            with self.assertRaises(ValueError):module.prepare()


if __name__=='__main__':unittest.main()
