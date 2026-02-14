package com.tp3.choco;

import com.tp3.io.TransactionDB;
import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.variables.BoolVar;
import org.chocosolver.solver.variables.IntVar;

public class ClosedItemsetCP {

    public static class Result {
        public final long patternCount;
        public final long elapsedMillis;

        public Result(long c, long t) {
            patternCount = c;
            elapsedMillis = t;
        }
    }

    public static Result run(TransactionDB db,
                             double minsup,
                             int minSize,
                             int catSize,
                             int minCategories,
                             int printLimit) {

        int m = db.m;
        int n = db.n;

        int alpha = (minsup < 1.0)
                ? (int) Math.ceil(minsup * m)
                : (int) minsup;

        Model model = new Model("Closed Itemset CP");

        BoolVar[] P = new BoolVar[n + 1];
        for (int i = 1; i <= n; i++) {
            P[i] = model.boolVar("P_" + i);
        }

        BoolVar[] T = new BoolVar[m];
        for (int t = 0; t < m; t++) {
            T[t] = model.boolVar("T_" + t);
        }

        for (int t = 0; t < m; t++) {
            IntVar sum = model.intVar(0, n);
            BoolVar[] absent = new BoolVar[n];
            int k = 0;

            for (int i = 1; i <= n; i++) {
                if (!db.D[t][i]) absent[k++] = P[i];
            }

            if (k > 0) {
                BoolVar[] a = new BoolVar[k];
                System.arraycopy(absent, 0, a, 0, k);
                model.sum(a, "=", sum).post();
                model.arithm(sum, "=", 0).reifyWith(T[t]);
            } else {
                model.arithm(T[t], "=", 1).post();
            }
        }

        IntVar freq = model.intVar(0, m);
        model.sum(T, "=", freq).post();
        model.arithm(freq, ">=", alpha).post();

        BoolVar[] items = new BoolVar[n];
        for (int i = 1; i <= n; i++) items[i - 1] = P[i];
        IntVar size = model.intVar(0, n);
        model.sum(items, "=", size).post();
        model.arithm(size, ">=", minSize).post();

        CategoryConstraint.add(model, P, n, catSize, minCategories);

        Solver solver = model.getSolver();
        long count = 0;
        long start = System.currentTimeMillis();

        while (solver.solve()) count++;

        long time = System.currentTimeMillis() - start;
        return new Result(count, time);
    }
}