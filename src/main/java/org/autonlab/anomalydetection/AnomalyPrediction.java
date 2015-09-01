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

	/*
	GenericPoint<Integer> fakePoint0 = new GenericPoint(320,0);
	Pair<Integer, GenericPoint<Integer>> fakeAnomaly0 = new Pair(0, fakePoint0);

	GenericPoint<Integer> fakePoint1 = new GenericPoint(340,0);
	Pair<Integer, GenericPoint<Integer>> fakeAnomaly1 = new Pair(0, fakePoint1);

	GenericPoint<Integer> fakePoint2 = new GenericPoint(360,0);
	Pair<Integer, GenericPoint<Integer>> fakeAnomaly2 = new Pair(0, fakePoint2);

	GenericPoint<Integer> fakePoint3 = new GenericPoint(380,0);
	Pair<Integer, GenericPoint<Integer>> fakeAnomaly3 = new Pair(0, fakePoint3);
	*/

	// fill in some fake data
	if (anomalyData == null) {
	    DataIOWriteAnomaly dataConn = new DataIOWriteAnomaly();
	    anomalyData = dataConn.getAnomalies("", "", "", "",
						"", null, "", 
						null, null);
	    dataConn.closeConnection();
	    dataConn = null;
	    

	    /*
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
	    for (int ii = 0; ii < 100; ii++) {
		anomalyData.get(tempKey0).add(fakeAnomaly0);
		anomalyData.get(tempKey1).add(fakeAnomaly1);
		anomalyData.get(tempKey2).add(fakeAnomaly2);
		anomalyData.get(tempKey3).add(fakeAnomaly3);
	    }

	    output.append ("Fake data:\n0,0: [320, 0]\n1,0: [340, 0]\n1,1: [360, 0]\n0,1: [380, 0]\n");
	    output.append("Best match has class=1.0 score 1.0. Over or under a score of 1.0 mean a deviation from the best match\n\n\n");
	    */
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

    /* 
     * As per the design, we have defined two types of patterns:
     * 1) if one or more histogram values are 2 or more stddev away from the mean, indicate those columns
     * 2) if all of the values are within 1 stddev from the mean, indicate that with a -1
     */
    public static ArrayList<Integer> patternAnomalyType(Pair<Integer, GenericPoint<Integer>> anomalyObservedData, Double[] mean, Double[] stddev) {
	GenericPoint<Integer> anomalyObservedDataHistograms = anomalyObservedData.getValue1();

	if (anomalyObservedDataHistograms.getDimensions() != mean.length) {
	    throw new RuntimeException("Size of anomaly observed data did not match size of mean array");
	}
	if (mean.length != stddev.length) {
	    throw new RuntimeException("Size of mean array does not match size of stddev array");
	}

	ArrayList<Integer> ret = new ArrayList(mean.length);

	/* check if any histogram values are 2 or more stddev away from the mean */
	for (int i = 0; i < anomalyObservedDataHistograms.getDimensions(); i++) {
	    if (Math.abs(anomalyObservedDataHistograms.getCoord(i)-mean[i]) >= (2*stddev[i])) {
		ret.add(i);
	    }
	}
	if (ret.size() > 0) {
	    ret.trimToSize();
	    return ret;
	}

	// If every single histogram value is between 1 and 2 stddev
	Boolean allAre = true;
	for (int i = 0; i < anomalyObservedDataHistograms.getDimensions(); i++) {
	    if (Math.abs(anomalyObservedDataHistograms.getCoord(i)-mean[i]) <= stddev[i] &&
		Math.abs(anomalyObservedDataHistograms.getCoord(i)-mean[i]) >= (2*stddev[i])) {
		allAre = false;
	    }
	}
    
	if (allAre) {
	    ret.add(-1);
	    ret.trimToSize();
	    return ret;
	}

	ret.trimToSize();
	return ret;
    }
}
