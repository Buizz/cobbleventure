"""Render the authored blocks with schematic colors (requires Pillow)."""
from PIL import Image, ImageDraw
import generate_round_laboratory as lab

COLORS = {
    'polished_deepslate': '#505658', 'gray_concrete': '#686c69',
    'smooth_sandstone': '#e3dfb7', 'end_stone_bricks': '#c6c793',
    'light_gray_terracotta': '#979782', 'smooth_quartz': '#f4f0df',
    'iron_block': '#dee2df', 'orange_terracotta': '#d79362',
    'red_terracotta': '#bd6758', 'light_blue_stained_glass': '#67b9da',
    'sea_lantern': '#c6ecdf', 'smooth_stone_slab': '#b8bcb6',
    'smooth_stone': '#b8bcb6', 'smooth_quartz_slab': '#f4f0df',
    'iron_door': '#b5c9cb', 'polished_andesite': '#8b9495', 'red_concrete': '#c24840',
}


def render():
    blocks = {p: v for p, v in lab.build_blocks().items() if v[0] != 'minecraft:air'}
    im = Image.new('RGB', (1300, 780), '#e2e8e0')
    draw = ImageDraw.Draw(im)

    def project(x, y, z):
        return (80 + x * 21 + z * 13, 440 + x * 5 - z * 9 - y * 24)

    for (x, y, z), value in sorted(blocks.items(), key=lambda item: (item[0][0] - item[0][2], item[0][1])):
        name = value[0].split(':')[1]
        rgb = tuple(bytes.fromhex(COLORS[name][1:]))
        lo, hi = 0, 1
        if name.endswith('_slab'):
            if dict(value[1])['type'] == 'top':
                lo = .5
            else:
                hi = .5
        faces = [
            ((0, 0, -1), [(x,y+lo,z), (x+1,y+lo,z), (x+1,y+hi,z), (x,y+hi,z)], .8),
            ((1, 0, 0), [(x+1,y+lo,z), (x+1,y+lo,z+1), (x+1,y+hi,z+1), (x+1,y+hi,z)], .65),
            ((0, 1, 0), [(x,y+hi,z), (x+1,y+hi,z), (x+1,y+hi,z+1), (x,y+hi,z+1)], 1),
        ]
        for (dx, dy, dz), verts, shade in faces:
            neighbor = blocks.get((x+dx, y+dy, z+dz))
            if neighbor and not neighbor[0].endswith('_slab'):
                continue
            fill = tuple(int(c * shade) for c in rgb)
            draw.polygon([project(*p) for p in verts], fill=fill,
                         outline=tuple(max(0, c-12) for c in fill))
    draw.text((35, 30), 'ROUND POKEMON LABORATORY | ' + ' x '.join(map(str, lab.SIZE)) + ' blocks', fill='#364b45')
    draw.text((35, 52), 'Actual structure geometry / schematic block colors', fill='#65776f')
    output = lab.ROOT / 'docs/assets/round_laboratory_preview.png'
    im.save(output)
    return output


if __name__ == '__main__':
    print(render())
