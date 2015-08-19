package org.autonlab.anomalydetection;

public class AnomalyPrediction {
    /* this is temporary until we retrieve the old anomalies 
     * from Essence. Maybe we'll cache stuff later. I dunno
     */
    private HashMap<Pair<Integer, Integer>, ArrayList<Pair<Integer, GenericPoint<Integer>>>> anomalyData;

    /**
     * @param anomalyObservedData
     * Then, for each pair of annotation and type we do a 1 vs all
     */
    private static Pair<Integer, Integer> predictAnomalyType(Pair<Integer, GenericPoint<Integer>> anomalyObservedData, MultiValueMap results) {
	// XYZ upgrade dimensions if needed
	// XYZ scale scores

	// Cycle through each possible prediction and see if the anomalyObservedData is similar to it
	for (Pair<Integer, Integer> tempPair : anomalyData.keySet()) {
	    
	    ArrayList<Pair<Integer, GenericPoint<Integer>>> one = anomalyData.get(tempPair);
	    ArrayList<Pair<Integer, GenericPoint<Integer>>> all = new ArrayList();
	    for (Pair<Integer, Integer> tempAllPair : anomalyData.keySet()) {
		if (!tempPair.equals(tempAllPair)) {
		    all.addAll(anomalyData.get(tempAllPair));
		}
	    }

	    svm_model svmModel = SVMCalc.generateModelOneVsAll(one, .9 all, .9);

	    // XYZ is chi squared kernel appropriate here?
	    // only use 1 thread because the test set is a single 
	    SVMKernel svmKernel = new SVMKernel(anomalyObservedData, all, AnomalyDetectionConfiguration.SVM_KERNEL_TYPE, AnomalyDetectionConfiguration.SVM_TYPE_PRECOMPUTED_KERNEL_TYPE, 1);
	    svm_node[][] bar = svmKernel.getData();

	    /* now we have the training data model, run the anomaly against it
	       to get the prediction */
	    double[] values = new double[1];

	    // XYZ should I expect the bar to only have a single row in it?
	    svm.svm_predict_values(svmModel, bar[0], values);
	    double prediction = values[0];

	    // this code returns a lower score for more anomalous so we flip it to match kdtree
	    prediction *= -1;

	    output.append("Prediction " + tempPair.getValue0() + "," + tempPair.getValue1() + ": score " + prediction + " for anomaly " + anomalyObservedData.getValue1().toString() + " with data \n");

	    if (results != null) {
		results.put(prediction, tempPair);
	    }
	}
    }
}
