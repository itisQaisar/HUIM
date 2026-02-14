package com.tp3.io;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Filter a SPMF HUIM dataset file of format:
 *   i1 i2 i3 ... : TU : u1 u2 u3 ...
 *
 * Supports:
 *  - include=...  keep only transactions containing ALL included items
 *  - exclude=...  remove excluded items from transactions (and their utilities)
 *
 * Recomputes TU as sum of remaining utilities.
 *
 * Usage:
 *   FilterHuimDatasetMain <inFile> <outFile> [include=1,2] [exclude=3,4]
 */
public class FilterHuimDatasetMain {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: FilterHuimDatasetMain <inFile> <outFile> [include=..] [exclude=..]");
            return;
        }

        String inFile = args[0];
        String outFile = args[1];

        Set<Integer> include = new HashSet<>();
        Set<Integer> exclude = new HashSet<>();

        for (int i = 2; i < args.length; i++) {
            String a = args[i].trim();
            if (a.startsWith("include=")) include.addAll(parseCsvInts(a.substring("include=".length())));
            else if (a.startsWith("exclude=")) exclude.addAll(parseCsvInts(a.substring("exclude=".length())));
            else System.out.println("[WARN] Unknown arg: " + a);
        }

        long kept = 0, read = 0, dropped = 0;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(inFile), StandardCharsets.UTF_8));
             BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8))) {

            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("#") || line.startsWith("%") || line.startsWith("@")) continue;

                read++;

                String[] parts = line.split(":");
                if (parts.length < 3) {
                    // not a HUIM utility line
                    continue;
                }

                int[] items = parseSpaceInts(parts[0]);
                int[] utils = parseSpaceInts(parts[2]);

                if (items.length != utils.length) {
                    // bad line
                    continue;
                }

                // include check
                if (!include.isEmpty()) {
                    Set<Integer> txItems = new HashSet<>();
                    for (int it : items) txItems.add(it);
                    boolean ok = true;
                    for (int inc : include) {
                        if (!txItems.contains(inc)) { ok = false; break; }
                    }
                    if (!ok) { dropped++; continue; }
                }

                // apply exclude
                ArrayList<Integer> outItems = new ArrayList<>();
                ArrayList<Integer> outUtils = new ArrayList<>();

                for (int i = 0; i < items.length; i++) {
                    int it = items[i];
                    int u = utils[i];
                    if (exclude.contains(it)) continue;
                    outItems.add(it);
                    outUtils.add(u);
                }

                if (outItems.isEmpty()) { dropped++; continue; }

                // recompute TU
                int tu = 0;
                for (int u : outUtils) tu += u;

                // write
                bw.write(joinInts(outItems));
                bw.write(":");
                bw.write(String.valueOf(tu));
                bw.write(":");
                bw.write(joinInts(outUtils));
                bw.newLine();

                kept++;
            }
        }

        System.out.println("Filtered dataset written: " + outFile);
        System.out.println("Read tx = " + read + " kept = " + kept + " dropped = " + dropped);
        System.out.println("include = " + include + " exclude = " + exclude);
    }

    private static List<Integer> parseCsvInts(String csv) {
        if (csv == null) return List.of();
        csv = csv.trim();
        if (csv.isEmpty()) return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .collect(Collectors.toList());
    }

    private static int[] parseSpaceInts(String s) {
        s = s.trim();
        if (s.isEmpty()) return new int[0];
        String[] toks = s.split("\\s+");
        int[] out = new int[toks.length];
        for (int i = 0; i < toks.length; i++) out[i] = Integer.parseInt(toks[i]);
        return out;
    }

    private static String joinInts(List<Integer> a) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.size(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(a.get(i));
        }
        return sb.toString();
    }
}