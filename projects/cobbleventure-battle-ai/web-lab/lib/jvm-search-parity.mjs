import {
  decideTwoTurnJson,
  decideWinRateJson,
} from "./shared-ai-core.mjs";

const callKey = (...parts) => parts.join("\u0000");

function indexed(entries, keyOf) {
  const result = new Map();
  for (const entry of entries ?? []) result.set(keyOf(entry), entry);
  return result;
}

export function replayJvmSearchTrace(trace) {
  if (trace?.schemaVersion !== 1) {
    throw new Error(`Unsupported JVM search trace schema: ${trace?.schemaVersion}`);
  }
  const candidates = indexed(
    trace.candidates,
    (entry) => callKey(entry.stateId, entry.sideIndex),
  );
  const transitions = indexed(
    trace.transitions,
    (entry) => callKey(
      entry.stateId,
      entry.sideZeroActionId,
      entry.sideOneActionId,
    ),
  );
  const winProbabilities = indexed(
    trace.winProbabilities,
    (entry) => callKey(entry.stateId, entry.sideIndex),
  );
  const terminals = indexed(trace.terminals, (entry) => entry.stateId);
  const requireEntry = (map, key, kind) => {
    const entry = map.get(key);
    if (entry === undefined) throw new Error(`Missing ${kind} replay entry: ${key}`);
    return entry;
  };
  const candidateCallback = (stateId, sideIndex) => JSON.stringify(
    requireEntry(candidates, callKey(stateId, sideIndex), "candidate").actions,
  );
  const transitionCallback = (stateId, sideZeroActionId, sideOneActionId) =>
    requireEntry(
      transitions,
      callKey(stateId, sideZeroActionId, sideOneActionId),
      "transition",
    ).nextStateId ?? null;
  const winProbabilityCallback = (stateId, sideIndex) => Number(
    requireEntry(
      winProbabilities,
      callKey(stateId, sideIndex),
      "win probability",
    ).value,
  );
  const terminalCallback = (stateId) =>
    requireEntry(terminals, stateId, "terminal").value === true;

  const args = [
    trace.initialStateId,
    trace.sideIndex,
    trace.maxNodes,
    candidateCallback,
    transitionCallback,
    winProbabilityCallback,
    terminalCallback,
  ];
  const raw = trace.algorithm === "win_rate"
    ? decideWinRateJson(...args)
    : decideTwoTurnJson(
      ...args,
      null,
      null,
      trace.transitionCacheNamespace ?? null,
    );
  return JSON.parse(raw);
}

export function compareJvmSearchTrace(trace) {
  const webDecision = replayJvmSearchTrace(trace);
  const webActionId = webDecision.selected?.id ?? null;
  return {
    matches: webActionId === (trace.selectedActionId ?? null),
    jvmActionId: trace.selectedActionId ?? null,
    webActionId,
    webDecision,
  };
}
