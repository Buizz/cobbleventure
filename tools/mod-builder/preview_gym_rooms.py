"""Draw an overhead cutaway from the authored gym block geometry (not a game screenshot)."""
from PIL import Image, ImageDraw, ImageFont
from generate_gym_rooms import ROOT, room

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
    image = Image.new("RGB", (1220, 970), "#15202b")
    draw = ImageDraw.Draw(image)
    font_path = "C:/Windows/Fonts/malgun.ttf"
    title = ImageFont.truetype(font_path, 27)
    label = ImageFont.truetype(font_path, 20)
    small = ImageFont.truetype(font_path, 15)
    draw.text((30, 18), "체육관 구성 · 공통 2개 방 + 속성별 관장룸", font=title, fill="#f3f5f7")
    draw.text((30, 60), "생성 블록 기반 평면 미리보기 / 천장 제외 / 각 방 32×32 / 아래쪽에서 입장", font=small, fill="#b7c4d0")
    cards = [("입장룸", "lobby", "normal"), ("기믹룸 · 트레이너 마크 8개", "gimmick", "normal"),
             ("관장룸 · 바위", "arena", "rock"), ("관장룸 · 물", "arena", "water"),
             ("관장룸 · 얼음", "arena", "ice")]
    for i, (name, kind, theme) in enumerate(cards):
        ox, oy = 30 + (i % 3) * 400, 115 + (i // 3) * 420
        draw.text((ox, oy - 25), name, font=label, fill="#f3f5f7")
        blocks, anchors = room(kind, theme)
        for x in range(32):
            for z in range(32):
                y = max(y for y in range(5) if blocks[x, y, z][0] != "minecraft:air")
                material = blocks[x, y, z][0].split(":")[1]
                px, pz = ox + x * 11, oy + (31 - z) * 11
                color = COLORS.get(material, "#b5bac0")
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
    draw.text((830, 555), "문 연결", font=title, fill="white")
    draw.multiline_text((830, 610), "외부 ↔ 입장룸\n입장룸 ↔ 기믹룸\n기믹룸 ↔ 관장룸\n\n관장룸 18속성 준비\n관장전 시작 시 P / L로 이동\n기존 월드 갱신 없음", font=label, fill="#b7c4d0", spacing=14)
    target = ROOT / "docs/assets/gym_rooms_preview.png"
    target.parent.mkdir(parents=True, exist_ok=True)
    image.save(target)


if __name__ == "__main__":
    main()
