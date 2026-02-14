package com.tp3.hui;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * UtilityDB for HUIM datasets (SPMF format):
 * items : TU : utilities
 */
public class UtilityDB {

    public static class Transaction {
        public final int tid;
        public int[] items;
        public int[] utils;
        public int tu;

        public Transaction(int tid, int[] items, int[] utils, int tu) {
            this.tid = tid;
            this.items = items;
            this.utils = utils;
            this.tu = tu;
        }
    }

    public final List<Transaction> transactions = new ArrayList<>();
    public int transactionCount;
    public int maxItemId;

    /** Your Main.java expects this name */
    public static UtilityDB loadFromFile(String path) throws IOException {
        return load(path);
    }

    public static UtilityDB load(String path) throws IOException {
        UtilityDB db = new UtilityDB();

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8))) {

            String line;
            int tid = 0;

            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split(":");
                if (parts.length < 3) throw new IOException("Bad line: " + line);

                int[] items = parseInts(parts[0].trim());
                int tu = Integer.parseInt(parts[1].trim());
                int[] utils = parseInts(parts[2].trim());

                if (items.length != utils.length)
                    throw new IOException("Mismatch items/utils at T" + tid + " -> " + line);

                for (int it : items) db.maxItemId = Math.max(db.maxItemId, it);

                db.transactions.add(new Transaction(tid, items, utils, tu));
                tid++;
            }

            db.transactionCount = tid;
        }

        return db;
    }

    private static int[] parseInts(String s) {
        if (s.isEmpty()) return new int[0];
        String[] tok = s.split("\\s+");
        int[] out = new int[tok.length];
        for (int i = 0; i < tok.length; i++) out[i] = Integer.parseInt(tok[i]);
        return out;
    }

    public long[] computeTWU() {
        long[] twu = new long[maxItemId + 1];
        for (Transaction t : transactions)
            for (int it : t.items)
                twu[it] += t.tu;
        return twu;
    }

    /**
     * Remove unpromising items by TWU and sort items inside each transaction
     * using ascending TWU (tie by item id).
     */
    public List<Integer> preprocessByTWU(long[] twu, int minUtil) {

        // 1) list promising items
        List<Integer> promising = new ArrayList<>();
        for (int i = 1; i < twu.length; i++)
            if (twu[i] >= minUtil)
                promising.add(i);

        // 2) sort them by ascending TWU
        promising.sort((a, b) -> {
            int c = Long.compare(twu[a], twu[b]);
            return (c != 0) ? c : Integer.compare(a, b);
        });

        // 3) build rank to sort transaction items
        int[] rank = new int[maxItemId + 1];
        Arrays.fill(rank, Integer.MAX_VALUE);
        for (int i = 0; i < promising.size(); i++)
            rank[promising.get(i)] = i;

        // 4) filter + reorder each transaction
        for (Transaction t : transactions) {

            int[] tmpItems = new int[t.items.length];
            int[] tmpUtils = new int[t.utils.length];
            int k = 0;

            for (int i = 0; i < t.items.length; i++) {
                int it = t.items[i];
                if (it < rank.length && rank[it] != Integer.MAX_VALUE) {
                    tmpItems[k] = it;
                    tmpUtils[k] = t.utils[i];
                    k++;
                }
            }

            if (k == 0) {
                t.items = new int[0];
                t.utils = new int[0];
                continue;
            }

            Integer[] order = new Integer[k];
            for (int i = 0; i < k; i++) order[i] = i;

            Arrays.sort(order, Comparator.comparingInt(i -> rank[tmpItems[i]]));

            int[] newItems = new int[k];
            int[] newUtils = new int[k];
            for (int i = 0; i < k; i++) {
                newItems[i] = tmpItems[order[i]];
                newUtils[i] = tmpUtils[order[i]];
            }

            t.items = newItems;
            t.utils = newUtils;
        }

        return promising;
    }
}