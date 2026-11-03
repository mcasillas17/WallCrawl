"""Artificial geometry only; no exercise artwork or approval is fabricated."""
import copy
import importlib.util
import json
from pathlib import Path
import shutil
import struct
import zlib
import subprocess
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location('packager', Path(__file__).with_name('package_illustrations.py'))
p = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(p)
SVG = b'<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512"><path fill="#fff" d="M10 10h10v10z"/></svg>'
# A 2x2 artificial alpha square: never a pose or exercise stand-in.
def chunk(kind, data):
    return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))
def png(raw, *, depth=8, color=6, interlace=0):
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 2, 2, depth, color, 0, 0, interlace)) + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b'')
PNG = png(b'\0' + b'\xff' * 4 + b'\0' * 4 + b'\0' + b'\0' * 8)


class PngValidationTest(unittest.TestCase):
    def test_catalog_has_the_recorded_png_master_for_every_published_frame(self):
        public = p.ROOT / 'art/condition-exercises/masters'
        provenance = json.loads((p.ROOT / 'app/src/main/assets/wallcrawl/illustration-provenance.json').read_text())
        for frame in provenance['frames']:
            name = f"{frame['exerciseId']}/{frame['variant']}/frame-{frame['frameNumber']}.png"
            with self.subTest(master=name):
                master = p.read_bytes(p.safe_path(public, name))
                p.validate_png(master)
                self.assertEqual(p.digest(master), frame['masterSha256'])

    def test_rgba8_rows_accept_each_defined_filter(self):
        for filter_number in range(5):
            p.validate_png(png((bytes([filter_number]) + b'\xff' * 8) * 2))

    def test_scanline_length_and_filter_bytes_are_validated(self):
        for raw in [b'X', b'\0' * 17, b'\0' * 19, b'\x05' + b'\0' * 17, b'\0' * 9 + b'\xff' + b'\0' * 8]:
            with self.subTest(raw=raw), self.assertRaises(ValueError):
                p.validate_png(png(raw))

    def test_packaging_explicitly_requires_noninterlaced_rgba8_masters(self):
        for options in [dict(color=6, depth=4), dict(color=6, depth=16), dict(color=4), dict(color=2), dict(color=3), dict(interlace=1)]:
            with self.subTest(options=options), self.assertRaisesRegex(ValueError, 'noninterlaced RGBA8'):
                p.validate_png(png(b'\0' * 18, **options))


class PackagingTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.assets = self.root / 'app/src/main/assets'
        self.destination = self.assets / 'exercise-illustrations'
        shipped = p.ROOT / 'app/src/main/assets'
        shutil.copytree(shipped / 'exercise-illustrations', self.destination)
        shutil.copytree(shipped / 'wallcrawl', self.assets / 'wallcrawl')
        shutil.copytree(shipped / 'workout-guide', self.assets / 'workout-guide')
        # Each artificial supplement starts from the historical bundle, even after real additions ship.
        catalog_path = self.assets / 'wallcrawl/catalog.json'
        catalog = json.loads(catalog_path.read_text())
        index_path = self.destination / 'index.json'
        index = json.loads(index_path.read_text())
        for exercise in catalog['exercises']:
            for frames in index['exercises'].pop(exercise['id']).values():
                for frame in frames:
                    (self.assets / frame).unlink()
        catalog['exercises'] = []
        catalog_path.write_text(json.dumps(catalog))
        index_path.write_text(json.dumps(index))
        provenance_path = self.assets / 'wallcrawl/illustration-provenance.json'
        provenance = json.loads(provenance_path.read_text())
        provenance['frames'] = []
        provenance_path.write_text(json.dumps(provenance))
        self.patch = patch.object(p, 'ROOT', self.root)
        self.patch.start()
        self.addCleanup(self.patch.stop)
        self.ledger = self.root / 'local-art/provenance.json'
        self.ledger.parent.mkdir()
        frames = []
        for variant in ['male', 'female']:
            for number in range(1, 4):
                stem = f'{variant}/frame-{number}'
                def file(suffix, data):
                    name = stem + suffix
                    target = self.ledger.parent / name
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes(data)
                    return {'path': name, 'sha256': p.digest(data)}
                source = file('-source.png', PNG)
                master = file('.png', PNG)
                derived = file('.svg', SVG)
                trace = file('.svg.trace.json', json.dumps({'schemaVersion': 1, 'inputSha256': master['sha256'], 'outputSha256': derived['sha256'], 'toolVersions': {'sharp': '0.35.5', 'potrace': '2.1.8', 'svgo': '4.1.0', 'node': 'v26.10.0'}, 'options': {'mask': 'alpha', 'threshold': 128}}).encode())
                generation = file('-generation.json', json.dumps({'tool': 'native-imagegen', 'prompt': 'ARTIFICIAL FIXTURE ONLY — not a generation claim', 'outputSha256': master['sha256']}).encode())
                frames.append(dict(exerciseId='fictional-geometry', variant=variant, frameNumber=number, source=source, master=master, derived=derived, trace=trace, generation=generation))
        source = json.loads((self.assets / 'wallcrawl/catalog.json').read_text())['source']
        self.document = {'schemaVersion': 1, 'source': source, 'frames': frames}
        self.save()

    def save(self):
        self.ledger.write_text(json.dumps(self.document))

    def promote_fixture_id(self):
        path = self.assets / 'wallcrawl/catalog.json'
        data = json.loads(path.read_text())
        data['exercises'] = [{'id': 'fictional-geometry'}]
        path.write_text(json.dumps(data))

    def state(self):
        return {str(f.relative_to(self.assets)): f.read_bytes() for f in self.assets.rglob('*') if f.is_file()}

    def test_clone_safe_check_does_not_need_old_ledgers_or_write(self):
        before = self.state()
        with patch.object(p, 'PILOT', self.root / 'unavailable'):
            p.validate_bundled_illustrations(self.destination)
        self.assertEqual(before, self.state())

    def test_numbered_instruction_chart_is_bound_to_the_correct_exercise_and_variant(self):
        frame = self.document['frames'][0]
        record = {'tool': 'native-imagegen', 'prompt': 'Artificial numbered chart', 'layout': 'sequence-chart',
                  'exerciseId': frame['exerciseId'], 'variant': 'unrelated', 'outputSha256': frame['master']['sha256']}
        data = json.dumps(record).encode()
        (self.ledger.parent / frame['generation']['path']).write_bytes(data)
        frame['generation']['sha256'] = p.digest(data)
        self.save()
        with self.assertRaisesRegex(ValueError, 'chart belongs to a different'):
            p.collect_supplement(self.ledger)

    def test_native_strip_cannot_substitute_a_different_phase(self):
        frame = self.document['frames'][0]
        record = {'tool': 'native-imagegen', 'prompt': 'Artificial geometry', 'nativeStrip': frame['source'],
                  'exerciseId': frame['exerciseId'], 'variant': frame['variant'],
                  'outputSha256': frame['source']['sha256']}
        data = json.dumps(record).encode()
        (self.ledger.parent / frame['generation']['path']).write_bytes(data)
        frame['generation']['sha256'] = p.digest(data)
        self.save()
        split = subprocess.CompletedProcess([], 0, json.dumps(['0' * 64, frame['master']['sha256'], '1' * 64]).encode())
        with patch.object(p.subprocess, 'run', return_value=split), self.assertRaisesRegex(ValueError, 'native strip cell'):
            p.collect_supplement(self.ledger)

    def test_one_variant_cannot_mix_three_individually_valid_strip_cells(self):
        for index, frame in enumerate(self.document['frames'][:3]):
            strip = PNG if index != 1 else png(b'\0' * 18)
            path = self.ledger.parent / f'strip-{index}.png'
            path.write_bytes(strip)
            record = {'tool': 'native-imagegen', 'prompt': 'Artificial geometry', 'exerciseId': frame['exerciseId'],
                      'variant': frame['variant'], 'nativeStrip': {'path': path.name, 'sha256': p.digest(strip)},
                      'outputSha256': p.digest(strip)}
            generation = json.dumps(record).encode()
            (self.ledger.parent / frame['generation']['path']).write_bytes(generation)
            frame['generation']['sha256'] = p.digest(generation)
        self.save()
        split = subprocess.CompletedProcess([], 0, json.dumps([p.digest(PNG)] * 3).encode())
        with patch.object(p.subprocess, 'run', return_value=split), self.assertRaisesRegex(ValueError, 'one native strip'):
            p.collect_supplement(self.ledger)

    def test_native_strip_names_its_exercise_and_variant(self):
        frame = self.document['frames'][0]
        for exercise, variant in [(frame['exerciseId'], 'female'), ('another-exercise', frame['variant'])]:
            with self.subTest(exercise=exercise, variant=variant):
                record = {'tool': 'native-imagegen', 'prompt': 'Artificial geometry', 'nativeStrip': frame['source'],
                          'exerciseId': exercise, 'variant': variant, 'outputSha256': frame['source']['sha256']}
                generation = json.dumps(record).encode()
                (self.ledger.parent / frame['generation']['path']).write_bytes(generation)
                frame['generation']['sha256'] = p.digest(generation)
                self.save()
                with self.assertRaisesRegex(ValueError, 'different exercise or variant'):
                    p.collect_supplement(self.ledger)

    def test_supplement_is_additive_and_preserves_all_historical_bytes(self):
        before = self.state()
        files = p.collect_supplement(self.ledger)
        self.assertEqual(6, sum(name.endswith('.svg') for name in files))
        self.assertTrue(all(not name.startswith('../') for name in files))
        self.assertEqual(before, self.state())
        self.promote_fixture_id()
        p.package_supplement(self.ledger, self.destination)
        for name, data in before.items():
            if name != 'exercise-illustrations/index.json' and name != 'wallcrawl/catalog.json' and name != 'wallcrawl/illustration-provenance.json':
                self.assertEqual(data, (self.assets / name).read_bytes(), name)
        p.validate_bundled_illustrations(self.destination)
        index = json.loads((self.destination / 'index.json').read_text())
        self.assertEqual(3, len(index['exercises']['fictional-geometry']['female']))
        provenance = json.loads((self.assets / 'wallcrawl/illustration-provenance.json').read_text())
        self.assertEqual('wallcrawl', provenance['source']['id'])
        self.assertEqual(6, len(provenance['frames']))

    def test_incomplete_or_duplicate_variants_are_rejected(self):
        for frames in [self.document['frames'][:-1], self.document['frames'] + [self.document['frames'][0]]]:
            with self.subTest(count=len(frames)):
                self.document['frames'] = frames
                self.save()
                with self.assertRaises(ValueError):
                    p.collect_supplement(self.ledger)

    def test_all_lineage_and_trace_digests_are_checked_before_writes(self):
        original = copy.deepcopy(self.document)
        for field in ['source', 'master', 'derived', 'trace', 'generation']:
            self.document = copy.deepcopy(original)
            self.document['frames'][-1][field]['sha256'] = '0' * 64
            self.save()
            before = self.state()
            with self.assertRaises(ValueError):
                p.package_supplement(self.ledger, self.destination)
            self.assertEqual(before, self.state())

    def test_malformed_png_with_matching_lineage_digests_is_rejected(self):
        frame = self.document['frames'][0]
        broken = b'\x89PNG\r\n\x1a\nnot-a-png'
        (self.ledger.parent / frame['master']['path']).write_bytes(broken)
        frame['master']['sha256'] = p.digest(broken)
        for field in ['trace', 'generation']:
            path = self.ledger.parent / frame[field]['path']
            document = json.loads(path.read_text())
            document['inputSha256' if field == 'trace' else 'outputSha256'] = p.digest(broken)
            path.write_text(json.dumps(document))
            frame[field]['sha256'] = p.digest(path.read_bytes())
        self.save()
        with self.assertRaises(ValueError):
            p.collect_supplement(self.ledger)

    def test_truncated_pixels_with_matching_digests_are_rejected_before_writes(self):
        self.promote_fixture_id()
        p.package_supplement(self.ledger, self.destination)
        before = self.state()
        frame = self.document['frames'][-1]
        truncated = png(b'X')  # CRC/deflate-valid; RGBA8 2x2 requires 18 scanline bytes.
        (self.ledger.parent / frame['master']['path']).write_bytes(truncated)
        frame['master']['sha256'] = p.digest(truncated)
        for field in ['trace', 'generation']:
            path = self.ledger.parent / frame[field]['path']
            document = json.loads(path.read_text())
            document['inputSha256' if field == 'trace' else 'outputSha256'] = p.digest(truncated)
            path.write_text(json.dumps(document))
            frame[field]['sha256'] = p.digest(path.read_bytes())
        self.save()
        with self.assertRaises(ValueError):
            p.collect_supplement(self.ledger)
        with self.assertRaises(ValueError):
            p.package_supplement(self.ledger, self.destination)
        self.assertEqual(before, self.state())

    def test_bad_trace_binding_is_rejected(self):
        frame = self.document['frames'][0]
        path = self.ledger.parent / frame['trace']['path']
        data = json.loads(path.read_text())
        data['inputSha256'] = '0' * 64
        path.write_text(json.dumps(data))
        frame['trace']['sha256'] = p.digest(path.read_bytes())
        self.save()
        with self.assertRaises(ValueError):
            p.collect_supplement(self.ledger)

    def test_unsafe_paths_and_symlinks_are_rejected(self):
        original = copy.deepcopy(self.document)
        for name in ['../escape.png', '/tmp/escape.png', 'male/../escape.png', 'male\\escape.png']:
            self.document = copy.deepcopy(original)
            self.document['frames'][0]['master']['path'] = name
            self.save()
            with self.assertRaises(ValueError):
                p.collect_supplement(self.ledger)
        self.document = original
        target = self.ledger.parent / self.document['frames'][0]['master']['path']
        target.unlink()
        target.symlink_to(self.ledger.parent / self.document['frames'][0]['source']['path'])
        self.save()
        with self.assertRaises(ValueError):
            p.collect_supplement(self.ledger)

    def test_invalid_svg_is_rejected_even_with_matching_digest(self):
        original = copy.deepcopy(self.document)
        for data in [b'<svg/>', SVG.replace(b'<path', b'<image'), SVG.replace(b'#fff', b'url(https://example.invalid/x)'), SVG.replace(b'<path', b'<path onclick="bad()"'), SVG.replace(b'512 512', b'1 1'), b'<!DOCTYPE svg [<!ENTITY x "test">]>' + SVG, SVG.replace(b'M10 10h10v10z', b'M10 10L'), SVG.replace(b'M10 10h10v10z', b'M10 10')]:
            self.document = copy.deepcopy(original)
            frame = self.document['frames'][0]
            target = self.ledger.parent / frame['derived']['path']
            target.write_bytes(data)
            frame['derived']['sha256'] = p.digest(data)
            trace_path = self.ledger.parent / frame['trace']['path']
            trace = json.loads(trace_path.read_text())
            trace['outputSha256'] = p.digest(data)
            trace_path.write_text(json.dumps(trace))
            frame['trace']['sha256'] = p.digest(trace_path.read_bytes())
            self.save()
            with self.assertRaises(ValueError):
                p.collect_supplement(self.ledger)

    def test_bundled_check_rejects_changed_missing_and_unexpected_files(self):
        target = self.destination / 'male/ab-wheel/frame-1.svg'
        original = target.read_bytes()
        for action in ['changed', 'missing', 'extra']:
            target.write_bytes(original)
            extra = self.destination / 'stale.txt'
            if extra.exists(): extra.unlink()
            if action == 'changed': target.write_bytes(SVG)
            elif action == 'missing': target.unlink()
            else: extra.write_text('stale')
            with self.subTest(action=action), self.assertRaises(ValueError):
                p.validate_bundled_illustrations(self.destination)

    def test_historical_notice_or_provenance_mutation_is_rejected(self):
        for name in ['NOTICE.md', 'provenance.json', 'ATTRIBUTION.md', 'LICENSE-ASSETS']:
            path = self.destination / name
            original = path.read_bytes()
            path.write_bytes(original + b' ')
            with self.subTest(name=name), self.assertRaises(ValueError):
                p.validate_bundled_illustrations(self.destination)
            path.write_bytes(original)

    def test_check_mode_writes_nothing_and_stale_files_fail_before_writes(self):
        self.promote_fixture_id()
        before = self.state()
        with self.assertRaises(ValueError):
            p.package_supplement(self.ledger, self.destination, check=True)
        self.assertEqual(before, self.state())
        stale = self.destination / 'male/stale/frame-1.svg'
        stale.parent.mkdir(parents=True)
        stale.write_bytes(SVG)
        before = self.state()
        with self.assertRaises(ValueError):
            p.package_supplement(self.ledger, self.destination)
        self.assertEqual(before, self.state())

    def test_cli_check_bundled_is_clone_safe(self):
        script = Path(__file__).with_name('package_illustrations.py')
        result = subprocess.run(['python3', str(script), '--check-bundled'], capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stderr)


if __name__ == '__main__':
    unittest.main()
