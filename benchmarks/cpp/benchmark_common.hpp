#pragma once

#include <array>
#include <chrono>
#include <cstdint>
#include <fstream>
#include <sstream>
#include <string>
#include <thread>
#include <vector>

#if defined(_WIN32)
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#else
#include <pthread/qos.h>
#include <unistd.h>
#endif

namespace benchcpp {
struct CaseData {
    std::string case_id;
    std::uint64_t iterations = 0;
    std::uint64_t parallel_chains = 1;
    std::string priority_mode = "high";
    std::string affinity_mode = "single_core";
    bool warmup = false;
    std::uint64_t repeat_index = 1;
};

struct Context {
    std::uint64_t pid = 0;
    std::uint64_t tid = 0;
    std::string requested_priority_mode;
    std::string requested_affinity_mode;
    std::string applied_priority_mode;
    std::string applied_affinity_mode;
    std::string scheduler_notes;
};

struct ResultPayload {
    std::string implementation;
    std::string variant;
    CaseData case_data;
    Context context;
    std::uint64_t loop_trip_count = 0;
    std::uint64_t remainder = 0;
    std::uint64_t elapsed_ns = 0;
    double ns_per_iteration = 0.0;
    double ns_per_add = 0.0;
    std::uint64_t checksum = 0;
};

inline const std::array<std::array<std::uint64_t, 2>, 8>& seed_pairs() {
    static const std::array<std::array<std::uint64_t, 2>, 8> pairs = {{
        {1u, 1u},
        {3u, 5u},
        {8u, 13u},
        {21u, 34u},
        {55u, 89u},
        {144u, 233u},
        {377u, 610u},
        {987u, 1597u},
    }};
    return pairs;
}

inline std::string trim(const std::string& value) {
    std::size_t begin = value.find_first_not_of(" \t\r\n");
    if (begin == std::string::npos) {
        return "";
    }
    std::size_t end = value.find_last_not_of(" \t\r\n");
    return value.substr(begin, end - begin + 1);
}

inline CaseData load_case_file(int argc, char** argv) {
    if (argc != 3 || std::string(argv[1]) != "--case-file") {
        throw std::runtime_error("Usage: <binary> --case-file <path>");
    }
    std::ifstream handle(argv[2]);
    if (!handle) {
        throw std::runtime_error("Unable to open case file");
    }
    CaseData case_data;
    std::string line;
    while (std::getline(handle, line)) {
        auto separator = line.find('=');
        if (separator == std::string::npos) {
            continue;
        }
        std::string key = trim(line.substr(0, separator));
        std::string value = trim(line.substr(separator + 1));
        if (key == "case_id") {
            case_data.case_id = value;
        } else if (key == "iterations") {
            case_data.iterations = std::stoull(value);
        } else if (key == "parallel_chains") {
            case_data.parallel_chains = std::stoull(value);
        } else if (key == "priority_mode") {
            case_data.priority_mode = value;
        } else if (key == "affinity_mode") {
            case_data.affinity_mode = value;
        } else if (key == "warmup") {
            case_data.warmup = (value == "true");
        } else if (key == "repeat_index") {
            case_data.repeat_index = std::stoull(value);
        }
    }
    return case_data;
}

inline std::vector<std::array<std::uint64_t, 2>> extend_seed_pairs(std::size_t count) {
    std::vector<std::array<std::uint64_t, 2>> values;
    values.reserve(count);
    for (std::size_t index = 0; index < count && index < seed_pairs().size(); ++index) {
        values.push_back(seed_pairs()[index]);
    }
    while (values.size() < count) {
        std::uint64_t left = values[values.size() - 2][0] + values.back()[1];
        std::uint64_t right = left + values.back()[0];
        values.push_back({left, right});
    }
    return values;
}

inline Context prepare_context(const CaseData& case_data) {
    Context context;
#if defined(_WIN32)
    context.pid = static_cast<std::uint64_t>(GetCurrentProcessId());
    context.tid = static_cast<std::uint64_t>(GetCurrentThreadId());
#else
    context.pid = static_cast<std::uint64_t>(getpid());
    context.tid = static_cast<std::uint64_t>(std::hash<std::thread::id>{}(std::this_thread::get_id()));
#endif
    context.requested_priority_mode = case_data.priority_mode;
    context.requested_affinity_mode = case_data.affinity_mode;
#if defined(__APPLE__)
    if (case_data.priority_mode == "high") {
        pthread_set_qos_class_self_np(QOS_CLASS_USER_INTERACTIVE, 0);
        context.applied_priority_mode = "advisory_macos";
    } else {
        context.applied_priority_mode = "unsupported";
    }
    context.applied_affinity_mode = case_data.affinity_mode == "single_core" ? "advisory_macos" : "unchanged";
    context.scheduler_notes = "C++ benchmark uses controller-side best effort scheduling only; macOS affinity remains advisory";
#else
    context.applied_priority_mode = "unsupported";
    context.applied_affinity_mode = case_data.affinity_mode == "single_core" ? "unsupported" : "unchanged";
    context.scheduler_notes = "C++ benchmark uses controller-side best effort scheduling only";
#endif
    return context;
}

inline std::uint64_t monotonic_ns() {
    return static_cast<std::uint64_t>(
        std::chrono::duration_cast<std::chrono::nanoseconds>(
            std::chrono::steady_clock::now().time_since_epoch()
        ).count()
    );
}

inline std::string escape_json(const std::string& value) {
    std::string escaped;
    escaped.reserve(value.size() + 8);
    for (char ch : value) {
        switch (ch) {
            case '\\': escaped += "\\\\"; break;
            case '"': escaped += "\\\""; break;
            case '\n': escaped += "\\n"; break;
            case '\r': escaped += "\\r"; break;
            case '\t': escaped += "\\t"; break;
            default: escaped += ch; break;
        }
    }
    return escaped;
}

inline std::string host_os() {
#if defined(__APPLE__)
    return "macos";
#elif defined(_WIN32)
    return "windows";
#else
    return "unknown";
#endif
}

inline std::string host_arch() {
#if defined(__aarch64__) || defined(__arm64__) || defined(_M_ARM64)
    return "arm64";
#elif defined(__x86_64__) || defined(_M_X64) || defined(__amd64__)
    return "x64";
#else
    return "unknown";
#endif
}

inline std::string to_json(const ResultPayload& payload) {
    std::ostringstream builder;
    builder << '{'
            << "\"schema_version\":1"
            << ",\"implementation\":\"" << escape_json(payload.implementation) << '"'
            << ",\"language\":\"cpp\""
            << ",\"variant\":\"" << escape_json(payload.variant) << '"'
            << ",\"case_id\":\"" << escape_json(payload.case_data.case_id) << '"'
            << ",\"warmup\":" << (payload.case_data.warmup ? "true" : "false")
            << ",\"repeat_index\":" << payload.case_data.repeat_index
            << ",\"iterations\":" << payload.case_data.iterations
            << ",\"parallel_chains\":" << payload.case_data.parallel_chains
            << ",\"loop_trip_count\":" << payload.loop_trip_count
            << ",\"remainder\":" << payload.remainder
            << ",\"timer_kind\":\"steady_clock_ns\""
            << ",\"elapsed_ns\":" << payload.elapsed_ns
            << ",\"ns_per_iteration\":" << payload.ns_per_iteration
            << ",\"ns_per_add\":" << payload.ns_per_add
            << ",\"result_checksum\":\"" << payload.checksum << '"'
            << ",\"host_os\":\"" << host_os() << '"'
            << ",\"host_arch\":\"" << host_arch() << '"'
            << ",\"pid\":" << payload.context.pid
            << ",\"tid\":" << payload.context.tid
            << ",\"requested_priority_mode\":\"" << escape_json(payload.context.requested_priority_mode) << '"'
            << ",\"requested_affinity_mode\":\"" << escape_json(payload.context.requested_affinity_mode) << '"'
            << ",\"applied_priority_mode\":\"" << escape_json(payload.context.applied_priority_mode) << '"'
            << ",\"applied_affinity_mode\":\"" << escape_json(payload.context.applied_affinity_mode) << '"'
            << ",\"scheduler_notes\":\"" << escape_json(payload.context.scheduler_notes) << '"'
            << ",\"runtime_name\":\"cpp\""
            << ",\"platform_extras\":{}"
            << '}';
    return builder.str();
}
}  // namespace benchcpp
