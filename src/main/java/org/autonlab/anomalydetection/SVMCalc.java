package org.autonlab.anomalydetection;

import com.savarese.spatial.*;
import java.util.*;
import java.util.concurrent.locks.*;
import libsvm.*;
import org.apache.commons.collections.map.*;
import org.javatuples.*; //Tuples, Pair

/**
 * We use a chi-squared kernel. k(x,y) = 1- (sum[1->n, (xi-yi)^2 / .5(xi+yi)])
 * See http://crsouza.blogspot.com/2010/03/kernel-functions-for-machine-learning.html
 */
// MultiValueMap is not thread safe
// also, NU_START_POW_LOW is modified
public class SVMCalc {
     // cache of processed models. This is shared across concurrent accesses so we need to protect it with a lock
    static volatile HashMap<Integer, HashMap<GenericPoint<String>, HashMap<GenericPoint<String>, svm_model>>> _svmModelsCache = new HashMap();
    static volatile ReentrantLock _svmModelsCacheLock = new ReentrantLock();
  
    private static svm_model generateModel(ArrayList<Pair<Integer, GenericPoint<Integer>>> histograms, double targetCrossTrainAccuracy, ArrayList<Pair<Integer, GenericPoint<Integer>>> histogramsAnomaly, double targetAnomalyAccuracy) {
	TreeMap<Double, Double> nuValues = new TreeMap();
	System.out.println("YYY -------------------------");
	// generate a list of nu values to try (we can add to this later)
	for (double testNU : AnomalyDetectionConfiguration.NU_BASE_LIST) {
	    for (int testPow = AnomalyDetectionConfiguration.NU_START_POW_LOW; testPow <= AnomalyDetectionConfiguration.NU_START_POW_HIGH; testPow++) {
		nuValues.put(testNU * Math.pow(10, testPow), -1.0); // negative indicates that we still need to calculate it
	    }
	}

	// fill in the svm_problem with the histogram data points
	svm_problem svmProblem = new svm_problem();
	svmProblem.l = histograms.size();
	svmProblem.y = new double[histograms.size()];
	Arrays.fill(svmProblem.y, 1.0); // all of our training data is non-anomalous
	svmProblem.x =  (new SVMKernel(histograms, histograms, AnomalyDetectionConfiguration.SVM_KERNEL_TYPE, AnomalyDetectionConfiguration.SVM_TYPE_PRECOMPUTED_KERNEL_TYPE, AnomalyDetectionConfiguration.NUM_THREADS)).getData();

	svm_problem svmProblemAnomaly = null;
	if (histogramsAnomaly != null) {
	    svmProblemAnomaly = new svm_problem();
	    svmProblemAnomaly.l = histogramsAnomaly.size();
	    svmProblemAnomaly.y = new double[histogramsAnomaly.size()];
	    Arrays.fill(svmProblemAnomaly.y, -1.0); // set all of this data to anomalous
	    svmProblemAnomaly.x =  (new SVMKernel(histogramsAnomaly, histograms, AnomalyDetectionConfiguration.SVM_KERNEL_TYPE, AnomalyDetectionConfiguration.SVM_TYPE_PRECOMPUTED_KERNEL_TYPE, AnomalyDetectionConfiguration.NUM_THREADS)).getData();
	}

	svm_parameter svmParameter = new svm_parameter();
	svmParameter.svm_type = svm_parameter.ONE_CLASS;
	svmParameter.kernel_type = AnomalyDetectionConfiguration.SVM_KERNEL_TYPE;
	svmParameter.cache_size = AnomalyDetectionConfiguration.SVM_CACHE_SIZE;
	svmParameter.eps = AnomalyDetectionConfiguration.SVM_EPS;
	// the library uses kfold
	svmParameter.nu = allCrossValidate(svmProblem, svmParameter, nuValues, targetCrossTrainAccuracy, histogramsAnomaly, svmProblemAnomaly, targetAnomalyAccuracy);
	if (svmParameter.nu == -1) {
	    throw new RuntimeException("nu was not set");
	}
	System.out.println("YYY picked a nu of " + svmParameter.nu);

	// I don't know what limits we should set for expanding but I just don't want to get stuck in an infinite loop
	// or somehow have so small a nu that it stops being relevant
	int expandTimes = 0;
	while (svmParameter.nu == nuValues.firstKey() && expandTimes < 5) {
	    System.out.println("YYY expanding");
	    for (double testNU : AnomalyDetectionConfiguration.NU_BASE_LIST) {
		for (int testPow = AnomalyDetectionConfiguration.NU_START_POW_LOW; testPow > AnomalyDetectionConfiguration.NU_START_POW_LOW - AnomalyDetectionConfiguration.NU_EXPAND_INCREMENT; testPow--) {
		    nuValues.put(testNU * Math.pow(10, testPow), -1.0); // negative indicates that we still need to calculate it
		}
	    }

	    // The previous nu could still be the best option. We set this to -1 so allCrossValidate reconsiders it
	    // It is a hack because it causes us to re-do the work of calculating it. If this becomes a performance
	    // problem we can do something smarter
	    nuValues.put(svmParameter.nu, -1.0);

	    AnomalyDetectionConfiguration.NU_START_POW_LOW -= AnomalyDetectionConfiguration.NU_EXPAND_INCREMENT;
	    svmParameter.nu = allCrossValidate(svmProblem, svmParameter, nuValues, targetCrossTrainAccuracy, histogramsAnomaly, svmProblemAnomaly, 0.9);
	    expandTimes++;
	}

	System.out.println("YYY selected nu of " + svmParameter.nu);
	return svm.svm_train(svmProblem, svmParameter);
    }

    /**
     * For all entries in the nuValues TreeMap, if the value is negative, calculate the cross-validation accuracy
     * for nu equal to the TreeMap's key. Save the accuracy as the value in the TreeMap. If the value is not negative
     * we assume the accuracy has been previously calculated and we skip it. This allows us to expand the range of 
     * nu values we try if we decide to try additional values
     *
     * @param svmProblem precomputed svm_problem with the training data
     * @param svmParameter preset svm_parameter with the configuration parameters all set except for nu
     * @param nuValues a TreeMap of nu -> cross-validation accuracy
     * @param targetCrossTrainAccuracy The targetCrossTrainAccuracy accuracy. This function will return a nu that returns the closest accuracy to this or -1 if nuValues required no work
     *
     * @return a nu that generates the accuracy closest (absolute value) to the targetCrossTrainAccuracy parameter
     */
    private static double allCrossValidate(svm_problem svmProblem, svm_parameter svmParameter, TreeMap<Double, Double> nuValues, double targetCrossTrainAccuracy, ArrayList<Pair<Integer, GenericPoint<Integer>>> histogramsAnomaly, svm_problem svmProblemAnomaly, double targetAnomalyAccuracy) {
	double closestNUAccuracyDiff = Integer.MAX_VALUE;
	double closestNU = -1;

	for (Double nu : nuValues.keySet()) {
	    // if the value for this nu is non-negative, it means we've already calculated the accuracy so we can skip it
	    if (nuValues.get(nu) >= 0.0) {
		continue;
	    }
	    svmParameter.nu = nu;

	    String error_msg = svm.svm_check_parameter(svmProblem, svmParameter);
	    if (error_msg != null) {
		System.out.println("ERROR from parameter check " + error_msg);
	    }

	    double[] crossValidationResults = new double[svmProblem.l];
	    svm.svm_cross_validation(svmProblem, svmParameter, 4, crossValidationResults);

	    int total_correct = 0;
	    for (int i = 0; i < svmProblem.l; i++) {
		if (crossValidationResults[i] == svmProblem.y[i]) {
		    total_correct++;
		}
	    }

	    int totalCorrectAnomaly = -1;
	    if (histogramsAnomaly != null) {
		svm_model trainModel = svm.svm_train(svmProblem, svmParameter);
		totalCorrectAnomaly = 0;
		int index = 0;
		for (Pair<Integer, GenericPoint<Integer>> onePoint : histogramsAnomaly) {
		    double[] values = new double[1];
		    svm.svm_predict_values(trainModel, svmProblemAnomaly.x[index], values);
		    double prediction = values[0];

		    // this code returns a lower score for more anomalous so we flip it to match kdtree
		    prediction *= -1;
		    if (prediction >= 0) {
			totalCorrectAnomaly++;
		    }
		    index++;
		}
	    }

	    double accuracy = (1.0 * total_correct)/(1.0 * svmProblem.l);
	    System.out.print("YYY Cross Validation Accuracy = " + accuracy + " for nu " + nu + "\n");  

	    double accuracyAnomaly = -1;
	    if (totalCorrectAnomaly != -1) {
		accuracyAnomaly = (1.0 * totalCorrectAnomaly) / (1.0 * svmProblemAnomaly.l);
		System.out.print("YYY Cross Validation Accuracy for Anomaly = " + accuracyAnomaly + " for nu " + nu + "\n");  
	    }

	    // these two cases can eventually be collapsed into one case but I'm not sure where this nu calculation via anomaly will go in the fugure
	    // so I'm leaving it separate for now
	    if (totalCorrectAnomaly == -1) {
		// If our current best nu is at the edge of the range and the current nu is just as good but not on the edge, use it instead
		if ((closestNU == nuValues.lastKey() || closestNU == nuValues.firstKey()) &&
		    Math.abs(Math.abs(accuracy-targetCrossTrainAccuracy) - closestNUAccuracyDiff) < .000001 && nu != nuValues.lastKey() && nu != nuValues.firstKey()) {
		    closestNUAccuracyDiff = Math.abs(accuracy - targetCrossTrainAccuracy);
		    closestNU = nu;
		    System.out.println("YYY this is better because it is not on the range edge");
		}
		else if (Math.abs(accuracy - targetCrossTrainAccuracy) < closestNUAccuracyDiff) {
		    closestNUAccuracyDiff = Math.abs(accuracy - targetCrossTrainAccuracy);
		    closestNU = nu;
		    System.out.println("YYY this is closer to " + targetCrossTrainAccuracy + " with diff " + closestNUAccuracyDiff);
		}
		nuValues.put(nu, accuracy);
	    }
	    else {
		// If our current best nu is at the edge of the range and the current nu is just as good but not on the edge, use it instead
		if ((closestNU == nuValues.lastKey() || closestNU == nuValues.firstKey()) &&
		    Math.abs(Math.abs(accuracy-targetCrossTrainAccuracy) + Math.abs(accuracyAnomaly-targetAnomalyAccuracy) - closestNUAccuracyDiff) < .000001 && nu != nuValues.lastKey() && nu != nuValues.firstKey()) {
		    closestNUAccuracyDiff = Math.abs(accuracy - targetCrossTrainAccuracy) + Math.abs(accuracyAnomaly-targetAnomalyAccuracy);
		    closestNU = nu;
		    System.out.println("YYY this is better because it is not on the range edge");
		}
		else if (Math.abs(accuracy - targetCrossTrainAccuracy)  + Math.abs(accuracyAnomaly-targetAnomalyAccuracy) < closestNUAccuracyDiff) {
		    closestNUAccuracyDiff = Math.abs(accuracy - targetCrossTrainAccuracy) + Math.abs(accuracyAnomaly-targetAnomalyAccuracy);
		    closestNU = nu;
		    System.out.println("YYY this is closer to " + targetCrossTrainAccuracy + " with diff " + closestNUAccuracyDiff);
		}
		nuValues.put(nu, accuracy * accuracyAnomaly / 2);
	    }
	}

	return closestNU;
    }

    /**
     * @param trainID ID of the model to use to train on
     * @param trainKey Key index for the training set histograms
     * @param testID ID of the model to use to test on
     * @param testKey Key index for the test set histograms
     * @param anomalyID ID for a dataset that we will consider as all anomalies when training or null
     * @param results If not null, every result will be recorded here as score->timestamp. We use a MultiValueMap so duplicate scores will still be recorded
     *
     * @return some text that can be displayed to the user
     */
    public static StringBuilder runOneTestSVM(Integer trainID, GenericPoint<String> trainKey, GenericPoint<String> trainValue, Integer testID, GenericPoint<String> testKey, GenericPoint<String> testValue, Integer anomalyID, GenericPoint<String> anomalyKey, GenericPoint<String> anomalyValue, MultiValueMap results) {

	StringBuilder output = new StringBuilder();

    	svm_model svmModel = null;

	if (DaemonService.allHistogramsMap.get(trainID) == null) {
	    output.append("Error: trainID " + trainID + " not found");
	    return output;
	}
	if (DaemonService.allHistogramsMap.get(trainID).get(trainValue) == null) {
	    output.append("Error: trainValue " + trainValue + " not found");
	    return output;
	}
	if (DaemonService.allHistogramsMap.get(trainID).get(trainValue).get(trainKey) == null) {
	    output.append("Error: trainIP, trainApp pair of " + trainKey.toString() + " for trainID " + trainID + " not found");
	    return output;
	}
	if (DaemonService.allHistogramsMap.get(testID) == null) {
	    output.append("Error: testID " + testID + " not found");
	    return output;
	}
	if (DaemonService.allHistogramsMap.get(testID).get(testValue) == null) {
	    output.append("Error: testValue " + testValue + " not found");
	    return output;
	}
	if (DaemonService.allHistogramsMap.get(testID).get(testValue).get(testKey) == null) {
	    output.append("Error: testKey of " + testKey.toString() + " for testID " + testID + " not found");
	    return output;
	}
	if (DaemonService.allHistogramsMap.get(anomalyID) != null) {
	    if (DaemonService.allHistogramsMap.get(anomalyID).get(anomalyValue) == null) {
		output.append("Error: anomalyValue " + anomalyValue + " not found");
		return output;
	    }
	    if (DaemonService.allHistogramsMap.get(anomalyID).get(anomalyValue).get(anomalyKey) == null) {
		output.append("Error: anomalyKey of " + anomalyKey.toString() + " for anomalyID " + anomalyID + " not found");
		return output;
	    }
	}

	ArrayList<Pair<Integer, GenericPoint<Integer>>> anomalyHistogram = null;
	if (anomalyID != null) {
	    anomalyHistogram = DaemonService.allHistogramsMap.get(anomalyID).get(anomalyValue).get(anomalyKey).getValue1();
	}
	boolean changed = HistoTuple.upgradeWindowsDimensions(trainValue, DaemonService.allHistogramsMap.get(trainID).get(trainValue).get(trainKey).getValue1(), DaemonService.allHistogramsMap.get(testID).get(testValue).get(testKey).getValue1(), anomalyHistogram);

	_svmModelsCacheLock.lock();

	if (changed) {
	    _svmModelsCache.remove(trainID);
	    _svmModelsCache.remove(testID);
	}

	if (_svmModelsCache.get(trainID) == null ||
	    _svmModelsCache.get(trainID).get(trainValue) == null ||
	    _svmModelsCache.get(trainID).get(trainValue).get(trainKey) == null) {
	    _svmModelsCacheLock.unlock();

	    // this calculation can take some time so we unlock the cache and we'll recheck before we save it to cache
	    ArrayList<Pair<Integer, GenericPoint<Integer>>> anomalyData = null;
	    if (anomalyID != null) {
		anomalyData = DaemonService.allHistogramsMap.get(anomalyID).get(anomalyValue).get(anomalyKey).getValue1();
	    }
	    svmModel = SVMCalc.generateModel(DaemonService.allHistogramsMap.get(trainID).get(trainValue).get(trainKey).getValue1(), .9, anomalyData, .9);

	    _svmModelsCacheLock.lock();
	    if (_svmModelsCache.get(trainID) == null) {
		_svmModelsCache.put(trainID, new HashMap<GenericPoint<String>, HashMap<GenericPoint<String>, svm_model>>());
	    }
	    if (_svmModelsCache.get(trainID).get(trainValue) == null) {
		_svmModelsCache.get(trainID).put(trainValue, new HashMap<GenericPoint<String>, svm_model>());
	    }
	    if (_svmModelsCache.get(trainID).get(trainValue).get(trainKey) == null) {
		_svmModelsCache.get(trainID).get(trainValue).put(trainKey, svmModel);
	    }
	}
	else {
	    svmModel = _svmModelsCache.get(trainID).get(trainValue).get(trainKey);
	    System.out.println("SVM Model cache hit");

	}
	_svmModelsCacheLock.unlock();

	// test the training set against itself to get a scaling factor
	double anomalyScale = DaemonService.allHistogramsMap.get(trainID).get(trainValue).get(trainKey).getValue0();
	if (anomalyScale < 0.0) {
	    SVMKernel svmKernel = new SVMKernel(DaemonService.allHistogramsMap.get(trainID).get(trainValue).get(trainKey).getValue1(), DaemonService.allHistogramsMap.get(trainID).get(trainValue).get(trainKey).getValue1(), AnomalyDetectionConfiguration.SVM_KERNEL_TYPE, AnomalyDetectionConfiguration.SVM_TYPE_PRECOMPUTED_KERNEL_TYPE, AnomalyDetectionConfiguration.NUM_THREADS);
	    svm_node[][] bar = svmKernel.getData();

	    int index = 0;
	    anomalyScale = 0.0;
	    /* loop through the histograms to generate the predictions */
	    for (Pair<Integer, GenericPoint<Integer>> onePoint : DaemonService.allHistogramsMap.get(trainID).get(trainValue).get(trainKey).getValue1()) {
		double[] values = new double[1];

		svm.svm_predict_values(svmModel, bar[index], values);
		double prediction = values[0];

		// this code returns a lower score for more anomalous so we flip it to match kdtree
		prediction *= -1;
		System.out.println("prediction is " + prediction);
		if (anomalyScale < prediction) {
		    anomalyScale = prediction;
		}
		index++;
	    }

	    // this can happen if the data is very simliar or there isn't a lot of it. If we do nothing all of the results end up as "Infinity"
	    if (anomalyScale == 0.0) {
		anomalyScale = 1.0;
	    }

	    // the documentation for Pair doesn't say this but for some reason setAt0 doesn't overwrite the value, it returns a copy of the Pair with the new value
	    DaemonService.allHistogramsMap.get(trainID).get(trainValue).put(trainKey, DaemonService.allHistogramsMap.get(trainID).get(trainValue).get(trainKey).setAt0(anomalyScale));
	    System.out.println("Calculated scaling factor of " + anomalyScale);
	}
	else {
	    System.out.println("Using cached scaling factor of " + anomalyScale);
	}

	// If we're running many instances of similar test data against the same training data
	// we might want to implement a cache that's per-training set and save it externally
	// rather than the current scheme of only caching within an instance of SVMKernel
	SVMKernel svmKernel = new SVMKernel(DaemonService.allHistogramsMap.get(testID).get(testValue).get(testKey).getValue1(), DaemonService.allHistogramsMap.get(trainID).get(trainValue).get(trainKey).getValue1(), AnomalyDetectionConfiguration.SVM_KERNEL_TYPE, AnomalyDetectionConfiguration.SVM_TYPE_PRECOMPUTED_KERNEL_TYPE, AnomalyDetectionConfiguration.NUM_THREADS);
	svm_node[][] bar = svmKernel.getData();

	int index = 0;
	/* loop through the histograms to generate the predictions */
	for (Pair<Integer, GenericPoint<Integer>> onePoint : DaemonService.allHistogramsMap.get(testID).get(testValue).get(testKey).getValue1()) {
	    double[] values = new double[1];

	    svm.svm_predict_values(svmModel, bar[index], values);
	    double prediction = values[0];

	    // this code returns a lower score for more anomalous so we flip it to match kdtree
	    prediction *= -1;

	    prediction /= anomalyScale;

	    output.append("predicted " + prediction + " for " + onePoint.getValue1().toString() + " with data \n");

	    if (results != null) {
		results.put(prediction, onePoint);
	    }
	    index++;
	}

	return output;
    }

    public static void removeModelFromCache(int id) {
	_svmModelsCacheLock.lock();
	_svmModelsCache.remove(id);
	_svmModelsCacheLock.unlock();
    }

    /**
     * Test every combination against every other combinatino
     */
    public static StringBuilder runAllTestSVM(GenericPoint<String> valueType) {
	StringBuilder output = new StringBuilder();

	for (Integer keyID : DaemonService.allHistogramsMap.keySet()) {
	    for (Integer keyIDInner : DaemonService.allHistogramsMap.keySet()) {
		if (!DaemonService.allHistogramsMap.get(keyID).containsKey(valueType) ||
		    !DaemonService.allHistogramsMap.get(keyIDInner).containsKey(valueType)) {
		    continue;
		}
		for (GenericPoint<String> key : DaemonService.allHistogramsMap.get(keyID).get(valueType).keySet()) {
		    for (GenericPoint<String> keyInner : DaemonService.allHistogramsMap.get(keyIDInner).get(valueType).keySet()) {
			// A MultiValueMap is a HashMap where the value is an Collection of values (to handle duplicate keys)
			MultiValueMap resultsHash = new MultiValueMap();

			output.append("Highest 5 scores for ID " + keyID + " : <" + key.toString() + "> vs ID " + keyIDInner + " : <" + keyInner.toString() + ">\n");
			runOneTestSVM(keyID, key, valueType, keyIDInner, keyInner, valueType, null, null, null, resultsHash);

			List<Double> resultsHashList = new ArrayList<Double>(resultsHash.keySet());
			Collections.sort(resultsHashList); // ascending order
			Collections.reverse(resultsHashList); //descending order
			int ii = 0;
			for (Double score : resultsHashList) {
			    //if (ii == 0 && score == 0.0) {
			    //	output.append("[All scores are zero. Not printing]\n");
			    //	break;
			    //}
			    if (ii >= 5) {
				break;
			    }
			    for (Pair<Integer, GenericPoint<Integer>> onePoint : ((Collection<Pair<Integer, GenericPoint<Integer>>>)resultsHash.getCollection(score))) {
				Integer timestamp = onePoint.getValue0();
				output.append(score + " at time " + timestamp + "( " + ((Collection<Pair<Integer, GenericPoint<Integer>>>)resultsHash.getCollection(score)).size() + " with this score)\n");
				break;
			    }
			    ii++;
			}
		    }
		}
	    }
	}
	return output;
    }
}