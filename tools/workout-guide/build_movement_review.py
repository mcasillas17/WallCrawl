"""Render the source-level follow-up for the 68 previously unresolved movement requests."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path

from build_condition_reference import DOSSIERS, publication_url

FOLDER = DOSSIERS / 'movements'
GROUPS = {'hip-knee': 'Hip, knee and lifting', 'spine-shoulder-systemic': 'Spine, shoulder and systemic conditions',
          'lower-leg-hand': 'Lower leg, ankle, foot and hand'}
STATUSES = {'specified': 'Specified', 'partial': 'Partially specified', 'program': 'Program',
            'assessment-dependent': 'Assessment-dependent'}
# The original art audit classified this exact subset of the 89 research requests as unresolved.
ORIGINAL_IDS_SHA256 = '0fd38c55a3819de21bb47792a26467eefc7b70ebd5d3e99aa656940b7ad9d50b'


def build_review():
    requests = {r['id']: r for r in json.loads((DOSSIERS / 'exercise-requests.json').read_text())['requests']}
    sections = []
    ids = []
    counts = Counter()
    urls = set()
    for filename, heading in GROUPS.items():
        sections.extend(['', f'## {heading}'])
        for row in json.loads((FOLDER / f'{filename}.json').read_text()):
            key, status = row['requestId'], row['status']
            request = requests[key]
            ids.append(key)
            counts[status] += 1
            if not row['sources'] or not row['execution'].strip():
                raise ValueError(f'Missing movement or sources for {key}')
            name = request['name']
            if name.endswith('unresolved'):
                name = name.replace('—', ',').split(',')[0].removesuffix(' execution unresolved').removesuffix(' variant unresolved').strip()
            sections.extend(['', f'### {name}', '', f'`{key}` · **{STATUSES[status]}**', '', row['execution']])
            if status == 'specified':
                brief = row['artBrief']
                if row['remainingGap'] or set(brief) != {'start', 'middle', 'end'} or not all(brief.values()):
                    raise ValueError(f'Incomplete art brief for {key}')
                sections.extend(['', *[f'- **{phase.title()}**. {brief[phase]}' for phase in ('start', 'middle', 'end')]])
            else:
                if not row['remainingGap'] or row.get('artBrief'):
                    raise ValueError(f'Missing or contradictory gap for {key}')
                sections.extend(['', f"**What remains open**. {row['remainingGap']}"])
            sections.append('')
            for source in row['sources']:
                url = publication_url(source['url'])
                urls.add(url)
                if not all(source.get(k, '').strip() for k in ('title', 'location', 'access')):
                    raise ValueError(f'Incomplete source for {key}')
                sections.append(f"- {source['location']} ({source['access']}). [{source['title']}]({url})")
    if len(ids) != 68 or hashlib.sha256('\n'.join(sorted(ids)).encode()).hexdigest() != ORIGINAL_IDS_SHA256:
        raise ValueError('Movement review must cover precisely the 68 original open requests')
    intro = ['# Movement descriptions and illustration briefs', '',
             f"This follow-up traces the original 68 open requests to {len(urls)} distinct source links. A source link can lead to a study, an appendix, an author’s manual or a thesis. Access and exact locations appear alongside each description.", '',
             '| Evidence for the movement | Requests |', '| --- | ---: |',
             *[f'| {label} | {counts[status]} |' for status, label in STATUSES.items()], '',
             'A specified movement has enough detail to brief an illustrator. A partial record identifies the missing detail. Programs combine multiple movements or stages. Assessment-dependent requests rely on a clinician or a participant-specific choice. Study doses describe the research participants. They do not set workout defaults.', '',
             'The [original request register](../exercise-requests.json) preserves the earlier audit. The [additional bibliography from PR 105](../pr-105/BIBLIOGRAPHY.md) includes other research for eight overlapping conditions.', '',
             '## Two existing catalog matches']
    for match in json.loads((FOLDER / 'catalog-matches.json').read_text()):
        intro.extend(['', f"### {requests[match['requestId']]['name'].split(',')[0]}", '',
                      match['finding'], '', ' · '.join(f"[{s['title']}]({s['url']})" for s in match['sources'])])
    return '\n'.join(intro + sections) + '\n'


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    output = FOLDER / 'README.md'
    content = build_review()
    if args.check:
        if output.read_text() != content:
            raise SystemExit('Regenerate the movement guide with build_movement_review.py')
    else:
        output.write_text(content)
