"""Schematic geometry preview; not an in-game texture or lighting capture."""
from pathlib import Path

from PIL import Image, ImageDraw
from power_plant_exterior import power_plant_exterior_layout
from starter_gym import power_plant_dungeon_layout

ROOT = Path(__file__).resolve().parents[2]
COLORS = {
    'smooth_stone': '#aeb5ba', 'smooth_stone_slab': '#aeb5ba',
    'light_gray_concrete': '#b9bcc1', 'gray_concrete': '#616978',
    'polished_andesite': '#85929a', 'smooth_quartz': '#eeeae0',
    'smooth_quartz_slab': '#eeeae0', 'yellow_concrete': '#e6ba43',
    'red_terracotta': '#a35e64', 'brick_slab': '#b57277',
    'polished_deepslate_slab': '#3e4652', 'polished_deepslate': '#3e4652',
    'light_blue_stained_glass': '#72b6db', 'light_blue_concrete': '#5796b8',
    'sea_lantern': '#c8e6e3', 'red_concrete': '#b94947',
    'iron_bars': '#b5bbc3', 'lightning_rod': '#c88761',
}


def render():
    size, source = power_plant_exterior_layout()
    blocks = {p: s for p, s in source.items() if s[0] != 'minecraft:air'}
    image = Image.new('RGB', (1160, 800), '#dde5e3')
    draw = ImageDraw.Draw(image)

    def project(x, y, z):
        return (80 + x * 26 + z * 14, 530 + x * 5 - z * 10 - y * 24)

    for (x, y, z), state in sorted(blocks.items(), key=lambda item: (item[0][0] - item[0][2], item[0][1])):
        rgb = tuple(bytes.fromhex(COLORS[state[0].split(':')[1]][1:]))
        slab = state[0].endswith('_slab')
        top_slab = dict(state[1]).get('type') == 'top'
        low = y + (0.5 if slab and top_slab else 0)
        high = y + (0.5 if slab and not top_slab else 1)
        faces = [
            ((0, 0, -1), [(x, low, z), (x+1, low, z), (x+1, high, z), (x, high, z)], .84),
            ((1, 0, 0), [(x+1, low, z), (x+1, low, z+1), (x+1, high, z+1), (x+1, high, z)], .67),
            ((0, 1, 0), [(x, high, z), (x+1, high, z), (x+1, high, z+1), (x, high, z+1)], 1),
        ]
        for (dx, dy, dz), points, shade in faces:
            neighbor = blocks.get((x+dx, y+dy, z+dz))
            if neighbor and not slab and not neighbor[0].endswith('_slab'):
                continue
            color = tuple(int(c * shade) for c in rgb)
            draw.polygon([project(*p) for p in points], fill=color,
                         outline=tuple(max(0, c - 10) for c in color))
    draw.text((35, 28), 'POWER PLANT / EXTERIOR / 27 x 23 footprint / 15 high', fill='#344652')
    draw.text((35, 50), 'Geometry preview - schematic colors, not an in-game screenshot', fill='#627079')
    output = ROOT / 'docs/assets/power_plant_exterior_preview.png'
    output.parent.mkdir(parents=True, exist_ok=True)
    image.save(output)

    _, interior = power_plant_dungeon_layout()
    plan = Image.new('RGB', (650, 690), '#dde5e3')
    draw = ImageDraw.Draw(plan)
    for (x, y, z), state in interior.items():
        if y != 1:
            continue
        color = '#d7dce0' if state[0] == 'minecraft:air' else '#586574'
        draw.rectangle((37+x*12, 640-z*12, 48+x*12, 651-z*12), fill=color)
    import json
    metadata = ROOT / 'content-projects/cobbleventure-main/content/structures/dungeons/power_plant_interior.structure.json'
    for anchor in json.loads(metadata.read_text(encoding='utf-8'))['anchors']:
        x, _, z = anchor['position']
        color = {'entry': '#39a871', 'exit': '#39a871', 'boss': '#d84d50', 'gate': '#dc9e39'}.get(anchor['kind'], '#548bc6')
        draw.ellipse((38+x*12, 641-z*12, 47+x*12, 650-z*12), fill=color)
    draw.text((35, 20), 'INDEPENDENT INTERIOR / 48 x 48 / existing event route retained', fill='#344652')
    draw.text((35, 40), 'Green: entry/exit | Red: boss | Orange: gate | Blue: events', fill='#627079')
    plan.save(ROOT / 'docs/assets/power_plant_interior_plan.png')
    return output


if __name__ == '__main__':
    print(render())
