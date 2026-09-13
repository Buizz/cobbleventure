# 수작업 석영고원 외관 보완

2026-09-11에 저장된 60×72×80 NBT를 기준으로 작업했다. 기존 절차식 생성 모델은 사용하지 않았다.

- 기존 비어 있지 않은 블록 8,979개와 모든 상태·블록 엔티티를 그대로 보존했다.
- 빈 공간에 5,339개 블록을 추가했다. 완성된 정면 오른쪽 탑의 패턴을 다른 탑에 이어 붙이고 측후면 벽, 창, 오크럼 띠, 낮은 지붕을 보완했다.
- 기존 정면, 앞쪽 장식, 바닥, 문 및 메타데이터는 그대로 유지했다.
- 문은 실제 `create:framed_glass_door`이며 기존 `door` 좌표 [29,1,40], 복귀점 [29,1,41]을 유지한다.
- 백업: `backups/indigo-plateau/20260911-202211/`. 원본 NBT, 메타데이터, 보존 검증 수치를 포함한다.
- 보완 도구: `tools/structure-builder/complete_authored_indigo_plateau.py`. 명시적인 `--apply`에서만 실행하며 일반 빌드에는 연결하지 않는다.
- 4면 도식: `docs/assets/indigo-authored/completed-elevations.png`. 블록 재료 색상만 표현한 도식이며 게임 텍스처 렌더링은 아니다.

이전 `generate_indigo_plateau.py --overwrite`를 실행하면 수작업 건축을 잃으므로 이 건물에는 사용하지 않는다.
