package com.tp3;

import com.tp3.hui.HuiMiner;
import com.tp3.hui.UtilityDB;

public class Main {

    public static void main(String[] args) throws Exception {

        if (args.length < 2) {
            System.out.println("Usage: java com.tp3.Main <inputFile> <minUtil>");
            System.out.println("Example: java com.tp3.Main data/mushroom_utility.txt 2000");
            return;
        }

        String input = args[0];
        int minUtil = Integer.parseInt(args[1]);

        UtilityDB db = UtilityDB.loadFromFile(input);

        // constructor signature that your Main was trying to use
        HuiMiner miner = new HuiMiner(
                db,
                minUtil,
                20,     // printLimit
                60,     // timeLimitSeconds
                true,   // debug
                10,     // debugTopK
                3,      // debugShowTx
                3,      // debugJoinSamples
                0       // debugMaxNodes (0 = no stop)
        );

        HuiMiner.Result res = miner.run();

        System.out.println("HUI count = " + res.huiCount);
        System.out.println("Elapsed(ms) = " + res.elapsedMs);
        System.out.println("TimeLimitHit = " + res.timeLimitHit);
    }
}