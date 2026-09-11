"""Research configuration validation, kept independent from the HTTP editor."""
import re

COSTS = {'z_gems', 'exclusive_z_gems', 'mega_money', 'iv_point', 'ev_point', 'dynamax_level_mushrooms', 'gmax_mushrooms'}
MATERIALS = {'blank_z', 'mega', 'mushroom'}
TYPES = set('normal fire water electric grass ice fighting poison ground flying psychic bug rock ghost dragon dark steel fairy'.split())


def validate(data):
    def require(condition, message):
        if not condition:
            raise ValueError(message)

    def resource(value):
        return isinstance(value, str) and re.fullmatch(r'[a-z0-9_.-]+:[a-z0-9_/.-]+', value)

    require(isinstance(data, dict), '연구 설정은 객체여야 합니다.')
    require(set(data) <= {'$schema', 'schema_version', 'costs', 'materials', 'z_crystals', 'dynamax_excluded'}, '알 수 없는 연구 설정 키입니다.')
    require(type(data.get('schema_version')) is int and data['schema_version'] == 1, '연구 설정 버전은 1이어야 합니다.')
    costs = data.get('costs')
    require(isinstance(costs, dict) and set(costs) == COSTS, '모든 연구 비용 항목이 필요합니다.')
    require(all(type(v) is int and 0 <= v <= 1_000_000 for v in costs.values()), '연구 비용은 0~1,000,000 정수여야 합니다.')
    materials = data.get('materials')
    require(isinstance(materials, dict) and set(materials) == MATERIALS, '연구 재료 항목이 필요합니다.')
    require(all(resource(v) for v in materials.values()), '올바른 재료 아이템 ID가 필요합니다.')
    require(isinstance(data.get('dynamax_excluded'), list) and all(isinstance(v, str) and v for v in data['dynamax_excluded']), '다이맥스 제외 목록을 확인하세요.')
    rows = data.get('z_crystals')
    require(isinstance(rows, list) and len(rows) <= 128, 'Z크리스탈 목록은 128개 이하여야 합니다.')
    seen = set()
    for row in rows:
        require(isinstance(row, dict) and set(row) == {'item', 'type', 'users', 'move'}, 'Z크리스탈 필드를 확인하세요.')
        require(resource(row['item']) and row['item'] not in seen, 'Z크리스탈 ID가 잘못되었거나 중복입니다.')
        seen.add(row['item'])
        require(isinstance(row['type'], str) and row['type'].lower() in TYPES, 'Z크리스탈 속성을 확인하세요.')
        require(isinstance(row['users'], list) and all(isinstance(v, str) and v for v in row['users']), '전용 Z기술 대상 종·폼을 확인하세요.')
        require(isinstance(row['move'], str) and bool(row['move']) == bool(row['users']), '전용 Z기술은 대상 종·폼과 원본 기술을 함께 지정해야 합니다.')
