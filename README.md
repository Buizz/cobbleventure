# Cobbleventure

**Cobbleventure(코블벤처)** 는 Cobblemon 위에서 자신만의 포켓몬 어드벤처를 만들고
플레이할 수 있도록 게임 기능과 제작 도구를 함께 제공하는 프로젝트입니다.

코드를 직접 수정하는 대신 로컬 웹 도구인 **Content Studio**에서 NPC, 트레이너,
리그, 월드맵, 마을, 던전, 포켓몬 출현과 경제 시스템을 구성하고, 검증된 게임용
콘텐츠로 내보내는 작업 흐름을 지향합니다.

> **개발 상태**
>
> Cobbleventure는 현재 활발히 개발 중이며 완성된 정식 배포판이 아닙니다.
> 데이터 형식과 기능이 바뀔 수 있고, 생성한 결과는 개발 환경에서 먼저 검증해야
> 합니다. 현재 게임 빌드 기준은 Minecraft 1.21.1, NeoForge, Cobblemon 1.8입니다.

> [대표 이미지 촬영 안내: Cobbleventure 로고와 함께 Content Studio 대시보드,
> 월드맵, NPC 편집 화면이 한눈에 보이는 가로형 이미지. 개인 경로와 개발 로그는
> 보이지 않도록 촬영]

## 무엇을 만들 수 있나요?

Content Studio는 여러 JSON 파일과 게임 리소스를 직접 찾아다니지 않고 하나의
프로젝트로 관리할 수 있게 해 줍니다.

- 포켓몬 팀, 기술, 특성, 도구, AI와 전투 규칙을 갖춘 트레이너
- 외형, 행동, 대화, 선택지, 이벤트와 전투가 연결된 NPC
- 체육관 관장, 사천왕, 챔피언, 배지와 레벨캡으로 구성된 리그
- 마을, 도로, 관문, 숲, 동굴과 던전을 배치하는 세대별 월드맵
- 바이옴, 시간, 날씨, 희귀도와 낚시 방식에 따른 포켓몬 출현
- 상점, 드롭, 제작법, 연구, 보육과 같은 모험의 경제·성장 요소
- 상황별 음악, 테마 건물, NPC 앵커와 실내 공간 연결
- 콘텐츠 오류 검사, 게임용 콘텐츠 생성과 설치 패키지 빌드

게임 엔진과 기본 콘텐츠는 분리되어 있습니다. 같은 Cobbleventure 기능을 사용하면서도
자신만의 지역, 등장인물, 진행 순서와 전투 구성을 별도의 콘텐츠 프로젝트로
관리할 수 있습니다.

> [기능 이미지 촬영 안내: Content Studio의 월드맵 화면에서 마을과 길이 배치된
> 모습. 왼쪽 메뉴와 현재 프로젝트 이름도 함께 보이도록 촬영]

## Content Studio로 시작하기

현재 제작 환경은 **Windows 10/11**을 기준으로 합니다. Content Studio 자체를
실행하려면 [Python 3.10 이상](https://www.python.org/downloads/)만 필요합니다.
전체 게임 팩이나 모드를 빌드할 때는 Java JDK 21과 Node.js가 추가로 필요합니다.

### 1. 프로젝트 받기

GitHub의 `Code → Download ZIP`으로 내려받아 쓰기 가능한 폴더에 압축을 풀거나,
Git을 사용한다면 다음 명령으로 복제합니다.

```powershell
git clone https://github.com/Buizz/cobbleventure.git
cd cobbleventure
```

### 2. Content Studio 실행하기

프로젝트 폴더에서 `build.bat web`을 실행합니다.

```bat
build.bat web
```

터미널에 서버 주소가 표시되면 브라우저에서
[http://127.0.0.1:8765](http://127.0.0.1:8765)을 엽니다. 처음 실행하면 예제이자
기본 콘텐츠인 `Cobbleventure Main` 프로젝트가 자동으로 열립니다.

> [실행 이미지 촬영 안내: `build.bat web` 실행 후 로컬 주소가 표시된 터미널과,
> 브라우저에 열린 Content Studio 대시보드를 나란히 배치]

Content Studio는 인증 기능이 없는 로컬 제작 도구입니다. 외부 네트워크에 공개하지
말고 `127.0.0.1`에서만 사용하세요. 종료할 때는 실행한 터미널에서 `Ctrl+C`를
누릅니다.

### 3. 나만의 콘텐츠 편집하기

기본 프로젝트를 살펴보거나, `project.json`과 `content` 폴더가 있는 별도 프로젝트를
상단의 **현재 프로젝트** 메뉴에서 불러올 수 있습니다. 예제를 보존하고 싶다면
`content-projects/cobbleventure-main`을 복사하고 복사본의 `project.json`에서
`id`와 `name`을 바꾼 뒤 편집하세요.

처음부터 모든 영역을 채울 필요는 없습니다. 작은 콘텐츠는 다음 순서로 시작하는
것이 좋습니다.

1. **배틀 프리셋**에서 포켓몬 팀과 전투 규칙을 만듭니다.
2. **NPC**에서 외형과 대화를 만들고 배틀 프리셋을 연결합니다.
3. **마을 프리셋** 또는 **월드맵**에 NPC와 플레이 공간을 배치합니다.
4. 각 화면에서 **검증 후 저장**을 누릅니다.
5. **대시보드**에서 **전체 검증**을 실행합니다.

저장 요청은 서버에서 다시 검사됩니다. 오류가 있으면 원본 파일을 덮어쓰지 않고
수정할 항목을 알려 줍니다. 콘텐츠 ID는 다른 문서가 참조하므로 처음 만들 때 의미가
분명한 소문자 영문·숫자·밑줄 이름을 사용하는 것이 좋습니다.

> [편집 이미지 촬영 안내: NPC 편집 화면에서 외형 미리보기, 대화, 연결된 배틀
> 프리셋과 `검증 후 저장` 버튼이 함께 보이는 화면]

### 4. 검사하고 게임용 콘텐츠 만들기

일반 제작자는 Content Studio의 **빌드 · 콘텐츠 교체** 화면에서 버튼을 사용하는
것이 가장 간단합니다.

| 작업 | 사용 시점 | 결과 |
|---|---|---|
| 전체 검증 | 편집 중 수시로 | 잘못된 형식과 끊어진 참조를 찾음 |
| 콘텐츠 빌드 | 콘텐츠 배포 전 | 엔진을 다시 빌드하지 않고 버전이 붙은 콘텐츠 ZIP 생성 |
| 콘텐츠 교체 | 게임에서 시험할 때 | 선택한 콘텐츠를 지정한 게임 인스턴스에 안전하게 적용 |
| 전체 빌드 | 엔진과 콘텐츠를 함께 시험할 때 | 자체 모드와 콘텐츠가 포함된 개발용 설치 ZIP 생성 |

콘텐츠 교체는 외부 콘텐츠를 지원하는 Cobbleventure 엔진을 먼저 설치한 환경에서
사용해야 합니다. 게임을 완전히 종료한 뒤 실행하며, 실패하면 기존 콘텐츠를
복구하도록 설계되어 있습니다.

명령줄을 선호한다면 저장소 루트에서 다음 명령을 사용할 수 있습니다.

```bat
build.bat validate
build.bat content
```

생성물은 `dist` 폴더에 저장됩니다. `build.bat pack`과 전체 테스트는 JDK 21,
Node.js 및 빌드에 필요한 게임 의존성을 갖춘 개발 환경을 요구합니다.

> [빌드 이미지 촬영 안내: `빌드 · 콘텐츠 교체` 화면에서 버전 입력란, 전체 검증,
> 콘텐츠 빌드, 콘텐츠 교체 버튼과 최근 성공 결과가 함께 보이는 화면]

## 권장 제작 흐름

콘텐츠가 서로 참조하기 때문에 규모가 큰 프로젝트는 다음 순서로 구성하면 오류를
줄일 수 있습니다.

`게임 데이터·음악 → 바이옴 → 배틀 프리셋 → NPC → 리그·건물 → 마을·던전 → 월드맵 → 경제·제작 → 전체 검증 → 콘텐츠 빌드`

화면별 설명과 실제 입력 예시는 [Content Studio 사용자 가이드](guide/README.md)에서
확인할 수 있습니다.

## 문서 안내

### 콘텐츠 제작자

- [Content Studio 사용자 가이드](guide/README.md)
- [처음 설치하고 실행하기](guide/getting-started.md)
- [전체 콘텐츠 제작 흐름](guide/workflow.md)
- [빌드와 콘텐츠 적용](guide/build-and-deploy.md)
- [JSON 데이터 카탈로그](docs/JSON_CATALOG.md)
- [NBT 구조물 편집 가이드](docs/NBT_STRUCTURE_EDITING.md)

### 개발자와 기여자

- [프로젝트 기획서](docs/PROJECT_PLAN.md)
- [구현 설계 문서](docs/implementation/README.md)
- [콘텐츠·엔진 분리 계약](docs/implementation/CONTENT_ENGINE_SEPARATION.md)
- [콘텐츠 빌드 파이프라인](docs/implementation/CONTENT_BUILD_PIPELINE.md)
- [의존 모드 관리표](docs/MOD_DEPENDENCIES.md)
- [테스트용 치트 명령어](docs/CHEAT_COMMANDS.md)

## 저장소 구성

```text
content-projects/   Content Studio에서 여는 콘텐츠 프로젝트
guide/              콘텐츠 제작자를 위한 화면별 사용 가이드
projects/           Cobbleventure의 NeoForge 모드와 독립 코어
tools/              Content Studio, 검증기와 패키징 도구
pack/               게임 팩 프로필과 패키징 설정
docs/               기획, 구현 설계와 기술 문서
```

저장소에는 다른 프로젝트나 모드팩의 JAR, 개인 게임 폴더, 월드, 로그와 빌드 결과물을
커밋하지 않습니다. 외부 모드는 각 배포처의 라이선스와 이용 조건을 따릅니다.

## 외부 그래픽 크레딧

트레이너 카드의 배지 그래픽은 다음 픽셀 아트를 가공하여 사용합니다.

- 1~6세대: [JcFerggy, 16x16 Pokemon Badge Sprites: Gen 1-6](https://www.deviantart.com/jcferggy/art/16x16-Pokemon-Badge-Sprites-Gen-1-6-544204402). 원본 설명에 따라 하나지방 배지 기반 작업은 SoaringSkies0에게도 크레딧합니다.
- 8세대 가라르: Cobbleventure 프로젝트 자체 제작·편집본
- 9세대 팔데아: [ProfessorMorDBG, Paldea Badges demake large](https://www.deviantart.com/professormordbg/art/Paldea-Badges-demake-large-1142694862)

제작자 허가 기록, 원본 보관 위치와 nearest-neighbour 가공 방식은
[그래픽 출처 및 사용 기록](docs/asset-permissions/README.md)에서 관리합니다.

Pokémon, Minecraft, Cobblemon 및 문서에 언급된 타사 프로젝트의 명칭과 디자인에
관한 권리는 각 권리자에게 있습니다. Cobbleventure는 해당 권리자들의 공식
프로젝트가 아닙니다.
