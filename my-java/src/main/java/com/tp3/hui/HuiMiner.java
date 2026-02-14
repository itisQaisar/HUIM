package com.tp3.hui;

import java.util.*;

/**
 * HUI-Miner with Utility-Lists (HEAVY DEBUG).
 * Goal: help you match EFIM on a small dataset and find where counts diverge.
 */
public class HuiMiner {

    public static class Entry {
        public final int tid;
        public final int iutil;
        public final int rutil;
        public Entry(int tid, int iutil, int rutil) {
            this.tid = tid;
            this.iutil = iutil;
            this.rutil = rutil;
        }
    }

    public static class UtilityList {
        public final int item;
        public final ArrayList<Entry> entries = new ArrayList<>();
        public long sumIutil = 0;
        public long sumRutil = 0;

        public UtilityList(int item) { this.item = item; }

        public void add(Entry e) {
            entries.add(e);
            sumIutil += e.iutil;
            sumRutil += e.rutil;
        }
    }

    public static class Result {
        public final long huiCount;
        public final long elapsedMs;
        public final boolean timeLimitHit;
        public Result(long huiCount, long elapsedMs, boolean timeLimitHit) {
            this.huiCount = huiCount;
            this.elapsedMs = elapsedMs;
            this.timeLimitHit = timeLimitHit;
        }
    }

    private final UtilityDB db;
    private final int minUtil;
    private final int printLimit;
    private final long deadlineNanos;

    // Debug toggles
    private final boolean debug;
    private final int debugTopK;
    private final int debugShowTx;
    private final int debugJoinSamples;
    private final long debugMaxNodes;

    private long huiCount = 0;
    private int printed = 0;
    private boolean timeLimitHit = false;

    private long dfsNodes = 0;
    private long joinsBuilt = 0;

    public HuiMiner(UtilityDB db, int minUtil, int printLimit, int timeLimitSeconds) {
        this(db, minUtil, printLimit, timeLimitSeconds,
                true, 10, 3, 3, 0);
    }

    // THIS matches your Main.java error message signature
    public HuiMiner(UtilityDB db,
                    int minUtil,
                    int printLimit,
                    int timeLimitSeconds,
                    boolean debug,
                    int debugTopK,
                    int debugShowTx,
                    int debugJoinSamples,
                    int debugMaxNodes) {

        this.db = db;
        this.minUtil = minUtil;
        this.printLimit = Math.max(0, printLimit);
        this.debug = debug;
        this.debugTopK = Math.max(0, debugTopK);
        this.debugShowTx = Math.max(0, debugShowTx);
        this.debugJoinSamples = Math.max(0, debugJoinSamples);
        this.debugMaxNodes = Math.max(0, debugMaxNodes);

        if (timeLimitSeconds <= 0) this.deadlineNanos = Long.MAX_VALUE;
        else this.deadlineNanos = System.nanoTime() + (long) timeLimitSeconds * 1_000_000_000L;
    }

    public Result run() {
        long start = System.currentTimeMillis();

        if (debug) {
            System.out.println("[DEBUG] ====== HUI-Miner HEAVY DEBUG ======");
            System.out.println("[DEBUG] txCount=" + db.transactionCount + " maxItemId=" + db.maxItemId + " minUtil=" + minUtil);
            sanityCheckTransactions();
            printFirstTransactions("Before preprocess", debugShowTx);
        }

        long[] twu = db.computeTWU();
        List<Integer> promising = db.preprocessByTWU(twu, minUtil);

        if (debug) {
            printFirstTransactions("After preprocess", debugShowTx);
            System.out.println("[DEBUG] promisingItems=" + promising.size());

            if (debugTopK > 0 && !promising.isEmpty()) {
                ArrayList<Integer> byTwu = new ArrayList<>(promising);
                byTwu.sort((a, b) -> Long.compare(twu[b], twu[a]));
                int k = Math.min(debugTopK, byTwu.size());
                System.out.println("[DEBUG] Top " + k + " items by TWU:");
                for (int i = 0; i < k; i++) {
                    int it = byTwu.get(i);
                    System.out.println("[DEBUG]   item=" + it + " TWU=" + twu[it]);
                }
            }
        }

        Map<Integer, UtilityList> mapUL = new HashMap<>(promising.size() * 2);
        for (int it : promising) mapUL.put(it, new UtilityList(it));

        // Fill entries (rutil right->left)
        for (UtilityDB.Transaction t : db.transactions) {
            if (t.items.length == 0) continue;

            int ru = 0;
            for (int idx = t.items.length - 1; idx >= 0; idx--) {
                int item = t.items[idx];
                int u = t.utils[idx];

                UtilityList ul = mapUL.get(item);
                if (ul != null) ul.add(new Entry(t.tid, u, ru));

                ru += u;
            }
        }

        ArrayList<UtilityList> uls = new ArrayList<>();
        for (int it : promising) {
            UtilityList ul = mapUL.get(it);
            if (ul != null && !ul.entries.isEmpty()) uls.add(ul);
        }

        if (debug) debugLevel1Stats(uls);

        huiCount = 0;
        printed = 0;
        timeLimitHit = false;
        dfsNodes = 0;
        joinsBuilt = 0;

        huiSearch(new int[0], null, uls, 0);

        long elapsed = System.currentTimeMillis() - start;

        if (debug) {
            System.out.println("[DEBUG] DFS nodes=" + dfsNodes + " joinsBuilt=" + joinsBuilt);
            System.out.println("[DEBUG] ===================================");
        }

        return new Result(huiCount, elapsed, timeLimitHit);
    }

    private void huiSearch(int[] prefix, UtilityList ulPrefix, List<UtilityList> ULs, int depth) {
        if (timeLimitHit) return;

        if (System.nanoTime() > deadlineNanos) {
            timeLimitHit = true;
            return;
        }

        if (debugMaxNodes > 0 && dfsNodes >= debugMaxNodes) {
            timeLimitHit = true;
            return;
        }

        for (int i = 0; i < ULs.size(); i++) {
            UtilityList X = ULs.get(i);

            dfsNodes++;

            if (X.sumIutil >= minUtil) {
                huiCount++;
                if (printed < printLimit) {
                    printed++;
                    printPattern(prefix, X.item, X.sumIutil);
                }
            }

            if (X.sumIutil + X.sumRutil < minUtil) continue;

            ArrayList<UtilityList> exts = new ArrayList<>();
            int printedJoin = 0;

            for (int j = i + 1; j < ULs.size(); j++) {
                UtilityList Y = ULs.get(j);

                UtilityList XY = construct(ulPrefix, X, Y);

                if (XY != null && !XY.entries.isEmpty()) {
                    if (XY.sumIutil + XY.sumRutil >= minUtil) exts.add(XY);

                    if (debug && printedJoin < debugJoinSamples && depth <= 1) {
                        printedJoin++;
                        System.out.println("[DEBUG][JOIN d=" + depth + "] X=" + X.item + " (|=" + X.entries.size() +
                                ")  Y=" + Y.item + " (|=" + Y.entries.size() +
                                ")  => XY(|=" + XY.entries.size() + ", sumI=" + XY.sumIutil +
                                ", sumR=" + XY.sumRutil + ", UB=" + (XY.sumIutil + XY.sumRutil) + ")");
                    }
                }

                if (System.nanoTime() > deadlineNanos) { timeLimitHit = true; return; }
            }

            int[] newPrefix = append(prefix, X.item);
            huiSearch(newPrefix, X, exts, depth + 1);

            if (timeLimitHit) return;
        }
    }

    private UtilityList construct(UtilityList ulPrefix, UtilityList ulX, UtilityList ulY) {
        joinsBuilt++;

        UtilityList out = new UtilityList(ulY.item);

        Map<Integer, Entry> mapP = null;
        if (ulPrefix != null) {
            mapP = new HashMap<>(ulPrefix.entries.size() * 2);
            for (Entry ep : ulPrefix.entries) mapP.put(ep.tid, ep);
        }

        int ix = 0, iy = 0;
        while (ix < ulX.entries.size() && iy < ulY.entries.size()) {
            Entry a = ulX.entries.get(ix);
            Entry b = ulY.entries.get(iy);

            if (a.tid == b.tid) {
                int iutil;
                if (ulPrefix == null) iutil = a.iutil + b.iutil;
                else {
                    Entry ep = mapP.get(a.tid);
                    if (ep == null) { ix++; iy++; continue; }
                    iutil = a.iutil + b.iutil - ep.iutil;
                }

                out.add(new Entry(a.tid, iutil, b.rutil));
                ix++; iy++;
            } else if (a.tid < b.tid) ix++;
            else iy++;
        }

        return out;
    }

    // ---------- debug helpers ----------

    private void sanityCheckTransactions() {
        int badTU = 0;

        for (UtilityDB.Transaction t : db.transactions) {
            int sumU = 0;
            for (int u : t.utils) sumU += u;
            if (sumU != t.tu) badTU++;
        }

        System.out.println("[DEBUG] TU consistency: badTU=" + badTU + " (sum(utils)!=TU)");
    }

    private void printFirstTransactions(String title, int n) {
        System.out.println("[DEBUG] --- " + title + " (first " + n + ") ---");
        for (int i = 0; i < Math.min(n, db.transactions.size()); i++) {
            UtilityDB.Transaction t = db.transactions.get(i);
            System.out.println("[DEBUG] T" + t.tid + " TU=" + t.tu +
                    " items=" + Arrays.toString(t.items) +
                    " utils=" + Arrays.toString(t.utils));
        }
    }

    private void debugLevel1Stats(List<UtilityList> uls) {
        System.out.println("[DEBUG] nonEmpty ULs(level-1)=" + uls.size());

        long maxUB = 0;
        UtilityList best = null;
        for (UtilityList ul : uls) {
            long ub = ul.sumIutil + ul.sumRutil;
            if (ub > maxUB) { maxUB = ub; best = ul; }
        }

        if (best != null) {
            System.out.println("[DEBUG] Max UB(level-1)=" + maxUB + " at item=" + best.item +
                    " sumI=" + best.sumIutil + " sumR=" + best.sumRutil + " entries=" + best.entries.size());
        }
    }

    private static int[] append(int[] prefix, int item) {
        int[] out = Arrays.copyOf(prefix, prefix.length + 1);
        out[prefix.length] = item;
        return out;
    }

    private void printPattern(int[] prefix, int last, long util) {
        StringBuilder sb = new StringBuilder();
        sb.append("HUI: ");
        for (int x : prefix) sb.append(x).append(' ');
        sb.append(last);
        sb.append("  util=").append(util);
        System.out.println(sb);
    }
}