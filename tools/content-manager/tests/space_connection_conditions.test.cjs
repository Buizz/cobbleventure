const assert = require('node:assert/strict');
const { test } = require('node:test');
const { readFileSync } = require('node:fs');
const { runInNewContext } = require('node:vm');
require('../web/player-condition-editor.js');

test('space connection badge form updates the selected edge and survives rerender', () => {
  const source = readFileSync(require.resolve('../web/space-connections.js'), 'utf8');
  const start = source.indexOf('function renderInspector()');
  const listeners = {};
  const list = { innerHTML: '' };
  const editor = {
    dataset: {}, querySelector: () => list,
    addEventListener: (type, listener) => { listeners[type] = listener; },
  };
  const inspector = { innerHTML: '' };
  const edge = {
    id: 'arena-entry', from: { node: 'lobby', anchor: 'next' },
    to: { node: 'arena', anchor: 'door' }, condition_mode: 'all',
    conditions: [{ type: 'badge', badge: 'cobbleventure:badge/kanto/boulder' }],
  };
  let dirty = 0;
  const context = {
    flow: { selectedEdgeId: edge.id }, selectedGraph: () => ({ connections: [edge] }),
    $: selector => selector === '#space-flow-inspector' ? inspector : editor,
    escapeHtml: globalThis.PlayerConditionEditor.escapeHtml,
    PlayerConditionEditor: globalThis.PlayerConditionEditor,
    structuredClone, markDirty: () => { dirty++; },
  };
  runInNewContext(source.slice(start, source.indexOf('\n}', start) + 2), context);
  context.renderInspector();
  assert.match(inspector.innerHTML, /space-edge-conditions/);
  listeners.change({ type: 'change', target: {
    value: 'cobbleventure:badge/kanto/cascade', dataset: { gateConditionField: 'badge' },
    closest: () => ({ dataset: { gateConditionIndex: '0' } }), matches: () => false,
  } });
  assert.equal(edge.conditions[0].badge, 'cobbleventure:badge/kanto/cascade');
  assert.equal(dirty, 1);
  context.renderInspector();
  assert.deepEqual(globalThis.PlayerConditionEditor.read(editor), edge.conditions);
});
