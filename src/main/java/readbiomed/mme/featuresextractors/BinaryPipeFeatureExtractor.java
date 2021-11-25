package readbiomed.mme.featuresextractors;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import gov.nih.nlm.nls.mti.datasetfilters.featureselectors.bf.RemoveLowFrequencyFeatures;
import gov.nih.nlm.nls.mti.documents.Document;
import gov.nih.nlm.nls.mti.featuresextractors.FeatureExtractor;
import gov.nih.nlm.nls.mti.instances.BinaryInstance;
import gov.nih.nlm.nls.mti.instances.Instance;
import gov.nih.nlm.nls.mti.instances.Instances;
import gov.nih.nlm.nls.utils.CommonWords;
import gov.nih.nlm.nls.utils.Trie;
import gov.nih.nlm.nls.utils.Utils;

public class BinaryPipeFeatureExtractor implements FeatureExtractor
{
  private boolean lower_case = false;
  private boolean remove_numbers = false;
  private boolean remove_stop_words = false;
  
  private int ng_size = 1;
  
  private String frequency_threshold = null;

  private Trie <Integer> trie_terms = null;

  private Map <Integer, String> term_map = null;

  private static final Pattern p = Pattern.compile("\\|");
  
  private class NGrams
  {
    private String [] buffer = null;

    public NGrams (int size)
    { buffer = new String [size]; }

    public String [] add (String term)
    {
      String [] result = new String [buffer.length];
    	
      // Organize the buffer
      for (int i = 0; i < buffer.length -1; i++)
      {	buffer[i] = buffer[i+1]; }
      buffer[buffer.length-1] = term;

      StringBuilder string = new StringBuilder();
      
      for (int i = buffer.length - 1; i >= 0; i--)
      {
    	if (string.length() == 0)
    	{ 
    	  result[i] = buffer[i];
    	  string.append(buffer[i]);
    	}
    	else
    	{
    	  if (buffer[i] != null)
    	  {
            string.insert(0, " ");
            string.insert(0, buffer[i]);
          
            result[i] = string.toString();
    	  }
    	  else
    	  { break; }
    	}
      }

      // Deliver the strings
      return result;
    }
  }

  /**
   * 
   * @param options - -l to lower case / upper case if not indicated. -n to remove numbers. -c to remove stop words. -fn to remove terms with document frequency lower than (or equal to) n. -tn ngram size n
   * @param trie_terms - trie structure used to map token strings to token ids
   * @param term_map - mapping table between token ids and token strings
   */
  public void setup(String options, Trie<Integer> trie_terms, Map <Integer, String> term_map)
  {
	this.trie_terms = trie_terms;
    this.term_map = term_map;
    
    // Process options
    String [] tokens = options.split(" ");

    for (String token : tokens)
    {
      if (token.equals("-l"))
      { lower_case = true; }
      else if (token.equals("-s"))
      {  }
      else if (token.equals("-n"))
      { remove_numbers = true; }
      else if (token.equals("-c"))
      { remove_stop_words = true; }
      else if (token.startsWith("-f"))
      { frequency_threshold = token.replaceAll("-f", ""); }
      else if (token.startsWith("-t"))
      { ng_size = Integer.parseInt(token.replaceAll("-t", "")); }
    }
  }

  public Instance prepareInstance(Document d)
  {
	BinaryInstance i = new BinaryInstance();
	
    Set <Integer> tokens = new HashSet <Integer> ();
	
	// Add features
	for (Map.Entry <String, String> field : d.getFields().entrySet())
	{
	  String text = null;

	  if (lower_case)
	  { text = field.getValue().toLowerCase(); }
	  else
	  { text = field.getValue(); }

	  String prefix = "";

	  if (!field.getKey().equals("text"))
	  { prefix = field.getKey() + "_"; }
	  
	  NGrams ng = new NGrams(ng_size);

      for (String token : p.split(text))
      {
    	if (token.length() > 0)
    	{
    	  if (!remove_stop_words || !CommonWords.checkWord(token))
    	  {
    		if (!remove_numbers || !Utils.isNumber(token))
    		{
    		  for (String ngtoken : ng.add(token))
    		  {
    			if (ngtoken != null)
    			{
    		      if (prefix.length() > 0)
    		      { ngtoken = new StringBuilder(prefix).append(ngtoken).toString(); }

    	          // Look for the token in the trie
    	          Integer id = trie_terms.get(ngtoken);

    	          // Add it if is not there
    	          if (id == null)
    	          {
    	    	    // Allow multiple threads to work on it
    	    	    synchronized (trie_terms)
    	    	    {
    	    	      id = trie_terms.size();
    	              trie_terms.insert(ngtoken, id);

    	              term_map.put(id, ngtoken);
    	    	    }
    	          }

                  tokens.add(id);
    			}
    		  }
    		}
    	  }
    	}
      }
	}
	
    int [] features = new int [tokens.size()];
    
    int ti = 0;
    
    for (Integer id : tokens)
    {
      features[ti] = id; 
      ti++;
    }

    i.setBinaryFeatures(features);
	
  	return i;
  }

  public Instances postfilter(Instances is)
  {
	if (frequency_threshold != null && Integer.parseInt(frequency_threshold) > 0)
	{
	  RemoveLowFrequencyFeatures rlff = new RemoveLowFrequencyFeatures();
	  // Threshold without more parameters
      rlff.setup(frequency_threshold, null, null);
      // Filter, no category is considered, so -1
	  return rlff.filter(is, -1);
	}

	return is;
  }
}