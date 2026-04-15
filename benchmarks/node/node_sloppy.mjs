import {
  buildResult,
  emitResult,
  extendSeedPairs,
  loadCaseFile,
  maskU64,
  prepareContext,
} from "./node_benchmark_common.mjs";

const caseData = loadCaseFile();
const loopTripCount = Math.floor(caseData.iterations / caseData.parallelChains);
const remainder = caseData.iterations % caseData.parallelChains;
const states = extendSeedPairs(caseData.parallelChains);
const context = prepareContext(caseData.priorityMode, caseData.affinityMode);

const start = process.hrtime.bigint();
for (let index = 0; index < loopTripCount; index += 1) {
  for (const state of states) {
    state[0] = maskU64(state[0] + state[1]);
    state[1] = maskU64(state[0] + state[1]);
  }
}
for (let index = 0; index < remainder; index += 1) {
  states[0][0] = maskU64(states[0][0] + states[0][1]);
  states[0][1] = maskU64(states[0][0] + states[0][1]);
}
const end = process.hrtime.bigint();

let checksum = 0n;
for (const [left, right] of states) {
  checksum ^= left ^ right;
}

emitResult(
  buildResult({
    implementation: "node_sloppy",
    caseData,
    context,
    elapsedNs: end - start,
    loopTripCount,
    remainder,
    checksum,
  }),
);
