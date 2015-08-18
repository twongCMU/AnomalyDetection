package org.autonlab.anomalydetection;

public class AnomalyPrediction {
    /* this is temporary until we retrieve the old anomalies 
     * from essence. Maybe we'll cache stuff later. I dunno
     */
    private HashMap<Pair<Integer, Integer>, ArrayList<Pair<Integer, GenericPoint<Integer>>>> anomalyData;

    /**
     * Then, for each pair of annotation and type we do a 1 vs all
     */
    private static Pair<Integer, Integer> predictAnomalyType(GenericPoint<Integer> anomalyObservedData) {

	for (Pair<Integer, Integer> tempPair : anomalyData.keySet()) {
	    
	    ArrayList<Pair<Integer, GenericPoint<Integer>>> one = anomalyData.get(tempPair);
	    ArrayList<Pair<Integer, GenericPoint<Integer>>> all = new ArrayList();
	    for (Pair<Integer, Integer> tempAllPair : anomalyData.keySet()) {
		if (!tempPair.equals(tempAllPair)) {
		    all.addAll(anomalyData.get(tempAllPair));
		}
	    }

	    // XYZ one-class is specified here. We'll have to fix that
	    svm_model svmModel = SVMCalc.generateModel(one, .9 all, .9);

	    // XYZ anomalyObservedData isn't a proper time -> Point histogram so fix this
	    // XYZ is chi squared kernel appropriate here?
	    SVMKernel svmKernel = new SVMKernel(anomalyObservedData, all, AnomalyDetectionConfiguration.SVM_KERNEL_TYPE, AnomalyDetectionConfiguration.SVM_TYPE_PRECOMPUTED_KERNEL_TYPE, AnomalyDetectionConfiguration.NUM_THREADS);
	    svm_node[][] bar = svmKernel.getData();

	    /* now we have the training data model, run the anomaly against it
	       to get the prediction */
	    double[] values = new double[1];

	    // XYZ should I expect the bar to only have a single row in it?
	    svm.svm_predict_values(svmModel, bar[0], values);
	    double prediction = values[0];
	    
	}
    }
}
