#include "benchmark_common.hpp"

#include <array>
#include <iostream>

namespace {
using namespace benchcpp;

std::uint64_t run_generic(std::uint64_t loop_trip_count, std::uint64_t remainder, std::uint64_t parallel_chains) {
    auto states = extend_seed_pairs(static_cast<std::size_t>(parallel_chains));
    for (std::uint64_t outer = 0; outer < loop_trip_count; ++outer) {
        for (auto& state : states) {
            state[0] = state[0] + state[1];
            state[1] = state[0] + state[1];
        }
    }
    for (std::uint64_t index = 0; index < remainder; ++index) {
        states[0][0] = states[0][0] + states[0][1];
        states[0][1] = states[0][0] + states[0][1];
    }
    std::uint64_t checksum = 0;
    for (const auto& state : states) {
        checksum ^= state[0] ^ state[1];
    }
    return checksum;
}

std::uint64_t run_four(std::uint64_t loop_trip_count, std::uint64_t remainder) {
    auto seeds = extend_seed_pairs(4);
    auto [a0, b0] = seeds[0];
    auto [a1, b1] = seeds[1];
    auto [a2, b2] = seeds[2];
    auto [a3, b3] = seeds[3];
    for (std::uint64_t index = 0; index < loop_trip_count; ++index) {
        a0 = a0 + b0; b0 = a0 + b0;
        a1 = a1 + b1; b1 = a1 + b1;
        a2 = a2 + b2; b2 = a2 + b2;
        a3 = a3 + b3; b3 = a3 + b3;
    }
    for (std::uint64_t index = 0; index < remainder; ++index) {
        a0 = a0 + b0;
        b0 = a0 + b0;
    }
    return a0 ^ b0 ^ a1 ^ b1 ^ a2 ^ b2 ^ a3 ^ b3;
}

std::uint64_t run_eight(std::uint64_t loop_trip_count, std::uint64_t remainder) {
    auto seeds = extend_seed_pairs(8);
    std::array<std::uint64_t, 16> values{};
    for (std::size_t index = 0; index < 8; ++index) {
        values[index * 2] = seeds[index][0];
        values[index * 2 + 1] = seeds[index][1];
    }
    for (std::uint64_t outer = 0; outer < loop_trip_count; ++outer) {
        for (std::size_t index = 0; index < values.size(); index += 2) {
            values[index] = values[index] + values[index + 1];
            values[index + 1] = values[index] + values[index + 1];
        }
    }
    for (std::uint64_t index = 0; index < remainder; ++index) {
        values[0] = values[0] + values[1];
        values[1] = values[0] + values[1];
    }
    std::uint64_t checksum = 0;
    for (std::uint64_t value : values) {
        checksum ^= value;
    }
    return checksum;
}
}  // namespace

int main(int argc, char** argv) {
    using namespace benchcpp;
    CaseData case_data = load_case_file(argc, argv);
    Context context = prepare_context(case_data);
    std::uint64_t loop_trip_count = case_data.iterations / case_data.parallel_chains;
    std::uint64_t remainder = case_data.iterations % case_data.parallel_chains;

    std::uint64_t start = monotonic_ns();
    std::uint64_t checksum = case_data.parallel_chains == 4 ? run_four(loop_trip_count, remainder)
        : case_data.parallel_chains == 8 ? run_eight(loop_trip_count, remainder)
        : run_generic(loop_trip_count, remainder, case_data.parallel_chains);
    std::uint64_t finish = monotonic_ns();

    ResultPayload payload;
    payload.implementation = "cpp_optimized";
    payload.variant = "optimized";
    payload.case_data = case_data;
    payload.context = context;
    payload.loop_trip_count = loop_trip_count;
    payload.remainder = remainder;
    payload.elapsed_ns = finish - start;
    payload.ns_per_iteration = case_data.iterations == 0 ? 0.0 : static_cast<double>(payload.elapsed_ns) / static_cast<double>(case_data.iterations);
    payload.ns_per_add = case_data.iterations == 0 ? 0.0 : static_cast<double>(payload.elapsed_ns) / static_cast<double>(case_data.iterations * 2);
    payload.checksum = checksum;
    std::cout << to_json(payload) << '\n';
    return 0;
}
