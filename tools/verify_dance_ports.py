"""Check the two standalone dance releases against their executed-test reports.

Run from any directory after each project's build and automated checks. This
checks the release archives themselves; it does not claim a manual GPU test.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import struct
import zipfile


ROOT = Path(__file__).resolve().parents[1]
EDITIONS = {
    'fabric': {
        'project': 'party-fabric-26.3', 'minecraft': '26.3',
        'mod_id': 'wardendance', 'java_class_version': 69,
        'jar': 'wardendance-fabric-26.3-1.0.0.jar',
        'report': 'docs/dance-fabric-26.3-validation.json',
    },
    'forge': {
        'project': 'party-forge-1.8.9', 'minecraft': '1.8.9',
        'mod_id': 'witherdance', 'java_class_version': 52,
        'jar': 'witherdance-forge-1.8.9-1.0.0.jar',
        'report': 'docs/dance-forge-1.8.9-validation.json',
    },
}


def language_properties(data: bytes) -> dict[str, str]:
    result = {}
    for line in data.decode('utf-8-sig').splitlines():
        line = line.strip()
        if not line or line.startswith(('#', '!')):
            continue
        key, separator, value = line.partition('=')
        assert separator and key and value, 'Malformed language entry'
        assert key not in result, 'Duplicate language key: ' + key
        result[key] = value
    return result


def verify(name: str) -> dict:
    config = EDITIONS[name]
    jar = ROOT / config['project'] / 'build/libs' / config['jar']
    report = json.loads((ROOT / config['report']).read_text(encoding='utf-8-sig'))
    assert report['minecraft'] == config['minecraft']
    assert report['version'] == '1.0.0'
    assert report['jar'] == jar.name
    digest = hashlib.sha256(jar.read_bytes()).hexdigest()
    assert digest == report['sha256'], 'Report does not match release JAR'
    assert jar.stat().st_size == report['bytes']
    tests = report['dedicated_server_tests']
    assert tests['failed'] == 0 and tests['passed'] > 0, 'Server checks must pass'
    assert report['model_animation_assertions'] > 0, 'Actual model checks required'
    assert report.get('manual_client_visual_and_key_test') == 'not performed'

    with zipfile.ZipFile(jar) as archive:
        assert archive.testzip() is None, 'Archive CRC error'
        names = set(archive.namelist())
        assert not any(n.startswith(('net/minecraft/', 'net/minecraftforge/',
                                     'net/fabricmc/', 'dev/wardenpact/', 'dev/witherpact/',
                                     'assets/wardenpact/', 'assets/witherpact/',
                                     'data/wardenpact/', 'data/witherpact/'))
                       or '/test/' in n or '/tests/' in n or '/smoke/' in n or '/gametest/' in n
                       or 'SmokeTests' in n or 'dancetests' in n.lower()
                       for n in names), 'Bundled game, loader, equipment, or test classes'
        classes = sorted(n for n in names if n.endswith('.class'))
        assert classes, 'No mod classes found'
        for entry in classes:
            data = archive.read(entry)
            assert data[:4] == b'\xca\xfe\xba\xbe'
            assert struct.unpack('>H', data[6:8])[0] == config['java_class_version'], entry
        if name == 'fabric':
            metadata = json.loads(archive.read('fabric.mod.json'))
            assert metadata['id'] == config['mod_id']
            assert metadata['version'] == '1.0.0'
            assert '26.3' in metadata['depends']['minecraft']
            for side in ('main', 'client'):
                assert metadata['entrypoints'][side]
                for entrypoint in metadata['entrypoints'][side]:
                    class_name = entrypoint if isinstance(entrypoint, str) else entrypoint['value']
                    assert class_name.replace('.', '/') + '.class' in names, class_name
            zh = json.loads(archive.read('assets/wardendance/lang/zh_cn.json'))
            en = json.loads(archive.read('assets/wardendance/lang/en_us.json'))
        else:
            metadata = json.loads(archive.read('mcmod.info'))
            assert len(metadata) == 1
            assert (metadata[0]['modid'], metadata[0]['version'], metadata[0]['mcversion']) == (
                config['mod_id'], '1.0.0', '1.8.9')
            zh = language_properties(archive.read('assets/witherdance/lang/zh_CN.lang'))
            en = language_properties(archive.read('assets/witherdance/lang/en_US.lang'))
        assert zh and set(zh) == set(en), 'Language key mismatch'
        assert all(isinstance(value, str) and value for value in (*zh.values(), *en.values()))

    jar.with_suffix('.jar.sha256').write_text(f'{digest}  {jar.name}\n', encoding='ascii')
    return {
        'project': config['project'], 'minecraft': config['minecraft'],
        'mod_id': config['mod_id'], 'version': '1.0.0',
        'jar': jar.name, 'bytes': jar.stat().st_size, 'sha256': digest,
        'java_class_version': config['java_class_version'], 'class_count': len(classes),
        'archive_crc_valid': True, 'no_bundled_tests_game_loader_or_equipment': True,
        'language_keys_match': True, 'dedicated_server_tests': {
            'passed': tests['passed'], 'failed': tests['failed']},
        'model_animation_assertions': report['model_animation_assertions'],
        'manual_client_visual_and_key_test': 'not performed',
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--edition', dest='editions', action='append', choices=list(EDITIONS))
    options = parser.parse_args()
    results = {name: verify(name) for name in (options.editions or EDITIONS)}
    if set(results) == set(EDITIONS):
        output = ROOT / 'docs/dance-ports-validation.json'
        temporary = output.with_suffix('.json.tmp')
        temporary.write_text(json.dumps(results, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        temporary.replace(output)
    print(json.dumps(results, ensure_ascii=False))


if __name__ == '__main__':
    main()
