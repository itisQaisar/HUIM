package com.tp3.choco.huim;

import com.tp3.hui.UtilityDB;
import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.variables.BoolVar;
import org.chocosolver.solver.variables.IntVar;

import java.util.*;

/**
 * Declarative HUIM model (CP) with Choco:
 * - Decision vars: select[i] in {0,1} for each item
 * - For each transaction t: ut[t] = sum_{i in t} util(t,i) * select[i]
 * - totalUtility = sum_t ut[t]
 * - Constraint: totalUtility >= minUtil
 *
 * This enumerates ALL solutions (patterns) meeting minUtil.
 */
public class ChocoHuimModel {

    public static class Options {
        public int printLimit = 0;         // print first K solutions
        public int timeLimitSeconds = 0;   // 0 => no limit
        public boolean compactItems = true; // map item IDs to 0..n-1
    }

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

    public static Result solveAll(UtilityDB db, int minUtil, Options opt) {
        long start = System.currentTimeMillis();

        // 1) Build item universe (compact mapping to reduce variable count)
        ItemIndex index = opt.compactItems ? buildCompactIndex(db) : buildDenseIndex(db);
        int nItems = index.size();

        // 2) Create CP model
        Model model = new Model("HUIM-CP");

        // Decision vars: select[k] for each item in mapped index
        BoolVar[] select = model.boolVarArray("x", nItems);

        // Optional: user constraints will be added later in Part 3
        // HuimConstraints.apply(model, select, index, ...);

        // 3) For each transaction, create an IntVar utility contribution and link with scalar
        IntVar[] ut = new IntVar[db.transactionCount];
        for (int t = 0; t < db.transactionCount; t++) {
            UtilityDB.Transaction tr = db.transactions.get(t);

            // Build scalar arrays over items in this transaction
            BoolVar[] vars = new BoolVar[tr.items.length];
            int[] coeffs = new int[tr.items.length];

            for (int j = 0; j < tr.items.length; j++) {
                int rawItem = tr.items[j];
                int mapped = index.map(rawItem);
                vars[j] = select[mapped];
                coeffs[j] = tr.utils[j];
            }

            // ut_t in [0, TU]
            ut[t] = model.intVar("ut_" + t, 0, tr.tu);

            // ut_t = sum(u_j * x_item_j)
            model.scalar(vars, coeffs, "=", ut[t]).post();
        }

        // 4) totalUtility = sum ut[t]  and >= minUtil
        int maxTU = estimateMaxTotalUtility(db);
        IntVar totalUtility = model.intVar("TOTAL_UTIL", 0, maxTU);
        model.sum(ut, "=", totalUtility).post();
        model.arithm(totalUtility, ">=", minUtil).post();

        // 5) Solve (enumerate all solutions)
        Solver solver = model.getSolver();

        if (opt.timeLimitSeconds > 0) {
            solver.limitTime(opt.timeLimitSeconds + "s");
        }

        // A simple search strategy over boolean vars
        solver.setSearch(org.chocosolver.solver.search.strategy.Search.inputOrderLBSearch(select));

        long count = 0;
        int printed = 0;

        while (solver.solve()) {
            count++;

            if (opt.printLimit > 0 && printed < opt.printLimit) {
                printed++;
                System.out.println(formatSolution(select, index, totalUtility.getValue(), printed));
            }
        }

        boolean timeLimitHit = false;
        if (opt.timeLimitSeconds > 0) {
            double t = solver.getMeasures().getTimeCount(); // seconds
            timeLimitHit = t >= (opt.timeLimitSeconds - 1e-6);
        }

        long elapsed = System.currentTimeMillis() - start;
        return new Result(count, elapsed, timeLimitHit);
    }

    // -------------------------- helpers --------------------------

    private static String formatSolution(BoolVar[] select, ItemIndex index, int totalUtil, int k) {
        StringBuilder sb = new StringBuilder();
        sb.append("SOL#").append(k).append("  util=").append(totalUtil).append("  items={");

        boolean first = true;
        for (int i = 0; i < select.length; i++) {
            if (select[i].getValue() == 1) {
                int raw = index.unmap(i);
                if (!first) sb.append(' ');
                sb.append(raw);
                first = false;
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private static int estimateMaxTotalUtility(UtilityDB db) {
        long sum = 0;
        for (UtilityDB.Transaction t : db.transactions) sum += t.tu;

        // Choco IntVar is int-bounded. If sum is too large, clamp safely.
        if (sum > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) sum;
    }

    /**
     * Compact mapping: only items that appear in DB become variables.
     * raw item IDs -> [0..n-1]
     */
    private static ItemIndex buildCompactIndex(UtilityDB db) {
        TreeSet<Integer> set = new TreeSet<>();
        for (UtilityDB.Transaction t : db.transactions) {
            for (int it : t.items) set.add(it);
        }

        int n = set.size();
        int[] rawByIdx = new int[n];
        HashMap<Integer, Integer> idxByRaw = new HashMap<>(n * 2);

        int k = 0;
        for (int raw : set) {
            rawByIdx[k] = raw;
            idxByRaw.put(raw, k);
            k++;
        }
        return new ItemIndex(rawByIdx, idxByRaw);
    }

    /**
     * Dense mapping: variables for 0..maxItemId (not recommended if maxItemId huge).
     */
    private static ItemIndex buildDenseIndex(UtilityDB db) {
        int max = db.maxItemId;
        int n = max + 1;

        int[] rawByIdx = new int[n];
        HashMap<Integer, Integer> idxByRaw = new HashMap<>(n * 2);

        for (int i = 0; i < n; i++) {
            rawByIdx[i] = i;
            idxByRaw.put(i, i);
        }
        return new ItemIndex(rawByIdx, idxByRaw);
    }

    private static class ItemIndex {
        private final int[] rawByIdx;                  // idx -> raw
        private final HashMap<Integer, Integer> idxByRaw; // raw -> idx

        ItemIndex(int[] rawByIdx, HashMap<Integer, Integer> idxByRaw) {
            this.rawByIdx = rawByIdx;
            this.idxByRaw = idxByRaw;
        }

        int size() { return rawByIdx.length; }

        int map(int raw) {
            Integer v = idxByRaw.get(raw);
            if (v == null) throw new IllegalArgumentException("Unknown item id: " + raw);
            return v;
        }

        int unmap(int idx) { return rawByIdx[idx]; }
    }
}