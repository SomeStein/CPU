#include <errno.h>
#include <inttypes.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#if defined(_WIN32)
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#else
#include <pthread.h>
#include <time.h>
#include <unistd.h>
#endif

#define MAX_LINE_LENGTH 256
#define MAX_CHAINS 64

typedef struct {
    char implementation[64];
    char case_id[64];
    uint64_t iterations;
    uint64_t parallel_chains;
    char priority_mode[32];
    char affinity_mode[32];
    bool warmup;
    uint64_t repeat_index;
} CaseData;

typedef struct {
    uint64_t pid;
    uint64_t tid;
    const char *requested_priority_mode;
    const char *requested_affinity_mode;
    const char *applied_priority_mode;
    const char *applied_affinity_mode;
    const char *scheduler_notes;
    uint64_t extra_value;
    const char *timer_kind;
} Context;

static const uint64_t SEEDS[][2] = {
    {1u, 1u},
    {3u, 5u},
    {8u, 13u},
    {21u, 34u},
    {55u, 89u},
    {144u, 233u},
    {377u, 610u},
    {987u, 1597u},
};

static uint64_t mask_u64(uint64_t value) {
    return value;
}

static void trim_trailing_newline(char *value) {
    size_t length = strlen(value);
    while (length > 0 && (value[length - 1] == '\n' || value[length - 1] == '\r')) {
        value[length - 1] = '\0';
        length -= 1;
    }
}

static void copy_value(char *destination, size_t destination_size, const char *value) {
    snprintf(destination, destination_size, "%s", value);
}

static bool parse_case_file(const char *path, CaseData *case_data) {
    FILE *handle = fopen(path, "r");
    char line[MAX_LINE_LENGTH];
    if (handle == NULL) {
        return false;
    }

    memset(case_data, 0, sizeof(*case_data));
    copy_value(case_data->implementation, sizeof(case_data->implementation), "c_native");
    copy_value(case_data->priority_mode, sizeof(case_data->priority_mode), "high");
    copy_value(case_data->affinity_mode, sizeof(case_data->affinity_mode), "single_core");

    while (fgets(line, sizeof(line), handle) != NULL) {
        char *separator = strchr(line, '=');
        if (separator == NULL) {
            continue;
        }
        *separator = '\0';
        char *key = line;
        char *value = separator + 1;
        trim_trailing_newline(value);

        if (strcmp(key, "implementation") == 0) {
            copy_value(case_data->implementation, sizeof(case_data->implementation), value);
        } else if (strcmp(key, "case_id") == 0) {
            copy_value(case_data->case_id, sizeof(case_data->case_id), value);
        } else if (strcmp(key, "iterations") == 0) {
            case_data->iterations = strtoull(value, NULL, 10);
        } else if (strcmp(key, "parallel_chains") == 0) {
            case_data->parallel_chains = strtoull(value, NULL, 10);
        } else if (strcmp(key, "priority_mode") == 0) {
            copy_value(case_data->priority_mode, sizeof(case_data->priority_mode), value);
        } else if (strcmp(key, "affinity_mode") == 0) {
            copy_value(case_data->affinity_mode, sizeof(case_data->affinity_mode), value);
        } else if (strcmp(key, "warmup") == 0) {
            case_data->warmup = strcmp(value, "true") == 0;
        } else if (strcmp(key, "repeat_index") == 0) {
            case_data->repeat_index = strtoull(value, NULL, 10);
        }
    }

    fclose(handle);
    return case_data->iterations > 0 && case_data->parallel_chains > 0 && case_data->parallel_chains <= MAX_CHAINS;
}

static void seed_pairs(uint64_t states[][2], uint64_t count) {
    uint64_t index = 0;
    for (; index < count && index < (sizeof(SEEDS) / sizeof(SEEDS[0])); ++index) {
        states[index][0] = SEEDS[index][0];
        states[index][1] = SEEDS[index][1];
    }
    while (index < count) {
        states[index][0] = mask_u64(states[index - 1][0] + states[index - 1][1]);
        states[index][1] = mask_u64(states[index][0] + states[index - 1][0]);
        index += 1;
    }
}

#if defined(_WIN32)
static uint64_t monotonic_ns(Context *context) {
    LARGE_INTEGER counter;
    LARGE_INTEGER frequency;
    QueryPerformanceCounter(&counter);
    QueryPerformanceFrequency(&frequency);
    context->extra_value = (uint64_t)frequency.QuadPart;
    return (uint64_t)((counter.QuadPart * 1000000000ull) / frequency.QuadPart);
}

static Context prepare_context(const CaseData *case_data) {
    Context context;
    HANDLE thread = GetCurrentThread();
    context.pid = (uint64_t)GetCurrentProcessId();
    context.tid = (uint64_t)GetCurrentThreadId();
    context.requested_priority_mode = case_data->priority_mode;
    context.requested_affinity_mode = case_data->affinity_mode;
    context.applied_priority_mode = "unchanged";
    context.applied_affinity_mode = "unchanged";
    context.scheduler_notes = "";
    context.extra_value = 0;
    context.timer_kind = "qpc_ns";

    if (strcmp(case_data->priority_mode, "high") == 0) {
        if (SetThreadPriority(thread, THREAD_PRIORITY_HIGHEST)) {
            context.applied_priority_mode = "high";
        } else {
            context.applied_priority_mode = "failed";
            context.scheduler_notes = "SetThreadPriority failed";
        }
    }

    if (strcmp(case_data->affinity_mode, "single_core") == 0) {
        DWORD_PTR previous_mask = SetThreadAffinityMask(thread, 1);
        if (previous_mask != 0) {
            context.applied_affinity_mode = "single_core";
        } else {
            context.applied_affinity_mode = "failed";
            context.scheduler_notes = "SetThreadAffinityMask failed";
        }
    }
    return context;
}
#else
static uint64_t monotonic_ns(Context *context) {
    struct timespec ts;
#if defined(CLOCK_MONOTONIC_RAW)
    const clockid_t clock_id = CLOCK_MONOTONIC_RAW;
#else
    const clockid_t clock_id = CLOCK_MONOTONIC;
#endif
    (void)context;
    clock_gettime(clock_id, &ts);
    return ((uint64_t)ts.tv_sec * 1000000000ull) + (uint64_t)ts.tv_nsec;
}

static Context prepare_context(const CaseData *case_data) {
    Context context;
    uint64_t tid = (uint64_t)(uintptr_t)pthread_self();
    context.pid = (uint64_t)getpid();
    context.tid = tid;
    context.requested_priority_mode = case_data->priority_mode;
    context.requested_affinity_mode = case_data->affinity_mode;
    context.applied_priority_mode = strcmp(case_data->priority_mode, "high") == 0 ? "unsupported" : "unchanged";
    context.applied_affinity_mode = strcmp(case_data->affinity_mode, "single_core") == 0 ? "unsupported" : "unchanged";
    context.scheduler_notes = "best-effort scheduler controls unavailable on this host";
    context.extra_value = 0;
    context.timer_kind = "clock_gettime_ns";
    return context;
}
#endif

static uint64_t run_generic(uint64_t loop_trip_count, uint64_t remainder, uint64_t parallel_chains) {
    uint64_t states[MAX_CHAINS][2];
    uint64_t checksum = 0;
    seed_pairs(states, parallel_chains);

    for (uint64_t outer = 0; outer < loop_trip_count; ++outer) {
        for (uint64_t index = 0; index < parallel_chains; ++index) {
            states[index][0] = mask_u64(states[index][0] + states[index][1]);
            states[index][1] = mask_u64(states[index][0] + states[index][1]);
        }
    }

    for (uint64_t index = 0; index < remainder; ++index) {
        states[0][0] = mask_u64(states[0][0] + states[0][1]);
        states[0][1] = mask_u64(states[0][0] + states[0][1]);
    }

    for (uint64_t index = 0; index < parallel_chains; ++index) {
        checksum ^= states[index][0] ^ states[index][1];
    }
    return checksum;
}

static uint64_t run_four(uint64_t loop_trip_count, uint64_t remainder) {
    uint64_t a0 = SEEDS[0][0], b0 = SEEDS[0][1];
    uint64_t a1 = SEEDS[1][0], b1 = SEEDS[1][1];
    uint64_t a2 = SEEDS[2][0], b2 = SEEDS[2][1];
    uint64_t a3 = SEEDS[3][0], b3 = SEEDS[3][1];

    for (uint64_t index = 0; index < loop_trip_count; ++index) {
        a0 = mask_u64(a0 + b0);
        b0 = mask_u64(a0 + b0);
        a1 = mask_u64(a1 + b1);
        b1 = mask_u64(a1 + b1);
        a2 = mask_u64(a2 + b2);
        b2 = mask_u64(a2 + b2);
        a3 = mask_u64(a3 + b3);
        b3 = mask_u64(a3 + b3);
    }

    for (uint64_t index = 0; index < remainder; ++index) {
        a0 = mask_u64(a0 + b0);
        b0 = mask_u64(a0 + b0);
    }

    return a0 ^ b0 ^ a1 ^ b1 ^ a2 ^ b2 ^ a3 ^ b3;
}

static uint64_t run_eight(uint64_t loop_trip_count, uint64_t remainder) {
    uint64_t states[8][2];
    uint64_t checksum = 0;
    seed_pairs(states, 8);

    for (uint64_t outer = 0; outer < loop_trip_count; ++outer) {
        for (uint64_t index = 0; index < 8; ++index) {
            states[index][0] = mask_u64(states[index][0] + states[index][1]);
            states[index][1] = mask_u64(states[index][0] + states[index][1]);
        }
    }

    for (uint64_t index = 0; index < remainder; ++index) {
        states[0][0] = mask_u64(states[0][0] + states[0][1]);
        states[0][1] = mask_u64(states[0][0] + states[0][1]);
    }

    for (uint64_t index = 0; index < 8; ++index) {
        checksum ^= states[index][0] ^ states[index][1];
    }
    return checksum;
}

int main(int argc, char **argv) {
    CaseData case_data;
    Context context;
    uint64_t loop_trip_count;
    uint64_t remainder;
    uint64_t checksum;
    uint64_t start_ns;
    uint64_t end_ns;
    uint64_t elapsed_ns;
    double ns_per_iteration;
    double ns_per_add;

    if (argc != 3 || strcmp(argv[1], "--case-file") != 0) {
        fprintf(stderr, "Usage: c_native --case-file <path>\n");
        return 1;
    }

    if (!parse_case_file(argv[2], &case_data)) {
        fprintf(stderr, "Invalid case file: %s\n", argv[2]);
        return 1;
    }

    context = prepare_context(&case_data);
    loop_trip_count = case_data.iterations / case_data.parallel_chains;
    remainder = case_data.iterations % case_data.parallel_chains;

    start_ns = monotonic_ns(&context);
    if (case_data.parallel_chains == 4) {
        checksum = run_four(loop_trip_count, remainder);
    } else if (case_data.parallel_chains == 8) {
        checksum = run_eight(loop_trip_count, remainder);
    } else {
        checksum = run_generic(loop_trip_count, remainder, case_data.parallel_chains);
    }
    end_ns = monotonic_ns(&context);

    elapsed_ns = end_ns - start_ns;
    ns_per_iteration = case_data.iterations ? (double)elapsed_ns / (double)case_data.iterations : 0.0;
    ns_per_add = case_data.iterations ? (double)elapsed_ns / (double)(case_data.iterations * 2u) : 0.0;

    printf(
        "{"
        "\"schema_version\":1,"
        "\"implementation\":\"c_native\","
        "\"language\":\"c\","
        "\"variant\":\"native\","
        "\"case_id\":\"%s\","
        "\"warmup\":%s,"
        "\"repeat_index\":%" PRIu64 ","
        "\"iterations\":%" PRIu64 ","
        "\"parallel_chains\":%" PRIu64 ","
        "\"loop_trip_count\":%" PRIu64 ","
        "\"remainder\":%" PRIu64 ","
        "\"timer_kind\":\"%s\","
        "\"elapsed_ns\":%" PRIu64 ","
        "\"ns_per_iteration\":%.6f,"
        "\"ns_per_add\":%.6f,"
        "\"result_checksum\":\"%" PRIu64 "\","
        "\"host_os\":\"%s\","
        "\"host_arch\":\"%s\","
        "\"pid\":%" PRIu64 ","
        "\"tid\":%" PRIu64 ","
        "\"requested_priority_mode\":\"%s\","
        "\"requested_affinity_mode\":\"%s\","
        "\"applied_priority_mode\":\"%s\","
        "\"applied_affinity_mode\":\"%s\","
        "\"scheduler_notes\":\"%s\","
        "\"runtime_name\":\"c_native\","
        "\"platform_extras\":{\"counter_frequency\":%" PRIu64 "}"
        "}\n",
        case_data.case_id,
        case_data.warmup ? "true" : "false",
        case_data.repeat_index,
        case_data.iterations,
        case_data.parallel_chains,
        loop_trip_count,
        remainder,
        context.timer_kind,
        elapsed_ns,
        ns_per_iteration,
        ns_per_add,
        checksum,
#if defined(_WIN32)
        "windows",
        "x64",
#else
        "macos",
        "arm64",
#endif
        context.pid,
        context.tid,
        context.requested_priority_mode,
        context.requested_affinity_mode,
        context.applied_priority_mode,
        context.applied_affinity_mode,
        context.scheduler_notes,
        context.extra_value
    );

    return 0;
}
