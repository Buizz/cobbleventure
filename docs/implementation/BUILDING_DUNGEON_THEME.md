# 빌딩 던전 테마

실프주식회사의 기존 로켓단 시설 조각 계약을 복사한 독립 테마다.
테마 ID는 `building`, 풀은 `cobbleventure:dungeon_pool/building_test`다.
`rocket_silph_company.json`의 풀과 공동 조각 목록이 이 테마를 사용한다.
로켓단 기지와 포켓몬타워는 기존 조각을 계속 사용한다.

## 벽과 실내

- `building_lower_band`: 제공된 첫 번째 16×16 원본 타일. 바닥 바로 위 띠벽.
- `building_upper_band`: 두 번째 원본 타일. 천장 바로 아래 띠벽.
- `building_pale_wall`: 세 번째 원본 타일. 나머지 벽면과 블록의 윗면·아랫면.
- 바닥은 청록색 프리즈머린 벽돌, 천장은 매끄러운 석영과 바다 랜턴이다.
- 일반 방은 흰 책상·검은 모니터·의자·화분으로 꾸민다.
- 입구·지원·출구는 벤치가 있는 휴게 공간이다.
- 1×2 공동은 사무 공간, 2×2 공동은 사무 공간과 책장이 있는 수납 구역이다.

벽은 일반 고체 블록이며 건축 블록 탭에서 한국어 이름으로 찾을 수 있다.
띠는 수평 네 면에만 표시되므로 모서리에서도 같은 높이로 이어진다.
가구는 장식용이며 별도의 상호작용·회복·보상 기능을 추가하지 않는다.

모든 조각은 기존 크기·연결점·게임플레이 마커를 유지한다.
장식은 6블록 폭의 기본 통로와 마커 주변을 비워 두며, 계단에는 배치하지 않는다.
변경된 NBT는 새로 생성되는 던전부터 반영된다.

## 재생성과 검증

```powershell
python tools/structure-builder/generate_dungeon_piece_skins.py --skin building
python -m unittest discover -s tools/structure-builder/tests -p test_building_dungeon.py
python tools/content-manager/content_manager.py validate --root .
.\projects\cobbleventure-battle-ai\gradlew.bat -p projects/cobbleventure-theme-blocks build
```

생성기는 선택한 테마의 NBT를 덮어쓴다. 편집기로 수정한 조각은 먼저 보존한다.
회귀 테스트는 저장 NBT와 생성기 일치, 연결 계약, 마커의 발판과 머리 공간,
연결점·마커 사이의 이동 가능 여부 및 실프주식회사 풀 연결을 확인한다.
