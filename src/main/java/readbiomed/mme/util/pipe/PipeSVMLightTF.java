package readbiomed.mme.util.pipe;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import gov.nih.nlm.nls.utils.Trie;

/**
 * 
 *  Convert pipe notation to SVM Light representation
 * 
 * @author Antonio Jimeno Yepes (antonio.jimeno@gmail.com)
 *
 */
public class PipeSVMLightTF
{
  private static Pattern p = Pattern.compile("\\|");
	
  private static void processFile(String input_file_name,
		                          String output_file_name,
		                          Trie <Integer> trie,
		                          String category)
  throws IOException
  {
	String line;

	BufferedReader b = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(input_file_name))));
	BufferedWriter w = new BufferedWriter(new FileWriter(output_file_name));

	String PMID = null;
	
	boolean has_category = false;
	
	Map <Integer, Integer> token_count = 
			new TreeMap <Integer, Integer> ();

	while ((line = b.readLine()) != null)
	{
	  String [] tokens = p.split(line);

      if (PMID == null)
      { PMID = tokens[0]; }
      else
      {
    	if (!PMID.equals(tokens[0]))
    	{
    	  // Print current document
          w.write((has_category ? "+1" : "-1"));

          for (Map.Entry <Integer, Integer> tc : token_count.entrySet())
          { w.write(" " + tc.getKey() + ":" + tc.getValue()); }

          w.newLine();

          PMID = tokens[0];
          token_count.clear();
          has_category = false;
    	}
      }

      // Get the MHs
      if (tokens[1].equals("MH"))
      {
        for (int i = 2; i < tokens.length; i++)
        {
          if (tokens[i].equals(category))
          {
        	has_category = true;
            break;
          }
        }
      }
      // Get the tokens
      else
      {
        for (int i = 2; i < tokens.length; i++)
        {
          String token = tokens[1] + "_" + tokens[i];

          Integer token_id = trie.get(token);

          if (token_id == null)
          {
    	    // Allow multiple threads to work on it
   	        token_id = trie.size() + 1;
            trie.insert(token, token_id);
          }

          if (token_count.get(token_id) == null)          
          { token_count.put(token_id, 1); }
          else
          { token_count.put(token_id, token_count.get(token_id) + 1); }
        }
      }
	}

	b.close();

	w.close();
  }

  public static void main (String [ ] argc)
  throws IOException
  {
    Trie <Integer> trie = new Trie <Integer> ();

    processFile(argc[0],
    		    argc[0] + "." + argc[2] + ".svmlight",
    		    trie,
    		    argc[2]);

    processFile(argc[1],
		        argc[1] + "." + argc[2] + ".svmlight",
		        trie,
		        argc[2]);
  }
}