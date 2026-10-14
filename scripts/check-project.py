from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
xml = list((root / 'app/src/main').rglob('*.xml'))
for path in xml:
    ET.parse(path)
manifest = ET.parse(root / 'app/src/main/AndroidManifest.xml').getroot()
ns = '{http://schemas.android.com/apk/res/android}'
for tag in ('activity', 'service'):
    for item in manifest.findall(f'./application/{tag}'):
        name = item.attrib[ns + 'name']
        if name.startswith('.'):
            assert (root / 'app/src/main/java/app/selfonium' / (name[1:].replace('.', '/') + '.java')).is_file(), name
permissions = {item.get(ns + 'name') for item in manifest.findall('uses-permission')}
assert 'com.android.vending.BILLING' not in permissions
assert 'android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK' in permissions
print(f'PASS: {len(xml)} XML resources, manifest components and no billing permission')

assert 'android.permission.REQUEST_INSTALL_PACKAGES' in permissions
provider = manifest.find('./application/provider')
assert provider is not None and provider.get(ns + 'name') == '.update.UpdateFileProvider'
assert provider.get(ns + 'exported') == 'false' and provider.get(ns + 'grantUriPermissions') == 'true'
assert provider.get(ns + 'authorities') == '${applicationId}.updates'
paths = ET.parse(root / 'app/src/main/res/xml/update_paths.xml').getroot()
assert len(paths) == 1 and paths[0].tag == 'cache-path' and paths[0].get('path') == 'updates/'
print('PASS: updater install permission and non-exported, narrowly scoped cache FileProvider')
