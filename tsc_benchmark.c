#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <intrin.h>
#include <errno.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>

#if defined(_MSC_VER)
#pragma intrinsic(__rdtsc)
#endif

static uint64_t read_iterations_from_env(const char *name, uint64_t default_value) {
    const char *value = getenv(name);
    char *end = NULL;
    unsigned long long parsed = 0;

    if (value == NULL || *value == '\0') {
        return default_value;
    }

    errno = 0;
    parsed = strtoull(value, &end, 10);
    if (errno != 0 || end == value || *end != '\0' || parsed == 0) {
        return default_value;
    }

    return (uint64_t)parsed;
}

static void print_text_field(const char *key, const char *value) {
    printf("%s = %s\n", key, value);
}

static void print_u64_field(const char *key, uint64_t value) {
    printf("%s = %llu\n", key, (unsigned long long)value);
}

static void print_i32_field(const char *key, int value) {
    printf("%s = %d\n", key, value);
}

static void print_hex_field(const char *key, DWORD_PTR value) {
    printf("%s = 0x%llx\n", key, (unsigned long long)value);
}

static void print_double_field(const char *key, double value) {
    printf("%s = %.6f\n", key, value);
}

int main(void) {
    const uint64_t default_iterations = 10000000000ULL;
    const uint64_t parallel_chains = 4;
    const uint64_t adds_per_iteration = 2;
    const uint64_t requested_iterations = read_iterations_from_env("C_ITERATIONS", default_iterations);
    const uint64_t loop_trip_count = requested_iterations / parallel_chains;
    const uint64_t remainder = requested_iterations % parallel_chains;

    HANDLE thread = GetCurrentThread();
    DWORD pid = GetCurrentProcessId();
    DWORD tid = GetCurrentThreadId();
    DWORD_PTR requested_mask = 1ull << 0;
    DWORD_PTR previous_mask = SetThreadAffinityMask(thread, requested_mask);
    BOOL priority_ok = SetThreadPriority(thread, THREAD_PRIORITY_HIGHEST);
    int effective_priority = GetThreadPriority(thread);

    volatile uint64_t sink = 0;
    uint64_t a0 = 1, b0 = 1;
    uint64_t a1 = 3, b1 = 5;
    uint64_t a2 = 8, b2 = 13;
    uint64_t a3 = 21, b3 = 34;
    DWORD cpu_before = GetCurrentProcessorNumber();

    uint64_t counter_start = __rdtsc();

    for (uint64_t i = 0; i < loop_trip_count; ++i) {
        a0 = a0 + b0;
        b0 = a0 + b0;
        a1 = a1 + b1;
        b1 = a1 + b1;
        a2 = a2 + b2;
        b2 = a2 + b2;
        a3 = a3 + b3;
        b3 = a3 + b3;
    }

    for (uint64_t i = 0; i < remainder; ++i) {
        a0 = a0 + b0;
        b0 = a0 + b0;
    }

    uint64_t counter_end = __rdtsc();
    DWORD cpu_after = GetCurrentProcessorNumber();

    sink = a0 ^ b0 ^ a1 ^ b1 ^ a2 ^ b2 ^ a3 ^ b3;

    {
        uint64_t cycles = counter_end - counter_start;
        uint64_t executed_iterations = loop_trip_count * parallel_chains + remainder;
        uint64_t total_adds = executed_iterations * adds_per_iteration;

        print_text_field("implementation", "c_native");
        print_u64_field("pid", pid);
        print_u64_field("tid", tid);
        print_u64_field("iterations", executed_iterations);
        print_u64_field("parallel_chains", parallel_chains);
        print_u64_field("loop_trip_count", loop_trip_count);
        print_u64_field("remainder", remainder);
        print_hex_field("requested_affinity_mask", requested_mask);
        print_hex_field("previous_affinity_mask", previous_mask);
        print_i32_field("priority_set", priority_ok ? 1 : 0);
        print_i32_field("thread_priority", effective_priority);
        print_u64_field("cpu_before", cpu_before);
        print_u64_field("cpu_after", cpu_after);
        print_text_field("timer_kind", "rdtsc");
        print_u64_field("counter_start", counter_start);
        print_u64_field("counter_end", counter_end);
        print_u64_field("result", sink);
        print_u64_field("cycles", cycles);
        print_double_field("cycles/iteration", (double)cycles / (double)executed_iterations);
        print_double_field("cycles/add", (double)cycles / (double)total_adds);
    }

    return 0;
}
