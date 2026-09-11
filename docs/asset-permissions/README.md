# 그래픽 출처 및 사용 기록

이 폴더는 커뮤니티 그래픽의 출처와 사용 기록을 보존합니다. 포켓몬 관련 명칭과
디자인의 권리는 각 권리자에게 있으며, 아래 기록은 팬아트 제작자가 기여한 픽셀
그래픽에 관한 것입니다.

## 커뮤니티 트레이너 스킨

커뮤니티 스킨 원본과 이를 바탕으로 만든 작업본은 공개 저장소나 배포 산출물에
포함하지 않습니다. Content Studio의 **트레이너 스킨 설정**에서 PNG를 적용하면
`local-assets/skins/overrides/<namespace>/<slug>.png`에 로컬 전용으로 저장되며,
콘텐츠 빌드 때 같은 리소스 ID의 프로젝트 기본 스킨을 덮어씁니다.
`local-assets/skins/` 전체는 `.gitignore` 대상입니다.

관장 카탈로그의 RCT 외형도 동일한 화면에서 관리합니다. RCT 리소스 ID는 유지하되
로컬 교체본은 `local-assets/skins/overrides/rctmod/trainers/` 아래에 저장합니다.

일부 트레이너 스킨은 Planet Minecraft와 The Skindex에 공개된 커뮤니티 작업을
기반으로 합니다. 각 스킨의 제목, 제작자, 제작자 프로필과 원본 게시물은
[`trainer-skin-sources.json`](../../content-projects/cobbleventure-main/content/catalogs/trainer-skin-sources.json)에
기록합니다.

- 스킨의 권리는 각 제작자에게 있으며 Cobbleventure가 소유권을 주장하지 않습니다.
- 특정 게시물에 별도 라이선스나 이용 조건이 있으면 그 조건을 우선합니다.
- 원본 다운로드 파일은 로컬 참고 자료로만 보관하고 Git에 포함하지 않습니다.
- 게임용 텍스처는 원본 또는 Minecraft 모델에 맞춘 변환본일 수 있습니다.
- 제작자나 권리자가 출처 수정, 크레딧 추가, 파일 수정 또는 제거를 요청하면
  [GitHub Issues](https://github.com/Buizz/cobbleventure/issues)로 접수하여 확인 후
  저장소와 다음 배포본에 반영합니다.

현재 확인된 Planet Minecraft 제작자는 다음과 같습니다.

- [Skycrafts](https://www.planetminecraft.com/member/skycrafts/)
- [DemonKing69](https://www.planetminecraft.com/member/demonking69/)
- [enrorhf](https://www.planetminecraft.com/member/enrorhf/)

프로젝트의 코드 라이선스나 배포 조건은 위 제3자 스킨에 대한 재라이선스를
의미하지 않습니다.

## 트레이너 카드 뱃지

## 1~6세대

- 제작자: JcFerggy
- 원본: [16x16 Pokemon Badge Sprites: Gen 1-6](https://www.deviantart.com/jcferggy/art/16x16-Pokemon-Badge-Sprites-Gen-1-6-544204402)
- 추가 크레딧: 원본 설명에 따라 하나지방 뱃지의 기반 작업은 SoaringSkies0에게 크레딧합니다.
- 허가 기록: Cobblemon 모드 프로젝트에서 크레딧을 표기해 사용해도 되는지 질문했고, JcFerggy가 원하는 대로 사용해도 된다고 답했습니다. 댓글 캡처는 [jcferggy-badge-permission.png](jcferggy-badge-permission.png)에 보존합니다.
- 가공: 원본 시트의 16×16 셀을 추출하여 nearest-neighbour로 32×32 확대합니다.

## 8세대 가라르

- 제작자: Cobbleverse Overhaul 프로젝트
- 편집 원본: `tools/content-manager/assets/badges/galar-custom.png`
- 배열: 5열 × 2행, 각 셀 32×32이며 카탈로그의 가라르 관장 순서입니다.
- 이 파일을 직접 수정한 뒤 `python tools/content-manager/build_badge_atlas.py`를 실행하면 전역 아틀라스에 반영됩니다.

## 9세대 팔데아

- 제작자: ProfessorMorDBG
- 원본: [Paldea Badges demake large](https://www.deviantart.com/professormordbg/art/Paldea-Badges-demake-large-1142694862)
- 사용 조건: 게시자가 자유 사용 가능하다고 표시한 자료를 사용합니다.
- 가공: 전체 18개 중 체육관 뱃지 8개만 추출하여 nearest-neighbour로 32×32 축소합니다.

게임에 포함되는 최종 파일은 `content-projects/cobbleventure-main/content/resources/cobbleventure-player-menu/assets/cobbleventure_player_menu/textures/gui/badges.png`이며, 세부 셀 좌표와 변환 방식은 `tools/content-manager/badge-image-sources.json`에 기록됩니다.
