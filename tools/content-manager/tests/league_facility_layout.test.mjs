import assert from 'node:assert/strict';
import {test} from 'node:test';
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';

const source = readFileSync(new URL('../web/league-facilities.js', import.meta.url), 'utf8');
function functions(...names) {
  const context = {};
  for (const name of names) {
    const start = source.indexOf(`  function ${name}(`);
    assert.ok(start >= 0);
    runInNewContext(source.slice(start, source.indexOf('\n  }', start) + 4), context);
  }
  return context;
}
test('assigning an elite swaps stable trainer identities while room placements stay fixed', () => {
  const {assignTrainer} = functions('assignTrainer');
  const first = {id: 'elite_1', role:'elite', trainer: {name:'A'}, structure:'room_a', npc_anchor:'opponent'};
  const second = {id: 'elite_2', role:'elite', trainer: {name:'B'}, structure:'room_b', npc_anchor:'opponent'};
  const champion = {id:'champion', role:'champion', trainer:{name:'C'}, structure:'room_c'};
  const league = {stages: [first, second, champion]};
  assignTrainer(league, 0, 'elite_2');
  assert.deepEqual([first.id, first.trainer.name, first.structure], ['elite_2','B','room_a']);
  assert.deepEqual([second.id, second.trainer.name, second.structure], ['elite_1','A','room_b']);
  assignTrainer(league, 0, 'champion');
  assert.equal(first.id, 'elite_2');
  assignTrainer(league, 0, 'elite_1');
  assert.equal(first.trainer.name, 'A');
});
test('room choices require automatic arrival, exit, NPC and battle positions', () => {
  const {supportsRoom} = functions('supportsRoom');
  const room = {entry:'entry', exit:'exit', npc_anchor:'opponent'};
  const anchors = [{id:'entry',type:'arrival'}, {id:'exit',type:'transition'}, {id:'opponent',type:'npc_position'}, {id:'opponent_battle_player',type:'npc_position'}];
  assert.equal(supportsRoom(anchors, room), true);
  assert.equal(supportsRoom(anchors.slice(0,3), room), false);
  assert.equal(supportsRoom(anchors, {...room, fixed_npcs:{nurse:'nurse'}}), false);
});
test('facility page uses shared sticky header and five trainer and room slots', () => {
  const html = readFileSync(new URL('../web/index.html', import.meta.url), 'utf8');
  const page = html.slice(html.indexOf('id="league-facilities"'), html.indexOf('id="league"'));
  assert.match(page, /class="panel-heading" data-save-bar/);
  assert.match(page, /id="save-league-facilities"[^>]*form="lf-form"/);
  assert.match(source, /Array\.from\(\{length: 5\}/);
  assert.doesNotMatch(source, /data-edit-team|data-add-stage|data-field="entry"|data-field="npc_anchor"|외형 리소스/);
  assert.match(source, /window\.LeagueFacilityUi\.chooseStructure/);
});
