import {
  buildResult,
  emitResult,
  extendSeedPairs,
  loadCaseFile,
  maskU64,
  prepareContext,
} from "./node_benchmark_common.mjs";

function runGeneric(loopTripCount, remainder, parallelChains) {
  const states = extendSeedPairs(parallelChains);
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
  let checksum = 0n;
  for (const [left, right] of states) {
    checksum ^= left ^ right;
  }
  return checksum;
}

function runFour(loopTripCount, remainder) {
  let [[a0, b0], [a1, b1], [a2, b2], [a3, b3]] = extendSeedPairs(4);
  for (let index = 0; index < loopTripCount; index += 1) {
    a0 = maskU64(a0 + b0);
    b0 = maskU64(a0 + b0);
    a1 = maskU64(a1 + b1);
    b1 = maskU64(a1 + b1);
    a2 = maskU64(a2 + b2);
    b2 = maskU64(a2 + b2);
    a3 = maskU64(a3 + b3);
    b3 = maskU64(a3 + b3);
  }
  for (let index = 0; index < remainder; index += 1) {
    a0 = maskU64(a0 + b0);
    b0 = maskU64(a0 + b0);
  }
  return a0 ^ b0 ^ a1 ^ b1 ^ a2 ^ b2 ^ a3 ^ b3;
}

function runEight(loopTripCount, remainder) {
  const values = extendSeedPairs(8).flat();
  for (let index = 0; index < loopTripCount; index += 1) {
    for (let offset = 0; offset < 16; offset += 2) {
      values[offset] = maskU64(values[offset] + values[offset + 1]);
      values[offset + 1] = maskU64(values[offset] + values[offset + 1]);
    }
  }
  for (let index = 0; index < remainder; index += 1) {
    values[0] = maskU64(values[0] + values[1]);
    values[1] = maskU64(values[0] + values[1]);
  }
  return values.reduce((checksum, value) => checksum ^ value, 0n);
}

const caseData = loadCaseFile();
const loopTripCount = Math.floor(caseData.iterations / caseData.parallelChains);
const remainder = caseData.iterations % caseData.parallelChains;
const context = prepareContext(caseData.priorityMode, caseData.affinityMode);

const start = process.hrtime.bigint();
const checksum =
  caseData.parallelChains === 4
    ? runFour(loopTripCount, remainder)
    : caseData.parallelChains === 8
      ? runEight(loopTripCount, remainder)
      : runGeneric(loopTripCount, remainder, caseData.parallelChains);
const end = process.hrtime.bigint();

emitResult(
  buildResult({
    implementation: "node",
    caseData,
    context,
    elapsedNs: end - start,
    loopTripCount,
    remainder,
    checksum,
  }),
);
