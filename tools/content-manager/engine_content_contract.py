"""Validate the startup data consumed before Minecraft's resource managers exist."""
from pathlib import Path
import json
import math
import re

BEHAVIORS = {'stone', 'directional', 'fixed_directional', 'grave', 'double_display_case',
    'double_glass_counter', 'rocket_machine_one', 'rocket_machine_two', 'rocket_machine_three',
    'research_device', 'bookshelf', 'wide_furniture', 'large_bed', 'glow_window', 'double_glow_window'}
COLORS = {'COLOR_BLUE', 'COLOR_GREEN', 'COLOR_LIGHT_BLUE', 'COLOR_LIGHT_GRAY', 'COLOR_LIGHT_GREEN',
    'COLOR_ORANGE', 'COLOR_PURPLE', 'COLOR_YELLOW', 'GOLD', 'METAL', 'SAND', 'TERRACOTTA_YELLOW'}
CAMPAIGN_KEYS = {'generation', 'world_plan', 'settlement_directory', 'cave_directory', 'forest_directory',
    'underground_road_directory', 'starter_settlement', 'starter_settlement_suffix', 'professor_suffix',
    'starter_gate_binding', 'flag_trainer_prefix', 'flag_gym_kanto_prefix', 'flag_story_starter_received',
    'flag_gate_prefix', 'flag_gym_unknown_defeated', 'flag_rewards_field_move_prefix', 'flag_rewards_item_prefix',
    'flag_rewards_item_coin_case_guest', 'flag_rewards_feature_map', 'flag_rewards_feature_pc',
    'flag_rewards_feature_settlement_teleport'}


def validate(project: Path) -> None:
    catalogs = project / 'content/catalogs'
    campaign = json.loads((catalogs / 'campaign.json').read_text(encoding='utf-8'))
    values = campaign.get('values', {})
    if campaign.get('schema_version') != 1 or not CAMPAIGN_KEYS <= values.keys():
        raise ValueError('캠페인 카탈로그에 필수 엔진 설정이 없습니다.')
    if any(not isinstance(value, str) or not value.strip() for value in values.values()):
        raise ValueError('캠페인 설정은 빈 문자열이 아닌 문자열이어야 합니다.')
    for key in ('generation', 'world_plan', 'settlement_directory', 'cave_directory', 'forest_directory',
                'underground_road_directory', 'starter_gate_binding'):
        value = values[key]
        if value.startswith('/') or '..' in value or not re.fullmatch('[a-z0-9_./-]+', value):
            raise ValueError(f'캠페인 리소스 경로가 올바르지 않습니다: {key}')
    theme = json.loads((catalogs / 'theme-blocks.json').read_text(encoding='utf-8'))
    blocks = theme.get('blocks')
    if theme.get('schema_version') != 1 or not isinstance(blocks, list) or not blocks:
        raise ValueError('테마 블록 카탈로그가 비어 있거나 버전이 잘못됐습니다.')
    seen = set()
    for block in blocks:
        identifier = block.get('id', '')
        if not re.fullmatch('[a-z0-9_]+', identifier) or identifier in seen:
            raise ValueError(f'중복되거나 잘못된 테마 블록 ID: {identifier}')
        seen.add(identifier)
        if block.get('behavior') not in BEHAVIORS or block.get('map_color') not in COLORS:
            raise ValueError(f'지원하지 않는 테마 블록 동작 또는 지도색: {identifier}')
        if block.get('sound') not in {'STONE', 'WOOD', 'METAL', 'GLASS'} or block.get('push_reaction') not in {'NORMAL', 'BLOCK', 'DESTROY', 'IGNORE', 'PUSH_ONLY'}:
            raise ValueError(f'지원하지 않는 테마 블록 속성: {identifier}')
        if any(type(block.get(key)) is not bool for key in ('occlusion', 'requires_tool')):
            raise ValueError(f'테마 블록 불리언 속성이 없습니다: {identifier}')
        if type(block.get('light')) is not int or not 0 <= block['light'] <= 15:
            raise ValueError(f'테마 블록 밝기는 0~15여야 합니다: {identifier}')
        for key in ('hardness', 'resistance'):
            value = block.get(key)
            if type(value) not in (int, float) or not math.isfinite(value) or value < 0:
                raise ValueError(f'테마 블록 {key} 값이 올바르지 않습니다: {identifier}')
