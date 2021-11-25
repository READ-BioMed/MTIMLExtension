package readbiomed.mme.util.pipe;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

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
 * Convert the text into sentences.
 * Annotate the sentences with argumentative tags
 * Turn the argumentative tags into the annotated text
 *
 * @author Antonio Jimeno Yepes (antonio.jimeno@gmail.com)
 *
 */
public class PipeArgumentativeSentences
{
  private static int sentence_id = 0;

  private static int pmid_sentence_id = 0;

  private static final Pattern p =
		  Pattern.compile(Constants.tokenizationExpression);

  private static Pattern p_sen = Pattern.compile("\\. ");

  private static AbstractFaAction get_abstract = new AbstractFaAction()
  {
    public void invoke(StringBuffer yytext,int start,DfaRun runner)
    throws CallbackException
    {
      BufferedWriter w = (BufferedWriter)runner.clientData;
    	
      Map <String, String> map = Xml.splitElement(yytext, start);

      // Do not consider the label if it exists
      String text = map.get(Xml.CONTENT);

      if (text.length() > 0)
      {
        for (String sentence : p_sen.split(text))
        {
          StringBuilder features = new StringBuilder();
          
          features.append(sentence_id);
          sentence_id++;
        	
          for (String token : new HashSet <String> (Arrays.asList(p.split(sentence.toString().toLowerCase()))))
          {
            if (token.length() > 0)
            {
              features.append(" ")
                     .append(token);
            }
          }

          features.append(" P")
                  .append(pmid_sentence_id)
                  .append(" ")
                  .append("OBJECTIVE");

          try
          {
            w.write(features.toString());
            w.newLine();
          }
          catch (Exception e)
          { e.printStackTrace(); System.exit(-1); }
          
          pmid_sentence_id++;
        }
      }
    }
  };

  private static AbstractFaAction end_document = new AbstractFaAction()
  {
    public void invoke(StringBuffer yytext,int start,DfaRun runner)
    throws CallbackException
    {
      pmid_sentence_id = 1;
    }
  };
  
  public static void main (String [] argc)
  throws ReSyntaxException, CompileDfaException, IOException
  {
    Nfa nfa = new Nfa(Nfa.NOTHING);
    nfa.or(Xml.GoofedElement("AbstractText"), get_abstract);
    nfa.or(Xml.ETag("MedlineCitation"), end_document);
    Dfa dfa = nfa.compile(DfaRun.UNMATCHED_COPY);
    
    sentence_id = 0;
    
    String file = "C:\\datasets\\amia_set\\citations.train.xml.gz";
    
    BufferedWriter w =
  		new BufferedWriter(
  				new OutputStreamWriter(
  						new GZIPOutputStream(
  								new FileOutputStream("C:\\datasets\\amia_set\\pipe\\train.arg.features.gz"))));

    
    DfaRun dfaRun = new DfaRun(dfa);
    dfaRun.clientData = w;
    dfaRun.setIn(new ReaderCharSource(new GZIPInputStream(new FileInputStream(file)), "UTF-8"));
    dfaRun.filter();
    
    w.flush();
    w.close();
    
    sentence_id = 0;
    

    file = "C:\\datasets\\amia_set\\citations.test.xml.gz";
    
    w =
  		new BufferedWriter(
  				new OutputStreamWriter(
  						new GZIPOutputStream(
  								new FileOutputStream("C:\\datasets\\amia_set\\pipe\\test.arg.features.gz"))));

    
    dfaRun = new DfaRun(dfa);
    dfaRun.clientData = w;
    dfaRun.setIn(new ReaderCharSource(new GZIPInputStream(new FileInputStream(file)), "UTF-8"));
    dfaRun.filter();
    
    w.flush();
    w.close();
  }
}