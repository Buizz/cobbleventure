# 라운드형 포켓몬 연구소

참고 이미지의 둥근 양쪽 외벽, 크림색 배럴 지붕, 본체에서 돌출되어 지붕 위를 감싸는 흰 철골 프레임, 붉은 중앙 띠,
파란 창과 중앙 출입구를 블록으로 구성한 외관이다. 크기는 X/Y/Z 기준
43×13×23이며 정면은 북쪽(-Z)이다. 기존 오박사·화석 연구소와 같은
외관/독립 내부 공간 방식을 사용한다.

![블록 배치 미리보기](../assets/round_laboratory_preview.png)

미리보기는 실제 생성 블록의 형상과 대표색을 표시하며 게임 텍스처 렌더는 아니다.

- 원본: `content-projects/cobbleventure-main/content/structures/placeholder/round_laboratory.snbt`
- 게임·웹 뷰어용 동일 데이터: 같은 경로의 `round_laboratory.nbt`
- 리소스 ID: `cobbleventure:placeholder/round_laboratory`
- 출입문: `[21, 1, 3]`, 외부 도착 위치: `[21, 1, 2]`
- 연결 내부: 기존 `cobbleventure:interiors/laboratory` 재사용

웹에디터를 새로고침하면 **NBT 건물 설정**에서 `round_laboratory`를 검색할 수 있다.
마을 배치 목록에는 **포켓몬 연구소(라운드형)**으로 등록된다. 기존 마을의 연구소를
자동으로 교체하지 않으므로 원하는 마을에서 새 외관을 선택해 배치한다.
오박사나 보상 NPC는 자동으로 복제하지 않는다.

재생성:

```powershell
python tools/structure-builder/generate_round_laboratory.py
python tools/structure-builder/preview_round_laboratory.py
python -m unittest discover -s tools/structure-builder/tests -p test_round_laboratory.py
python tools/content-manager/content_manager.py validate --root .
```

생성기는 SNBT, NBT, 출입문 메타데이터를 함께 덮어쓴다. 형상을 코드로 변경할 때는
생성기를 수정하고 재생성한다. SNBT만 수동 편집하면 웹에디터용 NBT에는 반영되지 않는다.
웹 등록과 내부 연결은 `content/catalogs/building-settings.json`에서 관리한다.
