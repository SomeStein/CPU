package bench;

public final class JavaOptimized {
    private JavaOptimized() {
    }

    public static void main(String[] args) throws Exception {
        BenchCommon.CaseData caseData = BenchCommon.loadCase(args);
        BenchCommon.Context context = BenchCommon.prepareContext(caseData);
        int loopTripCount = caseData.iterations() / caseData.parallelChains();
        int remainder = caseData.iterations() % caseData.parallelChains();

        long start = System.nanoTime();
        long checksum = switch (caseData.parallelChains()) {
            case 4 -> runFour(loopTripCount, remainder);
            case 8 -> runEight(loopTripCount, remainder);
            default -> runGeneric(loopTripCount, remainder, caseData.parallelChains());
        };
        long end = System.nanoTime();

        long elapsed = end - start;
        double nsPerIteration = caseData.iterations() == 0 ? 0.0 : (double) elapsed / (double) caseData.iterations();
        double nsPerAdd = caseData.iterations() == 0 ? 0.0 : (double) elapsed / (double) (caseData.iterations() * 2L);
        System.out.println(
            BenchCommon.toJson(
                new BenchCommon.ResultPayload(
                    "java_optimized",
                    "optimized",
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

    private static long runGeneric(int loopTripCount, int remainder, int parallelChains) {
        long[][] states = BenchCommon.extendSeedPairs(parallelChains);
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
        long checksum = 0L;
        for (long[] state : states) {
            checksum ^= state[0] ^ state[1];
        }
        return checksum;
    }

    private static long runFour(int loopTripCount, int remainder) {
        long[][] seeds = BenchCommon.extendSeedPairs(4);
        long a0 = seeds[0][0];
        long b0 = seeds[0][1];
        long a1 = seeds[1][0];
        long b1 = seeds[1][1];
        long a2 = seeds[2][0];
        long b2 = seeds[2][1];
        long a3 = seeds[3][0];
        long b3 = seeds[3][1];
        for (int index = 0; index < loopTripCount; index += 1) {
            a0 = a0 + b0;
            b0 = a0 + b0;
            a1 = a1 + b1;
            b1 = a1 + b1;
            a2 = a2 + b2;
            b2 = a2 + b2;
            a3 = a3 + b3;
            b3 = a3 + b3;
        }
        for (int index = 0; index < remainder; index += 1) {
            a0 = a0 + b0;
            b0 = a0 + b0;
        }
        return a0 ^ b0 ^ a1 ^ b1 ^ a2 ^ b2 ^ a3 ^ b3;
    }

    private static long runEight(int loopTripCount, int remainder) {
        long[][] seeds = BenchCommon.extendSeedPairs(8);
        long[] values = new long[16];
        for (int index = 0; index < 8; index += 1) {
            values[index * 2] = seeds[index][0];
            values[index * 2 + 1] = seeds[index][1];
        }
        for (int outer = 0; outer < loopTripCount; outer += 1) {
            for (int index = 0; index < values.length; index += 2) {
                values[index] = values[index] + values[index + 1];
                values[index + 1] = values[index] + values[index + 1];
            }
        }
        for (int index = 0; index < remainder; index += 1) {
            values[0] = values[0] + values[1];
            values[1] = values[0] + values[1];
        }
        long checksum = 0L;
        for (long value : values) {
            checksum ^= value;
        }
        return checksum;
    }
}
