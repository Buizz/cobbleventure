import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..');
const source = fs.readFileSync(path.join(root, 'tools/content-manager/web/app.js'), 'utf8');
// Exercise the actual pure planner functions, without starting the editor DOM.
const lines = source.split('\n');
const declarations = [];
for (let index = 0; index < lines.length; index++) {
  if (!/^function \w+\(/.test(lines[index])) continue;
  let declaration = lines[index];
  while (true) {
    try { new vm.Script(declaration); break; }
    catch (error) { if (++index >= lines.length) throw error; declaration += '\n' + lines[index]; }
  }
  declarations.push(declaration);
}
const functions = declarations.join('\n');
const constants = ['dungeonPreviewFacingVectors', 'dungeonPreviewOppositeFacing', 'dungeonGridDirections']
  .map(name => source.match(new RegExp(`^const ${name} = [^]*?;`, 'm'))[0]).join('\n');
const content = path.join(root, 'content-projects/cobbleventure-main/content');
function jsonFiles(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => entry.isDirectory()
    ? jsonFiles(path.join(directory, entry.name))
    : entry.name.endsWith('.json') ? [JSON.parse(fs.readFileSync(path.join(directory, entry.name), 'utf8'))] : []);
}
const pieces = jsonFiles(path.join(content, 'dungeon_pieces'));
const state = { dungeonPieces: new Map(pieces.map(piece => [piece.piece_id, piece])), dungeonPlans: new Map(), dungeonPreview: { seed: 1 } };
const context = vm.createContext({ state, console, structuredClone });
vm.runInContext(`${functions}\n${constants}`, context);
const documents = jsonFiles(path.join(content, 'dungeons')).filter(document => document.terrain?.mode === 'nbt_pieces');
let checked = 0;
for (const original of documents) {
  for (const chamberShape of ['empty_chamber_1x2', 'empty_chamber_2x2']) {
    const document = structuredClone(original);
    document.vertical = { ...document.vertical, mode: 'flat', floor_count: [1, 1] };
    document.spatial_layout.chamber_pieces = pieces.filter(piece => piece.piece_id.endsWith('/' + chamberShape)
      && piece.tags.includes(document.terrain.piece_pool)).map(piece => piece.piece_id);
    for (let seed = 1; seed <= 40; seed++) {
      context.input = document; context.seed = seed; state.dungeonPreview.seed = seed;
      const result = vm.runInContext('runtimeNbtDungeonPlan(input, seed)', context);
      const label = `${document.dungeon_id}/${chamberShape}/seed=${seed}`;
      assert.equal(result.compileErrors.length, 0, `${label}: ${result.compileErrors.join('; ')}`);
      assert.ok(result.placements.some(piece => document.spatial_layout.chamber_pieces.includes(piece.pieceId)), `${label}: selected chamber missing`);
      assert.ok(result.placements.every(piece => !piece.connectorMismatch?.length), `${label}: missing corner/connector`);
      for (const piece of result.placements.filter(piece => piece.logicalNode === undefined && !piece.closesChamberPort)) {
        const degree = result.links.filter(link => link.from === piece.index || link.to === piece.index).length;
        assert.equal(degree, 2, `${label}: two logical routes merged inside corridor ${piece.index}`);
      }
      for (const link of result.links) {
        assert.equal(link.fromPosition.reduce((sum, value, axis) => sum + Math.abs(value - link.toPosition[axis]), 0), 1, `${label}: disconnected path`);
      }
      for (let i = 0; i < result.placements.length; i++) for (let j = i + 1; j < result.placements.length; j++) {
        const a = result.placements[i], b = result.placements[j];
        assert.ok(![0, 1, 2].every(axis => a.minimum[axis] < b.minimum[axis] + b.size[axis]
          && b.minimum[axis] < a.minimum[axis] + a.size[axis]), `${label}: overlapping pieces ${i}/${j}`);
      }
      checked++;
    }
  }
}
console.log(`PASS: ${checked} single-floor plans, both chamber footprints, port adjacency and NBT bounds`);
for (const original of documents) {
  for (const flat of [true, false]) for (let seed = 1; seed <= 20; seed++) {
    context.input = structuredClone(original); context.seed = seed; state.dungeonPreview.seed = seed;
    if (flat) context.input.vertical = { ...context.input.vertical, mode: 'flat', floor_count: [1, 1] };
    const plan = vm.runInContext('runtimeNbtDungeonPlan(input, seed)', context);
    const label = `${original.dungeon_id}/flat=${flat}/seed=${seed}`;
    assert.equal(plan.compileErrors.length, 0, `${label}: ${plan.compileErrors.join('; ')}`);
    assert.ok(plan.npcCapacity.valid, `${label}: ${JSON.stringify(plan.npcCapacity)}`);
  }
  for (const facings of [['north','east'], ['east','south'], ['south','west'], ['west','north']]) {
    context.input = original; context.facings = facings;
    const selection = vm.runInContext('dungeonRuntimePreviewPiece(input, "corridor", () => 0.5, "corner", facings, "", 0, "passage", 16)', context);
    assert.ok(selection?.piece, `${original.dungeon_id}: missing corner ${facings}`);
    assert.equal(selection.piece.connectors.length, 2);
  }
}
console.log('PASS: 120 saved-preset plans including NPC capacity, and all four corner orientations');
for (const [mode, groups, actors] of [['independent', 3, 3], ['cooperative', 2, 4]]) {
  const document = structuredClone(documents.find(document => document.dungeon_id.endsWith('rocket_casino_hideout')));
  document.multiplayer.mode = mode;
  document.encounters = [];
  document.generated_trainers.count = [groups, groups];
  const piece = pieces.find(piece => piece.piece_id === 'cobbleventure:dungeon_piece/rocket/empty_chamber_2x2');
  context.input = document;
  context.placements = [{ index: 0, pieceId: piece.piece_id, role: 'room', spaceKind: 'chamber',
    critical: true, minimum: [0, 0, 0], size: piece.size, rotation: 'none' }];
  const markers = vm.runInContext('runtimeDungeonContentMarkers(input, placements)', context);
  assert.equal(markers.npcCapacity.assigned, actors, `${mode}: authored slots in one chamber must be reusable`);
  assert.equal(markers.npcCapacity.valid, true);
}
console.log('PASS: one large chamber hosts three solo encounters or two cooperative pairs');
assert.equal(vm.runInContext('JSON.stringify(dungeonEditedFloorRange("discrete_floors", "flat", 2, 3))', context), '[2,3]');
assert.equal(vm.runInContext('JSON.stringify(dungeonEditedFloorRange("flat", "discrete_floors", 2, 3))', context), '[2,3]');
assert.equal(vm.runInContext('JSON.stringify(dungeonEditedFloorRange("flat", "discrete_floors", 1, 1))', context), '[2,2]');
assert.equal(vm.runInContext('dungeonNpcCapacityLabel({assigned:5, actorDemand:7, demand:7, capacity:36})', context),
  '5/7명 배치 · 7자리 필요 / 36개 후보 슬롯');
for (const mode of ['continuous', 'authored']) {
  context.input = structuredClone(documents[0]); context.input.vertical.mode = mode;
  assert.equal(vm.runInContext('runtimeNbtDungeonPlan(input, 7)', context), null);
  assert.ok(vm.runInContext('dungeonRuntimeVerticalProblem(input)', context));
  context.input.plan.mode = 'authored';
  assert.equal(vm.runInContext('dungeonRuntimeVerticalProblem(input)', context), '');
}
console.log('PASS: floor range retention, unsupported runtime modes, actual NPC assignment label');
