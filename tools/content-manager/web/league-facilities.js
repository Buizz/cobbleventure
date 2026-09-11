(() => {
  'use strict';
  const $ = selector => document.querySelector(selector);
  const escape = PlayerConditionEditor.escapeHtml;
  let catalog, available, current = 0, dirty = false, loading;
  const status = message => { $('#lf-status').textContent = message; };
  const changed = () => { dirty = true; status('저장하지 않은 변경사항이 있습니다.'); };
  const input = (label, key, value) => `<label><span>${escape(label)}</span><input data-field="${key}" value="${escape(value ?? '')}" required></label>`;
  const select = (label, key, value, entries) => `<label><span>${escape(label)}</span><select data-field="${key}">${entries.map(([id, name]) => `<option value="${escape(id)}" ${String(id) === String(value) ? 'selected' : ''}>${escape(name)}</option>`).join('')}</select></label>`;
  const slotLabel = index => index === 4 ? '챔피언' : `${index + 1}번째 엘리트`;
  function roomCard(room, key, title) {
    return `<article class="lf-room-card"><span class="lf-slot-label">${escape(title)}</span><strong>${escape(room.structure?.split('/').at(-1) || '방을 선택하세요')}</strong><small>${escape(room.structure || '지정된 방이 없습니다.')}</small><button class="button secondary" type="button" data-room-picker="${key}">${escape(title)} 방 검색</button></article>`;
  }
  // Move the existing trainer identity, not the room. Generated NPC IDs and
  // progression references remain stable when the challenge order changes.
  function assignTrainer(league, index, id) {
    const target = league.stages[index];
    const source = league.stages.find(room => room.id === id && room.role === target.role);
    if (!source || source === target) return;
    [target.id, source.id] = [source.id, target.id];
    [target.trainer, source.trainer] = [source.trainer, target.trainer];
  }
  function supportsRoom(anchors, room) {
    const required = [[room.entry, ['arrival', 'interior_spawn']], [room.exit, ['transition', 'door']]];
    if (room.npc_anchor) required.push([room.npc_anchor, ['npc_position']], [`${room.npc_anchor}_battle_player`, ['arrival', 'npc_position']]);
    if (room.leave) required.push([room.leave, ['transition', 'door']]);
    if (room.advance) required.push([room.advance, ['transition', 'door']]);
    for (const slot of [...Object.keys(room.fixed_npcs || {}), ...Object.keys(room.fixed_pokemon || {})]) required.push([slot, ['npc_position']]);
    return required.every(([name, types]) => (anchors || []).some(anchor => (anchor.id || anchor.label) === name && types.includes(anchor.type)));
  }
  function render() {
    $('#lf-list').innerHTML = catalog.leagues.map((league, index) => `<button type="button" class="document-button ${index === current ? 'is-active' : ''}" data-league="${index}"><strong>${escape(league.name || '새 리그')}</strong><small>엘리트 4명 · 챔피언 1명</small></button>`).join('');
    const league = catalog.leagues[current];
    $('#lf-delete').disabled = !league;
    $('#lf-title').textContent = league?.name || '리그 시설';
    if (!league) { $('#lf-editor').innerHTML = '<div class="issues empty">리그를 추가하세요.</div>'; return; }
    if (league.stages.length !== 5) { $('#lf-editor').innerHTML = '<div class="issues">엘리트 4명과 챔피언 1명으로 구성된 리그만 편집할 수 있습니다. 기존 단계 데이터는 유지됩니다.</div>'; return; }
    const generations = [['0', '지정 안 함'], ...Object.entries(available.generations || {}).map(([id, name]) => [id, `${id}세대 · ${name}`])];
    $('#lf-editor').innerHTML = `<section class="lf-section"><header><p class="eyebrow">01 / TRAINERS</p><h3>배치할 트레이너</h3><p>리그 구성에서 작성한 엘리트 4명과 챔피언 1명의 순서를 지정합니다.</p></header><div class="lf-five-grid">${league.stages.map((room, index) => `<article class="lf-trainer-card"><span class="lf-slot-label">${String(index + 1).padStart(2, '0')}</span>${select(slotLabel(index), `opponent.${index}`, room.id, league.stages.filter(candidate => candidate.role === room.role).map(candidate => [candidate.id, candidate.trainer.name.ko_kr]))}</article>`).join('')}</div><small class="lf-help">이미 배치된 엘리트를 선택하면 두 엘리트의 순서가 서로 바뀝니다.</small></section>
      <section class="lf-section"><header><p class="eyebrow">02 / ROOMS</p><h3>전투 방</h3><p>위 트레이너와 같은 순서로 사용할 방 5개를 선택합니다.</p></header><div class="lf-five-grid">${league.stages.map((room, index) => roomCard(room, String(index), slotLabel(index))).join('')}</div></section>
      <details class="lf-settings"><summary>로비·명예의 전당</summary><div class="lf-settings-body lf-two-grid">${roomCard(league.lobby, 'lobby', '로비')}${roomCard(league.hall, 'hall', '명예의 전당')}</div></details>
      <details class="lf-settings"><summary>입장 조건·도전 진행</summary><div class="lf-settings-body"><div class="form-grid">${input('리그 ID', 'id', league.id)}${input('표시 이름', 'name', league.name)}${select('진행 방식', 'mode', league.mode, [['checkpoint', '이어하기 — 마지막 승리 다음 방부터'], ['relay', '릴레이 — 중단하면 처음부터']])}${select('입장 조건 결합', 'condition_mode', league.condition_mode, [['all', '모두 충족'], ['any', '하나 이상 충족']])}</div><div class="gate-condition-builder" id="lf-conditions"><div data-gate-condition-list></div><button class="button secondary" type="button" data-gate-condition-add>조건 추가</button></div><label>입장 불가 안내<textarea data-field="locked_dialogue" rows="2">${escape(league.locked_dialogue.join('\n'))}</textarea></label></div></details>
      <details class="lf-settings"><summary>다음 세대·회차</summary><div class="lf-settings-body"><div class="form-grid">${select('현재 세대', 'generation', league.generation || 0, generations)}${select('다음 세대', 'next_generation', league.next_generation || 0, generations)}${select('세대 이동', 'generation_travel_mode', league.generation_travel_mode || 'disabled', [['disabled', '비활성'], ['travel_test', '이동 테스트 · 자산 유지']])}</div><p class="lf-help">이동 테스트에서는 아이템·포켓몬이 유지됩니다. 회차별 자산 보관·복원은 아직 적용하지 않습니다.</p></div></details>`;
    PlayerConditionEditor.initialize($('#lf-conditions'), {onChange() { league.conditions = structuredClone($('#lf-conditions').gateConditions); changed(); }});
    PlayerConditionEditor.render($('#lf-conditions'), league.conditions);
  }
  $('#lf-editor').addEventListener('input', event => {
    const field = event.target.dataset.field;
    if (!field || event.target.tagName === 'SELECT') return;
    catalog.leagues[current][field] = field === 'locked_dialogue' ? event.target.value.split('\n').filter(Boolean) : event.target.value;
    changed();
  });
  $('#lf-editor').addEventListener('change', event => {
    const field = event.target.dataset.field;
    if (!field || event.target.tagName !== 'SELECT') return;
    const league = catalog.leagues[current];
    if (field.startsWith('opponent.')) { assignTrainer(league, Number(field.split('.')[1]), event.target.value); changed(); render(); return; }
    league[field] = ['generation', 'next_generation'].includes(field) ? Number(event.target.value) : event.target.value;
    changed();
  });
  $('#lf-editor').addEventListener('click', event => {
    const button = event.target.closest('[data-room-picker]');
    if (!button) return;
    const league = catalog.leagues[current], key = button.dataset.roomPicker;
    const room = ['lobby', 'hall'].includes(key) ? league[key] : league.stages[Number(key)];
    // Only offer interiors with the markers needed by this room. Anchor names
    // stay internal and existing hand-authored positions are preserved.
    const owners = new Map(catalog.leagues.flatMap(item => [item.lobby, ...item.stages, item.hall]).filter(item => item !== room).map(item => [item.structure, item]));
    const ids = Object.entries(available.structures).filter(([id, anchors]) => {
      if (!supportsRoom(anchors, room)) return false;
      const owner = owners.get(id);
      return !owner || (league.stages.includes(room) && league.stages.includes(owner) && supportsRoom(available.structures[room.structure], owner));
    }).map(([id]) => id);
    window.LeagueFacilityUi.chooseStructure({selected: room.structure, ids, title: `${key === 'lobby' ? '로비' : key === 'hall' ? '명예의 전당' : slotLabel(Number(key))} 방 선택`, onSelect(id) {
      const owner = owners.get(id);
      if (owner) owner.structure = room.structure;
      room.structure = id; changed(); render();
    }});
  });
  $('#lf-list').addEventListener('click', event => { const button = event.target.closest('[data-league]'); if (button) { current = Number(button.dataset.league); render(); } });
  $('#lf-add').addEventListener('click', () => {
    const room = () => ({structure: '', entry: 'entry', exit: 'exit'});
    catalog.leagues.push({id: '', name: '새 리그', mode: 'checkpoint', condition_mode: 'all', conditions: [], locked_dialogue: ['입장 조건을 충족하지 못했습니다.'], lobby: {...room(), leave: 'leave', fixed_npcs: {}, fixed_pokemon: {}}, stages: Array.from({length: 5}, (_, index) => ({...room(), id: index === 4 ? 'champion' : `elite_${index + 1}`, role: index === 4 ? 'champion' : 'elite', trainer: newTrainer(slotLabel(index)), npc_anchor: 'opponent'})), hall: room()});
    current = catalog.leagues.length - 1; changed(); render();
  });
  $('#lf-delete').addEventListener('click', () => {
    if (!confirm('이 리그 설정을 삭제할까요? 건물의 로비 연결도 별도로 정리해야 합니다.')) return;
    catalog.leagues.splice(current, 1); current = Math.max(0, current - 1); changed(); render();
  });
  function newTrainer(name) {
    return {name: {ko_kr: name}, appearance: {source: 'rct_single', type: 'skin', resource: 'rctmod:trainers/single/kanto_league_lorelei'}, dialogue: {challenge: '준비가 됐다면 승부하자!', victory: '훌륭한 승부였다. 다음 방으로 나아가라.', defeat: '준비를 마치고 다시 도전해라.'}, battle: window.LeagueFacilityTeamEditor.template()};
  }
  async function load() {
    $('#save-league-facilities').disabled = true;
    try {
      const response = await fetch('/api/league-facilities');
      if (!response.ok) throw Error('리그 설정을 불러오지 못했습니다.');
      available = await response.json(); catalog = available.catalog;
      await Promise.all([PlayerConditionEditor.loadCatalogs(), window.LeagueFacilityTeamEditor.prepare()]);
      current = Math.min(current, Math.max(0, catalog.leagues.length - 1)); render(); dirty = false;
      $('#save-league-facilities').disabled = false; status('설정을 불러왔습니다.');
    } catch (error) { status(error.message); throw error; }
  }
  $('#lf-reload').addEventListener('click', () => { if (!dirty || confirm('저장하지 않은 변경사항을 버릴까요?')) load().catch(error => status(error.message)); });
  $('#lf-form').addEventListener('submit', async event => {
    event.preventDefault(); $('#save-league-facilities').disabled = true;
    try { await saveShared(); } catch (error) { status(error.message); }
    finally { $('#save-league-facilities').disabled = false; }
  });
  window.addEventListener('beforeunload', event => { if (dirty) { event.preventDefault(); event.returnValue = ''; } });
  async function saveShared() {
    for (const league of catalog.leagues) league.conditions = PlayerConditionEditor.validate(league.conditions);
    const response = await fetch('/api/league-facilities', {method: 'PUT', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(catalog)});
    const result = await response.json();
    if (!response.ok || !result.saved) throw Error(result.error || '리그 시설을 저장하지 못했습니다.');
    dirty = false; status('저장했습니다. 콘텐츠 빌드 후 서버를 재시작하세요.');
  }
  function ensure() {
    if (loading) return loading;
    if (catalog) return Promise.resolve();
    loading = load().finally(() => { loading = null; });
    return loading;
  }
  window.LeagueFacilitiesPanel = {
    ensure, saveShared, markChanged: changed, hasChanges() { return dirty; },
    trainerFor(id) {
      for (const league of catalog?.leagues || []) {
        const room = league.stages.find(room => `cobbleventure:npc/league/${league.id}_${room.id}` === id);
        if (room) return room.trainer;
      }
      return null;
    },
    activate() { ensure().then(render).catch(error => status(error.message)); },
    deactivate() {}
  };
})();
