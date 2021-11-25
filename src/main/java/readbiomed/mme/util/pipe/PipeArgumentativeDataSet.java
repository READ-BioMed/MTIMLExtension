package readbiomed.mme.util.pipe;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import gov.nih.nlm.nls.utils.CommonWords;
import gov.nih.nlm.nls.utils.Constants;
import monq.jfa.AbstractFaAction;
import monq.jfa.CallbackException;
import monq.jfa.CompileDfaException;
import monq.jfa.Dfa;
import monq.jfa.DfaRun;
import monq.jfa.Nfa;
import monq.jfa.ReSyntaxException;
import monq.jfa.ReaderCharSource;
import monq.jfa.Xml;

/**
 * 
 * @author Antonio Jimeno Yepes (antonio.jimeno@gmail.com)
 *
 */
public class PipeArgumentativeDataSet
{
  private static Pattern p = Pattern.compile("\t");
  
  public static Map <Integer, String> getAnnotation(String file_name)
  throws IOException
  {
	BufferedReader b =
			new BufferedReader(
					new InputStreamReader(
							new GZIPInputStream(
									new FileInputStream(
											file_name
	))));

	Map <Integer, String> annotations =
			new HashMap <Integer, String> ();

    String line;
    
    while ((line = b.readLine()) != null)
    {
      String [] tokens = p.split(line);
      
      Integer id = Integer.parseInt(tokens[0]);
      
      double value = 0;
      String label = null;
      
      for (int i = 1; i < tokens.length; i+=2)
      {
    	double tvalue = Double.parseDouble(tokens[i+1]);
    	
    	if (label == null || value < tvalue)
    	{
          label = tokens[i];
          value = tvalue;
    	}
      }

      annotations.put(id, label);
    }

	b.close();

	return annotations;
  }

  private static String PMID = null;
  private static int sentence_id = 0;
  
  private static Set <String> categories =
		  new HashSet <String> ();
  
  private static StringBuilder title_text =
		  new StringBuilder();

  private static Map <String, String> label_sentence =
		  new HashMap <String, String> ();

  private static Pattern p_sen = Pattern.compile("\\. ");
  
  private static final Pattern p_tokens = Pattern.compile(Constants.tokenizationExpression);
  
  private static Map <Integer, String> tannotations = null;
  
  private static AbstractFaAction get_PMID = new AbstractFaAction()
  {
    public void invoke(StringBuffer yytext,int start,DfaRun runner)
  	throws CallbackException
	{
	  if (PMID == null)
	  {
		Map <String, String> map = Xml.splitElement(yytext, start);
	    PMID = map.get(Xml.CONTENT);
	  }
	}
  };

  private static AbstractFaAction get_descriptor = new AbstractFaAction()
  {
    public void invoke(StringBuffer yytext,int start,DfaRun runner)
  	throws CallbackException
	{
      Map <String, String> map = Xml.splitElement(yytext, start);
      categories.add(map.get(Xml.CONTENT));
	}
  };
  
  private static AbstractFaAction end_document = new AbstractFaAction()
  {
    public void invoke(StringBuffer yytext,int start,DfaRun runner)
  	throws CallbackException
	{
      if (PMID != null)
      {
        if (title_text.length() > 0)
        {
          BufferedWriter w = (BufferedWriter)runner.clientData;
        	
          // Add title
          StringBuilder tokens = new StringBuilder(PMID).append("|title");

          for (String token : p_tokens.split(title_text.toString().toLowerCase()))
          {
        	if (token.length() > 0)
          	{
              if (token != null && !CommonWords.checkWord(token))
              { tokens.append("|").append(token); }
          	}
          }

          try
          {
            w.write(tokens.toString());
            w.newLine();
          }
          catch (Exception e)
          { e.printStackTrace(); }

          // Add sentences
          for (Map.Entry <String, String> entry : label_sentence.entrySet())
          {
            // Add title
            StringBuilder atokens = new StringBuilder(PMID).append("|abstract_" + entry.getKey());

            for (String token : p_tokens.split(entry.getValue().toString().toLowerCase()))
            {
              if (token.length() > 0)
              {
                if (token != null && !CommonWords.checkWord(token))
                { atokens.append("|").append(token); }
              }
            }

            try
            {
              w.write(atokens.toString());
              w.newLine();
            }
            catch (Exception e)
            { e.printStackTrace(); }
          }

          // Show categories
          if (categories.size() > 0)
          {
            StringBuilder cats = new StringBuilder(PMID).append("|MH");

            for (String category : categories)
            {
              cats.append("|")
                  .append(category);
            }

            try
            {
              w.write(cats.toString());
              w.newLine();
            }
            catch (Exception e)
            { e.printStackTrace(); }
          }
        }
      }

      PMID = null;
      title_text.setLength(0);
      label_sentence.clear();
      categories.clear();
	}
  };
  
  private static AbstractFaAction get_title = new AbstractFaAction()
  {
    public void invoke(StringBuffer yytext,int start,DfaRun runner)
  	throws CallbackException
	{
      Map <String, String> map = Xml.splitElement(yytext, start);

      // Standard abstract label
      if (map.get("Label") != null)
      { title_text.append(" ").append(map.get("Label")).append(":"); }

	  title_text.append(" ")
	            .append(map.get(Xml.CONTENT).replaceAll("&gt;", ">")
	      		                            .replaceAll("&lt;", "<"));
	}
  };
  
  private static AbstractFaAction get_abstract = new AbstractFaAction()
  {
    public void invoke(StringBuffer yytext,int start,DfaRun runner)
  	throws CallbackException
	{
      Map <String, String> map = Xml.splitElement(yytext, start);
      
      String text = map.get(Xml.CONTENT);
      
      if (text.length() > 0)
      {
        for (String sentence : p_sen.split(text))
        {
          String label = tannotations.get(sentence_id);
          
          String atext = label_sentence.get(label);
          
          if (atext == null)
          { label_sentence.put(label, sentence); }
          else
          { label_sentence.put(label, atext + " " + sentence); }

          sentence_id++;
        }
      }
	}
  };

  public static void main (String [] argc)
  throws IOException, CompileDfaException, ReSyntaxException
  {
    // Prepare the processing
    Nfa nfa = new Nfa(Nfa.NOTHING);
    nfa.or(Xml.GoofedElement("AbstractText"), get_abstract);
    nfa.or(Xml.GoofedElement("ArticleTitle"), get_title);
    nfa.or(Xml.GoofedElement("PMID"), get_PMID);
    nfa.or(Xml.GoofedElement("DescriptorName"), get_descriptor);
    nfa.or(Xml.ETag("MedlineCitation"), end_document);
    Dfa dfa = nfa.compile(DfaRun.UNMATCHED_COPY);
    
    // Process training data
    sentence_id = 0;
    tannotations = getAnnotation("C:\\datasets\\amia_set\\pipe\\train.arg.annotation.gz");
    {
      BufferedWriter w =
      		new BufferedWriter(
       				new OutputStreamWriter(
       						new GZIPOutputStream(
       								new FileOutputStream("C:\\datasets\\amia_set\\pipe\\train.arg.unigram.gz"))));

      DfaRun dfaRun = new DfaRun(dfa);
      dfaRun.clientData = w;
      dfaRun.setIn(new ReaderCharSource(new GZIPInputStream(new FileInputStream("C:\\datasets\\amia_set\\citations.train.xml.gz")), "UTF-8"));
      dfaRun.filter();

      w.close();
    }
    
    // Process test data
    sentence_id = 0;
    tannotations = getAnnotation("C:\\datasets\\amia_set\\pipe\\test.arg.annotation.gz");
    {
      BufferedWriter w =
      		new BufferedWriter(
       				new OutputStreamWriter(
       						new GZIPOutputStream(
       								new FileOutputStream("C:\\datasets\\amia_set\\pipe\\test.arg.unigram.gz"))));

      DfaRun dfaRun = new DfaRun(dfa);
      dfaRun.clientData = w;
      dfaRun.setIn(new ReaderCharSource(new GZIPInputStream(new FileInputStream("C:\\datasets\\amia_set\\citations.test.xml.gz")), "UTF-8"));
      dfaRun.filter();

      w.close();
    }
  }
}
