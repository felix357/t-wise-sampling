/*
 * This source file was generated by the Gradle 'init' task
 */
package org.example;

import java.io.File;

import org.example.commands.SamplingExecutionCommand;
import org.example.common.SamplingAlgorithm;
import org.example.common.SamplingConfig;
import org.example.common.TWiseCalculator;
import org.example.out.ResultWriter;
import org.example.parsing.FeatureModelParser;

import de.featjar.analysis.sat4j.computation.ComputeCoreDeadMIG;
import de.featjar.analysis.sat4j.computation.ComputeCoreSAT4J;
import de.featjar.analysis.sat4j.computation.YASA;
import de.featjar.analysis.sat4j.twise.CoverageStatistic;
import de.featjar.base.computation.Computations;
import de.featjar.base.computation.IComputation;
import de.featjar.base.data.Pair;
import de.featjar.formula.VariableMap;
import de.featjar.formula.assignment.BooleanAssignment;
import de.featjar.formula.assignment.BooleanClauseList;
import de.featjar.formula.assignment.BooleanSolutionList;
import de.featjar.formula.assignment.ComputeBooleanClauseList;
import de.featjar.formula.computation.ComputeCNFFormula;
import de.featjar.formula.computation.ComputeNNFFormula;
import de.featjar.formula.structure.IFormula;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(description = "Main application for sampling Analysis.")
public class SamplingAnalyzer {

    public static SamplingConfig samplingConfig = new SamplingConfig(SamplingAlgorithm.YASA, 2);
    public static File inputDir;
    public static File outputDir;

    public static void main(String[] args) {

        CommandLine commandLine = new CommandLine(new SamplingAnalyzer());

        commandLine.addSubcommand(new SamplingExecutionCommand());
        int exitCode = commandLine.execute(args);

        String input_file_name = inputDir.getName();

        IFormula formula = FeatureModelParser.convertXMLToFormula(input_file_name);

        ComputeBooleanClauseList cnf = Computations.of(formula)
                .map(ComputeNNFFormula::new)
                .map(ComputeCNFFormula::new)
                .set(ComputeCNFFormula.IS_PLAISTED_GREENBAUM, Boolean.TRUE)
                .map(ComputeBooleanClauseList::new);

        Pair<BooleanClauseList, VariableMap> computedCNF = cnf.compute();
        BooleanClauseList booleanClauseList = computedCNF.getKey();
        VariableMap variables = computedCNF.getValue();

        IComputation<BooleanClauseList> clauseListComputation = Computations.of(booleanClauseList);
        BooleanAssignment core = clauseListComputation.map(ComputeCoreSAT4J::new).compute();
        BooleanAssignment coreAndDeadFeatures = clauseListComputation.map(ComputeCoreDeadMIG::new).compute();

        BooleanSolutionList sample = null;

        if (samplingConfig.getSamplingAlgorithm() == SamplingAlgorithm.YASA) {
            YASA yasa = new YASA(clauseListComputation);
            sample = yasa.compute();
        }

        CoverageStatistic coverageStatistic = TWiseCalculator.computeTWiseStatistics(booleanClauseList, core, sample,
                samplingConfig.getT());

        ResultWriter.writeResultToFile(outputDir, coreAndDeadFeatures, sample, booleanClauseList, samplingConfig.getT(),
                samplingConfig.getSamplingAlgorithm(), coverageStatistic, variables);

        System.exit(exitCode);
    }
}