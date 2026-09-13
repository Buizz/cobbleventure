import { readFile } from "node:fs/promises";
import { resolve } from "node:path";

import { compareJvmSearchTrace } from "../lib/jvm-search-parity.mjs";

const defaultReport = resolve(
  import.meta.dirname,
  "../../build/reports/battle-ai/headless-eve.json",
);
const reportPath = resolve(process.argv[2] ?? defaultReport);
const report = JSON.parse(await readFile(reportPath, "utf8"));
const comparisons = [];

for (const side of ["red", "blue"]) {
  for (const decision of report[side]?.decisions ?? []) {
    if (!decision.searchReplay) continue;
    const comparison = compareJvmSearchTrace(decision.searchReplay);
    comparisons.push({
      side,
      turn: decision.turn,
      showdown: decision.showdown,
      ...comparison,
      webDecision: undefined,
    });
  }
}

const mismatches = comparisons.filter((entry) => !entry.matches);
const result = {
  schemaVersion: 1,
  reportPath,
  scenario: report.scenario,
  comparedDecisions: comparisons.length,
  matchingDecisions: comparisons.length - mismatches.length,
  mismatches,
  comparisons,
};
console.log(JSON.stringify(result, null, 2));

if (comparisons.length === 0 || mismatches.length > 0) process.exitCode = 1;
