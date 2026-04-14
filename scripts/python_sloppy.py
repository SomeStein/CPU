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
    iterations = get_positive_int_from_env("PY_SLOPPY_ITERATIONS", 2_000_000)
    parallel_chains = 1
    loop_trip_count = iterations
    remainder = 0
    adds_per_iteration = 2

    context = prepare_thread_context(DEFAULT_AFFINITY_MASK)
    state = [1, 1]
    cpu_before = get_current_processor_number()
    counter_start = query_thread_cycle_time(context["thread"])

    for _ in range(loop_trip_count):
        state[0] = mask_u64(state[0] + state[1])
        state[1] = mask_u64(state[0] + state[1])

    counter_end = query_thread_cycle_time(context["thread"])
    cpu_after = get_current_processor_number()

    result = state[0] ^ state[1]
    cycles = counter_end - counter_start
    total_adds = iterations * adds_per_iteration

    emit_record(
        {
            "implementation": "python_sloppy",
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
