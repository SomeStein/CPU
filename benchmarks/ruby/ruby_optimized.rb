require_relative "benchmark_common"

def run_generic(loop_trip_count, remainder, parallel_chains)
  states = BenchmarkCommon.extend_seed_pairs(parallel_chains).map(&:dup)
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
  states.reduce(0) { |memo, (left, right)| memo ^ left ^ right }
end

def run_four(loop_trip_count, remainder)
  (a0, b0), (a1, b1), (a2, b2), (a3, b3) = BenchmarkCommon.extend_seed_pairs(4)
  loop_trip_count.times do
    a0 = BenchmarkCommon.mask_u64(a0 + b0)
    b0 = BenchmarkCommon.mask_u64(a0 + b0)
    a1 = BenchmarkCommon.mask_u64(a1 + b1)
    b1 = BenchmarkCommon.mask_u64(a1 + b1)
    a2 = BenchmarkCommon.mask_u64(a2 + b2)
    b2 = BenchmarkCommon.mask_u64(a2 + b2)
    a3 = BenchmarkCommon.mask_u64(a3 + b3)
    b3 = BenchmarkCommon.mask_u64(a3 + b3)
  end
  remainder.times do
    a0 = BenchmarkCommon.mask_u64(a0 + b0)
    b0 = BenchmarkCommon.mask_u64(a0 + b0)
  end
  a0 ^ b0 ^ a1 ^ b1 ^ a2 ^ b2 ^ a3 ^ b3
end

def run_eight(loop_trip_count, remainder)
  values = BenchmarkCommon.extend_seed_pairs(8).flatten
  loop_trip_count.times do
    0.step(14, 2) do |index|
      values[index] = BenchmarkCommon.mask_u64(values[index] + values[index + 1])
      values[index + 1] = BenchmarkCommon.mask_u64(values[index] + values[index + 1])
    end
  end
  remainder.times do
    values[0] = BenchmarkCommon.mask_u64(values[0] + values[1])
    values[1] = BenchmarkCommon.mask_u64(values[0] + values[1])
  end
  values.reduce(0) { |memo, value| memo ^ value }
end

case_data = BenchmarkCommon.load_case_file(ARGV)
loop_trip_count = case_data["iterations"] / case_data["parallel_chains"]
remainder = case_data["iterations"] % case_data["parallel_chains"]
context = BenchmarkCommon.prepare_context(case_data)

start = BenchmarkCommon.monotonic_ns
checksum =
  case case_data["parallel_chains"]
  when 4
    run_four(loop_trip_count, remainder)
  when 8
    run_eight(loop_trip_count, remainder)
  else
    run_generic(loop_trip_count, remainder, case_data["parallel_chains"])
  end
finish = BenchmarkCommon.monotonic_ns

BenchmarkCommon.emit_result(
  BenchmarkCommon.build_result(
    implementation: "ruby_optimized",
    variant: "optimized",
    case_data: case_data,
    context: context,
    elapsed_ns: finish - start,
    loop_trip_count: loop_trip_count,
    remainder: remainder,
    checksum: checksum,
  ),
)
