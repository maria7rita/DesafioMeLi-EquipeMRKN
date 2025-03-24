package org.sbpo2025.challenge;

import org.apache.commons.lang3.time.StopWatch;

import com.google.ortools.Loader;
import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPObjective;
import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPVariable;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class ChallengeSolver {
	private final long MAX_RUNTIME = 600000; // milliseconds; 10 minutes
	protected List<Map<Integer, Integer>> orders;
	protected List<Map<Integer, Integer>> aisles;
	protected int nItems;
	protected int waveSizeLB;
	protected int waveSizeUB;
	protected int nOrders;
	protected int nAisles;

	public ChallengeSolver(List<Map<Integer, Integer>> orders, List<Map<Integer, Integer>> aisles, int nItems, int waveSizeLB, int waveSizeUB) {
		this.orders = orders;
		this.aisles = aisles;
		this.nItems = nItems;
		this.waveSizeLB = waveSizeLB;
		this.waveSizeUB = waveSizeUB;
		this.nOrders = orders.size();
		this.nAisles = aisles.size();
	}
	
	public void writeOutput(Individual individual, String outputFilePath) {
		
		ChallengeSolution challengeSolution = new ChallengeSolution(individual.usedOrders, individual.usedAisles);
		
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilePath));
			var orders = challengeSolution.orders();
			var aisles = challengeSolution.aisles();

			// Write the number of orders
			writer.write(String.valueOf(orders.size()));
			writer.newLine();

			// Write each order
			for (int order : orders) {
				writer.write(String.valueOf(order));
				writer.newLine();
			}

			// Write the number of aisles
			writer.write(String.valueOf(aisles.size()));
			writer.newLine();

			// Write each aisle
			for (int aisle : aisles) {
				writer.write(String.valueOf(aisle));
				writer.newLine();
			}

			writer.close();
			System.out.println("Output written to " + outputFilePath);

		} catch (IOException e) {
			System.err.println("Error writing output to " + outputFilePath);
			e.printStackTrace();
		}
	}
	
	public void solve(StopWatch stopWatch, int nGenerations, int populationSize, double[][] aisleRate, 
			double elitePercent, double mutantPercent, double percEliteHeritage, String outputFilePath) throws CloneNotSupportedException {
		Metaheuristic ag = new Metaheuristic(stopWatch, nGenerations, populationSize, aisleRate, elitePercent, mutantPercent, percEliteHeritage);
		ag.run(outputFilePath);
	}

	protected long getRemainingTime(StopWatch stopWatch) {
		return Math.max(TimeUnit.SECONDS.convert(MAX_RUNTIME - stopWatch.getTime(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS), 0);
	}


	/* --- Novas classes criadas ---*/
	public class Individual implements Cloneable {
		protected int[] chromosome;
		Set<Integer> usedOrders;
		Set<Integer> usedAisles;
		double productivity;

		public Individual() {
			chromosome = new int[nAisles];
			usedOrders = new HashSet<>();
			usedAisles = new HashSet<>();
			productivity = java.lang.Double.NEGATIVE_INFINITY;
		}

		public void encodeChromosome(double[][] aisleRate) {
			double funcScore = Math.random();
			double[] rateAdotado;
			if (funcScore <= 0.33) {
				rateAdotado = aisleRate[0];
			} else if (funcScore <= 0.66)	{
				rateAdotado = aisleRate[1];
			} else {
				rateAdotado = aisleRate[2];
			}
			
			for (int i = 0; i < nAisles; i++) {
				if (Math.random() < rateAdotado[i]) {
					chromosome[i] = 1;
				}
			}
		}

		public void decodeChromosome(Cache cache) {
			for (int i = 0; i < nAisles; i++) {
				if (chromosome[i] == 1) {
					usedAisles.add(i);
				}
			}
			
			if (usedAisles.size() > 0)	{
				if (!cache.containsKey(usedAisles))	{			
					Loader.loadNativeLibraries();

					MPSolver solver = MPSolver.createSolver("SCIP");
					double infinity = java.lang.Double.POSITIVE_INFINITY;

					int[] somaItensPorCorredor = new int[nItems];
					for (int j = 0; j < nItems; j++) {
						for (Integer aisleId : usedAisles) {
							if (aisles.get(aisleId).containsKey(j)) {
								somaItensPorCorredor[j] += aisles.get(aisleId).get(j);
							}
						}
					}

					int[] somaItensPedido = new int[nOrders];
					for (int i = 0; i < nOrders; i++) {
						for (int j = 0; j < nItems; j++) {
							if (orders.get(i).containsKey(j)) {
								somaItensPedido[i] += orders.get(i).get(j);
							}
						}
					}

					MPVariable[] x = new MPVariable[nOrders];
					for (int i = 0; i < nOrders; i++) {
						x[i] = solver.makeBoolVar("x["+i+"]");
					}

					MPObjective objective = solver.objective();     
					for (int i = 0; i < nOrders; i++) {
						objective.setCoefficient(x[i], somaItensPedido[i] * (1.0/usedAisles.size()));
					}
					objective.setMaximization();

					for (int j = 0; j < nItems; j++) {
						MPConstraint c = solver.makeConstraint(-infinity, somaItensPorCorredor[j], "Item " + j);
						for (int i = 0; i < nOrders; i++) {
							if (orders.get(i).containsKey(j)) {
								c.setCoefficient(x[i], orders.get(i).get(j));
							}
						}
					}

					MPConstraint c = solver.makeConstraint(waveSizeLB, waveSizeUB, "Tamanho da wave");
					for (int i = 0; i < nOrders; i++) {
						c.setCoefficient(x[i], somaItensPedido[i]);
					}
					
					MPSolver.ResultStatus resultStatus = solver.solve();

					if (resultStatus == MPSolver.ResultStatus.OPTIMAL) {
						for (int i = 0; i < nOrders; i++) {
							if (x[i].solutionValue() > 0.5) {
								usedOrders.add(i);
							}
						}
						productivity = solver.objective().value();
					}
					solver.reset();
					cache.putFitness(usedAisles, productivity);
				} else {
					productivity = cache.getFitness(usedAisles);
				}
			}
		}

		@Override
		protected Object clone() throws CloneNotSupportedException {
			return super.clone();
		}
	}

	public class Metaheuristic {
		protected int nGenerations;
		protected int populationSize;
		protected double elitePercent;
		protected double mutantPercent;
		protected double[][] aisleRate;
		protected double percEliteHeritage;
		protected Random random;
		protected StopWatch stopWatch;
		protected Individual bestIndividual;
		protected Cache cache;

		public Metaheuristic(StopWatch stopWatch, int nGenerations, int populationSize, double[][] aisleRate, double elitePercent, double mutantPercent, double percEliteHeritage) {
			this.nGenerations = nGenerations;
			this.populationSize = populationSize;
			this.aisleRate = aisleRate;
			this.elitePercent = elitePercent;
			this.mutantPercent = mutantPercent;
			this.percEliteHeritage = percEliteHeritage;
			random = new Random();
			this.stopWatch = stopWatch;
			bestIndividual = new Individual();
			cache = new Cache();
		}

		public void run(String outputFilePath) throws CloneNotSupportedException {
			Individual[] population = new Individual[populationSize];
			for (int i = 0; i < nGenerations; i++) {
				if (i == 0) {
					for (int j = 0; j < populationSize; j++) {
						population[j] = new Individual();
						population[j].encodeChromosome(aisleRate);
						population[j].decodeChromosome(cache);
						if (population[j].productivity > bestIndividual.productivity) {
							bestIndividual = (Individual) population[j].clone();
							writeOutput(bestIndividual, outputFilePath);
							if (getRemainingTime(stopWatch) < 10) {
								return;
							}
						}
					}
				} else {
					Arrays.sort(population, Comparator.comparingDouble(ind -> -ind.productivity));
					Individual[] newPopulation = new Individual[populationSize];               
					int eliteSize = (int) (elitePercent * populationSize);
					int mutantSize = (int) (mutantPercent * populationSize);

					for (int j = 0; j < eliteSize; j++) {
						newPopulation[j] = population[j];
					}

					for (int j = eliteSize; j < eliteSize + mutantSize; j++) {
						newPopulation[j] = new Individual();
						newPopulation[j].encodeChromosome(aisleRate);
						newPopulation[j].decodeChromosome(cache);
						if (newPopulation[j].productivity > bestIndividual.productivity) {
							bestIndividual = (Individual) newPopulation[j].clone();
							writeOutput(bestIndividual, outputFilePath);
							if (getRemainingTime(stopWatch) < 10) {
								return;
							}
						}
					}

					for (int j = eliteSize + mutantSize; j < populationSize; j++) {
						Individual parent1 = population[random.nextInt(eliteSize)];
						Individual parent2 = population[eliteSize + random.nextInt(populationSize - eliteSize)];
						Individual offspring = crossover(parent1, parent2, percEliteHeritage);
						offspring.decodeChromosome(cache);
						newPopulation[j] = offspring;
						if (newPopulation[j].productivity > bestIndividual.productivity) {
							bestIndividual = (Individual) newPopulation[j].clone();
							writeOutput(bestIndividual, outputFilePath);
							if (getRemainingTime(stopWatch) < 10) {
								return;
							}
						}
					}          
					population = newPopulation;
				}
				//System.out.println("Generation " + i + ": " + bestIndividual.productivity);
				System.gc();
			}
		}

		public Individual crossover(Individual parent1, Individual parent2, double percEliteHeritage)	{
			Individual offspring = new Individual();
			for (int i = 0; i < nAisles; i++) {
				if (Math.random() < percEliteHeritage) {
					offspring.chromosome[i] = parent1.chromosome[i];
				} else {
					offspring.chromosome[i] = parent2.chromosome[i];
				}
			}
			return offspring;
		}
	}

	public class Cache {
		protected Map<Set<Integer>, Double> storage;

		public Cache() {
			storage = new HashMap<>();
		}

		public boolean containsKey(Set<Integer> usedAisles) {
			return storage.containsKey(usedAisles);
		}

		public double getFitness(Set<Integer> usedAisles) {
			return storage.get(usedAisles);
		}

		public void putFitness(Set<Integer> usedAisles, double fitness) {
			storage.put(usedAisles, fitness);
		}
	}
}
