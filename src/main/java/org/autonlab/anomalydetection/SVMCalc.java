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
    static volatile HashMap<Integer, HashMap<Pair<String, String>, svm_model>> _svmModelsCache = new HashMap();
    static volatile Lock _svmModelsCacheLock = new ReentrantLock();

    public static HashMap<Pair<String, String>, svm_model> makeSVMModel(HashMap<Pair<String, String>, ArrayList<Pair<Integer, GenericPoint<Integer>>>> histograms, StringBuilder output, double targetAccuracy) {
	HashMap<Pair<String, String>, svm_model> newMap = new HashMap();

	ArrayList<Pair<Integer, GenericPoint<Integer>>> anomalyData = null;
	if (DaemonService.anomalyID >= 0) {
	    anomalyData = DaemonService.allHistogramsMap.get(DaemonService.anomalyID).get(new Pair(DaemonService.anomalyIp, DaemonService.anomalyApp));
	}
	for (Pair<String, String> keyAddr : histograms.keySet()) {
	    // change 99.9 to be a parameter or maybe input to the rest call that sets the other anomaly key info
	    newMap.put(keyAddr, generateModel(histograms.get(keyAddr), targetAccuracy, anomalyData, 99.9));
	}

	return newMap;
    }

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
	    svmParameter.nu = allCrossValidate(svmProblem, svmParameter, nuValues, targetCrossTrainAccuracy, histogramsAnomaly, svmProblemAnomaly, 0.0);
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
     * @param trainIP IP address within the trainID model to consider
     * @param trainApp Application name within the trainID model to consider
     * @param testID ID of the model to use to test on
     * @param testIP IP address within the testID model to consider
     * @param testApp Application name within the testID model to consider
     * @param results If not null, every result will be recorded here as score->timestamp. We use a MultiValueMap so duplicate scores will still be recorded
     *
     * @return some text that can be displayed to the user
     */
    public static StringBuilder runOneTestSVM(Integer trainID, String trainIP, String trainApp, Integer testID, String testIP, String testApp, MultiValueMap results) {

	StringBuilder output = new StringBuilder();

    	HashMap<Pair<String, String>, svm_model> allModels;

	if (DaemonService.allHistogramsMap.get(trainID) == null) {
	    output.append("Error: trainID " + trainID + " not found");
	    return output;
	}
	if (DaemonService.allHistogramsMap.get(trainID).get(new Pair(trainIP, trainApp)) == null) {
	    output.append("Error: trainIP, trainApp pair of " + trainIP + "," + trainApp + " for trainID " + trainID + " not found");
	    return output;
	}
	if (DaemonService.allHistogramsMap.get(testID) == null) {
	    output.append("Error: testID " + testID + " not found");
	    return output;
	}
	if (DaemonService.allHistogramsMap.get(testID).get(new Pair(testIP, testApp)) == null) {
	    output.append("Error: testIP, testApp pair of " + testIP + "," + testApp + " for testID " + testID + " not found");
	    return output;
	}

	boolean trainChanged = HistoTuple.upgradeWindowsDimensions(DaemonService.allHistogramsMap.get(trainID).get(new Pair(trainIP, trainApp)));
	boolean testChanged = HistoTuple.upgradeWindowsDimensions(DaemonService.allHistogramsMap.get(testID).get(new Pair(testIP, testApp)));

	_svmModelsCacheLock.lock();

	if (trainChanged) {
	    _svmModelsCache.remove(trainID);
	}
	if (testChanged) {
	    _svmModelsCache.remove(testID);
	}

	allModels = _svmModelsCache.get(trainID);
	if (allModels == null) {
	    _svmModelsCacheLock.unlock();

	    // this calculation can take some time so we unlock
	    allModels = SVMCalc.makeSVMModel(DaemonService.allHistogramsMap.get(trainID), output, 1.0);

	    _svmModelsCacheLock.lock();
	    _svmModelsCache.put(trainID, allModels);
	}
	else {
	    System.out.println("SVM Model cache hit");

	}
	_svmModelsCacheLock.unlock();

	// If we're running many instances of similar test data against the same training data
	// we might want to implement a cache that's per-training set and save it externally
	// rather than the current scheme of only caching within an instance of SVMKernel
	SVMKernel svmKernel = new SVMKernel(DaemonService.allHistogramsMap.get(testID).get(new Pair(testIP, testApp)), DaemonService.allHistogramsMap.get(trainID).get(new Pair(trainIP, trainApp)), AnomalyDetectionConfiguration.SVM_KERNEL_TYPE, AnomalyDetectionConfiguration.SVM_TYPE_PRECOMPUTED_KERNEL_TYPE, AnomalyDetectionConfiguration.NUM_THREADS);
	svm_node[][] bar = svmKernel.getData();

	svm_model oneModel = allModels.get(new Pair(trainIP, trainApp));
	int index = 0;
	for (Pair<Integer, GenericPoint<Integer>> onePoint : DaemonService.allHistogramsMap.get(testID).get(new Pair(testIP, testApp))) {
	    double[] values = new double[1];
	    svm.svm_predict_values(oneModel, bar[index], values);
	    double prediction = values[0];

	    // this code returns a lower score for more anomalous so we flip it to match kdtree
	    prediction *= -1;

	    output.append("predicted " + prediction + " for " + onePoint.getValue1().toString() + " with data " + bar[index][0].value + "\n");

	    if (results != null) {
		results.put(prediction, onePoint.getValue0());
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
    public static StringBuilder runAllTestSVM() {
	StringBuilder output = new StringBuilder();

	for (Integer keyID : DaemonService.allHistogramsMap.keySet()) {
	    for (Integer keyIDInner : DaemonService.allHistogramsMap.keySet()) {
		for (Pair<String, String> keyPair : DaemonService.allHistogramsMap.get(keyID).keySet()) {
		    for (Pair<String, String> keyPairInner : DaemonService.allHistogramsMap.get(keyIDInner).keySet()) {
			// A MultiValueMap is a HashMap where the value is an Collection of values (to handle duplicate keys)
			MultiValueMap resultsHash = new MultiValueMap();

			output.append("Highest 5 scores for ID " + keyID + " : <" + keyPair.getValue(0) + "," + keyPair.getValue(1) + "> vs ID " + keyIDInner + " : <" + keyPairInner.getValue(0) + "," + keyPairInner.getValue(1) + ">\n");
			runOneTestSVM(keyID, keyPair.getValue0(), keyPair.getValue1(), keyIDInner, keyPairInner.getValue0(), keyPairInner.getValue1(), resultsHash);

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
			    for (Integer timestamp : ((Collection<Integer>)resultsHash.getCollection(score))) {
				output.append(score + " at time " + timestamp + "( " + ((Collection<Integer>)resultsHash.getCollection(score)).size() + " with this score)\n");
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