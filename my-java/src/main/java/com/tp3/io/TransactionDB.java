package com.tp3.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class TransactionDB {

    public final int m;            // number of transactions
    public final int n;            // max item id
    public final boolean[][] D;    // D[t][i] = item i in transaction t
    public final List<int[]> transactions;

    private TransactionDB(int m, int n, boolean[][] D, List<int[]> transactions) {
        this.m = m;
        this.n = n;
        this.D = D;
        this.transactions = transactions;
    }

    public static TransactionDB load(String file) throws IOException {
        List<String> lines = Files.readAllLines(Path.of(file));
        List<int[]> trans = new ArrayList<>();
        int maxItem = 0;

        for (String l : lines) {
            l = l.trim();
            if (l.isEmpty() || l.startsWith("#") || l.startsWith("%")) continue;

            String[] parts = l.split("\\s+");
            List<Integer> items = new ArrayList<>();

            for (String p : parts) {
                if (p.equals("-1") || p.equals("-2")) continue;
                int v = Integer.parseInt(p);
                if (v > 0) {
                    items.add(v);
                    maxItem = Math.max(maxItem, v);
                }
            }

            int[] arr = items.stream().mapToInt(Integer::intValue).distinct().sorted().toArray();
            if (arr.length > 0) trans.add(arr);
        }

        int m = trans.size();
        int n = maxItem;
        boolean[][] D = new boolean[m][n + 1];

        for (int t = 0; t < m; t++) {
            for (int i : trans.get(t)) {
                D[t][i] = true;
            }
        }

        return new TransactionDB(m, n, D, trans);
    }
}