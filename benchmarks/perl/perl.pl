use strict;
use warnings;
use FindBin qw($Bin);
use lib $Bin;
use BenchmarkCommon qw(build_result emit_result extend_seed_pairs load_case_file mask_u64 monotonic_ns prepare_context);

sub run_generic {
  my ($loop_trip_count, $remainder, $parallel_chains) = @_;
  my @states = map { [@{$_}] } extend_seed_pairs($parallel_chains);
  for (1 .. $loop_trip_count) {
    for my $state (@states) {
      $state->[0] = mask_u64($state->[0] + $state->[1]);
      $state->[1] = mask_u64($state->[0] + $state->[1]);
    }
  }
  for (1 .. $remainder) {
    $states[0]->[0] = mask_u64($states[0]->[0] + $states[0]->[1]);
    $states[0]->[1] = mask_u64($states[0]->[0] + $states[0]->[1]);
  }
  my $checksum = 0;
  for my $state (@states) {
    $checksum ^= $state->[0] ^ $state->[1];
  }
  return $checksum;
}

sub run_four {
  my ($loop_trip_count, $remainder) = @_;
  my ($p0, $p1, $p2, $p3) = extend_seed_pairs(4);
  my ($a0, $b0) = @{$p0};
  my ($a1, $b1) = @{$p1};
  my ($a2, $b2) = @{$p2};
  my ($a3, $b3) = @{$p3};
  for (1 .. $loop_trip_count) {
    $a0 = mask_u64($a0 + $b0); $b0 = mask_u64($a0 + $b0);
    $a1 = mask_u64($a1 + $b1); $b1 = mask_u64($a1 + $b1);
    $a2 = mask_u64($a2 + $b2); $b2 = mask_u64($a2 + $b2);
    $a3 = mask_u64($a3 + $b3); $b3 = mask_u64($a3 + $b3);
  }
  for (1 .. $remainder) {
    $a0 = mask_u64($a0 + $b0);
    $b0 = mask_u64($a0 + $b0);
  }
  return $a0 ^ $b0 ^ $a1 ^ $b1 ^ $a2 ^ $b2 ^ $a3 ^ $b3;
}

sub run_eight {
  my ($loop_trip_count, $remainder) = @_;
  my @values = map { @{$_} } extend_seed_pairs(8);
  for (1 .. $loop_trip_count) {
    for (my $index = 0; $index < 16; $index += 2) {
      $values[$index] = mask_u64($values[$index] + $values[$index + 1]);
      $values[$index + 1] = mask_u64($values[$index] + $values[$index + 1]);
    }
  }
  for (1 .. $remainder) {
    $values[0] = mask_u64($values[0] + $values[1]);
    $values[1] = mask_u64($values[0] + $values[1]);
  }
  my $checksum = 0;
  $checksum ^= $_ for @values;
  return $checksum;
}

my $case_data = load_case_file(\@ARGV);
my $loop_trip_count = int($case_data->{iterations} / $case_data->{parallel_chains});
my $remainder = $case_data->{iterations} % $case_data->{parallel_chains};
my $context = prepare_context($case_data);

my $start = monotonic_ns();
my $checksum =
  $case_data->{parallel_chains} == 4 ? run_four($loop_trip_count, $remainder)
  : $case_data->{parallel_chains} == 8 ? run_eight($loop_trip_count, $remainder)
  : run_generic($loop_trip_count, $remainder, $case_data->{parallel_chains});
my $finish = monotonic_ns();

emit_result(
  build_result(
    implementation => "perl_optimized",
    variant => "optimized",
    case_data => $case_data,
    context => $context,
    elapsed_ns => ($finish - $start),
    loop_trip_count => $loop_trip_count,
    remainder => $remainder,
    checksum => $checksum,
  )
);
