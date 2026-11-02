"""Pin the inventory and fail closed on unsupported evidence/release claims."""
import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

MODULE = Path(__file__).with_name('validate_condition_research.py')
APPROVED_IDS = '''chronic-primary-low-back-pain lumbar-radicular-pain lumbar-spinal-stenosis nonspecific-neck-pain cervical-radicular-pain whiplash-associated-disorder adult-degenerative-scoliosis rotator-cuff-tendinopathy full-thickness-rotator-cuff-tear adhesive-capsulitis glenohumeral-osteoarthritis shoulder-instability acromioclavicular-osteoarthritis lateral-elbow-tendinopathy medial-elbow-tendinopathy distal-biceps-tendinopathy carpal-tunnel-syndrome ulnar-neuropathy-at-the-elbow de-quervain-tenosynovitis thumb-carpometacarpal-osteoarthritis finger-interphalangeal-osteoarthritis hip-osteoarthritis gluteal-tendinopathy femoroacetabular-impingement-syndrome adductor-related-groin-pain proximal-hamstring-tendinopathy iliopsoas-related-groin-pain piriformis-syndrome knee-osteoarthritis patellofemoral-pain patellar-tendinopathy iliotibial-band-related-lateral-knee-pain degenerative-meniscal-lesion anterior-cruciate-ligament-injury medial-collateral-ligament-sprain midportion-achilles-tendinopathy insertional-achilles-tendinopathy plantar-heel-pain-compatible-with-plantar-fasciitis lateral-ankle-sprain chronic-ankle-instability posterior-tibial-tendinopathy peroneal-tendinopathy medial-tibial-stress-syndrome lower-limb-bone-stress-injury first-metatarsophalangeal-osteoarthritis osteoporosis rheumatoid-arthritis axial-spondyloarthritis fibromyalgia hypermobility-spectrum-disorder'''.split()


def insufficient(condition_id):
    return {
        'schemaVersion': 1,
        'condition': {'id': condition_id, 'label': condition_id, 'aliases': [],
                      'distinctions': 'Do not infer neighboring diagnoses.'},
        'scope': dict.fromkeys(('population', 'stage', 'operativeStatus', 'exclusions', 'uncertainty'), 'unknown'),
        'searchLog': [{'date': '2026-11-01', 'service': 'PubMed', 'query': condition_id,
                       'decision': 'No suitable accessible protocol identified.', 'limitations': 'Pilot fixture.'}],
        'sources': [], 'findings': [], 'exerciseMappings': [], 'proposedRules': [],
        'gaps': ['Insufficient evidence; no condition-specific rules.'],
        'disposition': 'insufficient-evidence',
        'review': {'status': 'unreviewed', 'reviewer': None, 'reviewedOn': None, 'policy': None, 'evidence': None},
    }


class ConditionResearchTest(unittest.TestCase):
    def setUp(self):
        self.assertTrue(MODULE.exists(), 'validator implementation missing')
        spec = importlib.util.spec_from_file_location('condition_validator', MODULE)
        self.validator = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.validator)
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.directory = self.root / 'docs/research/conditions'
        self.directory.mkdir(parents=True)
        self.index = {'schemaVersion': 1, 'purpose': 'Research inventory, not supported conditions.',
                      'conditions': [{'id': key, 'label': key, 'region': 'fixture', 'status': 'pending'}
                                     for key in APPROVED_IDS]}
        self.write('index', self.index)
        self.write('exercise-requests', {'schemaVersion': 1, 'requests': []})
        catalog = self.root / 'app/src/main/assets/workout-guide/catalog.json'
        catalog.parent.mkdir(parents=True)
        catalog.write_text('{"exercises":[{"id":"bridge"}]}')
        self.dossier = insufficient(APPROVED_IDS[0])
        self.complete(self.dossier)

    def write(self, key, value):
        (self.directory / (key + '.json')).write_text(json.dumps(value), encoding='utf-8')

    def complete(self, dossier):
        self.write(dossier['condition']['id'], dossier)
        next(row for row in self.index['conditions'] if row['id'] == dossier['condition']['id'])['status'] = 'complete'
        self.write('index', self.index)

    def pilot(self):
        base = Path(__file__).resolve().parents[2] / 'docs/research/conditions'
        self.write('exercise-requests', json.loads((base / 'exercise-requests.json').read_text()))
        return json.loads((base / 'piriformis-syndrome.json').read_text())

    def protocol_source(self, status):
        pilot_path = Path(__file__).resolve().parents[2] / 'docs/research/conditions/piriformis-syndrome.json'
        source = json.loads(pilot_path.read_text())['sources'][0]
        source['access'] = {'status': status, 'date': '2026-11-01',
                            'details': 'Synthetic access boundary fixture; no claim of full-text retrieval.'}
        source['locations'] = ['Abstract > Methods']
        source['protocol'] = dict.fromkeys(source['protocol'], 'not reported in accessible text')
        self.dossier['sources'] = [source]
        return source

    def errors(self, complete=False):
        return self.validator.validate_corpus(self.root, complete)

    def rejected(self, marker):
        self.assertTrue(any(marker in error for error in self.errors()), self.errors())

    def test_explicit_insufficient_evidence_is_valid(self):
        self.assertEqual([], self.errors())

    def test_empty_sources_require_explicit_gap_for_every_disposition(self):
        for disposition in ('research-only', 'unresolved', 'insufficient-evidence',
                            'eligible-for-independent-content-review'):
            with self.subTest(disposition=disposition):
                self.dossier['disposition'] = disposition
                self.dossier['gaps'] = []
                self.complete(self.dossier)
                self.rejected('explicit gap')
                self.dossier['gaps'] = ['No suitable evidence established by the recorded search.']
                self.complete(self.dossier)
                self.assertEqual([], self.errors())

    def test_inventory_has_exact_approved_ids(self):
        actual = json.loads((Path(__file__).resolve().parents[2] / 'docs/research/conditions/index.json').read_text())
        self.assertEqual(set(APPROVED_IDS), {row['id'] for row in actual['conditions']})
        self.assertEqual(50, len(actual['conditions']))

    def test_duplicate_ids(self):
        self.index['conditions'][-1] = copy.deepcopy(self.index['conditions'][0])
        self.write('index', self.index)
        self.rejected('duplicate')

    def test_duplicate_fields_and_non_finite_numbers(self):
        path = self.directory / 'exercise-requests.json'
        for raw in ('{"schemaVersion":1,"schemaVersion":1,"requests":[]}',
                    '{"schemaVersion":1,"requests":[],"bad":NaN}',
                    '{"schemaVersion":1,"requests":[],"bad":Infinity}',
                    '{"schemaVersion":1,"requests":[],"bad":1e999}'):
            with self.subTest(raw=raw):
                path.write_text(raw)
                self.assertTrue(self.errors())

    def test_restriction_only_still_requires_search(self):
        self.dossier['searchLog'] = []
        self.complete(self.dossier)
        self.rejected('searchLog')

    def test_abstract_protocol_accepts_explicit_accessible_text(self):
        for field in ('execution', 'dose', 'frequency', 'supervision', 'progression', 'stoppingRules'):
            with self.subTest(field=field):
                source = self.protocol_source('abstract-only')
                source['protocol'][field] = 'reported in accessible text: Explicit method detail in the abstract.'
                self.complete(self.dossier)
                self.assertEqual([], self.errors())

    def test_full_text_protocol_keeps_unqualified_method(self):
        for field in ('execution', 'dose', 'frequency', 'supervision', 'progression', 'stoppingRules'):
            with self.subTest(field=field):
                source = self.protocol_source('full-text')
                source['protocol'][field] = 'Three sessions per week.'
                self.complete(self.dossier)
                self.assertEqual([], self.errors())

    def test_abstract_protocol_rejects_empty_accessible_text(self):
        for field in ('execution', 'dose', 'frequency', 'supervision', 'progression', 'stoppingRules'):
            for value in ('reported in accessible text: ', 'reported in accessible text:    '):
                with self.subTest(field=field, value=value):
                    source = self.protocol_source('abstract-only')
                    source['protocol'][field] = value
                    self.complete(self.dossier)
                    self.assertTrue(self.errors())

    def test_unavailable_protocol_rejects_accessible_text_claim(self):
        for field in ('execution', 'dose', 'frequency', 'supervision', 'progression', 'stoppingRules'):
            with self.subTest(field=field):
                source = self.protocol_source('unavailable')
                source['protocol'][field] = 'reported in accessible text: Three sessions per week.'
                self.complete(self.dossier)
                self.rejected('protocol.' + field)

    def test_abstract_protocol_rejects_unqualified_method(self):
        for field in ('execution', 'dose', 'frequency', 'supervision', 'progression', 'stoppingRules'):
            with self.subTest(field=field):
                source = self.protocol_source('abstract-only')
                source['protocol'][field] = 'Three sessions per week.'
                self.complete(self.dossier)
                self.rejected('protocol.' + field)

    def test_accessible_protocol_still_requires_source_locations(self):
        source = self.protocol_source('abstract-only')
        source['protocol']['frequency'] = 'reported in accessible text: Three sessions per week.'
        source['locations'] = []
        self.complete(self.dossier)
        self.rejected('locations')

    def test_access_status_required(self):
        pilot = self.pilot()
        del pilot['sources'][0]['access']['status']
        self.complete(pilot)
        self.rejected('access')

    def test_broken_source_and_mapping_references(self):
        pilot = self.pilot()
        pilot['findings'][0]['citations'][0]['sourceId'] = 'missing'
        self.complete(pilot)
        self.rejected('source')
        pilot['exerciseMappings'][0]['requestId'] = 'missing'
        self.complete(pilot)
        self.rejected('request')

    def test_unknown_catalog_exercise_rejected(self):
        self.dossier['exerciseMappings'] = [{'id': 'mapping', 'exerciseId': 'absent', 'requestId': None,
            'findingIds': [], 'matchRationale': 'No verified match.', 'status': 'candidate'}]
        self.complete(self.dossier)
        self.rejected('catalog')

    def test_fabricated_review_promotion_rejected(self):
        for field, value in [('disposition', 'accepted'), ('review', {'status': 'approved',
                'reviewer': 'AI agreement', 'reviewedOn': '2026-11-01', 'policy': 'owner approved design', 'evidence': 'none'})]:
            with self.subTest(field=field):
                dossier = copy.deepcopy(self.dossier)
                dossier[field] = value
                self.complete(dossier)
                self.rejected('review')

    def test_rule_requires_finding_and_applicability(self):
        self.dossier['proposedRules'] = [{'id': 'restriction', 'kind': 'exclusion',
            'basis': 'product-precaution', 'statement': 'Withhold movement.', 'findingIds': [],
            'citations': [], 'applicability': {'population': 'adults', 'stage': 'unknown', 'rationale': ''},
            'limitations': 'Unknown.', 'mappingIds': [], 'status': 'candidate'}]
        self.complete(self.dossier)
        self.rejected('finding')
        self.rejected('rationale')

    def test_unknown_and_wrong_field_types_rejected_without_crash(self):
        for field, value in [('schemaVersion', True), ('sources', None), ('condition', []),
                             ('review', 'approved'), ('unexpected', 'ignored?')]:
            with self.subTest(field=field):
                dossier = copy.deepcopy(self.dossier)
                dossier[field] = value
                self.write(APPROVED_IDS[0], dossier)
                self.assertTrue(self.errors())

    def test_required_complete_rejects_49_accepts_50(self):
        for key in APPROVED_IDS[:-1]:
            self.complete(insufficient(key))
        self.assertTrue(self.errors(True))
        self.complete(insufficient(APPROVED_IDS[-1]))
        self.assertEqual([], self.errors(True))

    def test_file_bytes_at_limit_and_above(self):
        path = self.directory / 'exercise-requests.json'
        raw = path.read_bytes()
        path.write_bytes(raw + b' ' * (self.validator.MAX_FILE_BYTES - len(raw)))
        self.assertEqual([], self.errors())
        with path.open('ab') as stream:
            stream.write(b' ')
        self.rejected('byte')

    def test_identifier_at_limit_and_above(self):
        for size in (self.validator.MAX_IDENTIFIER_LENGTH, self.validator.MAX_IDENTIFIER_LENGTH + 1):
            with self.subTest(size=size):
                key = 'a' * size
                self.index['conditions'][0]['id'] = key
                self.dossier['condition']['id'] = key
                for path in self.directory.glob('a*.json'):
                    path.unlink()
                original = self.directory / (APPROVED_IDS[0] + '.json')
                original.unlink(missing_ok=True)
                self.complete(self.dossier)
                self.assertEqual(size > self.validator.MAX_IDENTIFIER_LENGTH, bool(self.errors()))

    def test_depth_items_and_strings_at_limit_and_above(self):
        for name, boundary in [('depth', self.validator.MAX_JSON_DEPTH),
                               ('items', self.validator.MAX_ITEMS), ('string', self.validator.MAX_STRING_LENGTH)]:
            for size in (boundary, boundary + 1):
                with self.subTest(name=name, size=size):
                    if name == 'depth':
                        value = 0
                        for _ in range(size):
                            value = [value]
                    else:
                        value = ['x'] * size if name == 'items' else 'x' * size
                    path = self.directory / 'bounded.json'
                    path.write_text(json.dumps(value))
                    if size == boundary:
                        self.validator.read_document(path)
                    else:
                        with self.assertRaises(ValueError):
                            self.validator.read_document(path)
                    path.unlink()

    def test_malformed_nested_references_return_errors_without_crash(self):
        for target, field, value in [('source', 'locations', None), ('source', 'locations', 7),
                                     ('request', 'conditionIds', None), ('request', 'conditionIds', 7),
                                     ('dossier', 'sources', None)]:
            with self.subTest(target=target, field=field):
                pilot = self.pilot()
                requests = json.loads((self.directory / 'exercise-requests.json').read_text())
                if target == 'source':
                    pilot['sources'][0][field] = value
                elif target == 'request':
                    requests['requests'][0][field] = value
                    self.write('exercise-requests', requests)
                else:
                    pilot[field] = value
                self.complete(pilot)
                self.assertTrue(self.errors())

    def test_unlisted_dossier_rejected(self):
        self.write('unlisted', insufficient('unlisted'))
        self.rejected('unlisted')


if __name__ == '__main__':
    unittest.main()
