"""Build the read-only condition bibliography bundled with the Android app."""
import argparse
import json
from pathlib import Path
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[2]
DOSSIERS = ROOT / 'docs/research/conditions'
ASSET = ROOT / 'app/src/main/assets/condition-reference/library.json'

REGIONS_ES = {
    'Spine': 'Columna vertebral',
    'Shoulder': 'Hombro',
    'Elbow, wrist, hand': 'Codo, muñeca y mano',
    'Hip, groin, gluteal': 'Cadera, ingle y glúteos',
    'Knee': 'Rodilla',
    'Lower leg, ankle, foot': 'Parte inferior de la pierna, tobillo y pie',
    'Systemic or multiple regions': 'Sistémicas o de varias regiones',
}
NAMES_ES = {
    'chronic-primary-low-back-pain': 'Dolor lumbar primario crónico',
    'lumbar-radicular-pain': 'Dolor radicular lumbar',
    'lumbar-spinal-stenosis': 'Estenosis del canal lumbar',
    'nonspecific-neck-pain': 'Dolor cervical inespecífico',
    'cervical-radicular-pain': 'Dolor radicular cervical',
    'whiplash-associated-disorder': 'Trastorno asociado al latigazo cervical',
    'adult-degenerative-scoliosis': 'Escoliosis degenerativa del adulto',
    'rotator-cuff-tendinopathy': 'Tendinopatía del manguito rotador',
    'full-thickness-rotator-cuff-tear': 'Desgarro de espesor completo del manguito rotador',
    'adhesive-capsulitis': 'Capsulitis adhesiva',
    'glenohumeral-osteoarthritis': 'Artrosis glenohumeral',
    'shoulder-instability': 'Inestabilidad del hombro',
    'acromioclavicular-osteoarthritis': 'Artrosis acromioclavicular',
    'lateral-elbow-tendinopathy': 'Tendinopatía lateral del codo',
    'medial-elbow-tendinopathy': 'Tendinopatía medial del codo',
    'distal-biceps-tendinopathy': 'Tendinopatía distal del bíceps',
    'carpal-tunnel-syndrome': 'Síndrome del túnel carpiano',
    'ulnar-neuropathy-at-the-elbow': 'Neuropatía del nervio cubital en el codo',
    'de-quervain-tenosynovitis': 'Tenosinovitis de De Quervain',
    'thumb-carpometacarpal-osteoarthritis': 'Artrosis carpometacarpiana del pulgar',
    'finger-interphalangeal-osteoarthritis': 'Artrosis interfalángica de los dedos de la mano',
    'hip-osteoarthritis': 'Artrosis de cadera',
    'gluteal-tendinopathy': 'Tendinopatía glútea',
    'femoroacetabular-impingement-syndrome': 'Síndrome de pinzamiento femoroacetabular',
    'adductor-related-groin-pain': 'Dolor inguinal relacionado con los aductores',
    'proximal-hamstring-tendinopathy': 'Tendinopatía proximal de los isquiotibiales',
    'iliopsoas-related-groin-pain': 'Dolor inguinal relacionado con el iliopsoas',
    'piriformis-syndrome': 'Síndrome del piriforme',
    'knee-osteoarthritis': 'Artrosis de rodilla',
    'patellofemoral-pain': 'Dolor femoropatelar',
    'patellar-tendinopathy': 'Tendinopatía rotuliana',
    'iliotibial-band-related-lateral-knee-pain': 'Dolor lateral de rodilla relacionado con la banda iliotibial',
    'degenerative-meniscal-lesion': 'Lesión meniscal degenerativa',
    'anterior-cruciate-ligament-injury': 'Lesión del ligamento cruzado anterior',
    'medial-collateral-ligament-sprain': 'Esguince del ligamento colateral medial',
    'midportion-achilles-tendinopathy': 'Tendinopatía de la porción media del tendón de Aquiles',
    'insertional-achilles-tendinopathy': 'Tendinopatía insercional del tendón de Aquiles',
    'plantar-heel-pain-compatible-with-plantar-fasciitis': 'Dolor plantar del talón compatible con fascitis plantar',
    'lateral-ankle-sprain': 'Esguince lateral de tobillo',
    'chronic-ankle-instability': 'Inestabilidad crónica de tobillo',
    'posterior-tibial-tendinopathy': 'Tendinopatía del tibial posterior',
    'peroneal-tendinopathy': 'Tendinopatía de los peroneos',
    'medial-tibial-stress-syndrome': 'Síndrome de estrés tibial medial',
    'lower-limb-bone-stress-injury': 'Lesión ósea por estrés de la extremidad inferior',
    'first-metatarsophalangeal-osteoarthritis': 'Artrosis de la primera articulación metatarsofalángica',
    'osteoporosis': 'Osteoporosis',
    'rheumatoid-arthritis': 'Artritis reumatoide',
    'axial-spondyloarthritis': 'Espondiloartritis axial',
    'fibromyalgia': 'Fibromialgia',
    'hypermobility-spectrum-disorder': 'Trastorno del espectro de hipermovilidad',
}


def build_library():
    def read(path):
        return json.loads(path.read_text(encoding='utf-8'))

    rows = read(DOSSIERS / 'index.json')['conditions']
    if {row['id'] for row in rows} != NAMES_ES.keys():
        raise ValueError('Provide a Spanish reference label for every condition in the inventory')
    shipped = {row['id'] for row in read(ROOT / 'app/src/main/assets/wallcrawl/catalog.json')['exercises']}
    by_source = {}
    for request in read(DOSSIERS / 'exercise-requests.json')['requests']:
        if request['id'] in shipped:
            for ref in request['sourceRefs']:
                if ref['conditionId'] in request['conditionIds']:
                    by_source.setdefault((ref['conditionId'], ref['sourceId']), set()).add(request['id'])
    regions = {}
    for row in rows:
        condition_id = row['id']
        dossier = read(DOSSIERS / f'{condition_id}.json')
        region = row['region'].rsplit(' (', 1)[0]
        group = regions.setdefault(region, {'en': region, 'es': REGIONS_ES[region], 'entries': []})
        label = dossier['condition']['label']
        sources = []
        for source in dossier['sources']:
            url = urlsplit(source['url'])
            if url.scheme != 'https' or not url.hostname or url.username is not None or url.port not in (None, 443):
                raise ValueError(f'Unexpected source URL for {condition_id}/{source["id"]}')
            access = source['access']['status']
            if access not in ('full-text', 'abstract-only'):
                raise ValueError(f'Unrecognized source access for {condition_id}/{source["id"]}')
            sources.append({
                'id': source['id'], 'title': source['title'], 'url': source['url'], 'access': access,
                'population': source['population'], 'findings': source['outcomes'],
                'limitations': source['limitations'],
                'exerciseIds': sorted(by_source.get((condition_id, source['id']), ())),
            })
        group['entries'].append({
            'id': condition_id, 'en': label[:1].upper() + label[1:], 'es': NAMES_ES[condition_id],
            'aliases': dossier['condition']['aliases'], 'sources': sources,
        })
    return list(regions.values())


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    data = json.dumps(build_library(), ensure_ascii=False, indent=2) + '\n'
    if args.check:
        if not ASSET.is_file() or ASSET.read_text(encoding='utf-8') != data:
            raise SystemExit('Regenerate the condition reference with build_condition_reference.py')
    else:
        ASSET.parent.mkdir(parents=True, exist_ok=True)
        ASSET.write_text(data, encoding='utf-8')
