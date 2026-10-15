#!/usr/bin/env python3
"""Exercise production HTTP/provider code against a local fixture (no credentials/device required).

Requires the existing Gradle OkHttp caches. A small pinned desktop org.json JAR is
kept under .tooling; Android credential storage is replaced only in this
JVM fixture. This does not test Android storage, rendering or a real server.
"""
from pathlib import Path
import hashlib, os, subprocess, tempfile, urllib.request
root=Path(__file__).resolve().parents[1]
jar=root/'.tooling/test-libs/json-20240303.jar'
digest='3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed'
if not jar.is_file():
    jar.parent.mkdir(parents=True,exist_ok=True)
    data=urllib.request.urlopen('https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar',timeout=30).read()
    if hashlib.sha256(data).hexdigest()!=digest:raise SystemExit('JSON dependency integrity mismatch')
    jar.write_bytes(data)
if hashlib.sha256(jar.read_bytes()).hexdigest()!=digest:raise SystemExit('Cached JSON dependency integrity mismatch')
modules=Path(os.environ.get('GRADLE_USER_HOME',str(root/'.tooling/gradle-home')))/'caches/modules-2/files-2.1'
cp=[jar]
for group in ['com.squareup.okhttp3','com.squareup.okio','org.jetbrains.kotlin','org.jetbrains']:
    cp+=list((modules/group).glob('*/*/*/*.jar'))
assert any('okhttp-' in p.name for p in cp),'Build once to cache OkHttp first'
with tempfile.TemporaryDirectory(prefix='selfonium-provider-tests-') as temporary:
    work=Path(temporary)
    store=work/'app/selfonium/data/SourceStore.java';store.parent.mkdir(parents=True)
    store.write_text('package app.selfonium.data; public class SourceStore {public void save(app.selfonium.core.Source source){} public app.selfonium.core.Source get(String id){return null;}}')
    files=[*map(str,(root/'app/src/main/java/app/selfonium/core').glob('*.java')),str(root/'app/src/main/java/app/selfonium/net/Http.java'),str(root/'app/src/main/java/app/selfonium/net/SubsonicClient.java'),str(root/'app/src/main/java/app/selfonium/data/SourceRecords.java'),str(store),*map(str,(root/'app/src/test/java/app/selfonium/net').glob('*.java'))]
    files += [str(root/'app/src/main/java/app/selfonium/update'/name) for name in ('UpdatePolicy.java','ReleaseInfo.java','UpdateHttp.java','UpdateDownload.java')]
    files += list(map(str,(root/'app/src/test/java/app/selfonium/update').glob('*.java')))
    classpath=os.pathsep.join(map(str,cp))
    subprocess.run(['javac','--release','17','-cp',classpath,'-d',str(work),*files],check=True)
    subprocess.run(['java','-cp',str(work)+os.pathsep+classpath,'app.selfonium.net.ProviderSyncTest'],check=True)
    subprocess.run(['java','-cp',str(work)+os.pathsep+classpath,'app.selfonium.net.CatalogSyncTest'],check=True)
    subprocess.run(['java','-cp',str(work)+os.pathsep+classpath,'app.selfonium.net.HttpCancelTest'],check=True)

    subprocess.run(['java','-cp',str(work)+os.pathsep+classpath,'app.selfonium.update.UpdateTest'],check=True)
