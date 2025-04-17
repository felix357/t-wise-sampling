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
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.FileReader;

import de.featjar.analysis.ddnnife.solver.DdnnifeWrapper;
import de.featjar.analysis.sat4j.computation.ComputeCoreDeadMIG;
import de.featjar.analysis.sat4j.computation.ComputeCoreSAT4J;
import de.featjar.analysis.sat4j.computation.YASA;
import de.featjar.analysis.sat4j.twise.CoverageStatistic;
import de.featjar.base.computation.Computations;
import de.featjar.base.computation.IComputation;
import de.featjar.formula.VariableMap;
import de.featjar.formula.assignment.BooleanAssignment;
import de.featjar.formula.assignment.BooleanAssignmentGroups;
import de.featjar.formula.assignment.BooleanAssignmentList;
import de.featjar.formula.assignment.ComputeBooleanClauseList;
import de.featjar.formula.computation.ComputeCNFFormula;
import de.featjar.formula.computation.ComputeNNFFormula;
import de.featjar.formula.structure.IFormula;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import com.google.gson.Gson;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Command(description = "Main application for sampling Analysis.")
public class SamplingAnalyzer {

        public static SamplingConfig samplingConfig = new SamplingConfig(SamplingAlgorithm.YASA, 2);
        public static File inputDir;
        public static File outputDir;

        public static void main(String[] args) {
                CommandLine commandLine = new CommandLine(new SamplingAnalyzer());

                commandLine.addSubcommand(new SamplingExecutionCommand());
                int exitCode = commandLine.execute(args);

                IFormula formula = FeatureModelParser.convertXMLToFormula(inputDir.toPath().toString());

                ComputeBooleanClauseList cnf = Computations.of(formula).map(ComputeNNFFormula::new)
                                .map(ComputeCNFFormula::new)
                                .map(ComputeBooleanClauseList::new);

                BooleanAssignmentList computedCNF = cnf.compute();
                VariableMap variables = computedCNF.getVariableMap();

                IComputation<BooleanAssignmentList> booleanAssignmentListComputation = Computations.of(computedCNF);

                BooleanAssignmentList sample = null;

                switch (samplingConfig.getSamplingAlgorithm()) {
                        case YASA:
                                int T = samplingConfig.getT();
                                sample = processYasaSampling(computedCNF, T);
                                break;

                        case UNIFORM:
                                sample = processUniformSampling(computedCNF,
                                                samplingConfig.getNumberOfConfigurations());
                                break;

                        case INCLING:
                                sample = processInclingSampling(computedCNF, variables);
                                break;

                        default:
                                System.out.println("Unsupported sampling algorithm.");
                                break;
                }

                BooleanAssignment core = booleanAssignmentListComputation.map(ComputeCoreSAT4J::new).compute();
                BooleanAssignment coreAndDeadFeatures = booleanAssignmentListComputation.map(ComputeCoreDeadMIG::new)
                                .compute();

                CoverageStatistic coverageStatistic = TWiseCalculator.computeTWiseStatistics(computedCNF, core,
                                sample,
                                variables,
                                samplingConfig.getT());

                Long a = TWiseCalculator.computeTWiseCount(sample, samplingConfig.getT(),
                                new BooleanAssignment(new int[] {}),
                                computedCNF,
                                List.of());

                System.out.println("covered: " + a);

                ResultWriter.writeResultToFile(outputDir, coreAndDeadFeatures, sample,
                                samplingConfig.getT(),
                                samplingConfig.getSamplingAlgorithm(), coverageStatistic, variables);

                System.exit(exitCode);
        }

        public static List<int[]> convertToIntArrays(List<BooleanAssignment> booleanAssignments) {
                List<int[]> resultList = new ArrayList<>();
                for (BooleanAssignment assignment : booleanAssignments) {
                        resultList.add(assignment.simplify());
                }

                return resultList;
        }

        public static BooleanAssignmentList loadAssignmentsFromJson(String filePath, VariableMap variableMap) {
                try (FileReader reader = new FileReader(filePath)) {
                        Gson gson = new Gson();
                        Type listType = new TypeToken<List<Map<String, Object>>>() {
                        }.getType();

                        List<Map<String, Object>> jsonList = gson.fromJson(reader, listType);

                        Collection<BooleanAssignment> booleanAssignments = jsonList.stream()
                                        .map(entry -> {
                                                List<Object> literalsRaw = (List<Object>) entry.get("literals");

                                                List<Integer> literals = literalsRaw.stream()
                                                                .map(literal -> {
                                                                        if (literal instanceof Double) {
                                                                                return ((Double) literal).intValue();

                                                                        } else if (literal instanceof Integer) {
                                                                                return (Integer) literal;
                                                                        }
                                                                        throw new IllegalArgumentException(
                                                                                        "Unexpected literal type: "
                                                                                                        + literal.getClass());
                                                                })
                                                                .collect(Collectors.toList());

                                                return new BooleanAssignment(literals);

                                        })
                                        .collect(Collectors.toList());
                        return new BooleanAssignmentList(variableMap, booleanAssignments);

                } catch (IOException e) {
                        e.printStackTrace();
                        return null;
                } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                        return null;
                }
        }

        private static BooleanAssignmentList processYasaSampling(BooleanAssignmentList computedCNF, int T) {
                IComputation<BooleanAssignmentList> yasa = new YASA(Computations.of(computedCNF)).set(YASA.T, T);
                return yasa.compute();
        }

        private static BooleanAssignmentList processUniformSampling(BooleanAssignmentList computedCNF,
                        int numberOfSamples) {
                BooleanAssignmentGroups booleanAssignmentGroups = new BooleanAssignmentGroups(computedCNF);

                try (DdnnifeWrapper solver = new DdnnifeWrapper(booleanAssignmentGroups)) {
                        Random random = new Random();
                        Long seed = random.nextLong(Long.MAX_VALUE);

                        return solver.getRandomSolutions(numberOfSamples, seed).get();
                } catch (Exception e) {
                        e.printStackTrace();
                        return null;
                }
        }

        private static BooleanAssignmentList processInclingSampling(BooleanAssignmentList computedCNF,
                        VariableMap variables) {
                List<String> varNames = variables.getVariableNames();
                List<BooleanAssignment> booleanAssignments = computedCNF.getAll();
                List<int[]> assignments = convertToIntArrays(booleanAssignments);

                writeCnfJson(assignments, varNames);

                Path resultsPath = runInclingJar();

                if (resultsPath != null) {
                        return loadAssignmentsFromJson(resultsPath.toString(), variables);
                }
                return null;
        }

        private static void writeCnfJson(List<int[]> assignments, List<String> varNames) {
                Gson gson = new Gson();
                String assignmentsJson = gson.toJson(assignments);
                String varNamesJson = gson.toJson(varNames);

                try (FileWriter writer = new FileWriter("cnf.json")) {
                        writer.write("{\n");
                        writer.write("\"assignments\": " + assignmentsJson + ",\n");
                        writer.write("\"varNames\": " + varNamesJson + "\n");
                        writer.write("}");
                        System.out.println("Data written to cnf.json.");
                } catch (IOException e) {
                        e.printStackTrace();
                }
        }

        private static Path runInclingJar() {
                try {
                        Path basePath = Paths.get("").toAbsolutePath();
                        Path jarPath = basePath.resolve("app/libs/app-1.0.0-all.jar").normalize();
                        Path cnfPath = basePath.resolve("cnf.json").normalize();
                        Path resultsPath = basePath.resolve("results.json").normalize();

                        ProcessBuilder processBuilder = new ProcessBuilder("java", "-jar",
                                        jarPath.toString(), cnfPath.toString(), resultsPath.toString());
                        processBuilder.inheritIO();

                        Process process = processBuilder.start();
                        int exitCode = process.waitFor();

                        if (exitCode == 0) {
                                return resultsPath;
                        } else {
                                System.err.println("Incling JAR execution failed with exit code: " + exitCode);
                        }
                } catch (Exception e) {
                        e.printStackTrace();
                }
                return null;
        }

}