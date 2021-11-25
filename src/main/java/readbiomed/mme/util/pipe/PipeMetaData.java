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

public class PipeMetaData
{
  private static String PMID = null;
  
  private static Set <String> categories =
		  new HashSet <String> ();
  
  private static Set <String> field_values =
		  new HashSet <String> ();
  
  private static String field_name = null;

  private static final Pattern p = Pattern.compile(Constants.tokenizationExpression);
   
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
  
  private static AbstractFaAction get_field = new AbstractFaAction()
  {
    public void invoke(StringBuffer yytext,int start,DfaRun runner)
  	throws CallbackException
	{
      Map <String, String> map = Xml.splitElement(yytext, start);
      field_values.add(map.get(Xml.CONTENT));
	}
  };

  private static AbstractFaAction end_document = new AbstractFaAction()
  {
    public void invoke(StringBuffer yytext,int start,DfaRun runner)
  	throws CallbackException
	{
      if (PMID != null)
      {
        BufferedWriter w = (BufferedWriter)runner.clientData;

        StringBuilder tokens = new StringBuilder(PMID).append("|" + field_name);
          
        // Add MetaData here
        if (field_name.equals("Author"))
        {
          for (String value : field_values)
          { 
            for (String token : p.split(value.toString().toLowerCase()))
            {
              if (token.length() > 2
              && !CommonWords.checkWord(token)
              && !token.equals("lastname")
              && !token.equals("forename")
              && !token.equals("initials")
              )
          	  { tokens.append("|").append(token); }
            }
          }
        }
        else
        {
          for (String value : field_values)
          { tokens.append("|").append(value); }
        	
        }
        
        try
        {
          w.write(tokens.toString());
          w.newLine();
        }
        catch (Exception e)
        { e.printStackTrace(); }

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

      PMID = null;
      categories.clear();
      field_values.clear();
	}
  };

  public static void main (String [] argc)
  throws ReSyntaxException, CompileDfaException, IOException
  {
	String [] fields = { "NlmUniqueID", "Affiliation", "Author" };
	  
    for (String field : fields)
    {
      field_name = field;
    	
      Nfa nfa = new Nfa(Nfa.NOTHING);
      nfa.or(Xml.GoofedElement("PMID"), get_PMID);
      nfa.or(Xml.GoofedElement("DescriptorName"), get_descriptor);
      nfa.or(Xml.GoofedElement(field), get_field);
      nfa.or(Xml.ETag("MedlineCitation"), end_document);
      Dfa dfa = nfa.compile(DfaRun.UNMATCHED_COPY);

      {
        BufferedWriter w =
     	  	  new BufferedWriter(
     				  new OutputStreamWriter(
    						  new GZIPOutputStream(
     								  new FileOutputStream("C:\\datasets\\amia_set\\pipe\\train." + field + ".gz"))));

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
        								new FileOutputStream("C:\\datasets\\amia_set\\pipe\\test." + field + ".gz"))));

        DfaRun dfaRun = new DfaRun(dfa);
        dfaRun.clientData = w;
        dfaRun.setIn(new ReaderCharSource(new GZIPInputStream(new FileInputStream("C:\\datasets\\amia_set\\citations.test.xml.gz")), "UTF-8"));
        dfaRun.filter();

        w.close();
      }
    }
  }
}