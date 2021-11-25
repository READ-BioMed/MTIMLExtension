package readbiomed.mme.classifiers;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import gov.nih.nlm.nls.mti.classifiers.ova.OVAClassifier;
import gov.nih.nlm.nls.mti.classifiers.ova.Prediction;
import gov.nih.nlm.nls.mti.instances.BinaryInstance;
import gov.nih.nlm.nls.mti.instances.Instance;
import gov.nih.nlm.nls.mti.instances.Instances;

public class BayesianNetwork extends OVAClassifier
{

  /**
   *
   */
  private static final long serialVersionUID = -1198876626219043512L;

  int alpha = 1;
  int beta = 1;

  int unique_token_count;
  int unique_concept_count;

  double total_tokens = 0;

  double [] prior = new double [2];

  // P(W|C, Ct)
  Map <Integer, Map <Integer, Integer>> [] map_token_concept_category = new HashMap [2];
  Map <Integer, Integer> [] total_token_concept_count_category = new Map [2];

  // P(Ct|C)
  int [] total_concept_count_category = new int [2];
  Map <Integer, Integer> [] map_concept_category = new HashMap [2];

  public void setup(String options)
  {
  }

  public void train(Instances is, Map<Integer, String> term_map)
  {
        map_concept_category[0] = new HashMap <Integer, Integer> ();
        map_concept_category[1] = new HashMap <Integer, Integer> ();

        map_token_concept_category[0] = new HashMap <Integer, Map <Integer, Integer>> ();
        map_token_concept_category[1] = new HashMap <Integer, Map <Integer, Integer>> ();

        total_token_concept_count_category[0] = new HashMap <Integer, Integer> ();
        total_token_concept_count_category[1] = new HashMap <Integer, Integer> ();

    Set <Instance> cat_ins =
        is.getCategoryInstances().get(getCategory());

    unique_concept_count = is.getCategoryInstances().keySet().size();

    // Categories per instance
    Map <Instance, Set <Integer>> ins_cat = new HashMap <Instance, Set <Integer>> ();

    for (Instance ins : is.getInstances())
    { ins_cat.put(ins, new HashSet <Integer> ()); }

    for (Map.Entry <Integer, Set<Instance>> entry : is.getCategoryInstances().entrySet())
    {
      if (!entry.getKey().equals(getCategory()))
      {
        for (Instance ins : entry.getValue())
        {
          Set <Integer> categories = ins_cat.get(ins);
          categories.add(entry.getKey());
        }
      }
    }

    for (Instance i : is.getInstances())
    {
      int category = (cat_ins.contains(i) ? 1 : 0);

      prior[category]++;

      total_tokens += ((BinaryInstance)i).getBinaryFeatures().length;

      // Count class-category
      for (Integer cat : ins_cat.get(i))
      {
        if (map_concept_category[category].get(cat) == null)
        { map_concept_category[category].put(cat, 1); }
        else
        { map_concept_category[category].put(cat, map_concept_category[category].get(cat) + 1); }

        total_concept_count_category[category]++;

        // Count word|class-category
        for (int token_id : ((BinaryInstance)i).getBinaryFeatures())
        {
          Map <Integer, Integer> token_count = map_token_concept_category[category].get(cat);

          if (token_count == null)
          {
            token_count = new HashMap <Integer, Integer> ();
            map_token_concept_category[category].put(cat, token_count);
          }

          if (token_count.get(token_id) == null)
          { token_count.put(token_id, 1); }
          else
          { token_count.put(token_id, token_count.get(token_id) + 1); }

          // Total token count
          if (total_token_concept_count_category[category].get(cat) == null)
          { total_token_concept_count_category[category].put(cat, 1); }
          else
          { total_token_concept_count_category[category].put(cat, total_token_concept_count_category[category].get(cat) + 1); }
        }
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
        for (int category = 0; category < 2; category++)
        {
          double sum = 0.0;

          for (Integer cat : map_concept_category[category].keySet())
          {

            if ((map_token_concept_category[0].get(cat) != null &&
                map_token_concept_category[0].get(cat).get(token_id) != null)
                || (map_token_concept_category[1].get(cat) != null &&
                map_token_concept_category[1].get(cat).get(token_id) != null))
            {
              Integer count = (map_token_concept_category[category].get(cat).get(token_id) == null ? 0 : map_token_concept_category[category].get(cat).get(token_id));

              /*Set <Integer> ks = new HashSet <Integer> ();

              if (map_token_concept_category[0].get(cat) != null)
              { ks.addAll(map_token_concept_category[0].get(cat).keySet()); }

              if (map_token_concept_category[1].get(cat) != null)
              { ks.addAll(map_token_concept_category[1].get(cat).keySet()); }*/

              int ks = 0;

              if (map_token_concept_category[0].get(cat) != null)
              { ks += map_token_concept_category[0].get(cat).size(); }

              if (map_token_concept_category[1].get(cat) != null)
              { ks += map_token_concept_category[1].get(cat).size(); }

              // P(W|Ct,C)
              double p_w_ct_c = Math.log(alpha + (double)count) - Math.log ((alpha * (double)ks) + (double)total_token_concept_count_category[category].get(cat));

              // P(Ct|C)
              double p_ct_c = Math.log((beta + (double)map_concept_category[category].get(cat))) - Math.log((beta * (double)unique_concept_count) + (double)total_concept_count_category[category]);

              sum += Math.exp(p_w_ct_c + p_ct_c);
            }
          }

          //{ p_cat[category] += Math.log(sum); }
          if (!Double.isInfinite(Math.log(sum)))
          { p_cat[category] += Math.log(sum); }
          else
          { p_cat[category] += Math.log(alpha / ((alpha * unique_token_count) + total_tokens)); }
        }
      }
    }

    p_cat[0] += Math.log(prior[0]);
    p_cat[1] += Math.log(prior[1]);

    System.out.println(p_cat[0]);
    System.out.println(p_cat[1]);

    if (p_cat[0] > p_cat[1])
    { return new Prediction(0, p_cat[0]); }
    else
    { return new Prediction(1, p_cat[1]); }
  }
}