# 포켓몬타워 던전 테마

`pokemon_tower`의 17종 조각에 기존 테마 블록을 적용한다.
바닥은 `pokemon_tower_green_mosaic`, 벽 하단은 `pokemon_tower_purple_plinth`,
벽면은 `pokemon_tower_purple_pillar`, 천장 바로 아래는
`pokemon_tower_purple_cornice`를 사용한다.

입구와 출구는 넓게 비우고, 일반 방은 묘비 열로 추모 공간을 구성한다.
보스 방에는 중앙 전투 공간 양옆으로 묘비를 배치한다.
회복 방은 기존 회복 마커 주변 바닥에 밝은 청색 표식을 넣는다.

16×32와 32×32 공동은 `pokemon_tower_grave`로 구성한 간단한 미로다.
묘비 줄 사이에는 3블록 폭 통로를 두고, 좌우 끝에 번갈아 3블록 폭의
빈틈을 둔다. 출입구에서 다른 출입구와 모든 바닥 통로까지 점프 없이 이동할 수 있다.
기존 묘비의 높이와 충돌 형상은 그대로이므로 플레이어가 점프로 넘어가는 것은 가능하다.
퍼즐 강제 장벽이나 출현·전투·회복 규칙은 추가하지 않는다.

기존 방 크기·연결점·마커와 `cobbleventure:dungeon_pool/pokemon_tower_test`
연결을 유지한다. 새로 생성되는 던전에서 적용된다.

```powershell
python tools/structure-builder/generate_dungeon_piece_skins.py --skin pokemon_tower
python -m unittest discover -s tools/structure-builder/tests
python -m unittest discover -s tools/content-manager/tests -p test_dungeon_owned_trainers.py
python tools/content-manager/content_manager.py validate --root .
```

생성기는 선택한 테마의 NBT를 덮어쓰므로 수동 편집본을 먼저 보존한다.
