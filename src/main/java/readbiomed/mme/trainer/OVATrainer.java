package readbiomed.mme.trainer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import gov.nih.nlm.nls.mti.classifiers.ClassifierFactory;
import gov.nih.nlm.nls.mti.classifiers.ova.OVAClassifier;
import gov.nih.nlm.nls.mti.datasetfilters.DataSetFilterFactory;
import gov.nih.nlm.nls.mti.documents.Document;
import gov.nih.nlm.nls.mti.featuresextractors.FeatureExtractor;
import gov.nih.nlm.nls.mti.featuresextractors.FeatureExtractorFactory;
import gov.nih.nlm.nls.mti.instances.Instances;
import gov.nih.nlm.nls.mti.textprocessors.TextProcessor;
import gov.nih.nlm.nls.mti.textprocessors.TextProcessorFactory;
import readbiomed.mme.util.Trie;

/**
 * Trainer of {@link OVAClassifier} provided in a configuration file. The
 * generated models are serialized for posterior use. The training data is
 * expected from the standard input.
 * 
 * <code>gov.nih.nlm.nls.mti.trainer.OVATrainer text_extractor_class text_extractor_options feature_processor_class feature_processor_options configuration_file dictionary_file output_classifiers_file</code>
 * 
 * @author Antonio Jimeno Yepes (antonio.jimeno@gmail.com)
 *
 */
public class OVATrainer {
	private class FilterConfiguration {
		private String filter_name;
		private String filter_options;

		FilterConfiguration(String filter_name, String filter_options) {
			this.filter_name = filter_name;
			this.filter_options = filter_options;
		}

		public String getFilterName() {
			return filter_name;
		}

		public String getFilterOptions() {
			return filter_options;
		}
	}

	private class ClassifierConfiguration {
		private List<FilterConfiguration> fc = null;

		private String classifier_name;
		private String classifier_options;

		public ClassifierConfiguration(List<FilterConfiguration> fc, String classifier_name,
				String classifier_options) {
			this.fc = fc;
			this.classifier_name = classifier_name;
			this.classifier_options = classifier_options;
		}

		public List<FilterConfiguration> getFilterConfigurations() {
			return fc;
		}

		public String getClassifierName() {
			return classifier_name;
		}

		public String getClassifierOptions() {
			return classifier_options;
		}
	}

	private static String help() {
		return new StringBuilder(
				"OVATrainer text_extractor_class text_extractor_options feature_processor_class feature_processor_options configuration_file dictionary_file output_classifiers_file")
						.toString();
	}

	public static void main(String[] argc)
			throws ClassNotFoundException, InstantiationException, IllegalAccessException, IOException {
		if (argc.length != 7) {
			System.err.println(help());
			System.exit(-1);
		}

		// Load trie and classifiers if the file already exists
		Trie<Integer> trie_terms = null;
		Map<String, OVAClassifier> classifiers = null;
		Map<Integer, String> term_map = new HashMap<Integer, String>();

		File trie_file = new File(argc[5]);

		if (trie_file.exists()) {
			try {
				ObjectInputStream ot = new ObjectInputStream(new GZIPInputStream(new FileInputStream(argc[5])));
				trie_terms = (Trie<Integer>) ot.readObject();
				ot.close();

				term_map = trie_terms.getTermMap();

				System.err.println("Trie loaded: " + argc[5]);
				System.err.println("Trie size: " + trie_terms.size());
				System.err.println("Term map size: " + term_map.size());
			} catch (Exception e) {
				System.err.println("Error loading trie file, creating a new trie and classifier store objects");
				e.printStackTrace();

				trie_terms = new Trie<Integer>();
				classifiers = new HashMap<String, OVAClassifier>();
			}
		} else {
			System.err.println("New trie created");
			trie_terms = new Trie<Integer>();
		}

		if (classifiers == null) {
			File classifiers_file = new File(argc[6]);

			if (classifiers_file.exists()) {
				try {
					ObjectInputStream oc = new ObjectInputStream(new GZIPInputStream(new FileInputStream(argc[6])));
					classifiers = (Map<String, OVAClassifier>) oc.readObject();
					oc.close();

					System.out.println("Classifiers file loaded.");
				} catch (Exception e) {
					System.err.println("Error loading classifier file, creating a new classifier store object");
					e.printStackTrace();

					classifiers = new HashMap<String, OVAClassifier>();
				}
			} else {
				System.err.println("New classifiers storage created");
				classifiers = new HashMap<String, OVAClassifier>();
			}
		}

		Trie<Integer> trie_categories = new Trie<Integer>();

		Map<String, ClassifierConfiguration> categories = new HashMap<String, ClassifierConfiguration>();

		// Read configuration input
		BufferedReader b = new BufferedReader(new FileReader(argc[4]));

		String line;

		while ((line = b.readLine()) != null) {
			String[] tokens = line.split("\\|");

			if (tokens.length % 2 == 1 && tokens.length > 2) {
				// Read the filter configurations
				List<FilterConfiguration> fc = new LinkedList<FilterConfiguration>();

				int fc_count = (tokens.length - 3) / 2;

				for (int i = 0; i < fc_count; i++) {
					fc.add(new OVATrainer().new FilterConfiguration(tokens[1 + (2 * i)], tokens[2 + (2 * i)]));
				}

				ClassifierConfiguration cc = new OVATrainer().new ClassifierConfiguration(fc, tokens[tokens.length - 2],
						tokens[tokens.length - 1]);

				for (String category_name : tokens[0].split(";")) {
					categories.put(category_name, cc);
				}
			} else {
				System.err.println("Error in line expected odd number of arguments in configuration file");
				System.exit(-1);
			}
		}

		b.close();

		TextProcessor tp = TextProcessorFactory.create(argc[0], argc[1], System.in, trie_categories);

		FeatureExtractor fe = FeatureExtractorFactory.create(argc[2], argc[3], trie_terms, term_map);

		Document d = null;

		Instances is = new Instances();

		while ((d = tp.nextDocument()) != null) {
			is.addInstance(fe.prepareInstance(d), d.getCategories());
		}

		System.err.println("Features: " + trie_terms.size());
		System.err.println("Categories: " + trie_categories.size());

		// post-filtering
		is = fe.postfilter(is);

		// Training
		for (Map.Entry<String, ClassifierConfiguration> cc : categories.entrySet()) {
			Integer category = trie_categories.get(cc.getKey());

			// System.out.println(category);
			System.out.println("Category: " + cc.getKey());

			// Train classifier with sampling
			OVAClassifier c = (OVAClassifier) ClassifierFactory.createClassifier(cc.getValue().getClassifierName(),
					cc.getValue().getClassifierOptions());
			c.setCategory(category);

			Instances ist = is;

			for (FilterConfiguration fc : cc.getValue().getFilterConfigurations()) {
				System.out.println("Filter: " + fc.getFilterName() + " / " + fc.getFilterOptions());

				ist = DataSetFilterFactory.create(fc.getFilterName(), fc.getFilterOptions(), trie_terms, term_map)
						.filter(ist, category);
			}

			c.train(ist, term_map);

			classifiers.put(cc.getKey(), c);
		}

		// Serialization of the models

		// Serialize the trie
		ObjectOutputStream ot = new ObjectOutputStream(new GZIPOutputStream(new FileOutputStream(argc[5])));
		ot.writeObject(trie_terms);
		ot.close();

		// Serialize the classifiers
		ObjectOutputStream oc = new ObjectOutputStream(new GZIPOutputStream(new FileOutputStream(argc[6])));
		oc.writeObject(classifiers);
		oc.close();
	}
}