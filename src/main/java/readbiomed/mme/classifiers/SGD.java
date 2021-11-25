package readbiomed.mme.classifiers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import gov.nih.nlm.nls.mti.classifiers.ova.OVAClassifier;
import gov.nih.nlm.nls.mti.classifiers.ova.Prediction;
import gov.nih.nlm.nls.mti.classifiers.ova.bf.lf.HingeLoss;
import gov.nih.nlm.nls.mti.classifiers.ova.bf.lf.LF;
import gov.nih.nlm.nls.mti.classifiers.ova.bf.lf.ModifiedHuberLoss;
import gov.nih.nlm.nls.mti.instances.BinaryInstance;
import gov.nih.nlm.nls.mti.instances.Instance;
import gov.nih.nlm.nls.mti.instances.Instances;

/**
 * {@link OVAClassifier} that trains a classifier based on Stochastic Gradient
 * Descent <br/>
 * <br/>
 * Options to be used with the classifier: <br/>
 * <br/>
 * -td - d is the threshold, the default value is 0 <br/>
 * <br/>
 * -ad - size of the step at each iteration in gradient descent, the default
 * value is 0.001 <br/>
 * <br/>
 * -ld - regularization parameter lambda, the default value is 0.0001 <br/>
 * <br/>
 * -in - number of iterations, the default value is 100 <br/>
 * <br/>
 * -fc - loss function {@link LF} class name, the default loss function is
 * {@link HingeLoss} <br/>
 * <br/>
 * <code>@inproceedings{zhang2004solving,
  title={Solving large scale linear prediction problems using stochastic gradient descent algorithms},
  author={Zhang, T.},
  booktitle={Proceedings of the twenty-first international conference on Machine learning},
  pages={116},
  year={2004},
  organization={ACM}
}</code>
 * 
 * @author Antonio Jimeno Yepes (antonio.jimeno@gmail.com)
 * 
 *         Adds a seed to the SGD, so it is possible to reproduce the
 *         experiments
 *
 */

public class SGD extends OVAClassifier {
	private double[] w = null;
	private double b = 0;

	private double threshold = 0;

	private double alpha = 0.001;

	private double lambda = 0.0001;

	private int iterations = 100;

	private int seed = 1;

	// No need to serialize
	private LF lf = null;

	public String toString() {
		return SGD.class.getName() + "|alpha=" + alpha + "|lambda=" + lambda + "|iterations=" + iterations
				+ "|threshold=" + threshold + "|loss function=" + lf;
	}

	/**
	 * Default serial number
	 */
	private static final long serialVersionUID = 1L;

	private double h(Instance i) {
		double sum = b;

		BinaryInstance bi = (BinaryInstance) i;

		for (int f : bi.getBinaryFeatures()) {
			if (w.length > f) {
				sum += w[f];
			}
		}

		return sum;
	}

	@Override
	public int predict(Instance i) {
		return (h(i) > threshold ? 1 : 0);
	}

	@Override
	public Prediction predictConfidence(Instance i) {
		double score = h(i);
		return new Prediction((score > threshold ? 1 : 0), score);
	}

	public Prediction predictProbability(Instance i) {
		double score = h(i);

		double probability = 1 / (1 + Math.exp(-score));

		if (score > threshold) {
			return new Prediction(1, probability);
		} else {
			return new Prediction(0, probability);
		}
	}

	public void setup(String options) {
		if (options != null && options.length() > 0) {
			String[] tokens = options.split(" ");

			for (String option : tokens) {
				if (option.startsWith("-t")) {
					threshold = Double.parseDouble(option.replaceAll("-t", ""));
				} else if (option.startsWith("-a")) {
					alpha = Double.parseDouble(option.replaceAll("-a", ""));
				} else if (option.startsWith("-l")) {
					lambda = Double.parseDouble(option.replaceAll("-l", ""));
				} else if (option.startsWith("-i")) {
					iterations = Integer.parseInt(option.replaceAll("-i", ""));
				} else if (option.startsWith("-s")) {
					seed = Integer.parseInt(option.replaceAll("-s", ""));
				} else if (option.startsWith("-f")) {
					try {
						lf = (LF) Class.forName(option.replaceAll("-f", "")).getDeclaredConstructor().newInstance();
					} catch (Exception e) {
						System.err.println("Failed loading: " + option.replaceAll("-f", ""));
						System.err.println("Using Hinge loss (SVM) as default.");
					}
				}
			}
		}
	}

	private int maxFeatureId(Map<Integer, String> termMap) {
		int mfId = 0;

		for (Integer fid : termMap.keySet()) {
			if (fid > mfId) {
				mfId = fid;
			}
		}

		return mfId;
	}

	private void initW() {
		b = 0.0000000000000001;

		for (int i = 0; i < w.length; i++) {
			w[i] = 0.0000000000000001;
		}
	}

	public double lossExpectation(LF lf, List<Instance> is, Set<Instance> positives) {
		double sum = 0;

		for (Instance i : is) {
			sum += lf.loss(h(i), (positives.contains(i) ? 1 : -1));
		}

		return sum / is.size();
	}

	public void train(Instances is, Map<Integer, String> termMap) {
		if (lf == null) {
			lf = new ModifiedHuberLoss();
		}

		// Print the algorithm parameters
		System.out.println("Alpha: " + alpha);
		System.out.println("Lambda: " + lambda);
		System.out.println("Threshold: " + threshold);
		System.out.println("Iterations: " + iterations);
		System.out.println("Loss function: " + lf.getClass().getName());

		Set<Instance> positives = is.getCategoryInstances().get(getCategory());

		// Initialize the w vector with max feature id in set
		w = new double[maxFeatureId(termMap) + 1];

		initW();

		List<Instance> il = new ArrayList<Instance>(is.getInstances());

		for (int i = 0; i < iterations; i++) {
			// Shuffle examples
			Collections.shuffle(il, new Random(seed));

			for (Instance ins : il) {
				BinaryInstance bins = (BinaryInstance) ins;

				int y = (positives.contains(ins) ? 1 : -1);

				// Update w, feature value is always one, optimized for this
				double gradient = lf.gradient(h(ins), bins.getBinaryFeatures(), y);

				if (gradient != 0.0)
				{
					b = b - alpha * (gradient + lambda * b);

					for (int k : bins.getBinaryFeatures()) {
						w[k] = w[k] - alpha * (gradient + lambda * w[k]);
					}
				}
			}

			if (i % 10 == 0) {
				System.out.println("Iteration: " + i);
				System.out.println(lossExpectation(lf, il, positives));
			}
		}

		System.out.println("Vector length: " + w.length);
		System.out.println("b: " + b);
		for (int i = 0; i < w.length; i++) {
			System.out.println(termMap.get(i) + "|w[" + i + "]: " + w[i]);
		}
	}
}