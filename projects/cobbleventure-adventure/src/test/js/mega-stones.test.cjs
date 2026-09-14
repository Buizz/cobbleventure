// node projects/cobbleventure-adventure/src/test/js/mega-stones.test.cjs <extracted-showdown-directory>
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(process.argv[2]);
const {BattleActions} = require(path.join(root, 'sim/battle-actions.js'));
const {Dex} = require(path.join(root, 'sim/dex.js'));
const {Battle} = require(path.join(root, 'sim/battle.js'));
const replacement = fs.readFileSync(path.join(__dirname, '../../main/resources/showdown-mega-stone.js'), 'utf8').trim();
const original = BattleActions.prototype.canMegaEvo.toString();
assert.ok(original.includes('const megaEvolution = stone[species.name];') || original.includes(replacement));
const upgraded = original.replace('const megaEvolution = stone[species.name];', replacement);
const evaluate = body => new Function('pokemon', body.slice(body.indexOf('{') + 1, body.lastIndexOf('}')));
const before = evaluate(original);
const after = evaluate(upgraded);
const pokemon = (name, item) => ({species: {name}, getItem: () => item, baseMoves: [], volatiles: {}});
const mawilite = Dex.mod('cobblemon').items.get('mawilite');
if (!original.includes(replacement)) assert.equal(before(pokemon('Mawile', mawilite)), undefined);
let count = 0;
for (const item of Dex.mod('cobblemon').items.all()) {
    if (typeof item.megaStone !== 'string' || !item.megaEvolves) continue;
    assert.equal(after(pokemon(item.megaEvolves, item)), item.megaStone, item.id);
    assert.equal(after(pokemon('Magikarp', item)), null, item.id);
    assert.equal(after(pokemon(item.megaStone, item)), null, 'Already evolved: ' + item.id);
    count++;
}
assert.ok(count >= 40);
assert.equal(after(pokemon('Mawile', {megaStone: {Mawile: 'Mawile-Mega'}})), 'Mawile-Mega');
assert.equal(after(pokemon('Magikarp', {megaStone: {Mawile: 'Mawile-Mega'}})), undefined);
assert.equal(after(pokemon('Mawile', {})), null);
BattleActions.prototype.canMegaEvo = after;
const battle = new Battle({
    formatid: 'gen9customgame',
    p1: {name: 'RingTest', team: [{species: 'Mawile', item: 'Mawilite', ability: 'Intimidate', moves: ['ironhead'], movesInfo: [{pp: 15, maxPp: 15}]}]},
    p2: {name: 'Opponent', team: [{species: 'Blissey', ability: 'Natural Cure', moves: ['splash'], movesInfo: [{pp: 40, maxPp: 40}]}]},
});
if (battle.requestState === 'teampreview') {
    battle.choose('p1', 'team 1');
    battle.choose('p2', 'team 1');
}
assert.equal(battle.p1.activeRequest.active[0].canMegaEvo, true);
battle.choose('p1', 'move 1 mega');
battle.choose('p2', 'move 1');
assert.equal(battle.p1.active[0].species.name, 'Mawile-Mega');
assert.equal(battle.p1.active[0].canMegaEvo, null);
battle.destroy();
console.log(`PASS: ${count} native stones, mapped stones, wrong species, and Mawile battle evolution`);
