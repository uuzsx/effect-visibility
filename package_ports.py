"""Validate and package the six independent NeoForge ports without local game data."""
from pathlib import Path
import hashlib
import json
import shutil
import struct
import tomllib
import xml.etree.ElementTree as ET
import zipfile

root = Path(__file__).resolve().parent
output = root.parent.parent / 'outputs'
output.mkdir(exist_ok=True)
versions = ['1.21.1', '1.21.2', '26.1.1', '26.1.2', '26.2', '26.3']
release = '0.3.0'
entries = []
for mc in versions:
    project = root / 'versions' / mc
    properties = dict(line.split('=', 1) for line in (project / 'gradle.properties').read_text(encoding='utf-8').splitlines()
                      if '=' in line and not line.startswith('#'))
    assert properties['mod_version'] == release and properties['minecraft_version'] == mc
    neo = properties['neo_version']
    java = 21 if mc.startswith('1.') else 25
    source_jar = project / f'build/libs/effect-visibility-mc{mc}-neoforge-{release}.jar'
    with zipfile.ZipFile(source_jar) as jar:
        meta = tomllib.loads(jar.read('META-INF/neoforge.mods.toml').decode('utf-8'))
        assert meta['mods'][0]['version'] == release
        assert meta['mods'][0]['authors'] == '幼幼紫'
        assert '状态效果隐藏' in meta['mods'][0]['description']
        assert jar.read(meta['mods'][0]['logoFile']) == (root / 'assets/logo.png').read_bytes()
        deps = {d['modId']: d for d in meta['dependencies']['effect_visibility']}
        assert deps['minecraft']['versionRange'] == f'[{mc}]'
        assert deps['neoforge']['versionRange'] == f'[{neo},)'
        assert all(d['side'] == 'CLIENT' for d in deps.values())
        assert struct.unpack('>H', jar.read('dev/effectvisibility/EffectVisibility.class')[6:8])[0] == java + 44
        assert 'dev/effectvisibility/EffectPickerPolicy.class' in jar.namelist()
        assert not any('smoke' in name or 'examplemod' in name for name in jar.namelist())
        mixins = json.loads(jar.read('effect_visibility.mixins.json'))
        assert len(mixins['client']) == 2 and not mixins.get('mixins')
        assert mixins['compatibilityLevel'] == f'JAVA_{java}'
        for locale in ['en_us', 'zh_cn']:
            json.loads(jar.read(f'assets/effect_visibility/lang/{locale}.json'))
    suites = [ET.parse(project / f'build/test-results/test/TEST-dev.effectvisibility.{name}.xml').getroot()
              for name in ['VisibilityRulesTest', 'EffectPickerPolicyTest']]
    assert sum(int(s.get('tests')) for s in suites) == 11
    assert all(s.get('failures') == '0' and s.get('errors') == '0' for s in suites)
    build_log = (project / 'build-result.log').read_text(encoding='utf-8', errors='replace')
    log = (project / 'smoke-console.log').read_text(encoding='utf-8', errors='replace')
    assert 'BUILD SUCCESSFUL' in build_log
    assert 'BUILD SUCCESSFUL' in log and 'SMOKE_ASSERTIONS_PASSED' in log and 'SMOKE_SCREENSHOT' in log
    assert 'SMOKE_FAILED' not in log
    screenshot = project / 'run-smoke/screenshots/effect-visibility-settings.png'
    assert screenshot.is_file()
    # Check transformed classes, not just successful mixin configuration parsing.
    gui_class = 'net/minecraft/client/gui/Hud.class' if mc in ['26.2', '26.3'] else 'net/minecraft/client/gui/Gui.class'
    inventory_class = ('net/minecraft/client/gui/screens/inventory/EffectRenderingInventoryScreen.class'
                       if mc == '1.21.1' else 'net/minecraft/client/gui/screens/inventory/EffectsInInventory.class')
    for target in [gui_class, inventory_class]:
        transformed = (project / 'run-smoke/.mixin.out/class' / target).read_bytes()
        assert b'dev/effectvisibility/EffectVisibility' in transformed and b'effectVisibility$filter' in transformed
    filename = f'effect-visibility-{release}-mc{mc}-neoforge.jar'
    shutil.copyfile(source_jar, output / filename)
    entries.append({'minecraft': mc, 'neoforge_minimum': neo, 'java': java, 'file': filename,
                    'sha256': hashlib.sha256((output / filename).read_bytes()).hexdigest(), 'tests': 11,
                    'client_smoke': 'passed', 'screenshot': str(screenshot.relative_to(root))})

checksums = ''.join(f"{e['sha256']}  {e['file']}\n" for e in entries)
(output / 'effect-visibility-0.3.0-SHA256SUMS.txt').write_text(checksums, encoding='utf-8')
(output / 'effect-visibility-0.3.0-使用说明.txt').write_text((root / 'README.md').read_text(encoding='utf-8'), encoding='utf-8-sig')
verification = {'release': release, 'versions': entries,
                'scope': 'Each minimum NeoForge version: compile, 11 unit tests, client startup, both mixin targets transformed, UI save/cancel/resize/search/hide-all/reset, registered mod effect filtering without mutation.',
                'limits': 'No multiplayer or world-map interaction testing. Xaero ID exclusion is covered by unit tests on all versions; actual Xaero pair was tested previously on MC 26.1.2 with version 0.2.1.'}
(output / 'effect-visibility-0.3.0-verification.json').write_text(json.dumps(verification, ensure_ascii=False, indent=2), encoding='utf-8')
with zipfile.ZipFile(output / 'effect-visibility-0.3.0-all-neoforge.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
    archive.write(root / 'assets/logo.png', 'assets/logo.png')
    for e in entries:
        archive.write(output / e['file'], e['file'])
        archive.write(root / e['screenshot'], f"previews/mc{e['minecraft']}.png")
    for name in ['effect-visibility-0.3.0-使用说明.txt', 'effect-visibility-0.3.0-SHA256SUMS.txt', 'effect-visibility-0.3.0-verification.json']:
        archive.write(output / name, name)

source_files = ['build.gradle', 'settings.gradle', 'gradle.properties', 'gradlew', 'gradlew.bat',
                'LICENSE', 'TEMPLATE_LICENSE.txt', '.gitignore', '.gitattributes']
with zipfile.ZipFile(output / 'effect-visibility-0.3.0-all-source.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
    archive.write(root / 'assets/logo.png', 'effect-visibility-ports/assets/logo.png')
    for name in ['README.md', 'LICENSE', '.gitignore', 'build-all.ps1', 'package_ports.py']:
        archive.write(root / name, 'effect-visibility-ports/' + name)
    for mc in versions:
        project = root / 'versions' / mc
        prefix = f'effect-visibility-ports/versions/{mc}/'
        for name in source_files:
            archive.write(project / name, prefix + name)
        for directory in ['src', 'gradle']:
            for file in sorted((project / directory).rglob('*')):
                if file.is_file(): archive.write(file, prefix + file.relative_to(project).as_posix())
for entry in entries:
    print(f"{entry['minecraft']}: NeoForge >= {entry['neoforge_minimum']}; 11 tests; client smoke; transformed both targets; packaged")
print('All six ports packaged. No test mods, game data, or third-party jars in release/source archives.')
