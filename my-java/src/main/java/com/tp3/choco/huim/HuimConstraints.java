package com.tp3.choco.huim;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.variables.BoolVar;
import org.chocosolver.solver.variables.IntVar;

/**
 * Placeholder for Part 3 (user constraints).
 * We'll use this later to add:
 *  - include/exclude items
 *  - min/max pattern size
 *  - etc.
 */
public class HuimConstraints {

    private HuimConstraints() {}

    public static void addSizeConstraint(Model model, BoolVar[] select, int minSize, int maxSize) {
        // size = sum(select)
        IntVar size = model.intVar("SIZE", 0, select.length);
        model.sum(select, "=", size).post();

        if (minSize >= 0) model.arithm(size, ">=", minSize).post();
        if (maxSize >= 0) model.arithm(size, "<=", maxSize).post();
    }

    // include/exclude will be implemented when we parse CLI options in Part 3
}