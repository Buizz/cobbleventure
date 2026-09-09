# 로켓단 기지 공동

`rocket` 조각 17종에 기존 로켓단 기지 타일을 사용한다.
카지노 지하 기지의 `cobbleventure:dungeon_pool/rocket_test` 연결을 그대로 유지한다.

- 바닥: `rocket_base_olive_vent` (북쪽 방향으로 정렬)
- 벽: `rocket_base_blue_wall`
- 하단 띠: `rocket_base_blue_band`
- 천장 아래 배선 띠: `rocket_base_cyan_conduit`
- 대형 공동 바닥 표식: `rocket_base_yellow_light_panel`

일반 방에는 기계 1·2와 흰 작전 책상, 의자를 배치한다.
1×2 공동은 기계 1·2·3을 배치한 기계실이고, 2×2 공동은 낮은 청색 칸막이,
작전 테이블, 기계실과 상자 더미를 결합한다. 보물방은 창고 분위기로 꾸민다.
칸막이와 노란 바닥은 원작의 회전 타일 구역을 시각적으로 참고한 장식이며,
이동 강제·회전 퍼즐이나 추가 보상 기능은 없다.

`rocket_dungeon_furniture.py`는 기계 1·2의 `height=0..1`, 기계 3의
`part=0..19`를 모두 저장한다. 대형 기계의 방향별 배치는 Java 블록의
시계 방향 너비·후방 깊이 오프셋에 맞춘다. 일부 파트만 들어가는 위치는
배치하지 않는다. 6칸 기본 통로와 게임플레이 마커 주변은 비워 둔다.

```powershell
python tools/structure-builder/generate_dungeon_piece_skins.py --skin rocket
python -m unittest discover -s tools/structure-builder/tests
python -m unittest discover -s tools/content-manager/tests -p test_dungeon_owned_trainers.py
python tools/content-manager/content_manager.py validate --root .
```

생성기는 선택한 테마의 NBT를 덮어쓴다. 수동 편집본은 먼저 보존한다.
빌딩·포켓몬타워는 별도 조각으로 유지되며 이 명령으로 변경되지 않는다.
원래의 연결점·게임플레이 마커·방 크기를 유지한다. 새로 생성하는 던전부터
변경된 구조물을 사용한다.
