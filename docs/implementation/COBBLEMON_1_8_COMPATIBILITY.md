# Cobblemon 1.8 호환성 검증 기록

> 최종 검증일: 2026-09-08
> 상태: 정식 1.8 자체 모듈 빌드 및 핵심 트레이너 스택 서버 기동 통과

## 정식 검증 입력

- Minecraft: `1.21.1`
- NeoForge: `21.1.248`
- Cobblemon: `1.8.0+1.21.1`
- CurseForge 프로젝트/파일: `687131:8818732`
- 파일: `Cobblemon-neoforge-1.8.0+1.21.1.jar`
- SHA-256: `49994513E740408AFC6FE1D03E879C1ED7F42F8245020406D8872A1EE7961FDA`
- 로컬 보관 위치: `.tmp/cobblemon-1.8-release/`

Gradle 빌드는 `COBBLEVENTURE_COBBLEMON_TARGET=1.8`일 때 위 로컬 JAR을 사용하며,
생성되는 자체 모드의 Cobblemon 요구 범위는 `[1.8.0,1.9)`이다. 기본 빌드 대상은
계속 1.7.3이다.

## 자체 모드 결과

정식 1.8 JAR을 사용해 다음 빌드와 테스트가 성공했다.

| 모듈 | 결과 | 비고 |
|------|------|------|
| `cobbleventure-adventure` | 통과 | 1.7.3/1.8 `ModelWidget` 생성자 차이를 호환 계층으로 처리 |
| `cobbleventure-battle-ai` | 통과 | JVM 및 Kotlin/JS 공유 코어 테스트 포함 |
| `cobbleventure-player-menu` | 통과 | deprecated API 경고만 존재 |
| `cobbleventure-world-bootstrap` | 통과 | 전체 테스트와 개발 JAR 설치 포함 |
| `cobbleventure-casino` | 통과 | 전체 테스트 포함 |
| `cobbleventure-experience` | 통과 | 경험치 HUD·KO 즉시 지급·포획 경험치 테스트 포함 |
| `cobbleventure-theme-blocks` | 통과 | 리소스 동기화와 1.8 전용 JAR 설치 포함 |

`cobbleventure-pokefinder`는 CobbleNav 2.3.3의 HUD와 내부 레이아웃을 확장하는
모듈이므로 독립 대체품이 아니다. CobbleNav 2.4.0은 1.8 프로필에 포함하지만,
이 확장 모듈은 2.4.0 HUD 호환성 검증 전까지 제외한다.

## 1.8 외부 모드 갱신

2026-09-08 기준 트레이너 스택의 공식 1.8 NeoForge 파일을 다음과 같이 고정했다.

- RCT API `0.16.0-beta`: CurseForge `1152792:8826267`
- Radical Cobblemon Trainers `0.19.0-beta`: CurseForge `1009534:8827140`
- TBCS `0.15.0-beta`: CurseForge `1172731:8833923`
- Mega Showdown `1.0+1.8+1.21.1-beta2`: CurseForge `1189523:8820597`

- CobbleNav `2.4.0`: CurseForge `976014:8823427`
  ([공식 변경 내역](https://www.curseforge.com/minecraft/mc-mods/cobblemon-pokenav/files/8823427),
  2026-09-06 배포, Cobblemon 1.8.0 지원)

CobbleNav JAR의 Cobblemon 요구 범위 `[1.8.0,)`과 기존 포켓내비 아이템
`cobblenav:pokenav_item_red`를 확인했다. Player Menu는 1.8 빌드에서 2.4.0을
참조하고 허용하며, Kotlin 람다 이름 변경으로 연락처 버튼을 잘못 가로채지 않도록
지도 화면 생성 지점을 기준으로 연결한다. 실제 클라이언트에서 민호의 지급과
포켓내비 열기·지도·연락처 버튼 동작은 추가 확인이 필요하다.
Cobbleventure Pokefinder 확장만 1.8 프로필에서 제외한다.
1.7.3 프로필은 CobbleNav 2.3.3을 유지한다.

경험치 HUD, KO 즉시 지급, 포획 경험치는 외부 세 모드와 Tim Core를 제거하고
`cobbleventure-experience`로 대체했으며 1.7.3과 1.8 양쪽 빌드·테스트를 통과했다.

나머지 직접 연동 모드는 메타데이터상 1.8 로딩을 허용하지만 실제 클라이언트에서
기능별 스모크 테스트가 필요하다.

## 1.8 빌드와 패키징

PowerShell 또는 cmd에서 다음과 같이 자체 모드의 소스 호환성을 검사할 수 있다.

```bat
set COBBLEVENTURE_COBBLEMON_TARGET=1.8
build.bat test
build.bat pack
build.bat pack-server
```

정식 JAR은 `.tmp/cobblemon-1.8-release/`에서 자동 탐색한다. 다른 위치를 쓰려면
`COBBLEVENTURE_COBBLEMON_JAR`에 전체 경로를 지정한다.

1.8은 `pack/profiles/development-1.8.json`,
`pack/dependencies-1.8.lock.json`, `pack/overrides/development-1.8/mods`를 사용한다.
공통 설정과 콘텐츠는 기존 development overrides를 재사용하되 자체 JAR 디렉터리만
교체하므로 1.8 빌드가 1.7.3 JAR을 덮어쓰지 않는다.

## 서버 스모크 결과

NeoForge `21.1.248` 전용 서버에서 Cobblemon `1.8.0`, Kotlin for Forge `5.12.0`,
Architectury `13.0.11`, RCT API `0.16.0-beta`, RCT `0.19.0-beta`, TBCS
`0.15.0-beta`를 함께 기동했다. 서버가 `Done` 상태에 도달했고 RCT와 TBCS가 각각
트레이너 1,559명을 등록했다.

최소 스모크 구성에는 Mega Showdown을 넣지 않아 RCT 기본 데이터의
`mega_showdown:*` 아이템 검증 경고가 발생했다. 이는 1.8 개발팩에는 Mega Showdown이
고정되어 있으므로 전체 팩에서 다시 확인할 항목이다.

## 남은 런타임 확인

1. CurseForge에서 1.8 개발팩을 가져와 전체 클라이언트 로딩을 확인한다.
2. 새 월드, 야생·트레이너 전투와 TBCS 명령 연결을 확인한다.
3. 경험치 HUD, 플레이어 메뉴, 교배소, 상점과 카지노를 기능별로 확인한다.
4. Mega Showdown 장비를 사용하는 RCT 트레이너 데이터의 검증 경고가 사라지는지 확인한다.
