#include <windows.h>
#include <intrin.h>
#include <stdint.h>
#include <stdio.h>

#if defined(_MSC_VER)
#pragma intrinsic(__rdtsc)
#endif

int main(void) {
    HANDLE thread = GetCurrentThread();
    DWORD pid = GetCurrentProcessId();
    DWORD tid = GetCurrentThreadId();
    DWORD_PTR requested_mask = 1ull << 0;
    DWORD_PTR previous_mask = SetThreadAffinityMask(thread, requested_mask);
    BOOL priority_ok = SetThreadPriority(thread, THREAD_PRIORITY_HIGHEST);
    int effective_priority = GetThreadPriority(thread);

    const uint64_t N = 10000000000ULL;
    const uint64_t parallel_chains = 4;
    const uint64_t adds_per_iteration = 2;
    const uint64_t loop_trip_count = N / parallel_chains;
    const uint64_t remainder = N % parallel_chains;
    volatile uint64_t sink = 0;

    uint64_t a0 = 1, b0 = 1;
    uint64_t a1 = 3, b1 = 5;
    uint64_t a2 = 8, b2 = 13;
    uint64_t a3 = 21, b3 = 34;
    DWORD cpu_before = GetCurrentProcessorNumber();

    // Collect diagnostics outside the timed section so the loop timing stays unchanged.
    // Four independent chains expose instruction-level parallelism and make sub-1
    // TSC cycles per logical iteration achievable on fast cores.
    uint64_t t1 = __rdtsc();

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

    uint64_t t2 = __rdtsc();
    DWORD cpu_after = GetCurrentProcessorNumber();

    sink = a0 ^ b0 ^ a1 ^ b1 ^ a2 ^ b2 ^ a3 ^ b3;

    uint64_t cycles = t2 - t1;
    uint64_t executed_iterations = loop_trip_count * parallel_chains + remainder;
    uint64_t total_adds = executed_iterations * adds_per_iteration;

    printf("pid = %lu\n", (unsigned long)pid);
    printf("tid = %lu\n", (unsigned long)tid);
    printf("iterations = %llu\n", (unsigned long long)executed_iterations);
    printf("parallel_chains = %llu\n", (unsigned long long)parallel_chains);
    printf("loop_trip_count = %llu\n", (unsigned long long)loop_trip_count);
    printf("remainder = %llu\n", (unsigned long long)remainder);
    printf("requested_affinity_mask = 0x%llx\n", (unsigned long long)requested_mask);
    printf("previous_affinity_mask = 0x%llx\n", (unsigned long long)previous_mask);
    printf("priority_set = %d\n", priority_ok ? 1 : 0);
    printf("thread_priority = %d\n", effective_priority);
    printf("cpu_before = %lu\n", (unsigned long)cpu_before);
    printf("cpu_after = %lu\n", (unsigned long)cpu_after);
    printf("tsc_start = %llu\n", (unsigned long long)t1);
    printf("tsc_end = %llu\n", (unsigned long long)t2);
    printf("result = %llu\n", (unsigned long long)sink);
    printf("cycles = %llu\n", (unsigned long long)cycles);
    printf("cycles/iteration = %.6f\n", (double)cycles / (double)executed_iterations);
    printf("cycles/add = %.6f\n", (double)cycles / (double)total_adds);

    return 0;
}
