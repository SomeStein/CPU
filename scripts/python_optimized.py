from __future__ import annotations

from benchmark_common import (
    build_result,
    emit_result_json,
    extend_seed_pairs,
    load_case_from_argv,
    mask_u64,
    monotonic_ns,
    prepare_process_context,
)


def _run_generic(loop_trip_count: int, remainder: int, parallel_chains: int) -> int:
    states = [[left, right] for left, right in extend_seed_pairs(parallel_chains)]
    for _ in range(loop_trip_count):
        for state in states:
            state[0] = mask_u64(state[0] + state[1])
            state[1] = mask_u64(state[0] + state[1])
    for _ in range(remainder):
        states[0][0] = mask_u64(states[0][0] + states[0][1])
        states[0][1] = mask_u64(states[0][0] + states[0][1])
    checksum = 0
    for left, right in states:
        checksum ^= left ^ right
    return checksum


def _run_four(loop_trip_count: int, remainder: int) -> int:
    (a0, b0), (a1, b1), (a2, b2), (a3, b3) = extend_seed_pairs(4)
    for _ in range(loop_trip_count):
        a0 = mask_u64(a0 + b0)
        b0 = mask_u64(a0 + b0)
        a1 = mask_u64(a1 + b1)
        b1 = mask_u64(a1 + b1)
        a2 = mask_u64(a2 + b2)
        b2 = mask_u64(a2 + b2)
        a3 = mask_u64(a3 + b3)
        b3 = mask_u64(a3 + b3)
    for _ in range(remainder):
        a0 = mask_u64(a0 + b0)
        b0 = mask_u64(a0 + b0)
    return a0 ^ b0 ^ a1 ^ b1 ^ a2 ^ b2 ^ a3 ^ b3


def _run_eight(loop_trip_count: int, remainder: int) -> int:
    seeds = extend_seed_pairs(8)
    values = [value for pair in seeds for value in pair]
    for _ in range(loop_trip_count):
        for index in range(0, 16, 2):
            values[index] = mask_u64(values[index] + values[index + 1])
            values[index + 1] = mask_u64(values[index] + values[index + 1])
    for _ in range(remainder):
        values[0] = mask_u64(values[0] + values[1])
        values[1] = mask_u64(values[0] + values[1])
    checksum = 0
    for value in values:
        checksum ^= value
    return checksum


def main() -> int:
    case = load_case_from_argv()
    iterations = int(case["iterations"])
    parallel_chains = int(case["parallel_chains"])
    loop_trip_count = iterations // parallel_chains
    remainder = iterations % parallel_chains

    context = prepare_process_context(str(case["priority_mode"]), str(case["affinity_mode"]))

    start = monotonic_ns()
    if parallel_chains == 4:
        checksum = _run_four(loop_trip_count, remainder)
    elif parallel_chains == 8:
        checksum = _run_eight(loop_trip_count, remainder)
    else:
        checksum = _run_generic(loop_trip_count, remainder, parallel_chains)
    end = monotonic_ns()

    emit_result_json(
        build_result(
            implementation="python_optimized",
            case=case,
            context=context,
            elapsed_ns=end - start,
            loop_trip_count=loop_trip_count,
            remainder=remainder,
            result_checksum=checksum,
            timer_kind="perf_counter_ns",
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
