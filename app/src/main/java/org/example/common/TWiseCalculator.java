package org.example.common;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import de.featjar.analysis.sat4j.computation.MIGBuilder;
import de.featjar.analysis.sat4j.solver.ModalImplicationGraph;
import de.featjar.analysis.sat4j.twise.CoverageStatistic;
import de.featjar.analysis.sat4j.twise.TWiseCountComputation;
import de.featjar.analysis.sat4j.twise.TWiseCountComputation.CombinationList;
import de.featjar.analysis.sat4j.twise.TWiseCoverageComputation;
import de.featjar.base.computation.Computations;
import de.featjar.base.computation.Progress;
import de.featjar.formula.assignment.BooleanAssignment;
import de.featjar.formula.assignment.BooleanAssignmentList;

public class TWiseCalculator {
    public static Long computeTWiseCount(BooleanAssignmentList result, int tValue, BooleanAssignment variableFilter,
            BooleanAssignmentList booleanClauseList, List<int[]> combinations) {
        CombinationList combinationList = CombinationList.of(combinations);

        List<Object> tWiseCountdependencyList = new ArrayList<>();
        tWiseCountdependencyList.add(result); // samples
        tWiseCountdependencyList.add(tValue); // t value
        tWiseCountdependencyList.add(variableFilter); // variable filter
        tWiseCountdependencyList.add(combinationList); // combination filter

        TWiseCountComputation twiseCountComputation = new TWiseCountComputation(Computations.of(booleanClauseList));
        Long number = twiseCountComputation.compute(tWiseCountdependencyList, new Progress()).get();
        return number;
    }

    public static CoverageStatistic computeTWiseStatistics(BooleanAssignmentList booleanAssignmentList,
            BooleanAssignment core, BooleanAssignmentList result, Integer tValue) {
        BooleanAssignment variableFilter = new BooleanAssignment(new int[] {});
        Duration duration = Duration.ofMinutes(2);
        Long randomSeed = 1234567890123456789L;

        MIGBuilder migBuilder = new MIGBuilder(Computations.of(booleanAssignmentList));
        List<Object> depList = new ArrayList<>();
        depList.add(booleanAssignmentList);
        depList.add(core);
        ModalImplicationGraph modalImplGraph = migBuilder.compute(depList, new Progress()).get();

        List<Object> dependencyList = new ArrayList<>();
        dependencyList.add(booleanAssignmentList); // CNF clauses
        dependencyList.add(core); // core (check)
        dependencyList.add(booleanAssignmentList); // ASSUMED_CLAUSE_LIST (might be wrong)
        dependencyList.add(duration); // duration check
        dependencyList.add(randomSeed); // randomSeed
        dependencyList.add(tValue); // tValue
        dependencyList.add(result); // result
        dependencyList.add(modalImplGraph); // neue api: ModalImplGraph
        dependencyList.add(variableFilter); // Filter

        TWiseCoverageComputation tWiseCoverageComputation = new TWiseCoverageComputation(
                Computations.of(booleanAssignmentList));

        CoverageStatistic statistics = tWiseCoverageComputation.compute(dependencyList, new Progress()).get();
        return statistics;
    }
}
