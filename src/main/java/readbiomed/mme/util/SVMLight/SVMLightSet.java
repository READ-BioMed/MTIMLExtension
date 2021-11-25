package readbiomed.mme.util.SVMLight;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import gov.nih.nlm.nls.mti.documents.Document;
import gov.nih.nlm.nls.mti.featuresextractors.FeatureExtractor;
import gov.nih.nlm.nls.mti.featuresextractors.FeatureExtractorFactory;
import gov.nih.nlm.nls.mti.instances.BinaryInstance;
import gov.nih.nlm.nls.mti.instances.Instance;
import gov.nih.nlm.nls.mti.instances.Instances;
import gov.nih.nlm.nls.mti.textprocessors.TextProcessor;
import gov.nih.nlm.nls.mti.textprocessors.TextProcessorFactory;
import gov.nih.nlm.nls.utils.Trie;

/**
 * Generate SVMLight set. For BinaryInstances.
 * 
 * @author Antonio Jimeno Yepes (antonio.jimeno@gmail.com)
 *
 */
public class SVMLightSet
{
  public static void main (String [] argc)
  throws ClassNotFoundException, InstantiationException, IllegalAccessException, IOException
  {
	if (argc.length != 7)
	{
	  System.err.println("SVMLightSet text_processor parameters feature_extractor parameters category train_file test_file");
	  System.exit(-1);
	}
	
	Trie <Integer> trie_terms = new Trie <Integer> ();
	Map <Integer, String> term_map =
		new HashMap <Integer, String> ();
	Trie <Integer> trie_categories =
			new Trie <Integer> ();

    TextProcessor tp_train =
        	TextProcessorFactory.create(argc[0], argc[1], new FileInputStream(argc[5]), trie_categories);

    TextProcessor tp_test =
        	TextProcessorFactory.create(argc[0], argc[1], new FileInputStream(argc[6]), trie_categories);

    FeatureExtractor fe =
        	FeatureExtractorFactory.create(argc[2], argc[3], trie_terms, term_map);

    Document d = null;

    Instances is_train = new Instances();

    while ((d = tp_train.nextDocument()) != null)
    { is_train.addInstance(fe.prepareInstance(d), d.getCategories()); }

    Instances is_test = new Instances();

    while ((d = tp_test.nextDocument()) != null)
    { is_test.addInstance(fe.prepareInstance(d), d.getCategories()); }

    {
    BufferedWriter w = new BufferedWriter(new FileWriter(argc[5] + ".svmlight"));
    Set <Instance> positives = is_train.getCategoryInstances().get(trie_categories.get(argc[4])); 
    
    for (Instance i : is_train.getInstances())
    {
      if (positives.contains(i))
      { w.write("1"); }
      else
      { w.write("-1"); }
      
      BinaryInstance bi = (BinaryInstance)i;
      
      Arrays.sort(bi.getBinaryFeatures());
      
      for (int token : bi.getBinaryFeatures())
      {
    	w.write(" ");
    	w.write(String.valueOf(token+1));
    	w.write(":1");
      }
      
      w.newLine();
    }
    w.flush();
    w.close();
    }
    
    {
    BufferedWriter w = new BufferedWriter(new FileWriter(argc[6] + ".svmlight"));
    Set <Instance> positives = is_test.getCategoryInstances().get(trie_categories.get(argc[4])); 
    
    for (Instance i : is_test.getInstances())
    {
      if (positives.contains(i))
      { w.write("1"); }
      else
      { w.write("-1"); }
      
      BinaryInstance bi = (BinaryInstance)i;
      
      Arrays.sort(bi.getBinaryFeatures());
      
      for (int token : bi.getBinaryFeatures())
      {
    	w.write(" ");
    	w.write(String.valueOf(token+1));
    	w.write(":1");
      }
      
      w.newLine();
    }
    w.flush();
    w.close();
    }

  }
}
