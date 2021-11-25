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
import gov.nih.nlm.nls.utils.Constants;
import gov.nih.nlm.nls.utils.Trie;

public class PipeTextProcessorOriginal extends TextProcessor
{
	  Document [] buffer = new Document [1];
	  
	  boolean end = false;

	  InputStream in = null;
	  
	  Trie <Integer> trie_categories = null;
	  
	  Set <String> categories =
		  new HashSet <String> ();

	  Map <Integer, String> category_map =
		  new HashMap <Integer, String> ();

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
	
  public void setup(String options, InputStream in, Trie <Integer> trie_categories)
  {
    this.in = in;
    this.trie_categories = trie_categories;
  }

  public void run()
  {
    BufferedReader b = new BufferedReader(new InputStreamReader(in));
	String line;

	String [] field_name = null;
	
	Pattern p = Pattern.compile(Constants.tokenizationExpression);
	
	int count = 0;
	
	try
	{
	  while ((line = b.readLine()) != null)
	  {
		String [] tokens = line.split("\\|");
		  
		if (tokens.length > 1)
		{
          if (count == 0)
          {
        	field_name = new String [tokens.length - 1];
        	
        	for (int i = 0; i < field_name.length; i++)
        	{ field_name[i] = tokens[i]; }
          }
          else
          {
		    synchronized (buffer)
		    {
		      if (buffer[0] != null)
		      { buffer.wait(); }

		      buffer[0] = new Document();
			  buffer[0].setId(new Integer(count).toString());

			  for (int i = 0; i < field_name.length; i++)
			  { buffer[0].addField(field_name[i], tokens[i]); }

		      // Add the id to the mhs
			  for (String mh : p.split(tokens[tokens.length - 1]))
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
        }
		
		count++;
  	  }

	  b.close();
	}
	catch (Exception e)
	{
	  e.printStackTrace();
	  System.exit(-1);
	}
	finally
	{ end = true; }
  }
}