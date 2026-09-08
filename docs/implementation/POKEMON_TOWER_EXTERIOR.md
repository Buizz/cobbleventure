# 포켓몬타워 외관과 입장 이미지

- 외관: `content-projects/cobbleventure-main/content/structures/placeholder/pokemon_tower.nbt`
- 크기: 32×66×32 (반구형 돔과 정상 장식에 맞춰 높이 확장)
- 북쪽 현관의 입장 앵커 `[15, 1, 1]`, 귀환 위치 `[15, 1, 3]` 유지
- 넓은 28×28 사각 회색 기단 위에 폭이 좁은 탑을 얹은 형태. 모서리 깎기는 상층에만 적용
- 7개 층의 반복 창문, 밝은 기둥과 층간 띠, 팔각 테두리 위의 녹색 반구형 돔 (반지름·높이 각 11블록)
- 외관 상층은 장식용이며 실제 던전 공간은 기존 던전 조각으로 생성
- 입장 화면: `projects/cobbleventure-world-bootstrap/src/main/resources/assets/cobbleventure_bootstrap/textures/gui/dungeons/pokemon_tower.png`
- 외관 미리보기: `docs/assets/pokemon_tower_preview.png` (블록 형상과 개략 색상, 게임 스크린샷 아님)

재생성 및 검사:

```powershell
python tools/structure-builder/generate_pokemon_tower.py
python tools/structure-builder/preview_pokemon_tower.py
python -m unittest discover -s tools/structure-builder/tests -p test_pokemon_tower.py
python tools/content-manager/content_manager.py validate --root .
```

입장 이미지는 기존 로켓단 배경과 같은 Minecraft풍 3D 추모탑 내부로 제작했다.
생성 방식과 최종 프롬프트는 [던전 입장 배경 이미지](DUNGEON_ENTRY_ART.md)를 참고한다.
