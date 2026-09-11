import assert from 'node:assert/strict';
import { test } from 'node:test';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';

const source = readFileSync(new URL('../web/app.js', import.meta.url), 'utf8');
function editor() {
  const trainer = { name: {ko_kr: '칸나'}, appearance: {}, dialogue: {}, battle: {team: [{level: 60}]} };
  const entry = {id: 'elite_1', role: 'elite_four', trainer_id: 'cobbleventure:npc/league/kanto_elite_1'};
  const nodes = new Map();
  const node = key => {
    if (!nodes.has(key)) nodes.set(key, {hidden: false, disabled: false, value: '', closest: () => node(`${key}:label`)});
    return nodes.get(key);
  };
  node('#league-form').elements = new Proxy({}, {get: (_, name) => node(name)});
  const requests = [];
  const context = {
    state: {leagueProgression: {entries: [entry]}},
    window: {LeagueFacilitiesPanel: {
      trainerFor: id => id === entry.trainer_id ? trainer : null,
      markChanged() {}, hasChanges: () => true, saveShared: async () => requests.push('facility'),
    }},
    $: node, selectedLeagueEntry: () => entry,
    setLeagueTeamButtons() {}, renderTeam(target) { context.state.teamEditorTarget = target; },
    updateFocusedPokemon() {}, showIssues() {}, toast() {},
    request: async url => { requests.push(url); return {ok: true, data: {}}; },
    loadLists: async () => {}, renderLeagueEditor() {},
  };
  for (const name of ['leagueEntryBadgeId', 'leagueFacilityTrainer', 'configureLeagueEncounterVisibility', 'renderLeagueTeamEditor', 'updateLeagueEntryFromForm', 'saveLeagueProgression']) {
    const start = source.indexOf(`${name === 'saveLeagueProgression' ? 'async ' : ''}function ${name}(`);
    assert.ok(start >= 0);
    runInNewContext(source.slice(start, source.indexOf('\n}', start) + 2), context);
  }
  return {context, entry, trainer, node, requests};
}

test('elite and champion have no badge or NPC assignment controls; gym keeps its reward badge', () => {
  const {context: c, entry, node} = editor();
  for (const role of ['elite_four', 'gym_leader', 'champion']) {
    entry.role = role;
    c.configureLeagueEncounterVisibility(entry);
    assert.equal(node('#league-encounter-fields').hidden, false);
    assert.equal(node('#league-trainer-link').hidden, true);
    assert.equal(node('#league-display-badge-fields').hidden, true);
    assert.equal(node('badgeId:label').hidden, role !== 'gym_leader');
  }
  entry.badge_id = 'obsolete-badge';
  assert.equal(c.leagueEntryBadgeId(entry), undefined);
});

test('elite editor edits the canonical room team directly without a preset lookup', () => {
  const {context: c, entry, trainer, node} = editor();
  c.renderLeagueTeamEditor(entry);
  assert.equal(node('#league-team-editor').hidden, false);
  assert.equal(c.state.teamEditorTarget, '#league-team-list');
  assert.equal(c.state.leagueBattlePreset, trainer);
  c.state.leagueBattlePreset.battle.team[0].level = 59;
  assert.equal(trainer.battle.team[0].level, 59);
});

test('inline dialogue writes the canonical trainer and removes stale display badges', () => {
  const {context: c, entry, trainer, node} = editor();
  entry.badge_id = 'obsolete-badge';
  for (const [key, value] of Object.entries({id: entry.id, role: entry.role, trainerId: entry.trainer_id,
    nameKo: '칸나', nameEn: '', primaryType: 'ice', generation: '1', order: '9', levelCap: '60', region: 'kanto',
    appearanceSource: 'rct_single', appearanceResource: 'skin', challengeDialogue: '첫 대사\n다음 대사', victoryDialogue: '승리', defeatDialogue: '패배'})) node(key).value = value;
  c.updateLeagueEntryFromForm();
  assert.equal(trainer.dialogue.challenge, '첫 대사\n다음 대사');
  assert.equal(trainer.appearance.resource, 'skin');
  assert.equal(entry.badge_id, undefined);
  assert.equal(entry.encounter, undefined);
});

test('main save persists the shared facility trainer and progression without an authored NPC write', async () => {
  const {context: c, entry, node, requests} = editor();
  c.updateLeagueEntryFromForm = () => {};
  node('#league-form').reportValidity = () => true;
  c.renderLeagueTeamEditor(entry);
  await c.saveLeagueProgression();
  assert.deepEqual(requests, ['facility', '/api/league-progression']);
});

test('gym battle association is internal and has no preset selector', () => {
  const html = readFileSync(new URL('../web/index.html', import.meta.url), 'utf8');
  const form = html.slice(html.indexOf('id="league-form"'), html.indexOf('id="league-team-editor"'));
  assert.match(form, /<input name="battleId" type="hidden">/);
  assert.doesNotMatch(form, /<select name="battleId"/);
});
