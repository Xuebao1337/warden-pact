"""Validate the release JAR against its resources and dedicated-server report."""
from pathlib import Path
import hashlib
import io
import json
import struct
import zipfile
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
PROJECT = ROOT / 'forge-1.8.9'
JAR = PROJECT / 'build/libs/witherpact-forge-1.8.9-1.0.0.jar'

def main():
    tests = json.loads((PROJECT / 'run/witherpact-smoke-results.json').read_text(encoding='utf-8'))
    assert tests['passed'] == len(tests['checks']) and tests['passed'] >= 86
    assert tests['failed'] == 0 and not tests['failures']
    images = {}
    with zipfile.ZipFile(JAR) as archive:
        assert archive.testzip() is None, 'Corrupt archive entry'
        names = set(archive.namelist())
        assert not any('SmokeTests' in name or name.startswith(('net/minecraft/', 'net/minecraftforge/')) for name in names)
        required = ['WitherPact', 'PactWither', 'PactSkull', 'EchoGear', 'PactEvents', 'PactNetwork', 'client/PactArmorModel']
        assert all('dev/witherpact/' + name + '.class' in names for name in required)
        classes = [name for name in names if name.endswith('.class')]
        for name in classes:
            data = archive.read(name)
            assert data[:4] == b'\xca\xfe\xba\xbe' and struct.unpack('>H', data[6:8])[0] == 52, name
        metadata = json.loads(archive.read('mcmod.info'))[0]
        assert (metadata['modid'], metadata['version'], metadata['mcversion']) == ('witherpact', '1.0.0', '1.8.9')
        for name in names:
            if name.endswith('.png'):
                with Image.open(io.BytesIO(archive.read(name))) as image:
                    images[name] = list(image.size)
                    image.verify()
            elif name.endswith('.json') and name.startswith('assets/'):
                model = json.loads(archive.read(name))
                for texture in model.get('textures', {}).values():
                    if texture.startswith('witherpact:'):
                        assert 'assets/witherpact/textures/' + texture.split(':', 1)[1] + '.png' in names, name
        for layer in (1, 2):
            assert images['assets/witherpact/textures/models/armor/echo_layer_%d.png' % layer] == [64, 32]
        for skin in ('pact_wither', 'wither_mount'):
            assert images['assets/witherpact/textures/entity/' + skin + '.png'] == [64, 64]
    digest = hashlib.sha256(JAR.read_bytes()).hexdigest()
    checksum = JAR.with_suffix('.jar.sha256')
    checksum.write_text(digest + '  ' + JAR.name + '\n', encoding='ascii')
    report = {
        'date': '2026-11-28', 'minecraft': '1.8.9', 'forge': '11.15.1.2318',
        'version': '1.0.0', 'jar': JAR.name, 'bytes': JAR.stat().st_size, 'sha256': digest,
        'java_class_version': 52, 'class_count': len(classes), 'png_count': len(images),
        'archive_crc_valid': True, 'resource_references_valid': True,
        'no_test_classes_or_game_classes': True, 'client_visual_test': False,
        'dedicated_server_tests': tests,
    }
    (ROOT / 'docs/forge-1.8.9-validation.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({key: value for key, value in report.items() if key != 'dedicated_server_tests'}, ensure_ascii=False))

if __name__ == '__main__':
    main()
