use std::collections::HashMap;
use std::fs;
use std::time::Instant;

#[derive(Clone)]
struct CaseData {
    case_id: String,
    iterations: u64,
    parallel_chains: usize,
    priority_mode: String,
    affinity_mode: String,
    warmup: bool,
    repeat_index: u64,
}

fn main() {
    let case_data = load_case(std::env::args().collect());
    let loop_trip_count = case_data.iterations / case_data.parallel_chains as u64;
    let remainder = case_data.iterations % case_data.parallel_chains as u64;
    let started = Instant::now();
    let checksum = match case_data.parallel_chains {
        4 => run_four(loop_trip_count, remainder),
        8 => run_eight(loop_trip_count, remainder),
        _ => run_generic(loop_trip_count, remainder, case_data.parallel_chains),
    };
    let elapsed_ns = started.elapsed().as_nanos() as u64;
    println!(
        "{}",
        build_result(
            "rust",
            "default",
            &case_data,
            loop_trip_count,
            remainder,
            elapsed_ns,
            checksum
        )
    );
}

fn load_case(args: Vec<String>) -> CaseData {
    if args.len() != 3 || args[1] != "--case-file" {
        panic!("Usage: <binary> --case-file <path>");
    }
    let mut parsed: HashMap<String, String> = HashMap::new();
    let content = fs::read_to_string(&args[2]).expect("Unable to read case file");
    for line in content.lines() {
        if line.is_empty() || line.starts_with('#') || !line.contains('=') {
            continue;
        }
        let mut parts = line.splitn(2, '=');
        let key = parts.next().unwrap().trim().to_string();
        let value = parts.next().unwrap_or("").trim().to_string();
        parsed.insert(key, value);
    }
    CaseData {
        case_id: parsed.get("case_id").cloned().unwrap_or_default(),
        iterations: parsed.get("iterations").and_then(|value| value.parse().ok()).unwrap_or(0),
        parallel_chains: parsed.get("parallel_chains").and_then(|value| value.parse().ok()).unwrap_or(1),
        priority_mode: parsed.get("priority_mode").cloned().unwrap_or_else(|| "high".to_string()),
        affinity_mode: parsed.get("affinity_mode").cloned().unwrap_or_else(|| "single_core".to_string()),
        warmup: parsed.get("warmup").map(|value| value == "true").unwrap_or(false),
        repeat_index: parsed.get("repeat_index").and_then(|value| value.parse().ok()).unwrap_or(1),
    }
}

fn extend_seed_pairs(count: usize) -> Vec<(u64, u64)> {
    let mut pairs = vec![
        (1, 1),
        (3, 5),
        (8, 13),
        (21, 34),
        (55, 89),
        (144, 233),
        (377, 610),
        (987, 1597),
    ];
    while pairs.len() < count {
        let left = pairs[pairs.len() - 2].0.wrapping_add(pairs[pairs.len() - 1].1);
        let right = left.wrapping_add(pairs[pairs.len() - 1].0);
        pairs.push((left, right));
    }
    pairs.truncate(count);
    pairs
}

fn run_generic(loop_trip_count: u64, remainder: u64, parallel_chains: usize) -> u64 {
    let mut states = extend_seed_pairs(parallel_chains);
    for _ in 0..loop_trip_count {
        for state in &mut states {
            state.0 = state.0.wrapping_add(state.1);
            state.1 = state.0.wrapping_add(state.1);
        }
    }
    for _ in 0..remainder {
        states[0].0 = states[0].0.wrapping_add(states[0].1);
        states[0].1 = states[0].0.wrapping_add(states[0].1);
    }
    states.iter().fold(0u64, |memo, state| memo ^ state.0 ^ state.1)
}

fn run_four(loop_trip_count: u64, remainder: u64) -> u64 {
    let seeds = extend_seed_pairs(4);
    let (mut a0, mut b0) = seeds[0];
    let (mut a1, mut b1) = seeds[1];
    let (mut a2, mut b2) = seeds[2];
    let (mut a3, mut b3) = seeds[3];
    for _ in 0..loop_trip_count {
        a0 = a0.wrapping_add(b0); b0 = a0.wrapping_add(b0);
        a1 = a1.wrapping_add(b1); b1 = a1.wrapping_add(b1);
        a2 = a2.wrapping_add(b2); b2 = a2.wrapping_add(b2);
        a3 = a3.wrapping_add(b3); b3 = a3.wrapping_add(b3);
    }
    for _ in 0..remainder {
        a0 = a0.wrapping_add(b0);
        b0 = a0.wrapping_add(b0);
    }
    a0 ^ b0 ^ a1 ^ b1 ^ a2 ^ b2 ^ a3 ^ b3
}

fn run_eight(loop_trip_count: u64, remainder: u64) -> u64 {
    let seeds = extend_seed_pairs(8);
    let mut values = [0u64; 16];
    for (index, state) in seeds.iter().enumerate() {
        values[index * 2] = state.0;
        values[index * 2 + 1] = state.1;
    }
    for _ in 0..loop_trip_count {
        for index in (0..16).step_by(2) {
            values[index] = values[index].wrapping_add(values[index + 1]);
            values[index + 1] = values[index].wrapping_add(values[index + 1]);
        }
    }
    for _ in 0..remainder {
        values[0] = values[0].wrapping_add(values[1]);
        values[1] = values[0].wrapping_add(values[1]);
    }
    values.iter().fold(0u64, |memo, value| memo ^ *value)
}

fn build_result(
    implementation: &str,
    variant: &str,
    case_data: &CaseData,
    loop_trip_count: u64,
    remainder: u64,
    elapsed_ns: u64,
    checksum: u64,
) -> String {
    let total_adds = case_data.iterations * 2;
    let host_os = if cfg!(target_os = "macos") { "macos" } else if cfg!(target_os = "windows") { "windows" } else { "unknown" };
    let host_arch = if cfg!(target_arch = "aarch64") { "arm64" } else if cfg!(target_arch = "x86_64") { "x64" } else { "unknown" };
    let applied_affinity = if case_data.affinity_mode == "single_core" { "unsupported" } else { "unchanged" };
    format!(
        "{{\"schema_version\":1,\"implementation\":\"{implementation}\",\"language\":\"rust\",\"variant\":\"{variant}\",\"case_id\":\"{case_id}\",\"warmup\":{warmup},\"repeat_index\":{repeat_index},\"iterations\":{iterations},\"parallel_chains\":{parallel_chains},\"loop_trip_count\":{loop_trip_count},\"remainder\":{remainder},\"timer_kind\":\"instant_ns\",\"elapsed_ns\":{elapsed_ns},\"ns_per_iteration\":{ns_per_iteration},\"ns_per_add\":{ns_per_add},\"result_checksum\":\"{checksum}\",\"host_os\":\"{host_os}\",\"host_arch\":\"{host_arch}\",\"pid\":{pid},\"tid\":0,\"requested_priority_mode\":\"{priority_mode}\",\"requested_affinity_mode\":\"{affinity_mode}\",\"applied_priority_mode\":\"unsupported\",\"applied_affinity_mode\":\"{applied_affinity}\",\"scheduler_notes\":\"Rust benchmark uses controller-side best effort scheduling only\",\"runtime_name\":\"rust\",\"platform_extras\":{{}}}}",
        implementation = implementation,
        variant = variant,
        case_id = escape_json(&case_data.case_id),
        warmup = if case_data.warmup { "true" } else { "false" },
        repeat_index = case_data.repeat_index,
        iterations = case_data.iterations,
        parallel_chains = case_data.parallel_chains,
        loop_trip_count = loop_trip_count,
        remainder = remainder,
        elapsed_ns = elapsed_ns,
        ns_per_iteration = if case_data.iterations == 0 { 0.0 } else { elapsed_ns as f64 / case_data.iterations as f64 },
        ns_per_add = if total_adds == 0 { 0.0 } else { elapsed_ns as f64 / total_adds as f64 },
        checksum = checksum,
        host_os = host_os,
        host_arch = host_arch,
        pid = std::process::id(),
        priority_mode = escape_json(&case_data.priority_mode),
        affinity_mode = escape_json(&case_data.affinity_mode),
        applied_affinity = applied_affinity,
    )
}

fn escape_json(value: &str) -> String {
    value.replace('\\', "\\\\").replace('"', "\\\"")
}
