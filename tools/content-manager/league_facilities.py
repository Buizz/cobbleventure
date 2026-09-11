"""League authoring and compilation. External building routes only own the lobby."""
from __future__ import annotations

import copy
import hashlib
import json
import re
import tempfile
from pathlib import Path

CATALOG = 'content/catalogs/league-facilities.json'


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def options(root):
    structures = {}
    for path in sorted((root / 'content/structures/interiors').rglob('*.structure.json')):
        if not path.with_suffix('').with_suffix('.nbt').is_file():
            continue
        value = read(path)
        resource = 'cobbleventure:' + path.relative_to(root / 'content/structures').as_posix().removesuffix('.structure.json')
        structures[resource] = value.get('anchors', [])
    npcs = {}
    battle_npcs = []
    for path in sorted((root / 'content/source').rglob('*.json')):
        value = read(path)
        if value.get('enabled', True) and value.get('id', '').startswith('cobbleventure:npc/'):
            name = value.get('name', {})
            npcs[value['id']] = name.get('ko_kr', value['id']) if isinstance(name, dict) else name
            runtime = value.get('event_runtime', {})
            script = runtime.get('script_id', '').split(':', 1)[-1].removeprefix('event_script/')
            script_path = root / 'content/events/cobbleventure' / (script + '.cves')
            if runtime.get('engine') == 'cves_v5' and script_path.is_file() and 'await battle ' in script_path.read_text(encoding='utf-8'):
                battle_npcs.append(value['id'])
    generations = {}
    for path in sorted((root / 'content/worlds').glob('generation_*.json')):
        world = read(path)
        generation = int(path.stem.removeprefix('generation_'))
        generations[generation] = world.get('display_name', {}).get('ko_kr', path.stem)
    starter_path = root / 'content/catalogs/starter-settings.json'
    starts = [r['generation'] for r in read(starter_path).get('generations', []) if r.get('enabled', True)] if starter_path.is_file() else []
    return {'structures': structures, 'npcs': npcs, 'battle_npcs': battle_npcs,
            'generations': generations, 'starter_generations': starts}


def validate(data, available, validate_condition=None):
    def require(ok, message):
        if not ok:
            raise ValueError(message)

    require(isinstance(data, dict) and data.get('schema_version') == 1, '리그 설정 버전은 1이어야 합니다.')
    require(set(data) <= {'$schema', 'schema_version', 'leagues'}, '알 수 없는 리그 설정 필드입니다.')
    require(isinstance(data.get('leagues'), list), '리그 목록이 필요합니다.')
    seen, owned, generated_ids = set(), set(), set()
    for league in data['leagues']:
        require(isinstance(league, dict), '리그는 객체여야 합니다.')
        required = {'id', 'name', 'mode', 'condition_mode', 'conditions', 'locked_dialogue', 'lobby', 'stages', 'hall'}
        require(required <= set(league) <= required | {'generation', 'next_generation', 'generation_travel_mode'}, '리그 필드를 확인하세요.')
        lid = league.get('id')
        require(isinstance(lid, str) and re.fullmatch(r'[a-z0-9_-]+', lid) and lid not in seen, '리그 ID는 중복 없는 영문 소문자·숫자·밑줄·하이픈이어야 합니다.')
        seen.add(lid)
        generation, next_generation = league.get('generation', 0), league.get('next_generation', 0)
        travel_mode = league.get('generation_travel_mode', 'disabled')
        require(travel_mode in ('disabled', 'travel_test'), '세대 이동은 비활성 또는 이동 전용 테스트만 지원합니다.')
        for number in (generation, next_generation):
            require(type(number) is int and (number == 0 or number in available.get('generations', {})), '등록된 세대 맵을 선택하세요.')
        require(not next_generation or (generation and generation != next_generation), '현재 세대와 다른 다음 세대를 지정하세요.')
        if travel_mode == 'travel_test':
            require(next_generation in available.get('starter_generations', []), '다음 세대의 활성 시작 위치가 필요합니다.')
        require(isinstance(league['name'], str) and league['name'].strip(), f'{lid}: 표시 이름이 필요합니다.')
        require(league['mode'] in ('checkpoint', 'relay'), f'{lid}: 진행 방식은 checkpoint 또는 relay입니다.')
        require(league['condition_mode'] in ('all', 'any'), f'{lid}: 조건 결합 방식을 확인하세요.')
        require(isinstance(league['conditions'], list), f'{lid}: 입장 조건은 목록이어야 합니다.')
        for condition in league['conditions']:
            require(isinstance(condition, dict), '입장 조건은 객체여야 합니다.')
            if validate_condition:
                validate_condition(condition)
        require(isinstance(league['locked_dialogue'], list) and all(isinstance(s, str) for s in league['locked_dialogue']), '잠금 안내는 문자열 목록이어야 합니다.')
        stages = league['stages']
        require(isinstance(stages, list) and 2 <= len(stages) <= 16, f'{lid}: 엘리트 1~15명과 마지막 챔피언을 설정하세요.')
        stage_ids = set()
        for i, room in enumerate([league['lobby'], *stages, league['hall']]):
            is_stage = 1 <= i <= len(stages)
            require(isinstance(room, dict), '방 설정은 객체여야 합니다.')
            base = {'structure', 'entry', 'exit'}
            extra = {'id', 'role', 'trainer', 'npc_anchor'} if is_stage else ({'leave', 'fixed_npcs', 'fixed_pokemon'} if i == 0 else set())
            allowed = base | extra | ({'advance'} if i == len(stages) + 1 else set())
            require(base | extra <= set(room) <= allowed, f'{lid}: 방 설정 필드를 확인하세요 ({i}).')
            structure = room.get('structure')
            require(isinstance(structure, str) and structure in available['structures'], f'{lid}: 없는 내부 NBT입니다: {structure}')
            # Templates own their NPC definitions; sharing within/across leagues would conflict.
            require(structure not in owned, f'리그 방 NBT는 중복 사용할 수 없습니다: {structure}')
            owned.add(structure)
            anchors = {a.get('id', a.get('label')): a for a in available['structures'][structure]}

            def anchor(key, types):
                require(isinstance(key, str) and key in anchors and anchors[key].get('type') in types, f'{structure}: 올바른 {"/".join(types)} 마커가 필요합니다: {key}')
            anchor(room['entry'], ('arrival', 'interior_spawn'))
            anchor(room['exit'], ('door', 'transition'))
            if i == len(stages) + 1 and travel_mode == 'travel_test':
                anchor(room.get('advance'), ('door', 'transition'))
                require(room['advance'] != room['exit'], '다음 세대 이동과 로비 복귀 출구를 구분하세요.')
            if is_stage:
                sid = room['id']
                require(isinstance(sid, str) and re.fullmatch(r'[a-z0-9_-]+', sid) and sid not in stage_ids and sid != 'hall', f'{lid}: 중복 없는 단계 ID가 필요합니다 (hall은 명예의 전당 예약어).')
                stage_ids.add(sid)
                generated_id = npc_id(league, room)
                require(generated_id not in generated_ids and generated_id not in available['npcs'], f'자동 생성 NPC ID가 원본 또는 다른 리그와 중복됩니다: {generated_id}')
                generated_ids.add(generated_id)
                require(room['role'] == ('champion' if i == len(stages) else 'elite'), '마지막 방만 챔피언이어야 합니다.')
                trainer = room['trainer']
                require(isinstance(trainer, dict) and set(trainer) == {'name', 'appearance', 'dialogue', 'battle'}, '트레이너 이름·외형·대사·포켓몬 엔트리를 설정하세요.')
                require(isinstance(trainer['name'], dict) and isinstance(trainer['name'].get('ko_kr'), str) and trainer['name']['ko_kr'].strip(), '트레이너 이름이 필요합니다.')
                require(isinstance(trainer['dialogue'], dict) and set(trainer['dialogue']) == {'challenge', 'victory', 'defeat'}, '도전·승리·패배 대사가 필요합니다.')
                require(all(isinstance(v, str) and v.strip() for v in trainer['dialogue'].values()), '대사는 비어 있지 않은 문자열이어야 합니다.')
                appearance = trainer['appearance']
                require(isinstance(appearance, dict) and appearance.get('source') in ('rct_single', 'rct_group', 'custom', 'entity'), 'NPC 외형 종류를 확인하세요.')
                require(isinstance(appearance.get('resource'), str) and re.fullmatch(r'[a-z0-9_.-]+:[a-z0-9_/.-]+', appearance['resource']), 'NPC 외형 리소스를 지정하세요.')
                require(isinstance(trainer['battle'], dict), '전투 설정이 필요합니다.')
                # Reuse the battle editor's full validation (levels, moves, EVs, IVs, rules).
                from content_manager import validate_battle_preset_file
                with tempfile.TemporaryDirectory(prefix='league-battle-') as directory:
                    candidate = Path(directory) / 'battle.json'
                    candidate.write_text(json.dumps(battle_document(league, room)), encoding='utf-8')
                    _, errors = validate_battle_preset_file(candidate)
                require(not any(e.level == 'error' for e in errors), '; '.join(e.message for e in errors if e.level == 'error'))
                anchor(room['npc_anchor'], ('npc_position',))
                anchor(room['npc_anchor'] + '_battle_player', ('arrival', 'npc_position'))
            elif i == 0:
                anchor(room['leave'], ('door', 'transition'))
                require(room['leave'] != room['exit'], '로비 도전 출구와 외부 복귀 출구는 달라야 합니다.')
                slots = set()
                for field in ('fixed_npcs', 'fixed_pokemon'):
                    require(isinstance(room[field], dict), f'{field}는 객체여야 합니다.')
                    for slot, value in room[field].items():
                        anchor(slot, ('npc_position',))
                        require(slot not in slots and not slot.endswith('_battle_player'), f'중복되거나 전투 위치인 배치 마커입니다: {slot}')
                        slots.add(slot)
                        require(isinstance(value, str) and value.strip(), '배치할 대상을 지정하세요.')
                        if field == 'fixed_npcs':
                            require(value in available['npcs'], f'없는 NPC입니다: {value}')


def compile_settings(settings, data, source_root=None):
    """Expand lobby connections into runtime rooms without writing generated routes to authoring."""
    result = copy.deepcopy(settings)
    buildings = result.setdefault('buildings', {})
    owned = {room['structure']: (league['id'], kind)
             for league in data['leagues']
             for kind, room in [('lobby', league['lobby']), *[('stage', r) for r in league['stages']], ('hall', league['hall'])]}
    for building in buildings.values():
        for interior in building.get('interiors', []):
            owner = owned.get(interior['structure'])
            if owner and owner[1] != 'lobby':
                raise ValueError('리그 내부 방은 건물 연결에서 직접 추가할 수 없습니다. 해당 리그 로비를 연결하세요.')
    for league in data['leagues']:
        lobby = league['lobby']
        # Owned interiors may have unrelated authored music/placement options.
        for room in [lobby, *league['stages'], league['hall']]:
            item = buildings.setdefault(room['structure'], {})
            if item.get('fixed_npcs') or item.get('fixed_pokemon') or item.get('interiors') or item.get('door_routes'):
                raise ValueError(f'리그 방 배치·연결은 리그 화면에서 관리하세요: {room["structure"]}')
        owners = []
        for structure, building in list(buildings.items()):
            matches = [r for r in building.get('interiors', []) if r['structure'] == lobby['structure']]
            if matches:
                if len(matches) != 1:
                    raise ValueError('한 건물에 같은 리그 로비를 중복 연결할 수 없습니다.')
                owners.append((structure, building, matches[0]['key']))
        for structure, building, lobby_key in owners:
            if 'runtime_league' in building:
                raise ValueError(f'건물에는 리그 로비 하나만 연결할 수 있습니다: {structure}')
            allowed = {f'{lobby_key}:{lobby["leave"]}'}
            for source, target in building.get('door_routes', {}).items():
                if source.startswith(lobby_key + ':') and source not in allowed:
                    raise ValueError('리그 로비의 도전 출구는 리그 설정이 관리합니다.')
                if target.get('space') == lobby_key and target.get('door', target.get('arrival')) != lobby['entry']:
                    raise ValueError('외부 건물은 리그 로비의 입장 마커에 연결하세요.')
            leave_key = f'{lobby_key}:{lobby["leave"]}'
            if leave_key not in building.get('door_routes', {}):
                entrances = [source for source, target in building.get('door_routes', {}).items()
                             if source.startswith('exterior:') and target.get('space') == lobby_key
                             and target.get('door', target.get('arrival')) == lobby['entry']]
                if len(entrances) != 1:
                    raise ValueError(f'{structure}: 자동 복귀에는 외부 입구 연결 하나가 필요합니다. 입구가 여러 개면 복귀 연결을 지정하세요.')
                building.setdefault('door_routes', {})[leave_key] = {
                    'space': 'exterior', 'door': entrances[0].split(':', 1)[1]
                }
            for field in ('fixed_npcs', 'fixed_pokemon'):
                assignments = building.setdefault(field, {})
                if any(k.startswith(lobby_key + ':') for k in assignments):
                    raise ValueError('로비 NPC·포켓몬 배치는 리그 화면에서 관리하세요.')
                assignments.update({f'{lobby_key}:{k}': v for k, v in lobby[field].items()})
            rows = [dict(key=lobby_key, stage=-1, **lobby)]
            for index, room in enumerate([*league['stages'], league['hall']]):
                key = f'league_{league["id"]}_{room.get("id", "hall")}'
                if any(r['key'] == key for r in building.get('interiors', [])):
                    raise ValueError(f'리그 생성 공간 이름과 충돌합니다: {key}')
                building.setdefault('interiors', []).append({'key': key, 'structure': room['structure']})
                runtime_room = {k: copy.deepcopy(v) for k, v in room.items() if k != 'trainer'}
                if 'trainer' in room:
                    runtime_room['npc'] = npc_id(league, room)
                    building['fixed_npcs'][f'{key}:{room["npc_anchor"]}'] = runtime_room['npc']
                rows.append(dict(key=key, stage=index, **runtime_room))
            if source_root:
                for row in rows:
                    path = source_root / 'content/structures' / (row['structure'].split(':', 1)[1] + '.nbt')
                    row['revision'] = hashlib.sha256(path.read_bytes() + path.with_suffix('.structure.json').read_bytes()).hexdigest()
            routes = building.setdefault('door_routes', {})
            for index, room in enumerate(rows):
                next_room = rows[index + 1] if index + 1 < len(rows) else rows[0]
                routes[f'{room["key"]}:{room["exit"]}'] = {'space': next_room['key'], 'door': next_room['entry']}
            building['runtime_league'] = {k: copy.deepcopy(league[k]) for k in ('id', 'mode', 'condition_mode', 'conditions', 'locked_dialogue')}
            building['runtime_league']['rooms'] = rows
            for key in ('generation', 'next_generation', 'generation_travel_mode'):
                if key in league:
                    building['runtime_league'][key] = league[key]
    return result


def payload(root):
    return {'catalog': read(root / CATALOG), **options(root)}


def save(root, data, validate_condition):
    validate(data, options(root), validate_condition)
    settings = read(root / 'content/catalogs/building-settings.json')
    replacements = {l['id']: l for l in data['leagues']}
    for old in read(root / CATALOG)['leagues']:
        replacement = replacements.get(old['id'])
        if replacement and replacement['lobby']['structure'] == old['lobby']['structure']:
            continue
        if any(i['structure'] == old['lobby']['structure'] for b in settings['buildings'].values() for i in b.get('interiors', [])):
            raise ValueError(f'{old["name"]}: 리그 삭제·ID 변경·로비 NBT 교체 전에 외부 건물의 로비 연결을 해제하세요.')
    compile_settings(settings, data)
    path = root / CATALOG
    temporary = path.with_suffix('.json.tmp')
    temporary.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    temporary.replace(path)


def encounter_path(league, room):
    return f"league/{league['id']}_{room['id']}"


def npc_id(league, room):
    return 'cobbleventure:npc/' + encounter_path(league, room)


def battle_document(league, room):
    battle = copy.deepcopy(room['trainer']['battle'])
    battle['trainer_id'] = 'cobbleventure:trainer/' + encounter_path(league, room)
    return {'schema_version': 1, 'id': 'cobbleventure:battle/' + encounter_path(league, room),
            'enabled': True, 'name': copy.deepcopy(room['trainer']['name']), 'battle': battle}


def generated_encounters(root=None, data=None):
    if data is None:
        path = root / CATALOG
        if not path.is_file(): return []
        data = read(path)
    result = []
    for league in data['leagues']:
        for room in league['stages']:
            if 'trainer' not in room: continue
            trainer = room['trainer']
            path = encounter_path(league, room)
            script_id = 'cobbleventure:event_script/' + path
            flag = f"cobbleventure:flag/league/{league['id']}/{room['id']}/defeated"
            quote = lambda value: json.dumps(value, ensure_ascii=False)
            def say(key):
                return '\n'.join('    say npc ' + quote(line.strip()) for line in trainer['dialogue'][key].splitlines() if line.strip())
            script = ('event interact(range: 4) {\n  page default {\n' + say('challenge')
                      + '\n    choice "도전하시겠습니까?" {\n      "승부한다" {\n'
                      + '        id "challenge/battle" await battle ' + quote('cobbleventure:battle/' + path) + ' -> result\n'
                      + '        if result.outcome == "win" {\n          id "victory/progress" set_flag ' + quote(flag) + ' true\n'
                      + say('victory') + '\n        } else {\n' + say('defeat') + '\n        }\n      }\n'
                      + '      "다음에 도전한다" { stop }\n    }\n  }\n}\n')
            npc = {'schema_version': 4, 'id': npc_id(league, room), 'enabled': True,
                   'name': copy.deepcopy(trainer['name']), 'description': {'ko_kr': '리그 시설 설정에서 자동 생성한 NPC'},
                   'tags': ['trainer', 'league', league['id'], 'generated_from_league_facility'],
                   'placement_profile': {'classification': 'trainer', 'automatic_town_placement': False,
                                         'automatic_route_placement': False, 'preferred_biomes': []},
                   'npc': {'display_name': copy.deepcopy(trainer['name']), 'role': 'default',
                           'trainer_class': 'cobbleventure:trainer_class/' + ('champion' if room['role'] == 'champion' else 'elite_four'),
                           'appearance': copy.deepcopy(trainer['appearance']),
                           'behavior': {'movement': 'stationary', 'look_at_player': True, 'invulnerable': True, 'collision': True}},
                   'event_runtime': {'engine': 'cves_v5', 'authoring': 'custom', 'script_id': script_id},
                   'event_design': {'mode': 'easy_npc_events'},
                   'events': [{'id': 'on_interact', 'trigger': {'type': 'interact', 'range': 4},
                               'commands': [{'type': 'dialogue', 'id': 'fallback', 'speaker': 'npc', 'text': {'ko_kr': trainer['dialogue']['challenge']}}, {'type': 'end'}]}],
                   '_cves_binding_tag': 'cves_binding/cobbleventure/' + path}
            result.append({'path': path, 'npc': npc, 'battle': battle_document(league, room),
                           'script': script, 'script_id': script_id, 'flag': flag,
                           'binding': {'schema_version': 1, 'script_id': script_id}})
    return result
