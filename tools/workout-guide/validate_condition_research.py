#!/usr/bin/env python3
"""Validate the condition research inventory and draft evidence trails, offline.

This version accepts unreviewed candidate content only. A future recorded clinical
review policy must define promotion; an agent or owner-design approval cannot do so.
"""
import argparse
from datetime import date
import json
import math
from pathlib import Path
import re

from import_catalog import (
    CatalogImportError, _bounded_string, _read_json,
    _reject_duplicate_json_fields, _reject_non_finite_number,
)

MAX_FILE_BYTES = 262_144
MAX_JSON_DEPTH = 12
MAX_ITEMS = 1_000
MAX_STRING_LENGTH = 8_192
MAX_IDENTIFIER_LENGTH = 96
CONDITION_COUNT = 50
ACCESSIBLE_PROTOCOL_PREFIX = 'reported in accessible text: '
CORPUS_PATH = Path('docs/research/conditions')
ID_PATTERN = re.compile(r'[a-z0-9]+(?:-[a-z0-9]+)*\Z')


def read_document(path: Path):
    """Reject oversized input without reading it all; never normalize/truncate it."""
    def bounded(value, depth=0, label=None):
        label = label or path.name
        if depth > MAX_JSON_DEPTH:
            raise ValueError('maximum JSON nesting depth exceeded')
        if isinstance(value, float) and not math.isfinite(value):
            raise ValueError('non-finite number')
        if isinstance(value, str):
            _bounded_string(value, label, maximum=MAX_STRING_LENGTH)
        if isinstance(value, (dict, list)):
            if len(value) > MAX_ITEMS:
                raise ValueError('maximum JSON items exceeded')
            if isinstance(value, dict):
                for key in value:
                    _bounded_string(key, 'JSON field', maximum=MAX_STRING_LENGTH)
            items = value.items() if isinstance(value, dict) else enumerate(value)
            for key, item in items:
                bounded(item, depth + 1, f'{label}.{key}')

    try:
        if path.is_symlink():
            raise ValueError('research file must not be a symlink')
        with path.open('rb') as stream:
            raw = stream.read(MAX_FILE_BYTES + 1)
        if len(raw) > MAX_FILE_BYTES:
            raise ValueError(f'exceeds {MAX_FILE_BYTES}-byte input limit')
        value = json.loads(raw.decode('utf-8'),
                           object_pairs_hook=lambda pairs: _reject_duplicate_json_fields(pairs, path.name),
                           parse_constant=lambda _: _reject_non_finite_number(path.name))
        bounded(value)
        return value
    except (OSError, UnicodeError, ValueError, RecursionError, CatalogImportError) as error:
        raise ValueError(f'{path.name}: {error}') from error


def validate_corpus(root: Path, require_complete: bool) -> list[str]:
    """Return structural/evidence-link errors; completeness is research, not release."""
    errors = []
    directory = root / CORPUS_PATH

    def check(ok, message):
        if not ok:
            errors.append(message)
        return ok

    def fields(value, names, label):
        if not check(isinstance(value, dict), f'{label}: expected object'):
            return {}
        check(set(value) == set(names.split()), f'{label}: missing or unknown fields')
        return value

    def text(value, label):
        return check(isinstance(value, str) and bool(value.strip()), f'{label}: required nonblank text')

    def identifier(value, label):
        return check(isinstance(value, str) and len(value) <= MAX_IDENTIFIER_LENGTH
                     and bool(ID_PATTERN.fullmatch(value)), f'{label}: invalid identifier')

    def rows(value, label, required=False):
        if not check(isinstance(value, list), f'{label}: expected array'):
            return []
        check(not required or bool(value), f'{label}: must not be empty')
        return value

    def texts(value, label, required=False):
        result = rows(value, label, required)
        for item in result:
            text(item, label)
        return result

    def choices(value, allowed, label):
        check(isinstance(value, str) and value in allowed.split(), f'{label}: unsupported value')

    def dated(value, label):
        try:
            check(isinstance(value, str) and date.fromisoformat(value).isoformat() == value,
                  f'{label}: expected ISO date')
        except (TypeError, ValueError):
            check(False, f'{label}: expected ISO date')

    def table(value, label):
        result = {}
        for row in rows(value, label):
            if not check(isinstance(row, dict), f'{label}: expected object entry'):
                continue
            key = row.get('id')
            if identifier(key, label + '.id'):
                check(key not in result, f'{label}: duplicate ID {key}')
                result[key] = row
        return result

    def references(value, targets, label, required=True):
        values = rows(value, label, required)
        checked = []
        for key in values:
            if identifier(key, label):
                check(key in targets, f'{label}: unknown reference {key}')
                checked.append(key)
        check(len(checked) == len(set(checked)), f'{label}: duplicate references')
        return checked

    def load(name):
        try:
            return read_document(directory / (name + '.json'))
        except ValueError as error:
            errors.append(str(error))
            return {}

    def version(value, label):
        check(type(value.get('schemaVersion')) is int and value['schemaVersion'] == 1,
              f'{label}: schemaVersion must be integer 1')

    index = fields(load('index'), 'schemaVersion purpose conditions', 'index')
    version(index, 'index')
    text(index.get('purpose'), 'index.purpose')
    inventory = table(index.get('conditions'), 'index.conditions')
    check(len(inventory) == CONDITION_COUNT, 'index: requires exactly 50 unique conditions')
    for key, row in inventory.items():
        fields(row, 'id label region status', key)
        text(row.get('label'), key + '.label')
        text(row.get('region'), key + '.region')
        choices(row.get('status'), 'pending complete', key + '.status')
        if require_complete:
            check(row.get('status') == 'complete', f'{key}: research incomplete')
    requests_doc = fields(load('exercise-requests'), 'schemaVersion requests', 'exercise-requests')
    version(requests_doc, 'exercise-requests')
    requests = table(requests_doc.get('requests'), 'exercise-requests.requests')
    try:
        catalog = _read_json(root / 'app/src/main/assets/workout-guide/catalog.json', 'catalog')
        catalog_ids = {row['id'] for row in catalog['exercises']}
    except (OSError, ValueError, CatalogImportError, KeyError, TypeError) as error:
        errors.append(f'catalog: {error}')
        catalog_ids = set()
    expected_files = {'index.json', 'exercise-requests.json'} | {key + '.json' for key in inventory}
    for path in directory.glob('*.json'):
        check(path.name in expected_files, f'{path.name}: unlisted dossier')
    dossiers = {}
    for key, entry in inventory.items():
        path = directory / (key + '.json')
        if entry.get('status') == 'pending':
            check(not path.exists(), f'{key}: dossier exists but index status pending')
            continue
        dossier = fields(load(key), 'schemaVersion condition scope searchLog sources findings exerciseMappings proposedRules gaps disposition review', key)
        dossiers[key] = dossier
        version(dossier, key)
        condition = fields(dossier.get('condition'), 'id label aliases distinctions', key + '.condition')
        check(condition.get('id') == key, f'{key}: condition ID mismatches filename/index')
        text(condition.get('label'), key + '.condition.label')
        texts(condition.get('aliases'), key + '.aliases')
        text(condition.get('distinctions'), key + '.distinctions')
        scope = fields(dossier.get('scope'), 'population stage operativeStatus exclusions uncertainty', key + '.scope')
        for name in ('population', 'stage', 'operativeStatus', 'exclusions', 'uncertainty'):
            text(scope.get(name), f'{key}.scope.{name}')
        for search in rows(dossier.get('searchLog'), key + '.searchLog', True):
            search = fields(search, 'date service query decision limitations', key + '.searchLog')
            dated(search.get('date'), key + '.searchLog.date')
            for name in ('service', 'query', 'decision', 'limitations'):
                text(search.get(name), f'{key}.searchLog.{name}')
        sources = table(dossier.get('sources'), key + '.sources')
        for sid, source in sources.items():
            fields(source, 'id title doi pmid url publicationStatus access integrity design population comparator coInterventions outcomes followUp uncertainty harms limitations protocol locations', key + '.source.' + sid)
            for name in ('title', 'url', 'publicationStatus', 'design', 'population', 'comparator', 'coInterventions', 'outcomes', 'followUp', 'uncertainty', 'harms', 'limitations'):
                text(source.get(name), f'{sid}.{name}')
            check(isinstance(source.get('url'), str) and source['url'].startswith('https://'), f'{sid}.url: direct HTTPS paper link required')
            for name in ('doi', 'pmid'):
                if source.get(name) is not None:
                    text(source[name], f'{sid}.{name}')
            access = fields(source.get('access'), 'status date details', sid + '.access')
            choices(access.get('status'), 'full-text abstract-only unavailable', sid + '.access.status')
            dated(access.get('date'), sid + '.access.date')
            text(access.get('details'), sid + '.access.details')
            integrity = fields(source.get('integrity'), 'checkedOn method result', sid + '.integrity')
            dated(integrity.get('checkedOn'), sid + '.integrity.checkedOn')
            text(integrity.get('method'), sid + '.integrity.method')
            text(integrity.get('result'), sid + '.integrity.result')
            protocol = fields(source.get('protocol'), 'execution dose frequency supervision progression stoppingRules', sid + '.protocol')
            for name in ('execution', 'dose', 'frequency', 'supervision', 'progression', 'stoppingRules'):
                text(protocol.get(name), sid + '.protocol.' + name)
                value = protocol.get(name)
                if access.get('status') != 'full-text' and isinstance(value, str):
                    reported = (access.get('status') == 'abstract-only'
                                and value.startswith(ACCESSIBLE_PROTOCOL_PREFIX)
                                and bool(value[len(ACCESSIBLE_PROTOCOL_PREFIX):].strip()))
                    check(value.startswith('not reported') or reported,
                          f'{sid}: protocol.{name} needs an access-qualified statement or not reported')
            texts(source.get('locations'), sid + '.locations', True)

        def citations(value, label):
            result = set()
            for cite in rows(value, label, True):
                cite = fields(cite, 'sourceId location', label)
                sid, location = cite.get('sourceId'), cite.get('location')
                text(location, label + '.location')
                if identifier(sid, label + '.sourceId'):
                    check(sid in sources, f'{label}: unknown source {sid}')
                    if sid in sources:
                        check(location in rows(sources[sid].get('locations'), sid + '.locations'), f'{label}: unknown exact source location')
                    if isinstance(location, str):
                        result.add((sid, location))
            return result

        findings = table(dossier.get('findings'), key + '.findings')
        finding_citations = {}
        for fid, finding in findings.items():
            fields(finding, 'id kind statement citations applicability limitations', fid)
            choices(finding.get('kind'), 'study-result published-recommendation evidence-gap', fid + '.kind')
            for name in ('statement', 'applicability', 'limitations'):
                text(finding.get(name), fid + '.' + name)
            finding_citations[fid] = citations(finding.get('citations'), fid + '.citations')
        mappings = table(dossier.get('exerciseMappings'), key + '.exerciseMappings')
        for mid, mapping in mappings.items():
            fields(mapping, 'id exerciseId requestId findingIds matchRationale status', mid)
            eid, rid = mapping.get('exerciseId'), mapping.get('requestId')
            check((eid is None) != (rid is None), f'{mid}: name exactly one catalog exercise or request')
            if eid is not None:
                check(identifier(eid, mid + '.exerciseId') and eid in catalog_ids, f'{mid}: unknown catalog exercise')
            if rid is not None:
                check(identifier(rid, mid + '.requestId') and rid in requests, f'{mid}: unknown request')
                if isinstance(rid, str) and rid in requests:
                    check(key in rows(requests[rid].get('conditionIds'), rid + '.conditionIds'), f'{mid}: request condition mismatch')
            references(mapping.get('findingIds'), findings, mid + '.findingIds')
            text(mapping.get('matchRationale'), mid + '.matchRationale')
            choices(mapping.get('status'), 'candidate', mid + '.status: clinical review required')
        for rid, rule in table(dossier.get('proposedRules'), key + '.proposedRules').items():
            fields(rule, 'id kind basis statement findingIds citations applicability limitations mappingIds status', rid)
            choices(rule.get('kind'), 'supportive-prescription exclusion load-limit range-limit substitution symptom-response escalation', rid + '.kind')
            choices(rule.get('basis'), 'published-recommendation product-precaution unresolved-hypothesis', rid + '.basis')
            text(rule.get('statement'), rid + '.statement')
            text(rule.get('limitations'), rid + '.limitations')
            linked = references(rule.get('findingIds'), findings, rid + '.findingIds')
            cites = citations(rule.get('citations'), rid + '.citations')
            chain = set().union(*(finding_citations.get(fid, set()) for fid in linked))
            check(bool(cites) and cites <= chain, rid + ': citations must trace through linked findings')
            if rule.get('basis') == 'published-recommendation':
                check(any(findings[fid].get('kind') == 'published-recommendation' for fid in linked if fid in findings), rid + ': published recommendation requires a published finding')
            applicability = fields(rule.get('applicability'), 'population stage rationale', rid + '.applicability')
            for name in ('population', 'stage', 'rationale'):
                text(applicability.get(name), rid + '.applicability.' + name)
            references(rule.get('mappingIds'), mappings, rid + '.mappingIds', rule.get('kind') in ('supportive-prescription', 'substitution'))
            choices(rule.get('status'), 'candidate', rid + '.status: clinical review required')
        texts(dossier.get('gaps'), key + '.gaps')
        choices(dossier.get('disposition'), 'research-only unresolved insufficient-evidence eligible-for-independent-content-review', key + '.disposition: clinical review unavailable')
        if not sources or dossier.get('disposition') == 'insufficient-evidence':
            check(bool(dossier.get('gaps')), key + ': absent or insufficient evidence needs an explicit gap')
        if dossier.get('disposition') == 'insufficient-evidence':
            check(not mappings and not dossier.get('proposedRules'), key + ': insufficient evidence must withhold mappings/rules')
        review = fields(dossier.get('review'), 'status reviewer reviewedOn policy evidence', key + '.review')
        check(review.get('status') == 'unreviewed' and all(review.get(name) is None for name in ('reviewer', 'reviewedOn', 'policy', 'evidence')),
              key + ': clinical review promotion unavailable; provenance must remain absent')
    for rid, request in requests.items():
        fields(request, 'id conditionIds name execution equipment movementRequirements artwork reason sourceRefs status', rid)
        references(request.get('conditionIds'), inventory, rid + '.conditionIds')
        for name in ('name', 'execution', 'artwork', 'reason'):
            text(request.get(name), rid + '.' + name)
        for name in ('equipment', 'movementRequirements'):
            texts(request.get(name), rid + '.' + name, True)
        choices(request.get('status'), 'candidate', rid + '.status: clinical review required')
        for ref in rows(request.get('sourceRefs'), rid + '.sourceRefs', True):
            ref = fields(ref, 'conditionId sourceId location', rid + '.sourceRefs')
            cid, sid, location = ref.get('conditionId'), ref.get('sourceId'), ref.get('location')
            if identifier(cid, rid + '.conditionId') and identifier(sid, rid + '.sourceId'):
                check(cid in rows(request.get('conditionIds'), rid + '.conditionIds'), rid + ': source condition not declared')
                sources = table(dossiers.get(cid, {}).get('sources'), cid + '.sources')
                check(sid in sources, rid + ': unknown source reference')
                if sid in sources:
                    check(location in rows(sources[sid].get('locations'), sid + '.locations'), rid + ': unknown source location')
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--require-complete', action='store_true')
    args = parser.parse_args()
    errors = validate_corpus(Path(__file__).resolve().parents[2], args.require_complete)
    for error in errors:
        print(error)
    if not errors:
        print('Condition research corpus valid (research only; no clinical release).')
    return bool(errors)


if __name__ == '__main__':
    raise SystemExit(main())
