package com.tp3.choco.huim;

import com.tp3.hui.UtilityDB;

public class ChocoHuimMain {

    /**
     * Usage:
     *   mvn -q exec:java "-Dexec.mainClass=com.tp3.choco.huim.ChocoHuimMain" "-Dexec.args=..\data\mushroom_utility_SPMF.txt 500 5 30"
     *
     * With constraints (Part 3):
     *   ... "-Dexec.args=..\data\mushroom_utility_SPMF.txt 500 5 30 --include=119 --exclude=118 --minSize=1 --maxSize=3 --maxUtil=100000"
     *
     * Args:
     *   0 filePath
     *   1 minUtil
     *   2 printLimit
     *   3 timeLimitSeconds
     *   4+ optional constraints
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.out.println("Usage: <file> <minUtil> <printLimit> <timeLimitSeconds> [--include=..] [--exclude=..] [--minSize=N] [--maxSize=N] [--maxUtil=V]");
            return;
        }

        String file = args[0];
        int minUtil = Integer.parseInt(args[1]);
        int printLimit = Integer.parseInt(args[2]);
        int timeLimitSec = Integer.parseInt(args[3]);

        ChocoHuimConstraints cons = ChocoHuimConstraints.fromArgs(args, 4);

        UtilityDB db = UtilityDB.loadFromFile(file);

        ChocoHuimSolver solver = new ChocoHuimSolver(db, minUtil, printLimit, timeLimitSec, cons);
        ChocoHuimSolver.Result r = solver.solve();

        System.out.println("===== CHOCO HUIM (Declarative) =====");
        System.out.println("File = " + file);
        System.out.println("minUtil = " + minUtil);
        System.out.println("Constraints = " + cons);
        System.out.println("Solutions(HUI count) = " + r.solutionCount);
        System.out.println("Elapsed(ms) = " + r.elapsedMs);
        System.out.println("TimeLimitHit = " + r.timeLimitHit);
    }
}