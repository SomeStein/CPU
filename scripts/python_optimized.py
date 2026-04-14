from benchmark_common import (
    DEFAULT_AFFINITY_MASK,
    emit_record,
    get_current_processor_number,
    get_positive_int_from_env,
    mask_u64,
    prepare_thread_context,
    query_thread_cycle_time,
)


def main() -> int:
    iterations = get_positive_int_from_env("PY_OPT_ITERATIONS", 20_000_000)
    parallel_chains = 4
    loop_trip_count = iterations // parallel_chains
    remainder = iterations % parallel_chains
    adds_per_iteration = 2

    context = prepare_thread_context(DEFAULT_AFFINITY_MASK)
    a0, b0 = 1, 1
    a1, b1 = 3, 5
    a2, b2 = 8, 13
    a3, b3 = 21, 34
    cpu_before = get_current_processor_number()
    counter_start = query_thread_cycle_time(context["thread"])

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

    counter_end = query_thread_cycle_time(context["thread"])
    cpu_after = get_current_processor_number()

    result = a0 ^ b0 ^ a1 ^ b1 ^ a2 ^ b2 ^ a3 ^ b3
    cycles = counter_end - counter_start
    total_adds = iterations * adds_per_iteration

    emit_record(
        {
            "implementation": "python_optimized",
            "pid": context["pid"],
            "tid": context["tid"],
            "iterations": iterations,
            "parallel_chains": parallel_chains,
            "loop_trip_count": loop_trip_count,
            "remainder": remainder,
            "requested_affinity_mask": context["requested_affinity_mask"],
            "previous_affinity_mask": context["previous_affinity_mask"],
            "priority_set": context["priority_set"],
            "thread_priority": context["thread_priority"],
            "cpu_before": cpu_before,
            "cpu_after": cpu_after,
            "timer_kind": "query_thread_cycle_time",
            "counter_start": counter_start,
            "counter_end": counter_end,
            "result": result,
            "cycles": cycles,
            "cycles/iteration": cycles / iterations,
            "cycles/add": cycles / total_adds,
        }
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
