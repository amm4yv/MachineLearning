import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

public class BayesClassifier extends Classifier {

	private String[] output;
	private ArrayList<FeatureHeader> features;
	private ArrayList<ArrayList<DataSet>> data;
	private double[][] featureValues;
	private double[][] featureValues0;
	private double[][] featureValues1;
	private double[][] truth;
	private double output0P;

	public BayesClassifier(String namesFilepath) {
		super(namesFilepath);
		features = new ArrayList<FeatureHeader>();
		data = new ArrayList<ArrayList<DataSet>>();

		Scanner file = readFile(namesFilepath);
		output = file.nextLine().split("\\s+");
		for (String s : output)
			data.add(new ArrayList<DataSet>());

		file.nextLine();
		while (file.hasNextLine()) {
			String[] data = file.nextLine().split("\\s+");
			features.add(new FeatureHeader(data));
		}
	}

	@Override
	public void train(String trainingDataFilpath) {
		Scanner file = readFile(trainingDataFilpath);

		while (file.hasNextLine()) {
			String[] data = file.nextLine().split("\\s+");
			String result = data[data.length - 1];
			for (int i = 0; i < output.length; i++)
				if (result.equals(output[i]))
					this.data.get(i).add(new DataSet(data, features, output));
		}

		int rows0 = this.data.get(0).size();
		int rows1 = this.data.get(1).size();
		int cols = this.features.size();
		this.output0P = (double) rows0 / (rows0 + rows1);

		featureValues0 = new double[rows0][cols + 1];
		featureValues1 = new double[rows1][cols + 1];

		featureValues = new double[rows0 + rows1][cols + 1];
		truth = new double[rows0 + rows1][1];

		for (int i = 0; i < rows0; i++) {
			featureValues[i] = this.data.get(0).get(i).matrix;
			truth[i] = this.data.get(0).get(i).outputIndex;
			featureValues0[i] = this.data.get(0).get(i).matrix;
			;
		}

		for (int i = 0; i < rows1; i++) {
			featureValues[i + rows0] = this.data.get(1).get(i).matrix;
			truth[i + rows0] = this.data.get(1).get(i).outputIndex;
			featureValues1[i] = this.data.get(1).get(i).matrix;
		}


		for (double[] row : featureValues) {
			for (int i = 0; i < cols; i++) {
				if (!features.get(i).isNumeric()) {
					features.get(i).probabilities[0][(int) row[i + 1] - 1] = findProbability(
							i, row[i + 1], 0);
					features.get(i).probabilities[1][(int) row[i + 1] - 1] = findProbability(
							i, row[i + 1], 1);
				}
			}
		}


		testAndTrain("trainingData/censusShort.train");

	}

	public void testAndTrain(String fileName) {
		Scanner file = readFile(fileName);

		while (file.hasNextLine()) {
			String[] line = file.nextLine().split("\\s+");

			DataSet data = new DataSet(line, features, null);
			double[] values = data.matrix;

			double p0 = output0P;
			double p1 = 1 - output0P;

			for (int i = 0; i < features.size(); i++) {
				p0 *= calculateProbability(i, values[i + 1], 0);
				p1 *= calculateProbability(i, values[i + 1], 1);
			}

			String out = p0 > p1 ? output[0] : output[1];

			if (!out.equals(data.output)) {
				int outIndex = (int) data.outputIndex[0];
				for (int i = 0; i < features.size(); i++) {
					if (!features.get(i).isNumeric()) {
						// for (int j = 0; j <
						// features.get(i).probabilities[0].length; j++) {
						features.get(i).probabilities[outIndex][(int) values[i + 1] - 1] *= 1.001;
						features.get(i).probabilities[1 - outIndex][(int) values[i + 1] - 1] *= .999;
						// }
					}
				}
			}
		}
	}

	public double[] getMeanVariance(int index, int output) {
		double[] data = new double[2];
		data[0] = 0;
		data[1] = 0;
		int total = 0;

		double[][] values = output == 0 ? featureValues0 : featureValues1;

		for (double[] row : values) {
			data[0] += row[index];
			total++;
		}

		data[0] /= total;

		for (double[] row : values)
			data[1] += Math.pow(row[index] - data[0], 2);

		data[1] /= total;
		return data;
	}

	public double findProbability(int index, double value, int output) {

		int total = 0;
		// Adding 1 as smoothing factor so Jl is just j
		int j = features.get(index).values.length;
		double[][] values = output == 0 ? featureValues0 : featureValues1;

		for (double[] row : values) {
			if (row[index + 1] == value)
				total++;
		}

		return (double) (total + 1)
				/ (double) (this.data.get(output).size() + j);

	}

	@Override
	public void makePredictions(String testDataFilepath) {
		Scanner file = readFile(testDataFilepath);

		int count = 1;
		int correct = 0;

		while (file.hasNextLine()) {
			String[] line = file.nextLine().split("\\s+");

			DataSet data = new DataSet(line, features, null);
			double[] values = data.matrix;

			double p0 = output0P;
			double p1 = 1 - output0P;

			// System.out.print(p0 + " " + p1 + " ");

			for (int i = 0; i < features.size(); i++) {
				p0 *= calculateProbability(i, values[i + 1], 0);
				p1 *= calculateProbability(i, values[i + 1], 1);
			}

			// System.out.print(p0 + " " + p1 + " ");

			String out = p0 > p1 ? output[0] : output[1];
			System.out.println(out);
			if (out.equals(data.output))
				correct++;
			count++;
		}

		//System.out.println((double) correct / (count - 1));

	}

	public double calculateProbability(int i, double value, int output) {
		if (features.get(i).isNumeric()) {
			double[] mv = getMeanVariance(i + 1, output);
			// System.out.print("mean: " + mv[0] + " variance: " + mv[1]);
			double pow = -(Math.pow(value - mv[0], 2)) / (2 * mv[1]);
			double prob = (Math.pow(1, pow) / Math.sqrt((2 * Math.PI * mv[1])));
			// System.out.println(" " + prob);
			return prob;
		}

		return features.get(i).probabilities[output][(int) value - 1];
		// return findProbability(i, value, output);

	}

	public Scanner readFile(String filename) {
		Scanner s;
		try {
			s = new Scanner(new File(filename));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			s = null;
		}
		return s;
	}

}
