import json
import unittest

from build_condition_reference import ASSET, DOSSIERS, ROOT, build_library


class ConditionReferenceTest(unittest.TestCase):
    def test_shipped_bibliography_matches_all_canonical_sources_and_access_levels(self):
        library = json.loads(ASSET.read_text(encoding='utf-8'))
        self.assertEqual(library, build_library())
        rows = [row for group in library for row in group['entries']]
        inventory = json.loads((DOSSIERS / 'index.json').read_text(encoding='utf-8'))['conditions']
        self.assertEqual([row['id'] for row in rows], [row['id'] for row in inventory])
        for row in rows:
            dossier = json.loads((DOSSIERS / (row['id'] + '.json')).read_text(encoding='utf-8'))
            with self.subTest(condition=row['id']):
                self.assertTrue(row['es'].strip())
                self.assertEqual([(s['title'], s['url'], s['access']) for s in row['sources']],
                                 [(s['title'], s['url'], s['access']['status']) for s in dossier['sources']])
                self.assertTrue(all(s['findings'] and s['limitations'] for s in row['sources']))

    def test_illustrations_link_only_at_their_cited_sources_with_one_verified_upstream_match(self):
        requests = json.loads((DOSSIERS / 'exercise-requests.json').read_text())['requests']
        shipped = {e['id'] for e in json.loads((ROOT / 'app/src/main/assets/wallcrawl/catalog.json').read_text())['exercises']}
        expected = {(s['conditionId'], s['sourceId'], r['id']) for r in requests if r['id'] in shipped
                    for s in r['sourceRefs'] if s['conditionId'] in r['conditionIds']}
        expected.add(('carpal-tunnel-syndrome', 'cts-p1', 'tendon-gliding-sequence'))
        expected.add(('iliotibial-band-related-lateral-knee-pain', 'mckay-2020', 'side-lying-leg-raise'))
        actual = {(c['id'], s['id'], e) for group in build_library() for c in group['entries']
                  for s in c['sources'] for e in s['exerciseIds']}
        self.assertEqual(expected, actual)
        self.assertIn(('piriformis-syndrome', 'shamsi-2024', 'supine-wall-supported-piriformis-stretch'), actual)

    def test_extended_bibliography_preserves_access_and_omits_duplicate_or_unconfirmed_records(self):
        all_entries = [c for region in build_library() for c in region['entries']]
        self.assertEqual(8, sum(bool(c['relatedReading']) for c in all_entries))
        for c in all_entries:
            refs = c['relatedReading']
            self.assertEqual(len(refs), len({r['url'] for r in refs}))
            self.assertTrue(all(r['access'] in ('full_text', 'abstract_only', 'registry_or_summary') for r in refs))
        piriformis = next(c for c in all_entries if c['id'] == 'piriformis-syndrome')
        self.assertNotIn('https://doi.org/10.1186/s13102-023-00802-4', {r['url'] for r in piriformis['relatedReading']})
