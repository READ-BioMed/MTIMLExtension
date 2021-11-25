/****************************************************************************
*
*                          PUBLIC DOMAIN NOTICE
*         Lister Hill National Center for Biomedical Communications
*                      National Library of Medicine
*                      National Institues of Health
*           United States Department of Health and Human Services
*
*  This software is a United States Government Work under the terms of the
*  United States Copyright Act. It was written as part of the authors'
*  official duties as United States Government employees and contractors
*  and thus cannot be copyrighted. This software is freely available
*  to the public for use. The National Library of Medicine and the
*  United States Government have not placed any restriction on its
*  use or reproduction.
*
*  Although all reasonable efforts have been taken to ensure the accuracy
*  and reliability of the software and data, the National Library of Medicine
*  and the United States Government do not and cannot warrant the performance
*  or results that may be obtained by using this software or data.
*  The National Library of Medicine and the U.S. Government disclaim all
*  warranties, expressed or implied, including warranties of performance,
*  merchantability or fitness for any particular purpose.
*
*  For full details, please see the MetaMap Terms & Conditions, available at
*  http://metamap.nlm.nih.gov/MMTnCs.shtml.
*
***************************************************************************/
package readbiomed.mme.textprocessors;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import gov.nih.nlm.nls.mti.documents.Document;
import gov.nih.nlm.nls.mti.textprocessors.TextProcessor;
import gov.nih.nlm.nls.utils.Trie;
import monq.jfa.AbstractFaAction;
import monq.jfa.CallbackException;
import monq.jfa.Dfa;
import monq.jfa.DfaRun;
import monq.jfa.Nfa;
import monq.jfa.ReaderCharSource;
import monq.jfa.Xml;

/**
 * Processor of the MEDLINE XML representation
 * <br/>
 * <br/> 
 * -fss - ss is a list of XML tags specified by the following codes that indicates where the text should be extracted in addition to the title and the abstract of the citation:
 * j (NlmUniqueID), a (Affiliation), c (MeSHCode), n (Author), g (Agency), p (PublicationType) and d (DescriptorName)
 * <br/>
 * <br/>
 * -cs - name of the XML tag s that contains the categories linked to the documents, the default value is DescriptorName  
 * 
 * @author Antonio Jimeno Yepes (antonio.jimeno@gmail.com)
 *
 */
public class MEDLINEXMLTextProcessor extends TextProcessor
{
  private Document [] buffer = new Document [1];

  private boolean end = false;

  private InputStream in = null;
	  
  private Trie <Integer> trie_categories = null;
  
  private Set <String> categories =
	  new HashSet <String> ();

  private Map <Integer, String> category_map =
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

  private Dfa dfa = null;

  private StringBuilder text = new StringBuilder();

  private Map <String, String> map = new HashMap <String, String> ();
  
  private Map <String, String> fields = null;
  
  private String field_codes = null;
  
  private String category = "DescriptorName";
  
  private String PMID = null;
  
  private int count = 0;

  private AbstractFaAction get_PMID = new AbstractFaAction()
  {
    public void invoke(StringBuffer yytext,int start,DfaRun runner)
  	throws CallbackException
	{
	  if (PMID == null)
	  {
		map.clear();
	    Xml.splitElement(map, yytext, start);

	    PMID = map.get(Xml.CONTENT);
	  }
	}
  };

  private AbstractFaAction get_text = new AbstractFaAction()
  {
    public void invoke(StringBuffer yytext,int start,DfaRun runner)
  	throws CallbackException
	{
      map.clear();
      Xml.splitElement(map, yytext, start);

	  // Standard abstract label
	  if (map.get("Label") != null)
	  { text.append(" ").append(map.get("Label")).append(":"); }

      text.append(" ")
          .append(map.get(Xml.CONTENT).replaceAll("&gt;", ">")
        		                      .replaceAll("&lt;", "<"));
	}
  };

  private AbstractFaAction get_descriptor = new AbstractFaAction()
  {
    public void invoke(StringBuffer yytext,int start,DfaRun runner)
  	throws CallbackException
	{
      map.clear();
      Xml.splitElement(map, yytext, start);

      categories.add(map.get(Xml.CONTENT));
	}
  };

  private AbstractFaAction get_nlm_unique_id = new AbstractFaAction()
  {
    public void invoke(StringBuffer yytext,int start,DfaRun runner)
  	throws CallbackException
	{
      map.clear();
      Xml.splitElement(map, yytext, start);

      fields.put("journal", map.get(Xml.CONTENT));
	}
  };

  private AbstractFaAction get_affiliation = new AbstractFaAction()
  {
    public void invoke(StringBuffer yytext,int start,DfaRun runner)
  	throws CallbackException
	{
      map.clear();
      Xml.splitElement(map, yytext, start);

      fields.put("affiliation", map.get(Xml.CONTENT));
	}
  };
  
  private AbstractFaAction get_mesh_code = new AbstractFaAction()
  {
    public void invoke(StringBuffer yytext,int start,DfaRun runner)
  	throws CallbackException
	{
      map.clear();
      Xml.splitElement(map, yytext, start);

      if (fields.get("meshcode") == null)
      { fields.put("meshcode", map.get(Xml.CONTENT)); }
      else
      { fields.put("meshcode", fields.get("meshcode") + " " +  map.get(Xml.CONTENT)); }
	}
  };

  private static String processAuthorName(String author_name)
  {
	return author_name.replaceAll("\n","")
                      .replaceAll("\r","")
                      .replaceAll("<Author ValidYN=\"Y\">", "")
                      .replaceAll("<Initials>","XXXXX")
                      .replaceAll("<ForeName>", "XXXXX")
                      .replaceAll("<LastName>", "XXXXX")
                      .replaceAll("</Initials>", "")
                      .replaceAll("</ForeName>", "")
                      .replaceAll("</LastName>", "")
                      .replaceAll("</Author>", "")
    ;
  }

  private AbstractFaAction get_author = new AbstractFaAction()
  {
    public void invoke(StringBuffer yytext,int start,DfaRun runner)
  	throws CallbackException
	{
      if (fields.get("author") == null)
      { fields.put("author", processAuthorName(yytext.substring(start))); }
      else
      { fields.put("author", fields.get("author") + " " + processAuthorName(yytext.substring(start))); }
	}
  };

  private AbstractFaAction get_agency = new AbstractFaAction()
  {
    public void invoke(StringBuffer yytext,int start,DfaRun runner)
  	throws CallbackException
	{
      map.clear();
      Xml.splitElement(map, yytext, start);

      if (fields.get("agency") == null)
      { fields.put("agency", map.get(Xml.CONTENT).replaceAll(" ", "XXXX")); }
      else
      { fields.put("agency", fields.get("agency") + " " + map.get(Xml.CONTENT).replaceAll(" ", "XXXX")); }
	}
  };

  private AbstractFaAction get_publication_type = new AbstractFaAction()
  {
    public void invoke(StringBuffer yytext,int start,DfaRun runner)
  	throws CallbackException
	{
      map.clear();
      Xml.splitElement(map, yytext, start);

      if (fields.get("pt") == null)
      { fields.put("pt", map.get(Xml.CONTENT).replaceAll(" ", "XXXX")); }
      else
      { fields.put("pt", fields.get("pt") + " " + map.get(Xml.CONTENT).replaceAll(" ", "XXXX")); }
	}
  };

  private AbstractFaAction get_descriptor_name = new AbstractFaAction()
  {
    public void invoke(StringBuffer yytext,int start,DfaRun runner)
  	throws CallbackException
	{
      map.clear();
      Xml.splitElement(map, yytext, start);

      if (fields.get("descriptor") == null)
      { fields.put("descriptor", map.get(Xml.CONTENT).replaceAll(" ", "XXXX")); }
      else
      { fields.put("descriptor", fields.get("descriptor") + " " + map.get(Xml.CONTENT).replaceAll(" ", "XXXX")); }
	}
  };
  
  private AbstractFaAction get_additional_features = new AbstractFaAction()
  {
    public void invoke(StringBuffer yytext,int start,DfaRun runner)
  	throws CallbackException
	{
      map.clear();
      Xml.splitElement(map, yytext, start);

      if (fields.get("additional") == null)
      { fields.put("additional", map.get(Xml.CONTENT)); }
      else
      { fields.put("additional", fields.get("additional") + " " + map.get(Xml.CONTENT)); }
	}
  };
  
  private AbstractFaAction end_document = new AbstractFaAction()
  {
    public void invoke(StringBuffer yytext,int start,DfaRun runner)
  	throws CallbackException
	{
      synchronized (buffer)
	  {
	    if (buffer[0] != null)
	    {
	      try
	      {	buffer.wait(); }
	      catch (InterruptedException e)
	      {
			e.printStackTrace();
			System.exit(-1);
		  }
	    }

	    buffer[0] = new Document();
		buffer[0].setId(PMID);
		buffer[0].addField("text", text.toString());

		if (fields != null)
		{
		  for (Map.Entry <String, String> field : fields.entrySet())
		  { buffer[0].addField(field.getKey(), field.getValue()); }
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

      categories.clear();
      text.setLength(0);
      
      if (fields != null) { fields.clear(); }
      
      PMID = null;

      count++;

      if (count % 5000 == 0)
      { System.err.println("Citations: " + count); }
	}
  };

  @Override
  public void setup(String options, InputStream in,	Trie<Integer> trie_categories)
  {
	this.in = in;
    this.trie_categories = trie_categories;
    
    if (options != null)
    {
      String [] tokens = options.split(" ");

      for (String option : tokens)
      {
      	if (option.startsWith("-f"))
      	{
      	  field_codes = option.replaceAll("-f", "");
          fields = new HashMap <String, String> ();
          System.err.println("Field extraction activated");
        }
      	else if (option.startsWith("-c"))
      	{ category = option.replaceAll("-c", ""); }
      }    	
    }
  }

  public void run()
  {
	try
	{
      Nfa nfa = new Nfa(Nfa.NOTHING);
      nfa.or(Xml.GoofedElement("AbstractText"), get_text);
	  nfa.or(Xml.GoofedElement("ArticleTitle"), get_text);
	  
	  if (fields != null)
	  {
		if (field_codes.contains("j"))
		{ nfa.or(Xml.GoofedElement("NlmUniqueID"), get_nlm_unique_id); }
		
		if (field_codes.contains("a"))
		{ nfa.or(Xml.GoofedElement("Affiliation"), get_affiliation); }
		
		if (field_codes.contains("c"))
		{ nfa.or(Xml.GoofedElement("MeSHCode"), get_mesh_code); }

		if (field_codes.contains("n"))
		{ nfa.or(Xml.GoofedElement("Author"), get_author); }
		
		if (field_codes.contains("g"))
		{ nfa.or(Xml.GoofedElement("Agency"), get_agency); }
		
		if (field_codes.contains("p"))
		{ nfa.or(Xml.GoofedElement("PublicationType"), get_publication_type); }
		
		if (field_codes.contains("d"))
		{ nfa.or(Xml.GoofedElement("DescriptorName"), get_descriptor_name); }
		
		if (field_codes.contains("f"))
		{ nfa.or(Xml.GoofedElement("AdditionalFeatures"), get_additional_features); }
	  }
	  
	  nfa.or(Xml.GoofedElement(category), get_descriptor);
  	  nfa.or(Xml.GoofedElement("PMID"), get_PMID);
	  nfa.or(Xml.ETag("MedlineCitation"), end_document);
	  dfa = nfa.compile(DfaRun.UNMATCHED_COPY);

      // Parse the documents.
	  // The producer stops at the end of the MEDLINE document
      DfaRun dfaRun = new DfaRun(dfa);
      dfaRun.setIn(new ReaderCharSource(in, "UTF-8"));
      dfaRun.filter();
      
      System.err.println("Citations: " + count);
	}
	catch (Exception e)
	{ e.printStackTrace(); }
	finally
	{
      end = true;
      try { buffer.notify(); } catch (Exception ex) {}
	}
  }
}