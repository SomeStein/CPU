require "json"

module BenchmarkCommon
  MASK64 = (1 << 64) - 1
  SEED_PAIRS = [
    [1, 1],
    [3, 5],
    [8, 13],
    [21, 34],
    [55, 89],
    [144, 233],
    [377, 610],
    [987, 1597],
  ].freeze

  module_function

  def mask_u64(value)
    value & MASK64
  end

  def extend_seed_pairs(count)
    pairs = SEED_PAIRS.map(&:dup)
    while pairs.length < count
      left = mask_u64(pairs[-2][0] + pairs[-1][1])
      right = mask_u64(left + pairs[-1][0])
      pairs << [left, right]
    end
    pairs.first(count)
  end

  def load_case_file(argv)
    raise "Usage: ruby <script> --case-file <path>" unless argv.length == 2 && argv[0] == "--case-file"

    parsed = {}
    File.readlines(argv[1], chomp: true).each do |line|
      next if line.empty? || line.start_with?("#") || !line.include?("=")

      key, value = line.split("=", 2)
      parsed[key.strip] = value.strip
    end
    {
      "run_id" => parsed.fetch("run_id", ""),
      "profile_id" => parsed.fetch("profile_id", ""),
      "implementation" => parsed.fetch("implementation", ""),
      "case_id" => parsed.fetch("case_id", ""),
      "iterations" => parsed.fetch("iterations", "0").to_i,
      "parallel_chains" => parsed.fetch("parallel_chains", "1").to_i,
      "priority_mode" => parsed.fetch("priority_mode", "high"),
      "affinity_mode" => parsed.fetch("affinity_mode", "single_core"),
      "timer_mode" => parsed.fetch("timer_mode", "monotonic_ns"),
      "warmup" => parsed.fetch("warmup", "false") == "true",
      "repeat_index" => parsed.fetch("repeat_index", "1").to_i,
    }
  end

  def host_os
    return "macos" if RUBY_PLATFORM.include?("darwin")
    return "windows" if RUBY_PLATFORM.match?(/mswin|mingw|cygwin/)

    RUBY_PLATFORM
  end

  def host_arch
    return "arm64" if RUBY_PLATFORM.include?("arm64") || RUBY_PLATFORM.include?("aarch64")
    return "x64" if RUBY_PLATFORM.include?("x86_64") || RUBY_PLATFORM.include?("amd64")

    RUBY_PLATFORM
  end

  def prepare_context(case_data)
    is_macos = host_os == "macos"
    {
      "pid" => Process.pid,
      "tid" => 0,
      "requested_priority_mode" => case_data["priority_mode"],
      "requested_affinity_mode" => case_data["affinity_mode"],
      "applied_priority_mode" => case_data["priority_mode"] == "high" && is_macos ? "advisory_macos" : "unsupported",
      "applied_affinity_mode" => case_data["affinity_mode"] == "single_core" ? (is_macos ? "advisory_macos" : "unsupported") : "unchanged",
      "scheduler_notes" => is_macos ? "Ruby runtime uses controller-side best effort scheduling only; macOS affinity remains advisory" : "Ruby runtime uses controller-side best effort scheduling only",
    }
  end

  def monotonic_ns
    Process.clock_gettime(Process::CLOCK_MONOTONIC, :nanosecond)
  end

  def emit_result(payload)
    puts(JSON.generate(payload))
  end

  def build_result(implementation:, variant:, case_data:, context:, elapsed_ns:, loop_trip_count:, remainder:, checksum:)
    total_adds = case_data["iterations"] * 2
    {
      "schema_version" => 1,
      "implementation" => implementation,
      "language" => "ruby",
      "variant" => variant,
      "case_id" => case_data["case_id"],
      "warmup" => case_data["warmup"],
      "repeat_index" => case_data["repeat_index"],
      "iterations" => case_data["iterations"],
      "parallel_chains" => case_data["parallel_chains"],
      "loop_trip_count" => loop_trip_count,
      "remainder" => remainder,
      "timer_kind" => "clock_gettime_ns",
      "elapsed_ns" => elapsed_ns,
      "ns_per_iteration" => case_data["iterations"].zero? ? 0.0 : elapsed_ns.to_f / case_data["iterations"],
      "ns_per_add" => total_adds.zero? ? 0.0 : elapsed_ns.to_f / total_adds,
      "result_checksum" => checksum.to_s,
      "host_os" => host_os,
      "host_arch" => host_arch,
      "pid" => context["pid"],
      "tid" => context["tid"],
      "requested_priority_mode" => context["requested_priority_mode"],
      "requested_affinity_mode" => context["requested_affinity_mode"],
      "applied_priority_mode" => context["applied_priority_mode"],
      "applied_affinity_mode" => context["applied_affinity_mode"],
      "scheduler_notes" => context["scheduler_notes"],
      "runtime_name" => "ruby",
      "platform_extras" => {},
    }
  end
end
