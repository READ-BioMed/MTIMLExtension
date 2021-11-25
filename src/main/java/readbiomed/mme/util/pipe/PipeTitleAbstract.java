package readbiomed.mme.util.pipe;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
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
import readbiomed.mme.util.NGrams;

public class PipeTitleAbstract
{
  private static int ngram = 1;
	
  private static StringBuilder title_text =
		  new StringBuilder();

  private static StringBuilder abstract_text =
		  new StringBuilder();
  
  private static String PMID = null;

  private static Set <String> categories =
		  new HashSet <String> ();

  private static final Pattern p = Pattern.compile(Constants.tokenizationExpression);

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

      // Standard abstract label
      if (map.get("Label") != null)
      { abstract_text.append(" ").append(map.get("Label")).append(":"); }

	  abstract_text.append(" ")
	               .append(map.get(Xml.CONTENT).replaceAll("&gt;", ">")
	      		                               .replaceAll("&lt;", "<"));
	}
  };
  
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
        // Show unigrams
        if (title_text.length() > 0)
        {
          BufferedWriter w = (BufferedWriter)runner.clientData;

          {
            StringBuilder title_tokens = new StringBuilder(PMID).append("|title_" + ngram + "gram");

            NGrams ng = new NGrams(ngram);

            for (String token : p.split(title_text.toString().toLowerCase()))
            {
        	  if (token.length() > 0)
        	  {
        		for (String ngtoken : ng.add(token))
        		{
                  if(ngtoken != null && !CommonWords.checkWord(token))
          	      { title_tokens.append("|").append(ngtoken); }
        		}
        	  }
            }

            try
            {
              w.write(title_tokens.toString());
              w.newLine();
            }
            catch (Exception e)
            { e.printStackTrace(); }
          }

          {
        	if (abstract_text.length() > 0)
        	{
              StringBuilder abstract_tokens = new StringBuilder(PMID).append("|abstract_" + ngram + "gram");
              
              NGrams ng = new NGrams(ngram);

              for (String token : p.split(abstract_text.toString().toLowerCase()))
              {
        	    if (token.length() > 0)
        	    {
        	      for (String ngtoken : ng.add(token))
        	      {
        	        if (ngtoken != null && !CommonWords.checkWord(token))
          	        { abstract_tokens.append("|").append(ngtoken); }
        	      }
        	    }
              }

              try
              {
                w.write(abstract_tokens.toString());
                w.newLine();
              }
              catch (Exception e)
              { e.printStackTrace(); }
        	}
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
      abstract_text.setLength(0);
      categories.clear();
	}
  };

  public static void main (String [] argc)
  throws ReSyntaxException, CompileDfaException, IOException
  {
    Nfa nfa = new Nfa(Nfa.NOTHING);
    nfa.or(Xml.GoofedElement("AbstractText"), get_abstract);
    nfa.or(Xml.GoofedElement("ArticleTitle"), get_title);
    nfa.or(Xml.GoofedElement("PMID"), get_PMID);
    nfa.or(Xml.GoofedElement("DescriptorName"), get_descriptor);
    nfa.or(Xml.ETag("MedlineCitation"), end_document);
    Dfa dfa = nfa.compile(DfaRun.UNMATCHED_COPY);

    ngram = 1;
    
    {
      BufferedWriter w =
      		new BufferedWriter(
      				new OutputStreamWriter(
      						new GZIPOutputStream(
      								new FileOutputStream("C:\\datasets\\amia_set\\pipe\\train.tiab.unigram.gz"))));

      DfaRun dfaRun = new DfaRun(dfa);
      dfaRun.clientData = w;
      dfaRun.setIn(new ReaderCharSource(new GZIPInputStream(new FileInputStream("C:\\datasets\\amia_set\\citations.train.xml.gz")), "UTF-8"));
      dfaRun.filter();

      w.close();
    }
      
    {
      BufferedWriter w =
        		new BufferedWriter(
        				new OutputStreamWriter(
        						new GZIPOutputStream(
        								new FileOutputStream("C:\\datasets\\amia_set\\pipe\\test.tiab.unigram.gz"))));

      DfaRun dfaRun = new DfaRun(dfa);
      dfaRun.clientData = w;
      dfaRun.setIn(new ReaderCharSource(new GZIPInputStream(new FileInputStream("C:\\datasets\\amia_set\\citations.test.xml.gz")), "UTF-8"));
      dfaRun.filter();

      w.close();
    }

    ngram = 2;

    {
      BufferedWriter w =
    		new BufferedWriter(
    				new OutputStreamWriter(
    						new GZIPOutputStream(
    								new FileOutputStream("C:\\datasets\\amia_set\\pipe\\train.tiab.bigram.gz"))));

      DfaRun dfaRun = new DfaRun(dfa);
      dfaRun.clientData = w;
      dfaRun.setIn(new ReaderCharSource(new GZIPInputStream(new FileInputStream("C:\\datasets\\amia_set\\citations.train.xml.gz")), "UTF-8"));
      dfaRun.filter();

      w.close();
    }
    
    {
      BufferedWriter w =
      		new BufferedWriter(
      				new OutputStreamWriter(
      						new GZIPOutputStream(
      								new FileOutputStream("C:\\datasets\\amia_set\\pipe\\test.tiab.bigram.gz"))));

      DfaRun dfaRun = new DfaRun(dfa);
      dfaRun.clientData = w;
      dfaRun.setIn(new ReaderCharSource(new GZIPInputStream(new FileInputStream("C:\\datasets\\amia_set\\citations.test.xml.gz")), "UTF-8"));
      dfaRun.filter();

      w.close();
    }
  }
}