#!/usr/bin/env python3
"""Offline resource parity and compatibility checks for the six UI languages."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
res = root / 'app/src/main/res'
languages = ('en', 'uk', 'pl', 'de', 'fr', 'es')
checks = 0

def require(condition, message):
    global checks
    checks += 1
    assert condition, message

def resources(language):
    folder = res / ('values' if language == 'en' else f'values-{language}')
    result = {}
    for filename in ('strings.xml', 'updates.xml', 'storage.xml'):
        for item in ET.parse(folder / filename).getroot():
            key = (item.tag, item.get('name'))
            require(key not in result, f'Duplicate {language}: {key}')
            result[key] = item
    return result

def signature(text):
    return sorted(re.findall(r'%(?:\d+\$)?[-+0 #]*\d*(?:\.\d+)?[a-zA-Z]', text or ''))

reference = resources('en')
for language in languages:
    translated = resources(language)
    require(translated.keys() == reference.keys(), f'Resource parity: {language}')
    for key, item in translated.items():
        original = reference[key]
        values = list(item) if item.tag != 'string' else [item]
        for value in values:
            require(bool((value.text or '').strip().strip('"')), f'Empty {language} {key}')
            expected = original.text if item.tag == 'string' else original[0].text
            require(signature(value.text) == signature(expected), f'Format mismatch {language} {key}')
        if item.tag == 'plurals':
            required = {'one', 'few', 'many', 'other'} if language in ('uk','pl') else {'one','other'}
            require(required <= {value.get('quantity') for value in values}, f'Plural rules {language} {key}')
        elif item.tag == 'string-array':
            require(len(item) == len(original) == 18, f'Preset indices changed: {language}')
ns = '{http://schemas.android.com/apk/res/android}'
config = ET.parse(res / 'xml/locales_config.xml').getroot()
require(tuple(item.get(ns+'name') for item in config) == languages, 'Locale config')
manifest = ET.parse(root / 'app/src/main/AndroidManifest.xml').getroot()
require(manifest.find('application').get(ns+'localeConfig') == '@xml/locales_config', 'Manifest locale declaration')
legacy = ET.parse(res/'values/locale_legacy.xml').getroot()
keys, ids = list(legacy)
require(len(keys) == len(ids) == 305, 'Legacy presentation mapping changed')
require(keys.get('translatable') == ids.get('translatable') == 'false', 'Internal keys must not be translated')
for item in ids:
    require(('string', item.text.removeprefix('@string/')) in reference, 'Unresolved legacy string')
string_names = {name for kind,name in reference if kind == 'string'}
for path in (root/'app/src/main/java').rglob('*.java'):
    for name in re.findall(r'R\.string\.(\w+)', path.read_text()):
        require(name in string_names, f'Missing R.string.{name}: {path.name}')
main = (root/'app/src/main/java/app/selfonium/ui/MainActivity.java').read_text()
tabs=(root/'app/src/main/java/app/selfonium/ui/LibraryTabs.java').read_text()
sections=re.search(r'SECTIONS=\{([^}]+)\}',tabs)[1]
legacy_keys={item.text.strip(chr(34)) for item in keys}
require(all(key in legacy_keys for key in re.findall(r'"([^"]+)"',sections)), 'Library tabs require translated stable navigation keys')
eq = (root/'app/src/main/java/app/selfonium/ui/EqualizerDialog.java').read_text()
require('apply(EqualizerPresets.gains(at),0,EqualizerPresets.NAMES[at])' in eq, 'Preset storage must use stable keys')
require('if(Build.VERSION.SDK_INT>=33)' in (root/'app/src/main/java/app/selfonium/locale/AppLanguage.java').read_text(), 'Framework locale APIs must be guarded')
print(f'PASS: {checks} localization resource, placeholder, plural and compatibility checks ({len(languages)} languages)')
