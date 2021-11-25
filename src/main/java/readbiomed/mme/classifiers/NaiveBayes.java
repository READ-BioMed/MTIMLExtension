package readbiomed.mme.classifiers;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import gov.nih.nlm.nls.mti.classifiers.ova.OVAClassifier;
import gov.nih.nlm.nls.mti.classifiers.ova.Prediction;
import gov.nih.nlm.nls.mti.instances.BinaryInstance;
import gov.nih.nlm.nls.mti.instances.Instance;
import gov.nih.nlm.nls.mti.instances.Instances;

public class NaiveBayes extends OVAClassifier
{
  /**
   *
   */
  private static final long serialVersionUID = -178477268224042272L;

  int alpha = 1;
  int unique_token_count;

  double [] prior = new double [2];

  int [] total_token_count_category = new int [2];

  Map <Integer, Integer> [] map_token_category = new HashMap [2];

  public void setup(String options)
  {
  }

  public void train(Instances is, Map<Integer, String> term_map)
  {
    map_token_category[0] = new HashMap <Integer, Integer> ();
    map_token_category[1] = new HashMap <Integer, Integer> ();

    Set <Instance> cat_ins =
       is.getCategoryInstances().get(getCategory());

    for (Instance i : is.getInstances())
    {
      int category = (cat_ins.contains(i) ? 1 : 0);

      prior[category]++;

      for (int token_id : ((BinaryInstance)i).getBinaryFeatures())
      {
        Integer count = map_token_category[category].get(token_id);

        if (count == null)
        { count = 0; }

        map_token_category[category].put(token_id, count + 1);

        total_token_count_category[category]++;
      }
    }

    prior[0] = prior[0]/(double)is.getInstances().size();
    prior[1] = prior[1]/(double)is.getInstances().size();

    unique_token_count = term_map.size();
  }

  @Override
  public int predict(Instance i)
  {     return predictConfidence(i).getPrediction(); }

  @Override
  public Prediction predictConfidence(Instance i)
  {
    double [] p_cat = new double [2];

    p_cat[0] = 0.0;
    p_cat[1] = 0.0;

    for (int token_id : ((BinaryInstance)i).getBinaryFeatures())
    {
      if (token_id <= unique_token_count)
      {
        // Category 0
        if (map_token_category[0].get(token_id) != null)
        { p_cat[0] += Math.log((alpha + (double)map_token_category[0].get(token_id))) - Math.log((double)(alpha*unique_token_count + total_token_count_category[0])); }
        else
        { p_cat[0] += Math.log((double)alpha) - Math.log((double)(alpha*unique_token_count + total_token_count_category[0])); }

        // Category 1
        if (map_token_category[1].get(token_id) != null)
        { p_cat[1] += Math.log((alpha + (double)map_token_category[1].get(token_id)))- Math.log((double)(alpha*unique_token_count + total_token_count_category[1])); }
        else
        { p_cat[1] += Math.log((double)alpha) - Math.log((double)(alpha*unique_token_count + total_token_count_category[1])); }
      }
    }

    p_cat[0] += Math.log(prior[0]);
    p_cat[1] += Math.log(prior[1]);

    if (p_cat[0] > p_cat[1])
    { return new Prediction(0, p_cat[0]); }
    else
    { return new Prediction(1, p_cat[1]); }
  }
}