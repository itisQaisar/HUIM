package com.tp3.choco.huim;

import com.tp3.hui.UtilityDB;
import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.variables.BoolVar;
import org.chocosolver.solver.variables.IntVar;

import java.util.*;

/**
 * Declarative HUIM using Choco.
 *
 * Model idea:
 * - x_i: BoolVar for each "promising" item (TWU >= minUtil)
 * - For each transaction t:
 *      u_t = sum_{i in t} (x_i * util_{t,i})
 * - totalUtil = sum_t u_t
 * - Constraints:
 *      totalUtil >= minUtil
 *      optional: totalUtil <= maxUtil
 *      size constraints on sum_i x_i
 *      include/exclude constraints on x_i
 *
 * WARNING: Counting all solutions may explode. Always use a time limit.
 */
public class ChocoHuimSolver {

    public static class Result {
        public final long solutionCount;
        public final long elapsedMs;
        public final boolean timeLimitHit;

        public Result(long solutionCount, long elapsedMs, boolean timeLimitHit) {
            this.solutionCount = solutionCount;
            this.elapsedMs = elapsedMs;
            this.timeLimitHit = timeLimitHit;
        }
    }

    private final UtilityDB db;
    private final int minUtil;
    private final int printLimit;
    private final int timeLimitSeconds;
    private final ChocoHuimConstraints constraints;

    public ChocoHuimSolver(UtilityDB db,
                           int minUtil,
                           int printLimit,
                           int timeLimitSeconds,
                           ChocoHuimConstraints constraints) {
        this.db = db;
        this.minUtil = minUtil;
        this.printLimit = Math.max(0, printLimit);
        this.timeLimitSeconds = Math.max(0, timeLimitSeconds);
        this.constraints = (constraints == null) ? ChocoHuimConstraints.none() : constraints;
    }

    public Result solve() {
        long start = System.currentTimeMillis();

        // 1) Preprocess using TWU like specialized algorithms do (huge reduction)
        long[] twu = db.computeTWU();
        List<Integer> promising = db.preprocessByTWU(twu, minUtil);

        // 2) Map itemId -> index in variable array
        Map<Integer, Integer> itemToIdx = new HashMap<>(promising.size() * 2);
        for (int i = 0; i < promising.size(); i++) itemToIdx.put(promising.get(i), i);

        // 3) Build model
        Model model = new Model("CHOCO-HUIM");

        // x[i] = whether we pick item promising[i]
        BoolVar[] x = new BoolVar[promising.size()];
        for (int i = 0; i < promising.size(); i++) {
            x[i] = model.boolVar("x_" + promising.get(i));
        }

        // Size constraint: sum(x) in [minSize, maxSize]
        int maxSize = constraints.maxSize == Integer.MAX_VALUE ? promising.size() : Math.min(constraints.maxSize, promising.size());
        int minSize = Math.min(constraints.minSize, maxSize);
        IntVar sizeVar = model.intVar("size", 0, promising.size());
        model.sum(x, "=", sizeVar).post();
        model.arithm(sizeVar, ">=", minSize).post();
        model.arithm(sizeVar, "<=", maxSize).post();

        // Include / exclude constraints
        for (int it : constraints.includeItems) {
            Integer idx = itemToIdx.get(it);
            if (idx != null) model.arithm(x[idx], "=", 1).post();
            // if item not promising, it was removed by TWU => impossible to include at this minUtil
        }
        for (int it : constraints.excludeItems) {
            Integer idx = itemToIdx.get(it);
            if (idx != null) model.arithm(x[idx], "=", 0).post();
        }

        // 4) For each transaction, create u_t = sum(x_i * util_{t,i})
        List<UtilityDB.Transaction> txs = db.transactions;

        IntVar[] u = new IntVar[txs.size()];
        long totalMax = 0;
        for (int t = 0; t < txs.size(); t++) totalMax += txs.get(t).tu; // safe upper bound

        for (int t = 0; t < txs.size(); t++) {
            UtilityDB.Transaction tr = txs.get(t);

            // Build arrays for scalar constraint only with promising items
            ArrayList<BoolVar> vars = new ArrayList<>();
            ArrayList<Integer> coeffs = new ArrayList<>();

            for (int k = 0; k < tr.items.length; k++) {
                int item = tr.items[k];
                int util = tr.utils[k];

                Integer idx = itemToIdx.get(item);
                if (idx != null) {
                    vars.add(x[idx]);
                    coeffs.add(util);
                }
            }

            int ub = tr.tu; // <= sum of utils of that transaction after preprocess
            u[t] = model.intVar("u_" + t, 0, Math.max(0, ub));

            if (vars.isEmpty()) {
                model.arithm(u[t], "=", 0).post();
            } else {
                BoolVar[] vArr = vars.toArray(new BoolVar[0]);
                int[] cArr = coeffs.stream().mapToInt(Integer::intValue).toArray();
                model.scalar(vArr, cArr, "=", u[t]).post();
            }
        }

        // 5) totalUtil
        IntVar total = model.intVar("totalUtil", 0, (totalMax > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) totalMax));
        model.sum(u, "=", total).post();
        model.arithm(total, ">=", minUtil).post();

        if (constraints.maxUtil > 0) {
            int maxU = (constraints.maxUtil > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) constraints.maxUtil;
            model.arithm(total, "<=", maxU).post();
        }

        // 6) Solve / count
        Solver solver = model.getSolver();
        if (timeLimitSeconds > 0) {
            solver.limitTime(timeLimitSeconds + "s");
        }

        long count = 0;
        int printed = 0;

        while (solver.solve()) {
            count++;

            if (printed < printLimit) {
                printed++;
                // print items
                StringBuilder sb = new StringBuilder();
                sb.append("SOL#").append(printed)
                  .append("  util=").append(total.getValue())
                  .append("  size=").append(sizeVar.getValue())
                  .append("  items={");
                boolean first = true;
                for (int i = 0; i < x.length; i++) {
                    if (x[i].getValue() == 1) {
                        if (!first) sb.append(' ');
                        sb.append(promising.get(i));
                        first = false;
                    }
                }
                sb.append("}");
                System.out.println(sb);
            }
        }

        long elapsed = System.currentTimeMillis() - start;

        // Robust time-limit detection (no streams, no monitor assumptions)
        boolean timeLimitHit = false;
        if (timeLimitSeconds > 0) {
            timeLimitHit = elapsed >= timeLimitSeconds * 1000L;
        }

        return new Result(count, elapsed, timeLimitHit);
    }
}