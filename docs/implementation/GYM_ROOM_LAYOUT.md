# 체육관 방 구성

새 월드의 체육관은 `catalogs/gyms.json`의 모듈과 양방향 문 연결로 구성한다.
기존 월드의 구조물이나 NPC를 갱신하는 마이그레이션은 하지 않는다.

- `lobby`: 공통 입장룸. 대칭 석상, 체크 타일, 중앙 카펫 색 통로와 벤치.
- `gimmick`: 공통 기둥 통로. 주황색 바닥 마크 8개가 `trainer_1`~`trainer_8`이며,
  체육관에 등록된 트레이너만 해당 마크에 배치된다. 개별 퍼즐 로직은 아직 없다.
- `arena`: 속성별 관장룸. NPC는 관장 하나만 배치한다. 경기장 경계와 중앙선,
  중앙 원형 표식, 양쪽 대전 마크, 좌석 및 속성별 지형으로 구성한다.

각 방은 32×12×32 블록이며 Z=0, 40, 80에 배치된다. 방끼리 직접 이어진
복도가 아니라 `exterior:door ↔ lobby:door`, `lobby:next ↔ gimmick:door`,
`gimmick:next ↔ arena:door` 연결로 이동한다. 외부는 기존 문 상호작용을 사용하고,
내부 입·출구는 각 수작업 NBT에 작성한 베리어 면에 접촉하면 이동한다.
내부 앵커는 `transition`이며 `position`은 베리어 면 위에, `safe_spawn`은
베리어에서 안쪽으로 3블록 떨어진 안전한 바닥 위에 둔다.
기존 체육관 진입 조건과 트레이너 명단은 유지한다.

관장룸은 18개 속성 모두 준비한다. 실제 체육관은 `theme`와 같은
`interiors/gyms/arena_<type>`을 사용한다. `battle_player`는 `arrival` 또는
에딧월드의 `npc_position` 마커를 사용할 수 있다. 별도 `battle_leader`가 없으면
`leader` 마커가 관장 대전 위치다. 이 마커들은 자동으로 추가 NPC를 생성하지 않는다.
`BattlePositioningEvent`는 전투 시작 연출의 위치 고정 전에 발생한다.
체육관 시스템이 관장 태그·소속 방·안전한 발판을 확인한 후 참가자를 서로
마주 보게 배치한다. 다른 플레이어가 해당 방에서 대전을 준비하거나 진행
중이면 새 시작을 거절한다. 일반 트레이너 전투에는 이 위치 배치를 적용하지 않는다.

## 재생성 및 검증

```powershell
python tools/mod-builder/generate_gym_rooms.py
python -m unittest discover -s tools/mod-builder/tests -p test_gym_rooms.py
python -m unittest discover -s tools/content-manager/tests -p 'test_gym*.py'
python tools/content-manager/content_manager.py validate --root . --project content-projects/cobbleventure-main
```

생성기는 수작업한 `arena_rock.nbt`를 기준으로 다른 17개 관장룸만 갱신한다.
바위 원본·입장룸·기믹룸·체육관 카탈로그는 수정하지 않는다.
`packed_mud`, `dripstone_block`, `create:cut_dripstone`, `muddy_mangrove_roots`,
`pointed_dripstone`에 해당하는 지형 팔레트만 속성별 재질로 교체한다.
관람석·조명·경기장 경계선·중앙 표식과 모든 블록 좌표, 블록 엔티티, 모드 NBT 태그는 유지한다.
원본의 지형 재질을 바꾸면 생성기의 지형 매핑도 검토해야 한다.
마커 메타데이터는 바위 원본과 동일한 좌표·타입으로 복제하며 파일 ID만 바꾼다.
`python tools/mod-builder/preview_gym_rooms.py`로 실제 NBT 평면 비교 이미지를 생성한다.
`cobbleventure-player-menu`, `cobbleventure-world-bootstrap`의 빌드·테스트도 실행한다.
실제 출입, 관장과 대화 후 양쪽 대전 위치 이동, 승패 후 조작 복귀는 새 월드에서 확인한다.
