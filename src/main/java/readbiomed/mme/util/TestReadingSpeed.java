package readbiomed.mme.util;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;

import gov.nih.nlm.nls.mti.documents.Document;
import gov.nih.nlm.nls.mti.featuresextractors.FeatureExtractor;
import gov.nih.nlm.nls.mti.featuresextractors.FeatureExtractorFactory;
import gov.nih.nlm.nls.mti.instances.Instance;
import gov.nih.nlm.nls.mti.textprocessors.PipeTextProcessor;
import gov.nih.nlm.nls.mti.textprocessors.TextProcessor;
import gov.nih.nlm.nls.mti.textprocessors.TextProcessorFactory;

public class TestReadingSpeed {
	public static void main(String[] argc) throws FileNotFoundException, IOException, ClassNotFoundException,
			InstantiationException, IllegalAccessException {
		/*
		 * Pattern p = Pattern.compile("\\|"); try (BufferedReader b = new
		 * BufferedReader(new FileReader("/Users/ajimeno/Documents/MTI_ML/train.fast")))
		 * { for (String line; (line = b.readLine()) != null;) { p.split(line); } }
		 */

		TextProcessor tp = TextProcessorFactory.create(PipeTextProcessor.class.getName(), "",
				new FileInputStream("/Users/ajimeno/Documents/MTI_ML/train.fast"), new Trie<Integer>());
//-l -n -c -f1 -t2
		FeatureExtractor fe = FeatureExtractorFactory.create(
				"gov.nih.nlm.nls.mti.featuresextractors.BinaryFeatureExtractor", "-l -n -c -f1 ",
				new Trie<Integer>(), new HashMap<Integer, String>());

		System.out.println("Here");

		int count = 0;
		Document d;
		while ((d = tp.nextDocument()) != null) {
			Instance i = fe.prepareInstance(d);
			System.out.println(count++);
		}
		
		
	}
}