# 발전소 외관과 독립 내관

발전소는 서로 다른 NBT를 사용한다. 작은 외관의 크기를 바꾸더라도 던전 내부는
확대하거나 층을 추가할 수 있다. 일반 데이터 빌드는 저장된 NBT를 패키징하며
아래 생성기를 자동 실행하지 않는다.

| 용도 | NBT 리소스 | 크기 (X×Y×Z) |
| --- | --- | --- |
| 월드 외관 | `cobbleventure:placeholder/power_plant` | 27×15×23 |
| 던전 내관 | `cobbleventure:dungeons/power_plant_interior` | 48×10×48 |

원본은 `content-projects/cobbleventure-main/content/structures/` 아래의
`placeholder/power_plant.nbt`, `dungeons/power_plant_interior.nbt`이다.
각 NBT 옆의 `.structure.json`은 해당 구조의 로컬 좌표를 사용한다.

외관은 낮은 회색 건물, 청색 유리 정면, 붉은 골 지붕, 노란 처마 띠,
4개의 옥상 환기 설비를 사용한다. 입구는 북쪽(작은 Z 방향)이며
`dungeon_entry`는 `[13,1,3]`, 안전 귀환점은 `[13,1,0]`이다.
`worlds/generation_1.json`의 기존 연결은 이 외관 앵커를 계속 참조한다.

내관은 기존 발전기실·정비 구역·변전 구역·제어실 동선을 바탕으로 분리했다.
외부 굴뚝을 제거하고 독립된 10블록 높이의 천장으로 끝낸다.
중앙 기술자는 기존 벽과 겹치던 `[24,1,24]` 대신 통로의 `[24,1,22]`에 배치한다.
전투, 보상, 야생 포켓몬 풀과 제어실 잠금 조건은 그대로다.

## 내관 편집

에딧월드에서 `dungeons/power_plant_interior.nbt`를 편집하고 저장한다.
배치를 바꾸면 옆의 메타데이터에서 entry/exit, encounter/boss, healing_station,
loot, gate, objective 앵커도 이동한다. 앵커의 ID와 reference는 던전 JSON과
연결되므로 유지한다. 바닥 위 두 블록의 이동 공간과 전투 공간을 확보한다.
`control_room_lockdown`은 앵커 기준 X=-2…3, Y=0…2 범위를 막으므로
체크포인트 폭이 달라지면 던전 JSON의 gate 범위도 함께 조정한다.

생성기로 다시 만들면 해당 NBT의 수동 편집 내용이 덮어써진다.
원하는 쪽만 명시적으로 재생성한다. `--output`으로 별도 파일에 먼저 생성할 수 있다.

```powershell
python tools/mod-builder/generate_power_plant_dungeon.py --target exterior
python tools/mod-builder/generate_power_plant_dungeon.py --target interior
python tools/mod-builder/preview_power_plant.py
python -m unittest discover -s tools/mod-builder/tests -p test_power_plant.py
python tools/content-manager/content_manager.py validate --root . --project content-projects/cobbleventure-main
```

생성기 테스트의 바이트 일치 검사는 생성기 기반 원본의 재현성을 검사한다.
수동 편집을 원본으로 채택한다면 이 검사도 해당 편집 방식에 맞게 갱신한다.
미리보기는 블록 좌표와 단순 색상으로 그린 도식이며 실제 게임 렌더링은 아니다.
이미 생성된 월드의 건물은 NBT 파일 교체만으로 자동 재건축되지 않는다.
