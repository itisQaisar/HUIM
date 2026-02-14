package com.tp3.choco;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.variables.BoolVar;
import org.chocosolver.solver.variables.IntVar;

public class CategoryConstraint {

    public static void add(Model model,
                           BoolVar[] P,
                           int n,
                           int catSize,
                           int minCategories) {

        int nbCat = n / catSize;
        BoolVar[] used = new BoolVar[nbCat];

        for (int c = 0; c < nbCat; c++) {
            int start = c * catSize + 1;
            int end = (c + 1) * catSize;

            BoolVar[] items = new BoolVar[catSize];
            for (int i = 0; i < catSize; i++) {
                items[i] = P[start + i];
            }

            IntVar sum = model.intVar(0, catSize);
            model.sum(items, "=", sum).post();

            BoolVar usedCat = model.boolVar();
            model.arithm(sum, ">=", 1).reifyWith(usedCat);
            used[c] = usedCat;
        }

        IntVar nbUsed = model.intVar(0, nbCat);
        model.sum(used, "=", nbUsed).post();
        model.arithm(nbUsed, ">=", minCategories).post();
    }
}