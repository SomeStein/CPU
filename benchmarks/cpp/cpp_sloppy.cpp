#include "benchmark_common.hpp"

#include <iostream>

int main(int argc, char** argv) {
    using namespace benchcpp;
    CaseData case_data = load_case_file(argc, argv);
    Context context = prepare_context(case_data);
    std::uint64_t loop_trip_count = case_data.iterations / case_data.parallel_chains;
    std::uint64_t remainder = case_data.iterations % case_data.parallel_chains;
    auto states = extend_seed_pairs(static_cast<std::size_t>(case_data.parallel_chains));

    std::uint64_t start = monotonic_ns();
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
    std::uint64_t finish = monotonic_ns();

    std::uint64_t checksum = 0;
    for (const auto& state : states) {
        checksum ^= state[0] ^ state[1];
    }

    ResultPayload payload;
    payload.implementation = "cpp_sloppy";
    payload.variant = "sloppy";
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
