import fs from "node:fs";
import os from "node:os";

const MASK64 = (1n << 64n) - 1n;
const SEED_PAIRS = [
  [1n, 1n],
  [3n, 5n],
  [8n, 13n],
  [21n, 34n],
  [55n, 89n],
  [144n, 233n],
  [377n, 610n],
  [987n, 1597n],
];

export function maskU64(value) {
  return value & MASK64;
}

export function extendSeedPairs(count) {
  const pairs = SEED_PAIRS.map(([left, right]) => [left, right]);
  while (pairs.length < count) {
    const left = maskU64(pairs[pairs.length - 2][0] + pairs[pairs.length - 1][1]);
    const right = maskU64(left + pairs[pairs.length - 1][0]);
    pairs.push([left, right]);
  }
  return pairs.slice(0, count);
}

export function loadCaseFile() {
  if (process.argv.length !== 4 || process.argv[2] !== "--case-file") {
    throw new Error("Usage: node <script> --case-file <path>");
  }
  const raw = fs.readFileSync(process.argv[3], "utf8");
  const parsed = {};
  for (const line of raw.split(/\r?\n/)) {
    if (!line || line.startsWith("#") || !line.includes("=")) {
      continue;
    }
    const index = line.indexOf("=");
    parsed[line.slice(0, index).trim()] = line.slice(index + 1).trim();
  }
  return {
    runId: parsed.run_id,
    profileId: parsed.profile_id,
    implementation: parsed.implementation,
    caseId: parsed.case_id,
    iterations: Number.parseInt(parsed.iterations, 10),
    parallelChains: Number.parseInt(parsed.parallel_chains, 10),
    priorityMode: parsed.priority_mode,
    affinityMode: parsed.affinity_mode,
    timerMode: parsed.timer_mode,
    warmup: parsed.warmup === "true",
    repeatIndex: Number.parseInt(parsed.repeat_index, 10),
  };
}

export function prepareContext(priorityMode, affinityMode) {
  const isMacos = process.platform === "darwin";
  return {
    pid: process.pid,
    tid: 0,
    requestedPriorityMode: priorityMode,
    requestedAffinityMode: affinityMode,
    appliedPriorityMode: priorityMode === "high" && isMacos ? "advisory_macos" : "unsupported",
    appliedAffinityMode: affinityMode === "single_core" ? (isMacos ? "advisory_macos" : "unsupported") : "unchanged",
    schedulerNotes: isMacos
      ? "Node runtime uses controller-side best effort only; macOS affinity remains advisory"
      : "Node runtime uses controller-side best effort only",
    hostOs: process.platform === "darwin" ? "macos" : process.platform === "win32" ? "windows" : process.platform,
    hostArch: process.arch === "x64" ? "x64" : process.arch === "arm64" ? "arm64" : process.arch,
    runtimeName: "node",
  };
}

export function emitResult(result) {
  process.stdout.write(`${JSON.stringify(result)}\n`);
}

export function buildResult({
  implementation,
  caseData,
  context,
  elapsedNs,
  loopTripCount,
  remainder,
  checksum,
}) {
  const totalAdds = caseData.iterations * 2;
  return {
    schema_version: 1,
    implementation,
    language: "node",
    variant: "default",
    case_id: caseData.caseId,
    warmup: caseData.warmup,
    repeat_index: caseData.repeatIndex,
    iterations: caseData.iterations,
    parallel_chains: caseData.parallelChains,
    loop_trip_count: loopTripCount,
    remainder,
    timer_kind: "hrtime_ns",
    elapsed_ns: Number(elapsedNs),
    ns_per_iteration: caseData.iterations ? Number(elapsedNs) / caseData.iterations : 0,
    ns_per_add: totalAdds ? Number(elapsedNs) / totalAdds : 0,
    result_checksum: checksum.toString(),
    host_os: context.hostOs,
    host_arch: context.hostArch,
    pid: context.pid,
    tid: context.tid,
    requested_priority_mode: context.requestedPriorityMode,
    requested_affinity_mode: context.requestedAffinityMode,
    applied_priority_mode: context.appliedPriorityMode,
    applied_affinity_mode: context.appliedAffinityMode,
    scheduler_notes: context.schedulerNotes,
    runtime_name: context.runtimeName,
    platform_extras: {},
  };
}
