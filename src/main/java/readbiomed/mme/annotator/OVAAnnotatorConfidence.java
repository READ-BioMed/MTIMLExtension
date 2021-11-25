package readbiomed.mme.annotator;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import gov.nih.nlm.nls.mti.classifiers.ova.OVAClassifier;
import gov.nih.nlm.nls.mti.classifiers.ova.Prediction;
import gov.nih.nlm.nls.mti.documents.Document;
import gov.nih.nlm.nls.mti.featuresextractors.FeatureExtractor;
import gov.nih.nlm.nls.mti.featuresextractors.FeatureExtractorFactory;
import gov.nih.nlm.nls.mti.instances.Instance;
import gov.nih.nlm.nls.mti.textprocessors.TextProcessor;
import gov.nih.nlm.nls.mti.textprocessors.TextProcessorFactory;
import monq.net.FilterServiceFactory;
import monq.net.Service;
import monq.net.ServiceCreateException;
import monq.net.ServiceFactory;
import monq.net.TcpServer;
import readbiomed.mme.util.Trie;

/**
 * One-versus-All Annotator (OVAAnnotator) tool.
 * 
 * @author Antonio Jimeno Yepes (antonio.jimeno@gmail.com)
 *
 */
public class OVAAnnotatorConfidence implements Service {
	private static String help() {
		return new StringBuilder(
				"OVAAnnotatorConfidence text_extractor_class text_extractor_options feature_processor_class feature_processor_options dictionary_file output_classifiers_file [port - starts as a server]")
						.toString();
	}

	private static Trie<Integer> trie_terms = null;
	private static Map<String, OVAClassifier> classifiers = null;
	private static Trie<Integer> trie_categories = new Trie<Integer>();
	private static Map<Integer, String> term_map = new HashMap<Integer, String>();

	private static String tp_class = null;
	private static String tp_options = null;
	private static String fe_class = null;
	private static String fe_options = null;

	private Exception exception = null;
	private InputStream in = null;
	private OutputStream out = null;

	public void run() {
		try {
			TextProcessor tp = TextProcessorFactory.create(tp_class, tp_options, in, trie_categories);

			FeatureExtractor fe = FeatureExtractorFactory.create(fe_class, fe_options, trie_terms, term_map);

			Document d = null;

			while ((d = tp.nextDocument()) != null) {
				Instance i = fe.prepareInstance(d);

				for (Map.Entry<String, OVAClassifier> cc : classifiers.entrySet()) {
					Prediction p = cc.getValue().predictConfidence(i);

					StringBuilder result = new StringBuilder().append(d.getId()).append("|").append(cc.getKey())
							.append("|").append(p.getPrediction()).append("|").append(p.getConfidence()).append("\n");

					out.write(result.toString().getBytes());
				}
			}
		} catch (Exception e) {
			exception = e;
		}
	}

	public OVAAnnotatorConfidence(InputStream in, OutputStream out) {
		this.in = in;
		this.out = out;
	}

	public static void main(String[] argc) throws FileNotFoundException, IOException, ClassNotFoundException,
			InstantiationException, IllegalAccessException {
		if (argc.length != 6 && argc.length != 7) {
			System.err.println(help());
			System.exit(-1);
		}

		// Load trie
		ObjectInputStream ot = new ObjectInputStream(new GZIPInputStream(new FileInputStream(argc[4])));
		trie_terms = (Trie<Integer>) ot.readObject();
		ot.close();

		// Load classifiers
		ObjectInputStream oc = new ObjectInputStream(new GZIPInputStream(new FileInputStream(argc[5])));
		classifiers = (Map<String, OVAClassifier>) oc.readObject();
		oc.close();

		tp_class = argc[0];
		tp_options = argc[1];
		fe_class = argc[2];
		fe_options = argc[3];

		if (argc.length == 7) {
			int port = Integer.parseInt(argc[6]);

			FilterServiceFactory fsf = new FilterServiceFactory(new ServiceFactory() {
				public Service createService(InputStream in, OutputStream out, Object params)
						throws ServiceCreateException {
					return new OVAAnnotatorConfidence(in, out);
				}
			});

			try {
				TcpServer svr = new TcpServer(new ServerSocket(port), fsf, 100);

				svr.setLogging(System.out);
				System.out.println("OVAAnnotatorConfidence listening on port: " + port);
				svr.serve();
			} catch (java.net.BindException be) {
				System.out.println("The server is already up and running.");
			}
		} else {
			// Create an annotator and run it
			new OVAAnnotatorConfidence(System.in, System.out).run();
		}
	}

	@Override
	public Exception getException() {
		return exception;
	}
}