import assert from "node:assert/strict";
import test from "node:test";

import { compareJvmSearchTrace } from "../lib/jvm-search-parity.mjs";

const action = (id, score) => ({
  id,
  kind: "move",
  score,
  successProbability: 1,
  expectedDamage: score,
  nonConsecutive: false,
  statusMove: false,
  guaranteedKnockout: false,
  opponentKnockoutBeforeActionProbability: 0,
  heuristicSelected: false,
});

test("replays a JVM callback trace through the web shared core", () => {
  const ownBest = action("move:thunderbolt", 80);
  const ownOther = action("move:quickattack", 30);
  const opponent = action("move:surf", 70);
  const trace = {
    schemaVersion: 1,
    algorithm: "two_turn",
    initialStateId: "root",
    sideIndex: 0,
    maxNodes: 10,
    transitionCacheNamespace: "parity-test",
    selectedActionId: ownBest.id,
    candidates: [
      { stateId: "root", sideIndex: 0, actions: [ownBest, ownOther] },
      { stateId: "root", sideIndex: 1, actions: [opponent] },
    ],
    transitions: [
      {
        stateId: "root",
        sideZeroActionId: ownBest.id,
        sideOneActionId: opponent.id,
        nextStateId: "best-result",
      },
      {
        stateId: "root",
        sideZeroActionId: ownOther.id,
        sideOneActionId: opponent.id,
        nextStateId: "other-result",
      },
    ],
    winProbabilities: [
      { stateId: "best-result", sideIndex: 0, value: 0.8 },
      { stateId: "other-result", sideIndex: 0, value: 0.3 },
    ],
    terminals: [
      { stateId: "best-result", value: true },
      { stateId: "other-result", value: true },
    ],
  };

  const comparison = compareJvmSearchTrace(trace);
  assert.equal(comparison.matches, true);
  assert.equal(comparison.webActionId, ownBest.id);
});
