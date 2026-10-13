#!/usr/bin/env python3
"""Type-check Android sources and run offline lint using a prior Gradle build's caches.

Does not package an APK or replace scripts/build.sh. Requires the pinned SDK,
Gradle dependency caches and lint model already created by a successful build.
"""
from pathlib import Path
import os
import re
import xml.etree.ElementTree as ET
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
sdk = Path(os.environ.get('ANDROID_HOME', root / '.tooling/android-sdk'))
cache = Path(os.environ.get('GRADLE_USER_HOME', root / '.tooling/gradle-home')) / 'caches'
modules = cache / 'modules-2/files-2.1'
javac = shutil.which('javac')
if not os.environ.get('JAVA_HOME') and not javac:
    raise SystemExit('Install JDK 17 or 21, or set JAVA_HOME.')
jdk = Path(os.environ['JAVA_HOME']) if os.environ.get('JAVA_HOME') else Path(javac).resolve().parents[1]
model_source = root / 'app/build/intermediates/lint_report_lint_model/debug/generateDebugLintReportModel'
if not (sdk / 'platforms/android-35/android.jar').is_file() or not model_source.is_dir():
    raise SystemExit('Run scripts/build.sh once to populate Android SDK/dependencies/lint metadata.')

classpath = [sdk / 'platforms/android-35/android.jar']
classpath += list((cache / '8.11.1/transforms').glob('*/transformed/*/jars/classes.jar'))
for group in ['com.google.guava', 'com.squareup.okhttp3', 'com.squareup.okio',
              'org.jetbrains.kotlin', 'org.jetbrains', 'androidx.annotation',
              'com.google.errorprone', 'com.google.j2objc']:
    classpath += [p for p in (modules / group).glob('*/*/*/*.jar')
                  if not ('guava-32' in p.name or p.name.startswith('kotlin-gradle'))]
classpath += list((root / 'app/build/intermediates').rglob('R.jar'))
with tempfile.TemporaryDirectory(prefix='selfonium-android-') as temporary:
    work = Path(temporary)
    classes = work / 'classes'
    classes.mkdir()
    subprocess.run([str(jdk / 'bin/javac'), '--release', '17', '-proc:none',
                    '-cp', os.pathsep.join(map(str, classpath)), '-d', str(classes),
                    *map(str, (root / 'app/src/main/java').rglob('*.java'))], check=True)
    print('PASS: Android Java types compiled against SDK 35 and cached dependencies', flush=True)
    tests = list((root / 'app/src/androidTest/java').rglob('*.java'))
    if tests:
        test_classpath = [classes, *classpath, sdk / 'platforms/android-35/optional/android.test.runner.jar',
                          sdk / 'platforms/android-35/optional/android.test.base.jar']
        subprocess.run([str(jdk / 'bin/javac'), '--release', '17', '-proc:none',
                        '-cp', os.pathsep.join(map(str, test_classpath)), '-d', str(work / 'test-classes'),
                        *map(str, tests)], check=True)
        print('PASS: Android instrumentation tests type-compiled (not executed)', flush=True)
    aapt = sdk / 'build-tools/35.0.0/aapt2'
    manifest = ET.parse(root / 'app/src/main/AndroidManifest.xml')
    manifest.getroot().set('package', 'app.selfonium')
    ET.register_namespace('android', 'http://schemas.android.com/apk/res/android')
    manifest.write(work / 'AndroidManifest.xml', encoding='utf-8', xml_declaration=True)
    subprocess.run([str(aapt), 'compile', '--dir', str(root / 'app/src/main/res'),
                    '-o', str(work / 'compiled-res.zip')], check=True)
    subprocess.run([str(aapt), 'link', '-o', str(work / 'resource-check.apk'),
                    '-I', str(sdk / 'platforms/android-35/android.jar'),
                    '--manifest', str(work / 'AndroidManifest.xml'), '--min-sdk-version', '26',
                    '--target-sdk-version', '35', str(work / 'compiled-res.zip')], check=True)
    print('PASS: AAPT2 resource compilation and linking (resource-only archive, not installable APK)', flush=True)
    model = work / 'model'
    shutil.copytree(model_source, model)
    variant = model / 'debug.xml'
    xml = variant.read_text().replace('build/intermediates/javac/debug/compileDebugJavaWithJavac/classes', str(classes))
    xml = xml.replace('build/intermediates/lint_partial_results/debug/lintAnalyzeDebug/out', str(work / 'partial'))
    variant.write_text(xml)
    jars = list(modules.glob('*/*/*/*/*.jar'))
    version = re.search(r"versionName '([^']+)'", (root / 'app/build.gradle').read_text()).group(1)
    report = root / 'build/reports' / f'lint-cached-{version}.txt'
    report.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run([str(jdk / 'bin/java'), '-Xmx1536m', '-cp', os.pathsep.join(map(str, jars)),
                    'com.android.tools.lint.Main', '--lint-model', str(model), '--variant', 'debug',
                    '--sdk-home', str(sdk), '--offline', '--exitcode', '--text', str(report)], check=True)
    print(f'PASS: standalone Android Lint; report: {report}', flush=True)
