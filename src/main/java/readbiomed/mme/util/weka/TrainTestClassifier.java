package readbiomed.mme.util.weka;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import weka.classifiers.Evaluation;
import weka.classifiers.meta.AdaBoostM1;
import weka.classifiers.meta.FilteredClassifier;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;
import weka.core.tokenizers.NGramTokenizer;
import weka.filters.unsupervised.attribute.StringToWordVector;

/**
 * 
 * Evaluation of Weka text classification
 * 
 * @author antonio.jimeno@gmail.com (antonio.jimeno@gmail.com)
 *
 */
public class TrainTestClassifier {
	public static Instances loadDataset(String filename, ArrayList<Attribute> attributes) throws IOException {
		Instances dataset = new Instances("Text classifier", attributes, 1000);

		dataset.setClassIndex(0);

		/**
		 * Read data file, parse text and add to instance
		 */
		try (BufferedReader b = new BufferedReader(new FileReader(filename))) {
			b.readLine();
			for (String line; (line = b.readLine()) != null;) {
				String[] tokens = line.split("\\|");

				// basic validation
				if (!tokens[0].isEmpty() && !tokens[1].isEmpty()) {

					DenseInstance row = new DenseInstance(2);
					row.setValue(attributes.get(0), tokens[1]);
					row.setValue(attributes.get(1), tokens[0]);

					// add row to instances
					dataset.add(row);
				}
			}

		}
		
		return dataset;
	}

	public static void main(String[] argc) throws Exception {
		FilteredClassifier classifier = new FilteredClassifier();
		
		AdaBoostM1 c = null;
		c = new AdaBoostM1();
		String [] adaOptions = {"-W", "weka.classifiers.trees.J48", "-output-debug-info"};
		c.setOptions(adaOptions);
		classifier.setClassifier(c);
		
		//SMO c = new SMO();
		//String [] smoOptions = {"-output-debug-info"};
		//c.setOptions(smoOptions);
		
		classifier.setClassifier(c);

		{
			System.out.println("Loading training data");
			Attribute attributeText = new Attribute("textxxxxxxxx", (List<String>) null);

			ArrayList<String> classAttributeValues = new ArrayList<>();
			classAttributeValues.add("Y");
			classAttributeValues.add("N");
			Attribute classAttribute = new Attribute("labelxxxxxxxx", classAttributeValues);

			ArrayList<Attribute> wekaAttributes = new ArrayList<>();
			wekaAttributes.add(classAttribute);
			wekaAttributes.add(attributeText);

			Instances train = loadDataset("/Users/ajimeno/Documents/MTI_ML/excel.train.pipe", wekaAttributes);

			System.out.println("Training classifier");
			StringToWordVector filter = new StringToWordVector();
			filter.setAttributeIndices("last");
			NGramTokenizer tokenizer = new NGramTokenizer();
			tokenizer.setNGramMinSize(1);
			tokenizer.setNGramMaxSize(1);
			tokenizer.setDelimiters("\\W");
			filter.setTokenizer(tokenizer);
			filter.setLowerCaseTokens(true);
			filter.setWordsToKeep(1000000000);
			classifier.setFilter(filter);

			classifier.buildClassifier(train);
			System.out.println(classifier.toString());
		}
		{
			System.out.println("Testing classifier");
			Attribute attributeText = new Attribute("textxxxxxxxx", (List<String>) null);

			ArrayList<String> classAttributeValues = new ArrayList<>();
			classAttributeValues.add("Y");
			classAttributeValues.add("N");
			Attribute classAttribute = new Attribute("labelxxxxxxxx", classAttributeValues);

			ArrayList<Attribute> wekaAttributes = new ArrayList<>();
			wekaAttributes.add(classAttribute);
			wekaAttributes.add(attributeText);

			Instances testData = loadDataset("/Users/ajimeno/Documents/MTI_ML/excel.test.pipe", wekaAttributes);

			Evaluation eval = new Evaluation(testData);
			eval.evaluateModel(classifier, testData);
			System.out.println(eval.toSummaryString());
			System.out.println(eval.toClassDetailsString());
		}
	}
}