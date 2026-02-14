package com.tp3.choco.huim;

import java.util.*;

/**
 * User constraints for Part 3.
 * - include/exclude items
 * - size bounds
 * - optional max utility bound
 */
public class ChocoHuimConstraints {
    public final Set<Integer> includeItems = new HashSet<>();
    public final Set<Integer> excludeItems = new HashSet<>();
    public int minSize = 0;          // >= 0
    public int maxSize = Integer.MAX_VALUE; // unbounded
    public long maxUtil = -1;        // <= 0 means "no max util bound"

    public ChocoHuimConstraints() {}

    public static ChocoHuimConstraints none() {
        return new ChocoHuimConstraints();
    }

    public static ChocoHuimConstraints fromArgs(String[] args, int startIdx) {
        ChocoHuimConstraints c = new ChocoHuimConstraints();
        for (int i = startIdx; i < args.length; i++) {
            String a = args[i].trim();
            if (a.startsWith("--include=")) {
                parseIntListInto(a.substring("--include=".length()), c.includeItems);
            } else if (a.startsWith("--exclude=")) {
                parseIntListInto(a.substring("--exclude=".length()), c.excludeItems);
            } else if (a.startsWith("--minSize=")) {
                c.minSize = Math.max(0, Integer.parseInt(a.substring("--minSize=".length())));
            } else if (a.startsWith("--maxSize=")) {
                c.maxSize = Integer.parseInt(a.substring("--maxSize=".length()));
                if (c.maxSize < 0) c.maxSize = Integer.MAX_VALUE;
            } else if (a.startsWith("--maxUtil=")) {
                c.maxUtil = Long.parseLong(a.substring("--maxUtil=".length()));
            }
        }
        // if an item is both included and excluded, include wins (you can change that policy if needed)
        c.excludeItems.removeAll(c.includeItems);
        return c;
    }

    private static void parseIntListInto(String s, Set<Integer> out) {
        if (s == null) return;
        s = s.trim();
        if (s.isEmpty()) return;
        String[] parts = s.split("[,; ]+");
        for (String p : parts) {
            if (p == null) continue;
            p = p.trim();
            if (p.isEmpty()) continue;
            out.add(Integer.parseInt(p));
        }
    }

    @Override
    public String toString() {
        return "ChocoHuimConstraints{" +
                "include=" + includeItems +
                ", exclude=" + excludeItems +
                ", minSize=" + minSize +
                ", maxSize=" + (maxSize == Integer.MAX_VALUE ? "INF" : maxSize) +
                ", maxUtil=" + maxUtil +
                '}';
    }
}