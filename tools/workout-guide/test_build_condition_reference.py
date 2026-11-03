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

    def test_only_shipped_request_identities_link_and_only_at_their_cited_sources(self):
        requests = json.loads((DOSSIERS / 'exercise-requests.json').read_text())['requests']
        shipped = {e['id'] for e in json.loads((ROOT / 'app/src/main/assets/wallcrawl/catalog.json').read_text())['exercises']}
        expected = {(s['conditionId'], s['sourceId'], r['id']) for r in requests if r['id'] in shipped
                    for s in r['sourceRefs'] if s['conditionId'] in r['conditionIds']}
        actual = {(c['id'], s['id'], e) for group in build_library() for c in group['entries']
                  for s in c['sources'] for e in s['exerciseIds']}
        self.assertEqual(expected, actual)
        self.assertIn(('piriformis-syndrome', 'shamsi-2024', 'supine-wall-supported-piriformis-stretch'), actual)
