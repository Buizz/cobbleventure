# 리그 아치길

길 설정의 `길 종류`에서 **리그 아치길** (`league_arch`)을 선택하고 저장한다. 기존 도로 바닥 생성 규칙을 사용하며, 길 생성 뒤 아치·조각상 모듈을 12블록 간격으로 배치한다. 포켓몬 출현·레벨·음악 설정은 변경하지 않는다.

## 분리한 NBT

- `cobbleventure:road_decorations/league_arch`: 첫 아치와 조각상
- `cobbleventure:road_decorations/league_arch_2`: 두 번째 아치와 조각상
- `cobbleventure:road_decorations/league_arch_3`: 세 번째 아치와 조각상
- `cobbleventure:road_decorations/league_approach`: 원래 장식 전체를 함께 배치하기 위한 보존본

반복 모듈은 폭 20, 높이 12, 깊이 12(마지막 11)블록이다. 원본 블록 상태와 블록 엔티티 태그를 유지했다. 길 바닥 및 공기 블록은 포함하지 않으므로 NBT 배치가 기존 길을 지우지 않는다. 좌우 기둥의 밑동에는 원래 흙 블록이 남아 있다.

## 배치 범위

동서·남북 직선 구간에서 양 끝 12블록을 비우고 반복한다. NBT 회전은 90도 단위이며 대각선, 급커브, 짧은 구간은 건너뛴다. 거의 평탄한 곳만 사용하고, 건물·나무 등으로 점유된 공간이나 물 위에는 배치하지 않는다. 변경한 길은 월드의 길/지형을 다시 생성해야 적용되며 기존 월드를 자동으로 지우지 않는다.

## 석영고원 분리

`league/indigo_plateau.nbt`는 60×72×80에서 60×72×45로 줄였다. Z=45 이후 아치와 장식을 분리하고 앞길은 제거했다. Z=42~44의 길 바닥도 제거했다. 메인 건물과 문 앞 한 줄의 복귀 바닥은 보존했다. `door` [29,1,40] 및 복귀점 [29,1,41]을 변경하지 않았다.

분리 전 전체 원본: `backups/indigo-plateau/20260912-130222-split/`. 재현 스크립트: `tools/structure-builder/split_indigo_approach.py`. 스크립트는 원본 크기를 확인해 중복 분리를 거부한다.
