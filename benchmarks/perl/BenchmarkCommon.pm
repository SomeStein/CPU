package BenchmarkCommon;

use strict;
use warnings;
use Exporter qw(import);
use JSON::PP qw(encode_json);
use Time::HiRes qw(clock_gettime CLOCK_MONOTONIC);

our @EXPORT_OK = qw(
  build_result
  emit_result
  extend_seed_pairs
  load_case_file
  mask_u64
  monotonic_ns
  prepare_context
);

my $MASK64 = 0xFFFFFFFFFFFFFFFF;
my @SEED_PAIRS = (
  [1, 1],
  [3, 5],
  [8, 13],
  [21, 34],
  [55, 89],
  [144, 233],
  [377, 610],
  [987, 1597],
);

sub mask_u64 {
  my ($value) = @_;
  return $value & $MASK64;
}

sub extend_seed_pairs {
  my ($count) = @_;
  my @pairs = map { [@{$_}] } @SEED_PAIRS;
  while (@pairs < $count) {
    my $left = mask_u64($pairs[-2]->[0] + $pairs[-1]->[1]);
    my $right = mask_u64($left + $pairs[-1]->[0]);
    push @pairs, [$left, $right];
  }
  return @pairs[0 .. $count - 1];
}

sub load_case_file {
  my ($argv) = @_;
  die "Usage: perl <script> --case-file <path>\n" unless @{$argv} == 2 && $argv->[0] eq "--case-file";

  open my $handle, "<:encoding(UTF-8)", $argv->[1] or die "Unable to open case file: $!";
  my %parsed;
  while (my $line = <$handle>) {
    chomp $line;
    next if $line eq "" || $line =~ /^\s*#/ || index($line, "=") < 0;
    my ($key, $value) = split /=/, $line, 2;
    $parsed{$key} = $value;
    $parsed{$key} =~ s/^\s+|\s+$//g;
  }
  close $handle;
  return {
    run_id => $parsed{run_id} // "",
    profile_id => $parsed{profile_id} // "",
    implementation => $parsed{implementation} // "",
    case_id => $parsed{case_id} // "",
    iterations => int($parsed{iterations} // 0),
    parallel_chains => int($parsed{parallel_chains} // 1),
    priority_mode => $parsed{priority_mode} // "high",
    affinity_mode => $parsed{affinity_mode} // "single_core",
    timer_mode => $parsed{timer_mode} // "monotonic_ns",
    warmup => (($parsed{warmup} // "false") eq "true" ? JSON::PP::true : JSON::PP::false),
    repeat_index => int($parsed{repeat_index} // 1),
  };
}

sub monotonic_ns {
  return int(clock_gettime(CLOCK_MONOTONIC) * 1_000_000_000);
}

sub _host_os {
  return "macos" if $^O eq "darwin";
  return "windows" if $^O eq "MSWin32";
  return $^O;
}

sub _host_arch {
  my $arch = $ENV{PROCESSOR_ARCHITECTURE} // $ENV{HOSTTYPE} // "";
  return "arm64" if $arch =~ /arm64|aarch64/i;
  return "x64" if $arch =~ /x86_64|amd64/i;
  return $arch ne "" ? $arch : "unknown";
}

sub prepare_context {
  my ($case_data) = @_;
  my $is_macos = _host_os() eq "macos";
  return {
    pid => $$,
    tid => 0,
    requested_priority_mode => $case_data->{priority_mode},
    requested_affinity_mode => $case_data->{affinity_mode},
    applied_priority_mode => $case_data->{priority_mode} eq "high" && $is_macos ? "advisory_macos" : "unsupported",
    applied_affinity_mode => $case_data->{affinity_mode} eq "single_core" ? ($is_macos ? "advisory_macos" : "unsupported") : "unchanged",
    scheduler_notes => $is_macos ? "Perl runtime uses controller-side best effort scheduling only; macOS affinity remains advisory" : "Perl runtime uses controller-side best effort scheduling only",
  };
}

sub build_result {
  my (%args) = @_;
  my $case_data = $args{case_data};
  my $context = $args{context};
  my $total_adds = $case_data->{iterations} * 2;
  return {
    schema_version => 1,
    implementation => $args{implementation},
    language => "perl",
    variant => $args{variant},
    case_id => $case_data->{case_id},
    warmup => $case_data->{warmup},
    repeat_index => $case_data->{repeat_index},
    iterations => $case_data->{iterations},
    parallel_chains => $case_data->{parallel_chains},
    loop_trip_count => $args{loop_trip_count},
    remainder => $args{remainder},
    timer_kind => "clock_gettime_ns",
    elapsed_ns => $args{elapsed_ns},
    ns_per_iteration => $case_data->{iterations} ? ($args{elapsed_ns} / $case_data->{iterations}) : 0.0,
    ns_per_add => $total_adds ? ($args{elapsed_ns} / $total_adds) : 0.0,
    result_checksum => "$args{checksum}",
    host_os => _host_os(),
    host_arch => _host_arch(),
    pid => $context->{pid},
    tid => $context->{tid},
    requested_priority_mode => $context->{requested_priority_mode},
    requested_affinity_mode => $context->{requested_affinity_mode},
    applied_priority_mode => $context->{applied_priority_mode},
    applied_affinity_mode => $context->{applied_affinity_mode},
    scheduler_notes => $context->{scheduler_notes},
    runtime_name => "perl",
    platform_extras => {},
  };
}

sub emit_result {
  my ($payload) = @_;
  print encode_json($payload), "\n";
}

1;
