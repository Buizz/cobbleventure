"""Roof-free plan of the actual generated blocks, with door/NPC anchors."""
from PIL import Image, ImageDraw
import generate_round_laboratory_interiors as lab

COLORS = {
    'air': '#eeeeea', 'lab_floor': '#d5d7db', 'smooth_quartz': '#f2ecda',
    'house_cream_base_wall': '#cfcc96', 'iron_door': '#657581',
    'black_concrete': '#364952', 'light_blue_stained_glass': '#84c9e1',
    'azalea_leaves': '#719c62', 'bookshelf': '#aa8962', 'crafting_table': '#ac8754',
    'quartz_block': '#e4dfca', 'iron_block': '#b5c9c8', 'sea_lantern': '#b0e4df',
    'amethyst_block': '#9471b4', 'purple_stained_glass': '#b197ce',
    'cyan_stained_glass': '#68b8c5', 'pink_stained_glass': '#e5afc4',
    'smooth_quartz_slab': '#ddd6c5',
}
ACCENTS = dict(yellow='#e1c65c', purple='#ae83c8', cyan='#6cbeca', red='#cf7775', lime='#a3c57a')


def render():
    im = Image.new('RGB', (1320, 650), '#25343b')
    draw = ImageDraw.Draw(im)
    draw.text((25, 16), 'ROUND LAB | Lobby + 5 research rooms | Actual blocks, schematic colors', fill='white')
    scale = 10
    layouts = [('lobby', 30, 65)] + [(k, 25 + i * 260, 345) for i, k in enumerate(lab.ROOMS)]
    for key, ox, oz in layouts:
        size, blocks, anchors = lab.build_space(key)
        draw.text((ox, oz - 20), key.upper(), fill='white')
        for x in range(size[0]):
            for z in range(size[2]):
                # Slice below the ceiling; exposed flooring and furnishings.
                name = next(blocks[x, y, z][0].split(':')[-1] for y in range(4, -1, -1)
                            if blocks[x, y, z][0] != 'minecraft:air')
                color = COLORS.get(name, next((v for k, v in ACCENTS.items() if name.startswith(k)), '#ddd6c5'))
                draw.rectangle((ox + x * scale, oz + z * scale, ox + (x + 1) * scale - 1,
                                oz + (z + 1) * scale - 1), fill=color)
        for a in anchors:
            x, _, z = a['position']
            px, pz = ox + x * scale + 5, oz + z * scale + 5
            if a['type'] == 'npc_position':
                draw.ellipse((px - 4, pz - 4, px + 4, pz + 4), fill='#ffae51', outline='#26353b')
            else:
                draw.rectangle((px - 4, pz - 4, px + 4, pz + 4), fill='#fa7272', outline='white')
    draw.text((720, 85), 'Lobby doors, left to right:', fill='white')
    for i, label in enumerate(('TM crafting', 'Z-Move research', 'Mega Evolution', 'Dynamax (optional)', 'EV / IV adjustment')):
        draw.text((720, 110 + i * 22), f'{i + 1}. {label}', fill='white')
    draw.text((25, 610), 'Orange dots: reserved NPC anchors | Red squares: connected doors | Roof hidden', fill='white')
    output = lab.PROJECT.parents[1] / 'docs/assets/round_laboratory_interiors_preview.png'
    im.save(output)
    return output


if __name__ == '__main__':
    print(render())
