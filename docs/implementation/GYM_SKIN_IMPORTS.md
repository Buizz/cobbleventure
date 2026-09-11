# 관동 관장 스킨 교체

2026-09-02에 사용자가 제공한 PNG를 원본 그대로 등록했다. 모두 64×64,
Classic(Steve) 모델이며 리사이즈나 색상 변경을 하지 않았다. 제공받은 원본 파일은
로컬 참고 자료로만 취급하며 Git에는 포함하지 않는다.

| 관장 | 제작자·원본 | 기존 제공 파일명 (Git 미포함) | 등록 리소스 |
| --- | --- | --- | --- |
| 민화 | [Skycrafts, Erika (HGSS)](https://www.planetminecraft.com/skin/erika-hgss/) | Erika-HGSS-on-planetminecraft-com.png | `cobbleventure:trainer_skin/erika` |
| 강연 | [Skycrafts, Blaine (HGSS)](https://www.planetminecraft.com/skin/blaine-hgss/) | Blaine-HGSS-on-planetminecraft-com.png | `cobbleventure:trainer_skin/blaine` |
| 독수 | [Skycrafts, Koga (HGSS)](https://www.planetminecraft.com/skin/koga-hgss/) | Koga-HGSS-on-planetminecraft-com.png | `cobbleventure:trainer_skin/koga` |
| 초련 | [Skycrafts, Sabrina (HGSS)](https://www.planetminecraft.com/skin/sabrina-hgss/) | Sabrina-HGSS-on-planetminecraft-com.png | `cobbleventure:trainer_skin/sabrina` |
| 비주기(관장) | 기존 레인보우로켓단 비주기 스킨 재사용 | - | `cobbleventure:trainer_skin/rainbow_rocket_giovanni` |

관장 전투·트레이너 카드의 외형은 `league-progression.json`의
`encounter.appearance`, 캐릭터 목록은 `trainer-roster.json`의
`league_characters[].appearance`에 동일하게 연결한다.
별도 파일을 관장 JSON에 복제하거나 포켓몬 라인업·진행 순서를 바꾸지 않는다.

원본 제공 파일과 관계없이 빌드할 수 있도록 공개 리소스 경로에는 프로젝트 기본
스킨을 둔다. 사용자가 제공한 실제 텍스처는
`local-assets/skins/overrides/cobbleventure/`에만 보관하며 Content Studio 미리보기와
로컬 콘텐츠 빌드에서 같은 리소스 ID를 덮어쓴다. EasyNPC용 스킨은 기존 프리셋
생성기로 다시 생성한다.

커뮤니티 스킨의 권리 및 요청 대응 방침은
[`docs/asset-permissions/README.md`](../asset-permissions/README.md)에서 관리하고,
전체 제작자·원본 URL은 `trainer-skin-sources.json`에 기록한다.
