package bench;

public final class JavaSloppy {
    private JavaSloppy() {
    }

    public static void main(String[] args) throws Exception {
        BenchCommon.CaseData caseData = BenchCommon.loadCase(args);
        BenchCommon.Context context = BenchCommon.prepareContext(caseData);
        int loopTripCount = caseData.iterations() / caseData.parallelChains();
        int remainder = caseData.iterations() % caseData.parallelChains();
        long[][] states = BenchCommon.extendSeedPairs(caseData.parallelChains());

        long start = System.nanoTime();
        for (int outer = 0; outer < loopTripCount; outer += 1) {
            for (long[] state : states) {
                state[0] = state[0] + state[1];
                state[1] = state[0] + state[1];
            }
        }
        for (int index = 0; index < remainder; index += 1) {
            states[0][0] = states[0][0] + states[0][1];
            states[0][1] = states[0][0] + states[0][1];
        }
        long end = System.nanoTime();

        long checksum = 0L;
        for (long[] state : states) {
            checksum ^= state[0] ^ state[1];
        }

        long elapsed = end - start;
        double nsPerIteration = caseData.iterations() == 0 ? 0.0 : (double) elapsed / (double) caseData.iterations();
        double nsPerAdd = caseData.iterations() == 0 ? 0.0 : (double) elapsed / (double) (caseData.iterations() * 2L);
        System.out.println(
            BenchCommon.toJson(
                new BenchCommon.ResultPayload(
                    "java_sloppy",
                    "sloppy",
                    caseData,
                    context,
                    loopTripCount,
                    remainder,
                    elapsed,
                    nsPerIteration,
                    nsPerAdd,
                    checksum
                )
            )
        );
    }
}
