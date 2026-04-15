use strict;
use warnings;
use FindBin qw($Bin);
use lib $Bin;
use BenchmarkCommon qw(build_result emit_result extend_seed_pairs load_case_file mask_u64 monotonic_ns prepare_context);

my $case_data = load_case_file(\@ARGV);
my $loop_trip_count = int($case_data->{iterations} / $case_data->{parallel_chains});
my $remainder = $case_data->{iterations} % $case_data->{parallel_chains};
my @states = map { [@{$_}] } extend_seed_pairs($case_data->{parallel_chains});
my $context = prepare_context($case_data);

my $start = monotonic_ns();
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
my $finish = monotonic_ns();

my $checksum = 0;
for my $state (@states) {
  $checksum ^= $state->[0] ^ $state->[1];
}

emit_result(
  build_result(
    implementation => "perl_sloppy",
    variant => "sloppy",
    case_data => $case_data,
    context => $context,
    elapsed_ns => ($finish - $start),
    loop_trip_count => $loop_trip_count,
    remainder => $remainder,
    checksum => $checksum,
  )
);
