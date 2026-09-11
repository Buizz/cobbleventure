# 명예의 전당과 회차별 플레이어 프로필

## 적용 상태와 요구사항

명예의 전당은 다음 회차를 선택하기 전의 거점이다. 리그 클리어와 다음 회차 등록은 서로 다른 동작이다.

- 구현: 챔피언에게 승리한 회차의 클리어 상태 유지, 로비 입장 시 전당으로 자동 이동, 전당 출구에서 로비로 자유 복귀.
- 구현: 릴레이도 클리어 이후에는 이탈·로그아웃으로 클리어를 취소하지 않는다. 방 설정 변경도 클리어 기록을 지우지 않는다.
- 구현(이동 테스트): 리그의 현재/다음 세대 지정, 전당의 별도 출구에서 다음 세대 집 시작점으로 이동. 아이템·포켓몬 유지.
- 설계: 전당 등록에 의한 자산 보관·복원, 이전 세대 선택, 세대별 진행도 분리.
- 현재 등록 버튼이나 자산 초기화 명령을 활성화하지 않는다. 아래 저장·복구 체계가 구현되기 전에는 자산을 비우지 않는다.

전당에서 나오는 것만으로 다음 회차가 시작되지 않는다. 이전 회차로 돌아오면
그 회차에서 마지막으로 떠날 때의 아이템·포켓몬·진행도가 복원되어야 한다.
클리어 당시 상태로 매번 되감는 방식이 아니다. 돌아가서 아이템을 사용하거나 포켓몬을
성장시킨 뒤 다시 떠났다면, 다음 복귀에는 그 변경된 상태가 복원된다.

## 플레이어 동선

```text
챔피언 승리 → 현재 회차 리그 CLEARED → 명예의 전당
외부 → 클리어한 리그 로비 입장 → 명예의 전당 자동 안내
명예의 전당 출구 → 로비 → 외부 (자동 안내 반복 없음)

전당 등록 → 현재 회차 보관 → 다음 세대 프로필 최초 생성 → 빈 자산으로 새 시작점
회차 선택 → 현재 회차 보관 → 선택 회차의 마지막 저장본 복원 → 해당 회차 귀환점
```

회차 선택은 아직 리그를 깨지 않은 새 회차에서도 접근할 수 있어야 한다.
후속 구현에서는 플레이어 메뉴의 **회차 선택**을 통일된 진입점으로 두고 전당에서도 연다.
기존 회차로 돌아가기 위해 새 회차 리그를 다시 깨도록 요구하지 않는다.
전투·거래·저장 작업 중에는 전환할 수 없지만, 일반 탐험 중에는 선택할 수 있다.

전당에는 `hall_register` NPC 마커를 사용하는 등록 담당자를 추가하는 안을 사용한다.
이 마커/NPC/대화는 아직 배치·구현하지 않았다. 대화에는 새 회차 시작, 회차 선택,
돌아가기와 함께 보관 대상·새 시작 위치를 표시한다. 같은 등록 요청을 두 번 전송해도
회차를 두 개 만들지 않도록 서버 발급 요청 ID를 사용한다.

## 회차와 월드의 식별자

플레이어 UUID는 유지한다. **세대 맵 하나가 하나의 회차**다.
`cobbleventure:generation_1`이 1회차이고 `cobbleventure:generation_2`가 2회차다.
같은 세대의 월드를 새로 만들거나 임의 UUID 회차를 계속 생성하지 않는다.
다음 세대는 숫자에 1을 더해 추측하지 않고 리그의 `next_generation`으로 명시한다.
현재 테스트 연결은 1 → 2이며, 2의 다음 세대는 아직 없다.

```text
PlayerAccount(player_uuid)
  active_generation: cobbleventure:generation_1
  profiles[generation_dimension_id]:
    GenerationProfile(return_point, snapshot_revision, status)
  hall_records: [{generation_dimension_id, league_id, cleared_at, registered_at, roster_summary}]
```

자산 저장 키는 `(플레이어 UUID, 세대 차원 ID)`다. 아래 provider 계약의 `run_id`는
세대 차원 ID를 뜻한다. 세대별 차원은 플레이어들이 공유하며 각 플레이어의 자산과
진행도를 별도로 저장한다. 다른 플레이어의 월드나 데이터를 초기화하지 않는다.
같은 세대로 복귀하면 그 세대에서 마지막으로 떠난 상태를 복원한다.

리그·체육관·집·던전이 공용 내부 차원에 있어도 외부 건물의 소속 세대를 유지해야 한다.
물리적인 `building_interiors` 차원만으로 활성 회차를 결정하지 않는다. 향후 모든 이동을
PlaythroughService로 통일하고, 미니맵의 세대 이동도 같은 자산 보관·복원 절차를 사용한다.
상자·드롭·거래·포획 등 월드 자산의 소속 세대와 활성 세대도 검사해야 한다.

## 2세대 이동 테스트 설정

- 리그 화면: 현재 세대 `1`, 다음 세대 `2`, 세대 이동 `travel_test`.
- 전당: `hall_exit`는 로비 복귀, `hall_next_generation`은 다음 세대 이동.
  새 포털은 NBT 로컬 좌표 `(8, 2, 16)`이며 금색 테두리로 표시했다.
- 목적지: `johto_starter_town` → `facility_player_house_1` → `room_1` → `start`.
- 2세대는 평지 테스트 차원에 시작마을 한 곳과 플레이어 집 한 채만 사용한다.
  집 외관·내부 NBT를 재사용하며 1세대 집과 별도 인스턴스로 배치한다.
  일반 주거 건물, NPC, 트레이너, 상점, 체육관은 추가하지 않는다.
- 첫 이동 시 마을과 집을 배치하며 저장된 배치 완료 상태로 재방문 때 덮어쓰지 않는다.
- **테스트 모드에서는 아이템·포켓몬·진행도를 초기화하거나 복원하지 않는다.**
  정식 전당 등록과 자산 격리 완료를 의미하지 않는다.
- 콘텐츠와 모드를 빌드한 뒤 서버를 재시작해야 새 차원이 로드된다.

확인 절차:

1. OP는 `/cobbleventure_generation_test 2`로 전투 없이 2세대 집의 시작 위치를 확인한다.
2. 집에서 나가면 2세대 마을이어야 하며, 다시 들어갔다가 재접속해도 위치가 유지되어야 한다.
3. `/cobbleventure_generation_test 1`로 1세대 집으로 돌아간다. 두 명령 모두 자산을 유지한다.
4. 리그 클리어 후 전당의 `hall_exit`가 로비로 이동하고, `hall_next_generation`이
   2세대 집으로 이동하는지 확인한다. 미클리어 플레이어의 다음 세대 포털은 허용하지 않는다.

자동 검증은 콘텐츠 계약·NBT 마커·컴파일/회귀 테스트까지 수행한다.
실제 게임 접속을 통한 차원 로딩과 위 왕복 동선은 별도 확인해야 한다.

## 회차에 남길 데이터

| 영역 | 현재 저장·접근 근거 | 전환 정책 |
| --- | --- | --- |
| 인벤토리·갑옷·보조손·엔더 상자 | Minecraft 플레이어 저장 | ItemStack 전체 컴포넌트와 슬롯 보관. 중첩 컨테이너 내용도 포함 |
| 확장 가방·단축키·지급 이력 | `BagStorage`, `cobbleventure_player_menu.bag` | Items만 비우지 말고 전체 가방 문서를 회차 단위로 전환 |
| 파티·PC 모든 박스 | `Cobblemon.INSTANCE.getStorage().getParty/getPC` | 포켓몬 UUID, 기술·개체값·노력치·친밀도·지닌 도구 등 전체 데이터와 박스 위치 보관 |
| 맡긴 부모·알·작업 상태 | `DaycareSavedData`, overworld의 `cobbleventure_daycare_jobs` | owner UUID 단독 키를 `(owner, run)`으로 확장. 비활성 회차 작업은 정지 |
| 돈 | `ServerPlayerEventState.money()`의 Cobbledollars 연동 | 해당 모드 API로 잔액 읽기·복원. 아이템 인벤토리와 별도로 처리 |
| 카지노·가챠 | `CobbleventureCasino`, `cobbleventure_gacha_players` | 잔액·티켓·천장·보상 지급 이력을 함께 회차로 분리 |
| 배지·리그 상태 | `BadgeProgressNetwork`, `LeagueRuntimeSystem` | 배지·현재 회차 클리어·단계 저장. 새 회차에는 미클리어 |
| 스토리·기능·레벨캡 | `ServerPlayerEventState`: scoreboard 플래그, persistent 변수·기능·레벨캡 | 선언된 회차 범위 키와 점수만 교체. 서버 전체 scoreboard 초기화 금지 |
| NPC 전투 기록 | `TrainerBattleState`, `cobbleventure_trainer_battles` SavedData | 플레이어별 기록에 run_id 추가. 새 회차에서 이전 승리로 차단되지 않게 함 |
| 퀘스트·이벤트 | `QuestService`, `SavedEventSessionStore` | 완료·지급 이력 분리. 진행 중 await는 전환 전에 종료/보류 정책 적용 |
| 스타터·탐험·기술 해금 | 스타터 수령 플래그, 필드 기능 및 각 소유 모듈 | 새 시작에 필요한 초기값 적용. 이전 회차에서는 기존 값 복원 |
| 위치·귀환·사망 복귀점 | 차원·좌표, 포켓몬센터 복귀 등 | 현재 회차의 안전한 귀환점 보관. 복원 실패 시 해당 회차의 시작점으로만 대체 |
| 경험치·효과·생존 상태 | Minecraft 플레이어 상태 | 회차별 보관. 새 회차는 레벨 0, 효과 없음, 기본 건강·허기 |
| 외부 자산 | 월드 상자·농장·배치 포켓몬·공유 거래 등 | 별도 월드 소유 또는 명시적 run_id 격리. 미연동 제공자가 있으면 전환 차단 |

유지할 계정 정보: UUID·이름·스킨, 조작/UI 설정, 관리 권한, 회차 목록과 전당 등록 이력.
도감·칭호·치장 수집처럼 영구 성장에 해당하는 항목은 계정 공용 여부를 개별 지정한다.
아이템·포켓몬·돈으로 환전되거나 전투에 영향을 주는 항목은 기본적으로 회차 범위다.
계정 전당 기록은 열람용이며 이전 회차의 배지나 자산을 새 회차에 지급하지 않는다.

새 회차의 자산은 빈 인벤토리·가방·PC·파티, 돈 0으로 시작한다. 스타터는 새 회차의
정상 시작 이벤트로 받는다. 기본 조작 UI처럼 플레이에 필요한 시스템 접근권은 유지한다.
파티가 비어 있어도 시작 위치에서 회차 메뉴·스타터 선택에 접근할 수 있어야 한다.

## 저장소와 모듈 계약

별도 서버 모듈에 `PlaythroughService`와 `RunStateProvider` 계약을 둔다.
플레이어 playerdata 전체를 통째로 덮어쓰지 않는다. 접속 정보·권한·새 저장 메타데이터까지
되돌릴 위험이 있고, 파티/PC나 SavedData는 playerdata 바깥에 있기 때문이다.

각 모듈은 자신의 데이터에 대해 다음 기능을 구현한다. 이름은 제안 API다.

```text
providerId + schemaVersion
checkSwitchAllowed(player, sourceRun, targetRun)
capture(player, runId) -> versioned snapshot
validate(snapshot, registries) -> compatible / error
restore(player, runId, snapshot, transactionId)  # 재호출해도 같은 결과
createInitialState(startProfile) -> empty/default snapshot
verifyApplied(player, snapshot) -> checksum/count/identity result
synchronizeClient(player)
```

`clear()` 단독 호출은 공개하지 않는다. 빈 상태도 검증된 새 회차 snapshot으로 복원한다.
등록된 필수 provider가 하나라도 빠지면 시작을 거절한다. 모드 추가 시 플레이어 자산을
보유하는 모드인지 점검하고 provider 또는 명시적인 비자산 정책을 등록한다.

저장 배치 제안:

```text
world/cobbleventure/playthroughs/<player_uuid>/
  manifest.nbt                         # 활성 회차와 snapshot revision
  generations/<namespace>/<generation_id>/snapshots/<revision>/
    vanilla.nbt
    bag.nbt
    pokemon.nbt
    economy.nbt
    progression.nbt
    services.nbt
    index.nbt                          # provider 버전, 해시, 데이터 수
  transactions/<transaction_uuid>.nbt # 전환 저널
```

큰 파티/PC snapshot을 player persistent NBT 안에 중첩해 쌓지 않는다.
각 제공자 저장은 버전과 원본 모드 버전을 기록하며, 읽지 못하는 포켓몬/아이템은
빈 값으로 대체하지 않는다. 호환 불가 상태로 전환을 중단하고 기존 회차를 유지한다.
비활성 회차에 같은 UUID의 포켓몬 데이터가 보관될 수 있지만, 활성 저장소에는 한 회차만
존재해야 한다. 플레이어 간 회차 전환이나 다른 플레이어의 snapshot 요청은 허용하지 않는다.

## 데이터 유실·복제 방지 전환 절차

1. 서버가 회차 소유권·등록 자격·현재 revision·중복 요청을 검사한다.
2. 플레이어별 전환 잠금을 잡는다. 전투·교환·거래·부화·뽑기·메뉴 커서·컨테이너·배치/탑승
   등 변경 작업이 있으면 종료를 기다리거나 구체적인 이유로 거절한다. 동행 포켓몬은 회수한다.
3. 목적지 월드와 안전 위치, 모든 provider의 복원 가능 여부를 먼저 확인한다.
4. 현재 회차를 임시 revision에 캡처하고 포켓몬 UUID·아이템 수·해시를 검증한 뒤 디스크에 확정한다.
5. 출발/도착 snapshot revision과 요청 ID를 기록한 `PREPARED` 저널을 내구성 있게 저장한다.
6. `APPLYING` 상태에서 검증된 목적지 snapshot을 각 provider에 적용한다.
   모든 클라이언트 행동·지급 콜백은 잠금과 `(run_id, revision)` 검사를 통과해야 한다.
7. 적용 결과를 확인한 뒤 목적지 저장을 flush하고 활성 manifest를 원자적으로 교체한다.
8. 안전 위치 이동, 클라이언트 인벤토리·가방·파티·PC·돈·지도 동기화 후 `COMMITTED`로 마감한다.
   실제 게임 상태가 목적지 회차와 일치할 때만 잠금을 해제한다.

다중 모드 파일 저장은 한 번의 atomic rename으로 끝나는 트랜잭션이 아니다.
manifest/저널을 기준으로 로그인 전에 미완료 전환을 감지하고, 검증된 전체 snapshot을
다시 적용해 한쪽 상태로 수렴시킨다. `PREPARED` 이전 실패는 원래 회차 유지,
활성 manifest가 바뀌기 전 실패는 출발 snapshot 복원, manifest 확정 후 실패는 목적지
snapshot 재적용으로 복구한다. 복구 자체가 실패하면 접속 플레이를 잠그고 양쪽 원본을 보존한다.

같은 요청 ID 재전송은 기존 처리 결과를 반환한다. 명예의 전당 등록 이력도 동일 거래 ID로
멱등 처리해 재접속·연타로 회차 생성/보상이 중복되지 않게 한다. 백그라운드 저장과 알·보상
도착 콜백은 이전 run_id로 새 활성 회차를 수정할 수 없어야 한다.

## 구현 순서와 완료 기준

1. 회차 manifest·provider 계약·저널·이전 상태 import를 구현한다. 기존 플레이어는 현재 상태를
   1회차로 한 번만 가져오며, 백업 없이 초기 snapshot을 만들지 않는다.
2. 인벤토리/가방/파티/PC/화폐 provider와 왕복 테스트를 완성한다.
3. 배지·리그·스타터·퀘스트·NPC 승리·키우미·카지노 등 SavedData와 지연 작업을 분리한다.
4. 모든 이동·거래·획득 경로에서 세대별 회차 문맥을 검사한다.
5. 새 회차/기존 회차 선택 UI와 `hall_register` 대화를 연결한다. 새 화면은 전역 MenuTheme와
   공통 뒤로가기 버튼을 사용하며, 등록 전 보관되는 항목과 목적지를 보여준다.

필수 테스트: 1→2→1 왕복 원본 동일성, 2에서 얻은 자산이 1로 섞이지 않음,
1 복귀 후 변경사항 재저장, 포켓몬 UUID/PC 박스·지닌 도구 보존, 큰 가방/중첩 아이템,
키우미 부모·알 보존, 스타터 회차별 1회 지급, 연타·중복 패킷·다른 플레이어 접근,
저널 단계별 강제 종료/재접속·디스크 부족·손상·모드 버전 변경, 전투/거래와 경합,
이전 회차 드롭·상자·거래를 통한 자산 반입 차단을 포함한다.
