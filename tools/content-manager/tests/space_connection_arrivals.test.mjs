import assert from 'node:assert/strict';
import { test } from 'node:test';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';

const source = readFileSync(new URL('../web/space-connections.js', import.meta.url), 'utf8');
function editor() {
  const graph = { kind: 'building', nodes: [{ id: 'exterior', structure: 'league' }, { id: 'hall', structure: 'hall' }], connections: [] };
  const context = {
    flow: { structures: {
      league: { category: 'league', arrival_anchors: [{ label: 'lobby_return' }], transition_anchors: [{ label: 'champion_exit' }] },
      hall: { arrival_anchors: [{ label: 'hall_entry' }], transition_anchors: [{ label: 'hall_exit' }] },
    } },
    selectedGraph: () => graph, markDirty() {}, renderAll() {},
  };
  for (const name of ['supportsInteriorConnections', 'nodeAnchorEntries', 'connectTo']) {
    const start = source.indexOf(`function ${name}(`);
    assert.ok(start >= 0);
    runInNewContext(source.slice(start, source.indexOf('\n}', start) + 2), context);
  }
  return { context, graph };
}

test('league supports interior connections and arrival points are destination-only', () => {
  const { context, graph } = editor();
  assert.equal(context.supportsInteriorConnections({ category: 'league' }), true);
  assert.equal(context.nodeAnchorEntries(graph.nodes[0])[1].destinationOnly, true);
});

test('dragging hall exit to exterior arrival preserves outbound direction', () => {
  const { context, graph } = editor();
  context.flow.connectionDraft = { node: 'hall', anchor: 'hall_exit' };
  context.connectTo('exterior', 'lobby_return');
  assert.equal(graph.connections[0].from.node, 'hall');
  assert.equal(graph.connections[0].to.anchor, 'lobby_return');
});

test('arrival cannot become a source and champion exit connects to hall arrival', () => {
  const { context, graph } = editor();
  context.flow.connectionDraft = { node: 'exterior', anchor: 'lobby_return' };
  context.connectTo('hall', 'hall_exit');
  assert.equal(graph.connections.length, 0);
  context.flow.connectionDraft = { node: 'exterior', anchor: 'champion_exit' };
  context.connectTo('hall', 'hall_entry');
  assert.equal(graph.connections[0].from.anchor, 'champion_exit');
  assert.equal(graph.connections[0].to.anchor, 'hall_entry');
});
