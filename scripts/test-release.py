#!/usr/bin/env python3
"""Release policy checks; no real signing secrets or Android device required."""
import contextlib
import io
import json
import os
import subprocess
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import release


class ReleasePolicyTest(unittest.TestCase):
    def setUp(self):
        self.work = tempfile.TemporaryDirectory(prefix='selfonium signing tests ')
        self.root = Path(self.work.name)
        self.store = self.root / 'signing.p12'
        self.password = self.root / 'store-password'
        self.config = self.root / 'signing.json'
        self.store.write_bytes(b'fixture-encrypted-store')
        self.password.write_text('fixture-secret-not-a-real-key\n')
        self.config.write_text(json.dumps({'keystore':self.store.name,'password_file':self.password.name,'alias':'fixture'}))
        for path in (self.store, self.password, self.config):path.chmod(0o600)

    def tearDown(self):
        self.work.cleanup()

    def test_private_config_with_spaces_and_relative_paths(self):
        signing = release.load_signing(self.config)
        self.assertEqual(signing.keystore,self.store)
        self.assertEqual(signing.password,self.password)

    def test_group_readable_secret_rejected(self):
        for path in (self.store,self.password,self.config):
            path.chmod(0o640)
            with self.assertRaises(release.ReleaseError):release.load_signing(self.config)
            path.chmod(0o600)

    def test_repository_secret_rejected_even_if_ignored(self):
        with self.assertRaises(release.ReleaseError):release.private_file(release.ROOT/'build/signing.p12')

    def test_multiline_or_empty_password_rejected(self):
        for content in ('','\n','first\nsecond\n'):
            self.password.write_text(content)
            with self.assertRaises(release.ReleaseError):release.load_signing(self.config)

    def test_invalid_config_does_not_echo_content(self):
        self.config.write_text('{fixture-secret-not-a-real-key')
        with self.assertRaises(release.ReleaseError) as error:release.load_signing(self.config)
        self.assertNotIn('fixture-secret',str(error.exception))

    def test_wrong_signer_rejected(self):
        with patch.object(release,'capture',return_value=b'unrelated-certificate'):
            with self.assertRaisesRegex(release.ReleaseError,'certificate differs'):
                release.certificate(release.load_signing(self.config))

    def test_tool_errors_do_not_echo_secrets(self):
        result=type('Result',(),{'returncode':1,'stdout':b'fixture-secret','stderr':b'fixture-secret'})()
        with patch.object(release.subprocess,'run',return_value=result):
            with self.assertRaises(release.ReleaseError) as error:release.capture(['keytool','-list'])
        self.assertNotIn('fixture-secret',str(error.exception))

    def test_valid_release_manifest(self):
        release.validate_badging("package: name='app.selfonium' versionCode='9' versionName='0.6.2'\nsdkVersion:'26'\ntargetSdkVersion:'35'\n",'0.6.2',9)

    def test_debug_wrong_package_version_or_sdk_rejected(self):
        good="package: name='app.selfonium' versionCode='9' versionName='0.6.2'\nsdkVersion:'26'\ntargetSdkVersion:'35'\n"
        for invalid in (good+'application-debuggable\n',good.replace('app.selfonium','other.app'),good.replace("versionCode='9'","versionCode='8'"),good.replace("versionName='0.6.2'","versionName='0.6.1'"),good.replace("sdkVersion:'26'","sdkVersion:'28'"),good.replace("targetSdkVersion:'35'","targetSdkVersion:'34'")):
            with self.assertRaises(release.ReleaseError):release.validate_badging(invalid,'0.6.2',9)

    def test_extra_apk_signer_rejected(self):
        valid="package: name='app.selfonium' versionCode='9' versionName='0.6.2'\nsdkVersion:'26'\ntargetSdkVersion:'35'\n"
        certs=f'Signer #1 certificate SHA-256 digest: {release.SIGNER_SHA256}\nSigner #2 certificate SHA-256 digest: '+('a'*64)
        with patch.object(release,'capture',side_effect=[valid.encode(),certs.encode()]):
            with self.assertRaises(release.ReleaseError):release.verify_apk(self.root/'app.apk',self.root,'0.6.2',9)

    def test_backup_excludes_password_and_refuses_overwrite(self):
        target=self.root/'backup'
        with patch.object(release,'certificate',return_value=release.SIGNER_SHA256),contextlib.redirect_stdout(io.StringIO()):
            release.backup(release.load_signing(self.config),target)
            with self.assertRaises(FileExistsError):release.backup(release.load_signing(self.config),target)
        self.assertEqual((target/'selfonium-signing.p12').read_bytes(),self.store.read_bytes())
        self.assertEqual((target/'selfonium-signing.p12').stat().st_mode & 0o777,0o600)
        self.assertEqual(target.stat().st_mode & 0o777,0o700)
        self.assertEqual(set(p.name for p in target.iterdir()),{'selfonium-signing.p12','manifest.json','RESTORE.txt'})
        for path in target.iterdir():self.assertNotIn(b'fixture-secret',path.read_bytes())


class TagPolicyTest(unittest.TestCase):
    def setUp(self):
        self.work=tempfile.TemporaryDirectory(prefix='selfonium-tag-policy-');self.root=Path(self.work.name)
        self.git('init','-q','-b','main');self.git('config','user.name','Test Fixture');self.git('config','user.email','fixture@example.invalid')
        (self.root/'app').mkdir()
        self.version('0.6.1',8);self.git('tag','v0.6.1')
        self.version('0.6.2',9);self.git('tag','v0.6.2');self.git('update-ref','refs/remotes/origin/main','HEAD')
        self.patch=patch.object(release,'ROOT',self.root);self.patch.start()

    def tearDown(self):self.patch.stop();self.work.cleanup()

    def git(self,*args):return subprocess.check_output(['git','-C',str(self.root),*args],stderr=subprocess.DEVNULL).decode().strip()

    def version(self,name,code):
        (self.root/'app/build.gradle').write_text(f"versionName '{name}'\nversionCode {code}\n")
        self.git('add','.');self.git('commit','-qm','fixture')

    def test_reviewed_matching_tag(self):
        context=release.tag_context('v0.6.2')
        self.assertEqual(context['version_code'],9);self.assertEqual(context['commit'],self.git('rev-parse','HEAD'))

    def test_invalid_or_mismatched_tag(self):
        for tag in ('main','v01.6.2','v0.6.2-beta','v0.6.2;echo secret',''):
            with self.assertRaises(release.ReleaseError):release.tag_context(tag)
        self.git('tag','v0.6.3')
        with self.assertRaisesRegex(release.ReleaseError,'mismatch'):release.tag_context('v0.6.3')

    def test_unreviewed_commit_rejected(self):
        self.git('checkout','-qb','unreviewed');self.version('0.7.0',10);self.git('tag','v0.7.0')
        with self.assertRaisesRegex(release.ReleaseError,'reachable'):release.tag_context('v0.7.0')

    def test_reused_version_code_rejected(self):
        self.version('0.6.3',9);self.git('tag','v0.6.3');self.git('update-ref','refs/remotes/origin/main','HEAD')
        with self.assertRaisesRegex(release.ReleaseError,'increase'):release.tag_context('v0.6.3')

    def test_release_notes_require_actual_changelog_entry(self):
        (self.root/'CHANGELOG.md').write_text('# Changelog\n\n## Unreleased\n- Future.\n\n## 0.6.2\n- Current.\n\n## 0.6.1\n- Older.\n')
        output=self.root/'notes.md';release.release_notes('0.6.2',output)
        self.assertIn('Current.',output.read_text());self.assertNotIn('Future.',output.read_text());self.assertNotIn('Older.',output.read_text())
        with self.assertRaises(release.ReleaseError):release.release_notes('0.6.3',output)

    def test_unsigned_artifact_tampering_rejected(self):
        context=release.tag_context('v0.6.2');directory=self.root/'unsigned';directory.mkdir()
        (directory/'app-release-unsigned.apk').write_bytes(b'tampered')
        (directory/'context.json').write_text(json.dumps(context|{'unsignedSha256':'wrong'}))
        with self.assertRaisesRegex(release.ReleaseError,'checksum'):release.verify_unsigned(directory,'v0.6.2',context['commit'])


if __name__=='__main__':
    unittest.main()
