package org.sbpo2025.challenge;

import org.apache.commons.lang3.time.StopWatch;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Challenge {

	private List<Map<Integer, Integer>> orders;
	private List<Map<Integer, Integer>> aisles;
	private int nItems;
	private int waveSizeLB;
	private int waveSizeUB;

	public void readInput(String inputFilePath) {
		try {
			BufferedReader reader = new BufferedReader(new FileReader(inputFilePath));
			String line = reader.readLine();
			String[] firstLine = line.split(" ");
			int nOrders = Integer.parseInt(firstLine[0]);
			int nItems = Integer.parseInt(firstLine[1]);
			int nAisles = Integer.parseInt(firstLine[2]);

			// Initialize orders and aisles arrays
			orders = new ArrayList<>(nOrders);
			aisles = new ArrayList<>(nAisles);
			this.nItems = nItems;

			// Read orders
			readItemQuantityPairs(reader, nOrders, orders);

			// Read aisles
			readItemQuantityPairs(reader, nAisles, aisles);

			// Read wave size bounds
			line = reader.readLine();
			String[] bounds = line.split(" ");
			waveSizeLB = Integer.parseInt(bounds[0]);
			waveSizeUB = Integer.parseInt(bounds[1]);

			reader.close();
		} catch (IOException e) {
			System.err.println("Error reading input from " + inputFilePath);
			e.printStackTrace();
		}
	}

	private void readItemQuantityPairs(BufferedReader reader, int nLines, List<Map<Integer, Integer>> orders) throws IOException {
		String line;
		for (int orderIndex = 0; orderIndex < nLines; orderIndex++) {
			line = reader.readLine();
			String[] orderLine = line.split(" ");
			int nOrderItems = Integer.parseInt(orderLine[0]);
			Map<Integer, Integer> orderMap = new HashMap<>();
			for (int k = 0; k < nOrderItems; k++) {
				int itemIndex = Integer.parseInt(orderLine[2 * k + 1]);
				int itemQuantity = Integer.parseInt(orderLine[2 * k + 2]);
				orderMap.put(itemIndex, itemQuantity);
			}
			orders.add(orderMap);
		}
	}

	public double[][] getAisleRates(List<Map<Integer, Integer>> orders, List<Map<Integer, Integer>> aisles, int nItems) {
		
		double[][] percItensServed = new double[aisles.size()][orders.size()];
		double[] aisleScoreI = new double[aisles.size()];
        double[] aisleScoreII = new double[aisles.size()];
        double[] aisleScoreIII = new double[aisles.size()];

		for (int i = 0; i < aisles.size(); i++) {
			aisleScoreII[i] = 0.05;
			double weightedSum = 0;
            double totalWeight = 0;
			Map<Integer, Integer> aisle = aisles.get(i);
			for (int j = 0; j < orders.size(); j++) {
				Map<Integer, Integer> order = orders.get(j);
				int totalItems = order.values().stream().mapToInt(Integer::intValue).sum();
				for (Integer key : aisle.keySet()) {
					if (order.containsKey(key)) {
						double count = Math.min(order.get(key), aisle.get(key));
						percItensServed[i][j] += count;
					}
				}
				percItensServed[i][j] = percItensServed[i][j]/totalItems;
				aisleScoreI[i] += percItensServed[i][j];		
				weightedSum += percItensServed[i][j] * totalItems;
                totalWeight += totalItems;
			}
            aisleScoreIII[i] = (totalWeight > 0) ? weightedSum / totalWeight : 0;
			aisleScoreI[i] = aisleScoreI[i] / orders.size();
		}
		
		double[][] aisleRates = new double[3][aisles.size()];
		aisleRates[0] = aisleScoreI;
		aisleRates[1] = aisleScoreII;
		aisleRates[2] = aisleScoreIII;
		return aisleRates;
	}

	public static void main(String[] args) throws CloneNotSupportedException, IOException {

		int nGenerations = 1000;
		int populationSize = 50;
		double elitePercent = 0.30;
		double mutantPercent = 0.20;
		double percEliteHeritage = 0.7;

		String input = args[0]; //".\\datasets\\a\\instance_0001.txt";			//args[0]
		String output = args[1]; //".\\target\\teste";							//args[1]
		StopWatch stopWatch = StopWatch.createStarted();

		if (args.length != 2) {
             System.out.println("Usage: java -jar target/ChallengeSBPO2025-1.0.jar <inputFilePath> <outputFilePath>");
             return;
        }

		Challenge challenge = new Challenge();
		challenge.readInput(input);

		double[][] aisleRate = challenge.getAisleRates(challenge.orders, challenge.aisles, challenge.nItems);
		
		var challengeSolver = new ChallengeSolver(challenge.orders, challenge.aisles, challenge.nItems, challenge.waveSizeLB, challenge.waveSizeUB);
		challengeSolver.solve(stopWatch, nGenerations, populationSize, aisleRate, elitePercent, mutantPercent, percEliteHeritage, output);

	}

}
