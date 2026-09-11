# 테스트용 치트 명령어

개발 중 전투·탐험·상점 등을 빠르게 확인하기 위한 인게임 명령어 모음입니다.

## 바로 사용하기

아래 명령을 게임 채팅창에 **한 줄씩** 입력하면 자신의 테스트 준비를 할 수 있습니다.

```text
/cobbleventure_cheat all
/cobbleventure_cheat team
/cobbleventure_cheat money
/cobbleventure_cheat key_items
/cobbleventure_cheat items
```

| 순서 | 명령 | 결과 |
| --- | --- | --- |
| 1 | `all` | 배지·비전머신·메뉴·순간이동 목적지·기술머신 제작법 해제, 레벨 상한 100 |
| 2 | `team` | 기존 파티를 PC에 보관하고 강력한 포켓몬 6마리 지급 |
| 3 | `money` | 코블달러와 카지노칩을 각각 20억으로 설정 |
| 4 | `key_items` | 모든 중요도구 획득 |
| 5 | `items` | 회복·성장 소모품 9종을 각각 64개 지급 |

> **`all`에는 `team`, `money`, `key_items`, `items`가 포함되지 않습니다.** 필요한 명령을 별도로 실행하세요.

## 권한과 대상 지정

- **OP 권한 레벨 2 이상**이 필요합니다.
- 대상을 생략하면 실행한 자신에게 적용합니다.
- `@s`는 자신, `@a`는 현재 접속한 모든 플레이어입니다. 플레이어 이름도 지정할 수 있습니다.
- 아래 표의 `[대상]`, `[수량]`은 선택 입력을 뜻합니다. 대괄호는 실제로 입력하지 않습니다.
- 서버 콘솔에서는 대상을 반드시 지정하고, 맨 앞 `/`는 생략합니다.
- 모드 JAR을 교체했다면 게임과 서버를 재시작한 뒤 사용합니다.

```text
/cobbleventure_cheat badges
/cobbleventure_cheat hm @s
/cobbleventure_cheat money @a
/cobbleventure_cheat team PlayerName
```

서버 콘솔 예시:

```text
cobbleventure_cheat items PlayerName 128
```

## 전체 명령어 한눈에 보기

| 명령어 | 적용 내용 | 반복 실행 시 |
| --- | --- | --- |
| `/cobbleventure_cheat all [대상]` | 아래 진행도 명령 5개를 함께 적용 | 획득·해제 상태 유지 |
| `/cobbleventure_cheat badges [대상]` | 현재 카탈로그의 모든 세대 배지 지급 | 중복 지급 없음 |
| `/cobbleventure_cheat hm [대상]` | 비전머신 8종 획득 및 활성화 | 다시 활성화 |
| `/cobbleventure_cheat menus [대상]` | 지도·장소 순간이동·PC 해제, 레벨 상한 100 | 해제 상태 유지 |
| `/cobbleventure_cheat destinations [대상]` | 모든 지도 마을과 순간이동 가능 장소 방문 처리 | 방문 상태 유지 |
| `/cobbleventure_cheat tm [대상]` | 모든 기술머신 제작법 해제 | 해제 상태 유지 |
| `/cobbleventure_cheat team [대상]` | 강력한 포켓몬 6마리로 파티 교체, 레벨 상한 100 | 이전 파티도 PC로 이동하고 새 6마리 지급 |
| `/cobbleventure_cheat money [대상]` | 코블달러·카지노칩 각각 2,000,000,000으로 설정 | 누적하지 않고 다시 20억으로 설정 |
| `/cobbleventure_cheat key_items [대상]` | 카탈로그의 중요도구와 획득 기록 지급 | 부족한 수량만 보충 |
| `/cobbleventure_cheat items [대상] [수량]` | 회복·성장 아이템 9종 지급 | 실행할 때마다 추가 지급 |

## 진행도 해제

### 모든 배지 — `badges`

현재 배지 카탈로그에 등록된 모든 세대의 배지를 획득합니다. 트레이너 카드와 배지 보유 조건에 반영됩니다.

기존 개별 배지 명령도 사용할 수 있습니다. ID에 따옴표가 없어도 동작하며, 기존 따옴표 형식도 지원합니다.

```text
/cobbleventure_badge grant @s cobbleventure:badge/kanto/boulder
/cobbleventure_badge revoke @s cobbleventure:badge/kanto/boulder
```

### 모든 비전머신 — `hm`

**파도타기·공중날기·플래쉬·안개제거·락클레임·바다회오리·괴력·바위깨기**를 획득합니다.
ON/OFF 방식의 비전머신도 활성화합니다. 실제 아이템 지급이 아니라 비전머신 사용 권한을 설정합니다.

### 메뉴·목적지·기술머신 — `menus`, `destinations`, `tm`

- `menus`: 지도·장소 순간이동·PC를 해제하고 레벨 상한을 100으로 올립니다.
- `destinations`: 모든 지도 마을과 순간이동 가능 장소를 방문한 것으로 처리합니다. 순간이동 메뉴 자체의 해제는 `menus`가 담당합니다.
- `tm`: 기술머신 **제작법**을 해제합니다. 완성된 기술머신이나 제작 재료는 지급하지 않습니다.

이 세 가지와 `badges`, `hm`을 함께 적용하려면 `all`을 사용하세요.
스토리 퀘스트·던전·리그 승리 기록을 완료 처리하지는 않습니다.
전투 중 이동 제한이나 숲의 비행 제한 같은 게임 규칙도 유지됩니다.

## 강력한 포켓몬 6마리 — `team`

```text
/cobbleventure_cheat team
```

**뮤츠·레쿠쟈·제르네아스·루기아·칠색조·한카리아스**로 파티 6칸을 채웁니다.

| 설정 | 내용 |
| --- | --- |
| 레벨 | 전원 100레벨, 플레이어 레벨 상한도 100 |
| 개체값 | 전원 6V — 모든 개체값 31 |
| 노력치 | 역할에 맞춰 252 / 252 / 4 |
| 전투 준비 | 성격·특성·기술 4개·지닌도구 설정, 완전 회복 |
| 기존 파티 | 삭제하지 않고 PC로 이동 |

전투 중이거나 기존 파티를 보관할 PC 공간이 부족하면 해당 플레이어의 파티를 교체하지 않습니다.
반복 실행하면 기존에 지급받은 파티도 PC에 보관되고 새로운 6마리를 받습니다.

## 코블달러·카지노칩 20억 — `money`

```text
/cobbleventure_cheat money
```

두 통화의 잔액을 **각각 2,000,000,000**으로 직접 설정합니다.
기존 금액에 더하는 방식이 아니므로 반복 실행해도 누적되지 않습니다.
이미 20억보다 많은 경우에도 20억으로 맞춥니다.

자동으로 잔액을 유지하는 기능은 아닙니다. 소비 후 다시 실행하면 재충전됩니다.
이 명령은 **Cobbleventure Casino 모듈**이 등록합니다.

## 모든 중요도구 — `key_items`

```text
/cobbleventure_cheat key_items
```

현재 중요도구 카탈로그의 **포켓몬피리·포켓몬도감·포켓네비·동전케이스**를 획득합니다.

- 인벤토리·보조손·커서·확장 가방의 보유량을 확인해 부족한 수량만 지급합니다.
- 획득 기록도 저장하므로 기존 분실 복구 기능이 적용됩니다.
- 가방 공간이 부족하거나 등록되지 않은 도구가 있으면 해당 플레이어에게 일부만 지급하지 않습니다.
- 카탈로그가 변경되면 지급 대상도 해당 목록을 따릅니다.

## 회복·성장 아이템 — `items`

기본 지급:

```text
/cobbleventure_cheat items
```

| 지급 아이템 | 기본 수량 |
| --- | ---: |
| 풀회복약 | 64개 |
| 기력의덩어리 | 64개 |
| PP맥스 | 64개 |
| 이상한사탕 | 64개 |
| 경험사탕 XS / S / M / L / XL | 크기별 64개 |

총 **9종, 576개**를 확장 가방에 지급합니다.

수량은 **종류별 1~4,096개**까지 지정할 수 있습니다. 수량을 지정할 때는 대상도 함께 입력하세요.

```text
/cobbleventure_cheat items @s 128
/cobbleventure_cheat items @a 64
```

첫 번째 명령은 자신에게 9종을 각각 128개, 두 번째는 접속자 모두에게 각각 64개 지급합니다.
소모품이므로 반복 실행하면 추가 지급합니다. 가방 공간이 부족하면 해당 플레이어의 묶음 지급을 취소합니다.

## 명령이 동작하지 않을 때

| 상황 | 확인할 내용 |
| --- | --- |
| 명령이 보이지 않음 | OP 권한, 최신 모드 JAR 적용, 게임·서버 재시작 여부 |
| `money`만 보이지 않음 | Cobbleventure Casino 모듈 설치 여부 |
| 콘솔에서 실행 실패 | 플레이어 이름 또는 `@a` 지정 여부. 콘솔에서는 자기 자신인 `@s`를 사용할 수 없음 |
| `items` 수량 입력 실패 | `items @s 128`처럼 대상 뒤에 1~4,096 입력 |
| `team` 지급 실패 | 전투 종료 여부와 기존 파티를 옮길 PC 공간 |
| 도구·소모품 지급 실패 | 확장 가방 공간과 오류 메시지의 아이템 등록 여부 |
| `all`을 썼는데 돈·아이템·파티가 그대로임 | `money`, `key_items`, `items`, `team`은 별도 실행 |

여러 플레이어를 대상으로 실행하면 실패한 플레이어는 건너뛰고, 성공한 대상 수를 안내합니다.
필요한 아이템 자체가 등록되지 않은 소모품 묶음은 대상 전체의 지급을 시작하지 않습니다.

## 관련 자료

- [저장소 README](../README.md)
- [Player Menu 모듈](../projects/cobbleventure-player-menu/README.md)
- [Casino 모듈](../projects/cobbleventure-casino/README.md)
- [중요도구 카탈로그](../content-projects/cobbleventure-main/content/catalogs/important-items.json)
- [배지 카탈로그](../content-projects/cobbleventure-main/content/catalogs/badges.json)
