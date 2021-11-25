package readbiomed.mme.classifiers;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import gov.nih.nlm.nls.mti.classifiers.ova.OVAClassifier;
import gov.nih.nlm.nls.mti.classifiers.ova.Prediction;
import gov.nih.nlm.nls.mti.classifiers.ova.bf.C45;
import gov.nih.nlm.nls.mti.instances.Instance;
import gov.nih.nlm.nls.mti.instances.Instances;

/**
 * Implementation of AdaBoostM1, this implementation uses binary features and has C.45 as the default base learning algorithm 
 * <br/>
 * <br/>
 * Options to be used with the classifier:
 * <br/>
 * <br/>
 * -tn, where n is the number of iterations
 * 
 * @author Antonio Jimeno Yepes (antonio.jimeno@gmail.com)
 *
 */
public class AdaBoostM1 extends OVAClassifier
{
  private static final long serialVersionUID = 5044418508773657828L;
	
  private C45 [] h;
  private double [] alpha;
  private int T = 10;

  public String toString()
  { return AdaBoostM1.class.getName() + "|iterations=" + T; }
  
  private double adaBoostScore(Instance i)
  {
	double sum = 0.0;

    for (int c = 0; c < T; c++)
    {
      if (h[c] == null)
      { break; }
     
      sum += alpha[c] * (h[c].predict(i) == 1 ? 1 : -1);
    }

    return sum;	  
  }

  @Override
  public int predict(Instance i)
  {
    // Return prediction based on the sign
    return (Math.signum(adaBoostScore(i)) < 0 ? 0 : 1);
  }

  public Prediction predictProbability (Instance i)
  { 
	double score = adaBoostScore(i);
	
	double probability = 1/(1+Math.exp(-score));
	
	if (score > 0)
	{ return new Prediction(1, probability); }
	else
	{ return new Prediction(0, probability); }
  }
  
  public Prediction predictConfidence (Instance i)
  { 
	double score = adaBoostScore(i);
	
	if (score > 0)
	{ return new Prediction(1, score); }
	else
	{ return new Prediction(0, score); }
  }


  public static Instances resample(Instances is,
                                   int category,
                                   Instance [] instances,
                                   double [] D)
  {
	Instances isr = new Instances();
	isr.getCategoryInstances().put(category, new HashSet <Instance> (is.getCategoryInstances().get(category)));
	
	for (int iid = 0; iid < D.length; iid++)
    {
      for (int i = 0; i < D[iid] * is.getInstances().size(); i++)
      {	isr.getInstances().add(instances[iid]);}
    }

    System.out.println("Instances: " + isr.getInstances().size());
    System.out.println("Positives: " + isr.getCategoryInstances().get(category).size());
    
    return isr;
  }
  
  public void train(Instances is, Map<Integer, String> termMap)
  {
    // Weights for the instances
	Instance instances [] = is.getInstances().toArray(new Instance [0]);
	double D [] = new double [instances.length];
	  
	//Map <Instance, Double> D = new HashMap <Instance, Double> ();

	// Set D so all instances have the same weight
	for (int i = 0; i < D.length; i++)
	{ D[i] = 1/(double)D.length; }

	// Total number of iterations
	//T = 10;

	alpha = new double [T];
	h = new C45 [T];

	//System.out.println(mh);
	//int mh_id = lc.getTrieMH().get(mh);

	// Iterations
	for (int t = 0; t < T; t++)
	{
	  System.out.println("Iteration: " + t);
			
      Instances isr = resample(is, getCategory(), instances, D);	  

      // Train considering D_i
	  h[t] = new C45();
	  // Prunning and minimum number of instances in the leave nodes of the tree
	  h[t].setup("-p -m5");
	  h[t].setCategory(getCategory());
	  h[t].train(isr, termMap);
	  
      // Estimate the error
	  double e = 0.0;
		  
	  if (h[t] == null)
	  { System.out.println("No tree"); }
	  else
	  { h[t].printSplit(); }
	
	  Set <Instance> positives_sampled = isr.getCategoryInstances().get(getCategory()); 
	  
	  // Get when it is wrong
	  for (Instance d : isr.getInstances())
	  {
        int prediction = h[t].predict(d);
	  
	    if (!(
		  (prediction == 1 && positives_sampled.contains(d))
	    ||(prediction != 1 && !positives_sampled.contains(d))
	    ))
	    { e++; }
	  }
		  
	  e = e / isr.getInstances().size();
		  
	  System.out.println("e: " + e);
		  
	  // Error should be less than 0.5
	  if (e > 0.5 || e == 0.0)
	  {
		// If first iteration, keep the trained model
		if (t==0)
		{ alpha[t] = 1.0; }
		else
		{ h[t] = null; }
		break;
	  }

	  // Estimate alpha
	  alpha[t] = 0.5 * Math.log((1-e)/(double)e);
		  
	  System.out.println("alpha: " + alpha[t]);

	  Set <Instance> positives = is.getCategoryInstances().get(getCategory());
	  
	  double sum = 0;
	  // Estimate new D
	  for (int i = 0; i < D.length; i++)
      {
    	int prediction = h[t].predict(instances[i]);

    	double d_w;

    	if (
    		(prediction == 1 && positives.contains(instances[i]))
    	  ||(prediction != 1 && !positives.contains(instances[i]))
    	  )
    	{  
    	  // If properly classified
    	  d_w = D[i] * Math.pow(Math.E, -alpha[t]);
    	}
    	else
    	{
    	  // If not properly classified
    	  d_w = D[i] * Math.pow(Math.E, alpha[t]);
    	}

    	D[i] = d_w; 
    	
    	sum += d_w;
      }

	  // Normalize D, so it is a distribution
      for (int i = 0; i < D.length; i++)
      {
    	D[i] = D[i]/sum;
        System.out.println(i + "|" + D[i]);
      }
      
      // All properly classified
      if (sum == 0)
      { break; }
	}
  }

  /**
   * C.45 is the default classifier. No option has been set yet to change this choice.
   * 
   * @param options - -tn, where n is the number of iterations.
   */
  public void setup(String options)
  {
	if (options != null && options.length() > 0)
	{
      String [] tokens = options.split(" ");

      for (String option : tokens)
      {
    	if (option.startsWith("-t"))
    	{ T = Integer.parseInt(option.replaceAll("-t", "")); }
      }
	}
  }
}