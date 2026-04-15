require_relative "benchmark_common"

case_data = BenchmarkCommon.load_case_file(ARGV)
loop_trip_count = case_data["iterations"] / case_data["parallel_chains"]
remainder = case_data["iterations"] % case_data["parallel_chains"]
states = BenchmarkCommon.extend_seed_pairs(case_data["parallel_chains"]).map(&:dup)
context = BenchmarkCommon.prepare_context(case_data)

start = BenchmarkCommon.monotonic_ns
loop_trip_count.times do
  states.each do |state|
    state[0] = BenchmarkCommon.mask_u64(state[0] + state[1])
    state[1] = BenchmarkCommon.mask_u64(state[0] + state[1])
  end
end
remainder.times do
  states[0][0] = BenchmarkCommon.mask_u64(states[0][0] + states[0][1])
  states[0][1] = BenchmarkCommon.mask_u64(states[0][0] + states[0][1])
end
finish = BenchmarkCommon.monotonic_ns

checksum = states.reduce(0) { |memo, (left, right)| memo ^ left ^ right }
BenchmarkCommon.emit_result(
  BenchmarkCommon.build_result(
    implementation: "ruby_sloppy",
    variant: "sloppy",
    case_data: case_data,
    context: context,
    elapsed_ns: finish - start,
    loop_trip_count: loop_trip_count,
    remainder: remainder,
    checksum: checksum,
  ),
)
