package org.autonlab.anomalydetection;

import com.savarese.spatial.*;
import java.util.*;
import libsvm.*;
import org.apache.commons.collections.map.*;
import org.javatuples.*; //Tuples, Pair

public class AnomalyPrediction {
    /* this is temporary until we retrieve the old anomalies 
     * from Essence. Maybe we'll cache stuff later. I dunno
     */
    private static HashMap<Pair<Integer, Integer>, ArrayList<Pair<Integer, GenericPoint<Integer>>>> anomalyData = null;

    /**
     * @param anomalyObservedData
     * Then, for each pair of annotation and type we do a 1 vs all
     */
    public static void predictAnomalyType(Pair<Integer, GenericPoint<Integer>> anomalyObservedData, MultiValueMap results, StringBuilder output, ArrayList<Pair<Integer, GenericPoint<Integer>>> histogramDataForFakeAnomalies) {
	// XYZ upgrade dimensions if needed
	// XYZ scale scores

	// fill in some fake data
	if (anomalyData == null) {
	    anomalyData = new HashMap();

	    Pair<Integer, Integer> tempKey0 = new Pair(0,0);
	    Pair<Integer, Integer> tempKey1 = new Pair(1,0);
	    Pair<Integer, Integer> tempKey2 = new Pair(1,1);
	    Pair<Integer, Integer> tempKey3 = new Pair(0,1);
		    anomalyData.put(tempKey0, new ArrayList());
		    anomalyData.put(tempKey1, new ArrayList());
		    anomalyData.put(tempKey2, new ArrayList());
		    anomalyData.put(tempKey3, new ArrayList());
	    int state = 0;
	    for (Pair<Integer, GenericPoint<Integer>> fakeAnomaly : histogramDataForFakeAnomalies) {
		/*
		Pair<Integer, Integer> tempKey = new Pair(state & 0x0001, state & 0x0002);
		state++;

		if (!anomalyData.containsKey(tempKey)) {
		    anomalyData.put(tempKey, new ArrayList());
		}
		anomalyData.get(tempKey).add(fakeAnomaly);
		*/
		anomalyData.get(tempKey0).add(fakeAnomaly);
		anomalyData.get(tempKey1).add(fakeAnomaly);
		anomalyData.get(tempKey2).add(fakeAnomaly);
		anomalyData.get(tempKey3).add(fakeAnomaly);
	    }

	    for (Pair<Integer, Integer> tempKey  : anomalyData.keySet()) {
		if (output != null) {
		    output.append(tempKey.getValue0() + "," + tempKey.getValue1() + ": " + anomalyData.get(tempKey).size() + "\n");
		}
	    }
	}

	// Cycle through each possible prediction and see if the anomalyObservedData is similar to it
	for (Pair<Integer, Integer> tempPair : anomalyData.keySet()) {
	    
	    ArrayList<Pair<Integer, GenericPoint<Integer>>> one = anomalyData.get(tempPair);
	    ArrayList<Pair<Integer, GenericPoint<Integer>>> all = new ArrayList();
	    for (Pair<Integer, Integer> tempAllPair : anomalyData.keySet()) {
		if (!tempPair.equals(tempAllPair)) {
		    all.addAll(anomalyData.get(tempAllPair));
		}
	    }

	    svm_model svmModel = SVMCalc.generateModelOneVsAll(one, all, .9);

	    // XYZ is chi squared kernel appropriate here?
	    // only use 1 thread because the test set is a single 
	    ArrayList<Pair<Integer, GenericPoint<Integer>>> anomalyObservedDataPackaged = new ArrayList();
	    anomalyObservedDataPackaged.add(anomalyObservedData);

	    /* we have to do this every time because the order of one and all changes */
	    ArrayList<Pair<Integer, GenericPoint<Integer>>> combinedData = new ArrayList();
	    combinedData.addAll(one);
	    combinedData.addAll(all);
	    SVMKernel svmKernel = new SVMKernel(anomalyObservedDataPackaged, combinedData, AnomalyDetectionConfiguration.SVM_KERNEL_TYPE, AnomalyDetectionConfiguration.SVM_TYPE_PRECOMPUTED_KERNEL_TYPE, 1);
	   
	    svm_node[][] bar = svmKernel.getData();

	    /* now we have the training data model, run the anomaly against it
	       to get the prediction */
	    double[] values = new double[1];

	    // XYZ should I expect the bar to only have a single row in it?
	    double prediction = svm.svm_predict_values(svmModel, bar[0], values);
	    //double prediction = values[0];

	    // this code returns a lower score for more anomalous so we flip it to match kdtree
	    //prediction *= -1;

	    if (output != null) {
		output.append("Prediction " + tempPair.getValue0() + "," + tempPair.getValue1() + ": class " + prediction + " with score " + values[0] + " for anomaly " + anomalyObservedData.getValue1().toString() + " with data \n");
	    }

	    if (results != null) {
		results.put(prediction, tempPair);
	    }
	}
    }
}
