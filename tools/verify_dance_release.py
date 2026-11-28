"""Validate the standalone dance jar and capture actual automated results."""
from pathlib import Path
import hashlib
import json
import re
import struct
import zipfile

ROOT = Path(__file__).resolve().parents[1]
JAR = ROOT / 'party-26.2/build/libs/wardendance-neoforge-26.2-1.0.0.jar'
LOG = ROOT / '.tools/dance/full-check.log'
log = LOG.read_text(encoding='utf-8', errors='replace')
assert 'BUILD SUCCESSFUL' in log, 'Full verification command must complete successfully'
animation = re.search(r'DANCE_ANIMATION_CHECKS_PASSED=(\d+)', log)
assert animation and int(animation[1]) > 0, 'Missing actual model animation check'
gametests = re.search(r'All (\d+) required tests passed', log)
assert gametests and int(gametests[1]) >= 11, 'Missing successful dedicated-server test summary'
required = {
    'META-INF/neoforge.mods.toml',
    'dev/wardendance/WardenDance.class',
    'dev/wardendance/DanceRequest.class',
    'dev/wardendance/DanceParty.class',
    'dev/wardendance/DancingWarden.class',
    'dev/wardendance/client/WardenDanceClient.class',
    'dev/wardendance/client/DancingWardenModel.class',
    'dev/wardendance/client/DancingWardenRenderer.class',
    'assets/wardendance/lang/zh_cn.json',
    'assets/wardendance/lang/en_us.json',
}
with zipfile.ZipFile(JAR) as archive:
    names = set(archive.namelist())
    assert required <= names, required - names
    assert not any('/test/' in n or 'wardendancetests' in n or n.startswith('net/minecraft/') for n in names)
    classes = [n for n in names if n.endswith('.class')]
    for name in classes:
        data = archive.read(name)
        assert data[:4] == b'\xca\xfe\xba\xbe' and struct.unpack('>H', data[6:8])[0] == 69, name
    zh = json.loads(archive.read('assets/wardendance/lang/zh_cn.json'))
    en = json.loads(archive.read('assets/wardendance/lang/en_us.json'))
    assert set(zh) == set(en)
    assert all(isinstance(value, str) and value for value in zh.values())
    metadata = archive.read('META-INF/neoforge.mods.toml').decode()
    assert 'modId="wardendance"' in metadata and 'versionRange="[26.2]"' in metadata

digest = hashlib.sha256(JAR.read_bytes()).hexdigest()
JAR.with_suffix('.jar.sha256').write_text(f'{digest}  {JAR.name}\n', encoding='ascii')
report = {
    'edition': 'Warden Dance Party', 'mod_id': 'wardendance', 'version': '1.0.0',
    'minecraft': '26.2', 'loader': 'NeoForge 26.2.0.95', 'java': 25,
    'jar': JAR.name, 'bytes': JAR.stat().st_size, 'sha256': digest,
    'dedicated_server_tests': {'passed': int(gametests[1]), 'failed': 0},
    'model_animation_assertions': int(animation[1]),
    'archive_validation': {'required_entries': len(required), 'java25_classes': len(classes),
                           'no_bundled_tests_or_minecraft': True, 'language_keys_match': True},
    'manual_client_visual_and_key_test': 'not performed',
}
output = ROOT / 'docs/dance-26.2-validation.json'
output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps(report, ensure_ascii=False))
