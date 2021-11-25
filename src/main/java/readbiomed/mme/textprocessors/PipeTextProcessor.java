package readbiomed.mme.textprocessors;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import gov.nih.nlm.nls.mti.documents.Document;
import gov.nih.nlm.nls.mti.textprocessors.TextProcessor;
import gov.nih.nlm.nls.utils.Trie;

public class PipeTextProcessor extends TextProcessor
{
  private Document [] buffer = new Document [1];

  private InputStream in = null;
  private Trie <Integer> trie_categories = null;

  private boolean end = false;

  private Map <Integer, String> category_map =
		  new HashMap <Integer, String> ();

  private static Pattern p_pipe = Pattern.compile("\\|");

  public void setup(String options, InputStream in,	Trie<Integer> trie_categories)
  {
    this.in = in;
    this.trie_categories = trie_categories;
  }

  public Map <Integer, String> getCategoryMap()
  { return category_map; }

  public Document nextDocument()
  {
	try
	{
	  synchronized (buffer)
	  {
        // Wait if stack is empty and we are not at the end
	    if (buffer[0] == null)
	    {
	      while (!end && buffer[0] == null)
	      { buffer.wait(1); }
	    }

	    if (buffer[0] != null)
	    {
	      Document return_buffer = buffer[0];
	      buffer[0] = null;
	      buffer.notify();
	      return return_buffer;
	    }
	  }
	}
	catch (Exception e)
	{
	  e.printStackTrace();
	  System.exit(-1);
	}

	return null;
  }	

  private void deliverDocument(String PMID,
		                       Map <String, StringBuilder> fields,
		                       Set <String> categories)
  {
    synchronized (buffer)
    {
	  if (buffer[0] != null)
	  {
	    try
	    { buffer.wait(); }
	    catch (InterruptedException e)
	    {
		  e.printStackTrace();
			System.exit(-1);
		  }
	    }

	    buffer[0] = new Document();
		buffer[0].setId(PMID);

		if (fields != null)
		{
		  for (Map.Entry <String, StringBuilder> field : fields.entrySet())
		  { buffer[0].addField(field.getKey(), field.getValue().toString()); }
		}

        // Add the id to the mhs
	    for (String mh : categories)
	    {
	      Integer cat_id = trie_categories.get(mh);

	      if (cat_id == null)
          {
	    	synchronized (trie_categories)
	    	{
	    	  cat_id = trie_categories.size();
	    	  trie_categories.insert(mh, cat_id);
	    	  category_map.put(cat_id, mh);
	    	}
          }
	      
	      buffer[0].getCategories().add(cat_id);
	      buffer[0].getCategoryNames().add(mh);
	    }

	    buffer.notify();
	  }
  }
  
  public void run()
  {
    String line = null;
    
	try
	{
      int count = 0;
	  end = false;
	  String PMID = null;

	  Map <String, StringBuilder> fields =
			  new HashMap <String, StringBuilder> ();

	  Set <String> categories =
			  new HashSet <String> ();

	  BufferedReader b = new BufferedReader(new InputStreamReader(in, "UTF-8"));

      while ((line = b.readLine()) != null)
	  {
        String [] tokens = p_pipe.split(line);

        if (PMID == null)
        { PMID = tokens[0]; }
        else
        {
          if (!PMID.equals(tokens[0]))
          {
            // Deliver the current document
            deliverDocument(PMID,
                            fields,
                            categories
            );

        	fields.clear();
        	categories.clear();
        	
        	count++;

        	PMID = tokens[0];
          }
        }

        if (tokens[1].equals("MH"))
        {
          for (int i=2; i < tokens.length; i++)
          { categories.add(tokens[i]); }
        }
        else
        {
          for (int i=2; i < tokens.length; i++)
          {
        	if (fields.get(tokens[1]) == null)
            { fields.put(tokens[1], new StringBuilder(tokens[i])); }
            else
        	{ fields.get(tokens[1]).append("|").append(tokens[i]); }
          }
        }
	  }

      b.close();

      if (PMID != null)
      { 
    	deliverDocument(PMID,
                        fields,
                        categories
        );
        count++;
      }

      System.err.println("Citations: " + count);
	}
	catch (Exception e)
	{
	  System.err.println("Line: " + line);
	  e.printStackTrace();
	}
	finally
	{
      end = true;
      try { buffer.notify(); } catch (Exception ex) {}
	}
  }
}