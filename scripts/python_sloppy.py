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


def main() -> int:
    case = load_case_from_argv()
    iterations = int(case["iterations"])
    parallel_chains = int(case["parallel_chains"])
    loop_trip_count = iterations // parallel_chains
    remainder = iterations % parallel_chains

    context = prepare_process_context(str(case["priority_mode"]), str(case["affinity_mode"]))
    states = [[left, right] for left, right in extend_seed_pairs(parallel_chains)]

    start = monotonic_ns()
    for _ in range(loop_trip_count):
        for state in states:
            state[0] = mask_u64(state[0] + state[1])
            state[1] = mask_u64(state[0] + state[1])

    for _ in range(remainder):
        states[0][0] = mask_u64(states[0][0] + states[0][1])
        states[0][1] = mask_u64(states[0][0] + states[0][1])
    end = monotonic_ns()

    checksum = 0
    for left, right in states:
        checksum ^= left ^ right

    emit_result_json(
        build_result(
            implementation="python_sloppy",
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
