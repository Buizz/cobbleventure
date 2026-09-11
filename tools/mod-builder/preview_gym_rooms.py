"""Draw an overhead cutaway from the authored gym block geometry (not a game screenshot)."""
from PIL import Image, ImageDraw, ImageFont
from generate_gym_rooms import ROOT, DIRECTORY
from content_manager import _read_minecraft_structure_root

COLORS = {
    "smooth_stone": "#abb0b2", "white_concrete": "#e9e7dc",
    "polished_andesite": "#737d80", "quartz_block": "#f3eddc",
    "chiseled_stone_bricks": "#8a8d81", "white_terracotta": "#d8b6a7",
    "light_gray_terracotta": "#b19385", "red_concrete": "#b54a49",
    "smooth_quartz": "#eee8d7", "yellow_terracotta": "#bc9b4b",
    "smooth_sandstone": "#dfcf92", "quartz_pillar": "#e9e1c5",
    "chiseled_quartz_block": "#d8d2bd", "orange_concrete": "#e58c42",
    "light_blue_concrete": "#73bbd6", "blue_concrete": "#426c9c",
    "gray_concrete": "#666d75", "andesite": "#999582",
    "cyan_concrete": "#3e939e", "prismarine": "#77a99b",
    "water": "#589fc2", "blue_ice": "#99cce4", "packed_ice": "#bde2ed",
}


def main():
    COLORS.update({"packed_mud": "#a68a65", "dripstone_block": "#967d6a", "pointed_dripstone": "#baa188",
        "cut_dripstone": "#947864", "muddy_mangrove_roots": "#785f49", "vanilla_mosaic": "#ece8dd",
        "polished_cut_calcite": "#dbd6c6", "red_seat": "#a4353f", "modern_light_10": "#e9e3c1",
        "smooth_quartz_stairs": "#ebe6d6", "small_andesite_brick_stairs": "#8b8f89", "ochre_froglight": "#f0dca1",
        "red_poke_wool_carpet": "#bb4550", "blue_terracotta": "#506384", "prismarine_wall": "#80aaa0",
        "prismarine_bricks": "#83b6a5", "dark_prismarine": "#345c52", "snow_block": "#e6f4f8",
        "red_sandstone": "#be693e", "basalt": "#55515a", "polished_basalt": "#6a6062",
        "red_nether_bricks": "#741f27", "blackstone_wall": "#372f36", "moss_block": "#6b8c3e",
        "azalea_leaves": "#64893f", "mossy_stone_bricks": "#85906c", "rooted_dirt": "#856746", "azalea": "#859c49",
        "magenta_terracotta": "#995b79", "amethyst_block": "#976aca", "purpur_block": "#ad7aa5",
        "purple_terracotta": "#79497e", "amethyst_cluster": "#c391ef", "black_concrete": "#26262a"})
    image = Image.new("RGB", (1220, 970), "#15202b")
    draw = ImageDraw.Draw(image)
    font_path = "C:/Windows/Fonts/malgun.ttf"
    title = ImageFont.truetype(font_path, 27)
    label = ImageFont.truetype(font_path, 20)
    small = ImageFont.truetype(font_path, 15)
    draw.text((30, 18), "수작업 바위 관장룸 기준 · 지형만 속성별 변형", font=title, fill="#f3f5f7")
    draw.text((30, 60), "실제 NBT 평면 / 천장 제외 / 관람석·조명·경계선·입구·마커 공통", font=small, fill="#b7c4d0")
    cards = [("바위 · 수작업 원본", "arena", "rock"), ("물", "arena", "water"), ("얼음", "arena", "ice"), ("불꽃", "arena", "fire"), ("풀", "arena", "grass"), ("에스퍼", "arena", "psychic")]
    for i, (name, kind, theme) in enumerate(cards):
        ox, oy = 30 + (i % 3) * 400, 115 + (i // 3) * 420
        draw.text((ox, oy - 25), name, font=label, fill="#f3f5f7")
        import json
        data = _read_minecraft_structure_root((DIRECTORY / f"arena_{theme}.nbt").read_bytes())
        blocks = {tuple(b["pos"]): (data["palette"][b["state"]]["Name"],) for b in data["blocks"]}
        anchors = json.loads((DIRECTORY / f"arena_{theme}.structure.json").read_text(encoding="utf-8"))["anchors"]
        for x in range(32):
            for z in range(32):
                y = max((y for y in range(5) if blocks[x, y, z][0] not in {"minecraft:air", "minecraft:barrier"}), default=0)
                material = blocks[x, y, z][0].split(":")[1]
                px, pz = ox + x * 11, oy + (31 - z) * 11
                color = "#15202b" if material == "air" else COLORS.get(material, "#b5bac0")
                draw.rectangle((px, pz, px + 10, pz + 10), fill=color)
                if y > 0 and 0 < x < 31 and 0 < z < 31:
                    draw.line((px, pz + 10, px + 10, pz + 10), fill="#48535c", width=2)
        for a in anchors:
            if a["type"] not in {"npc_position", "arrival"}:
                continue
            x, _, z = a["position"]
            px, pz = ox + x * 11 + 5, oy + (31 - z) * 11 + 5
            marker = "P" if a["label"] == "battle_player" else "L" if a["label"] in {"leader", "battle_leader"} else a["label"].split("_")[-1]
            draw.ellipse((px - 9, pz - 9, px + 9, pz + 9), fill="#15202b", outline="#ffffff", width=1)
            draw.text((px, pz - 1), marker, font=small, anchor="mm", fill="white")
        draw.text((ox, oy + 360), "입장 ↑" if kind != "arena" else "P 플레이어 / L 관장", font=small, fill="#b7c4d0")
    target = ROOT / "docs/assets/gym_rooms_preview.png"
    target.parent.mkdir(parents=True, exist_ok=True)
    image.save(target)


if __name__ == "__main__":
    main()
