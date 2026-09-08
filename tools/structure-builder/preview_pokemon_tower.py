"""Render the authored NBT geometry with schematic block colors (Pillow)."""
from PIL import Image, ImageDraw
import generate_pokemon_tower as tower

COLORS = {
    'smooth_stone': '#aaaeb1', 'light_gray_concrete': '#92929b',
    'green_terracotta': '#65724a', 'lime_terracotta': '#84934f',
    'end_stone_bricks': '#d5d89e', 'smooth_sandstone': '#e4dfb5',
    'gray_stained_glass': '#4b555c', 'sea_lantern': '#d5efdf',
    'polished_deepslate': '#41424b',
}


def render():
    blocks = {p: v for p, v in tower.build_blocks().items() if v[0] != 'minecraft:air'}
    image = Image.new('RGB', (1000, 1300), '#e3e8ec')
    draw = ImageDraw.Draw(image)

    def project(x, y, z):
        return (90 + x * 16 + z * 10, 1080 + x * 4 - z * 6 - y * 15)

    for (x, y, z), value in sorted(blocks.items(), key=lambda item: (item[0][0] - item[0][2], item[0][1])):
        rgb = tuple(bytes.fromhex(COLORS[value[0].split(':')[1]][1:]))
        faces = [
            ((0, 0, -1), [(x,y,z), (x+1,y,z), (x+1,y+1,z), (x,y+1,z)], .85),
            ((1, 0, 0), [(x+1,y,z), (x+1,y,z+1), (x+1,y+1,z+1), (x+1,y+1,z)], .65),
            ((0, 1, 0), [(x,y+1,z), (x+1,y+1,z), (x+1,y+1,z+1), (x,y+1,z+1)], 1),
        ]
        for (dx, dy, dz), verts, shade in faces:
            if (x+dx, y+dy, z+dz) in blocks:
                continue
            fill = tuple(int(c * shade) for c in rgb)
            draw.polygon([project(*p) for p in verts], fill=fill,
                         outline=tuple(max(0, c - 12) for c in fill))
    draw.text((30, 25), 'POKEMON TOWER | ' + ' x '.join(map(str, tower.SIZE)) + ' blocks', fill='#354047')
    draw.text((30, 45), 'Authored structure geometry / schematic colors', fill='#59646b')
    output = tower.ROOT / 'docs/assets/pokemon_tower_preview.png'
    output.parent.mkdir(parents=True, exist_ok=True)
    image.save(output)
    return output


if __name__ == '__main__':
    print(render())
