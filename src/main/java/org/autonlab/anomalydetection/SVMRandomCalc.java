package org.autonlab.anomalydetection;

import com.savarese.spatial.*;

import java.util.*;
import java.util.concurrent.locks.*;

import libsvm.*;

import org.apache.commons.collections.map.*;
import org.javatuples.*; //Tuples, Pair

/**
 * Using the the randomized features at:
 * http://www.eecs.berkeley.edu/~brecht/papers/07.rah.rec.nips.pdf
 * 
 * Using a Gaussian Kernel --> draw features from the D-dimensional standard normal.
 * 
 * A few points:
 * 1. Add points to existing dot-product of histograms?
 * 2. Make new dot-product entirely? --> going with this first.
 */
// MultiValueMap is not thread safe
// also, NU_START_POW_LOW is modified
public class SVMRandomCalc {
	// cache of processed models. This is shared across concurrent accesses so we need to protect it with a lock
	static volatile HashMap<Integer, HashMap<GenericPoint<String>, HashMap<GenericPoint<String>, Pair<GaussianRandomFeatures, svm_model>>>> _svmModelsCache = 
			new HashMap<Integer, HashMap<GenericPoint<String>, HashMap<GenericPoint<String>, Pair<GaussianRandomFeatures, svm_model>>>>();
	static volatile Lock _svmModelsCacheLock = new ReentrantLock();

	public static svm_node[][] append(svm_node[][] a, svm_node[][] b) {
		svm_node[][] result = new svm_node[a.length + b.length][];
		System.arraycopy(a, 0, result, 0, a.length);
		System.arraycopy(b, 0, result, a.length, b.length);
		return result;
	}

	public static double[] append(double[] a, double[] b) {
		double[] result = new double[a.length + b.length];
		System.arraycopy(a, 0, result, 0, a.length);
		System.arraycopy(b, 0, result, a.length, b.length);
		return result;
	}

	private static Pair<GaussianRandomFeatures, svm_model> 
	generateModelOneClass(ArrayList<Pair<Integer, GenericPoint<Integer>>> histograms, 
			double targetCrossTrainAccuracy, ArrayList<Pair<Integer, 
			GenericPoint<Integer>>> histogramsAnomaly, double targetAnomalyAccuracy, int rn) {

		// For quiet SVM
		//svm.svm_set_print_string_function(new QuietPrint());

		TreeMap<Double, Double> nuValues = new TreeMap<Double,Double>();
		System.out.println("YYY -------------------------");
		// generate a list of nu values to try (we can add to this later)
		for (double testNU : AnomalyDetectionConfiguration.NU_BASE_LIST) {
			for (int testPow = AnomalyDetectionConfiguration.NU_START_POW_LOW; testPow <= AnomalyDetectionConfiguration.NU_START_POW_HIGH; testPow++) {
				nuValues.put(testNU * Math.pow(10, testPow), -1.0); // negative indicates that we still need to calculate it
			}
		}

		int d = AnomalyDetectionConfiguration.SVM_D;
		if (rn > 0) d = rn;
		//Initialize before so we can pull out coefficiencts.
		SVMRandomGaussian svmrg = 
		new SVMRandomGaussian(	histograms, d,
								AnomalyDetectionConfiguration.SVM_GAMMA,
								AnomalyDetectionConfiguration.RFF_SINE,
								AnomalyDetectionConfiguration.NUM_THREADS);
		GaussianRandomFeatures grf = svmrg.getRandomFeatures();

		// fill in the svm_problem with the histogram data points
		svm_problem svmProblem = new svm_problem();
		svmProblem.l = histograms.size();

		svmProblem.y = new double[histograms.size()]; // Not necessary
		Arrays.fill(svmProblem.y, 1.0); // all of our training data is non-anomalous

		svmProblem.x = (svmrg.getData());

		svm_problem svmProblemAnomaly = null;
		// Done only for grid search using cross-validation
		if (histogramsAnomaly != null) {
			svmProblemAnomaly = new svm_problem();
			svmProblemAnomaly.l = histogramsAnomaly.size();

			svmProblemAnomaly.y = new double[histogramsAnomaly.size()]; // Not necessary
			Arrays.fill(svmProblemAnomaly.y, -1.0); // set all of this data to anomalous

			svmProblemAnomaly.x = (new SVMRandomGaussian(histogramsAnomaly, d, grf, AnomalyDetectionConfiguration.NUM_THREADS)).getData();
		}


		svm_parameter svmParameter = new svm_parameter();
		svmParameter.svm_type = svm_parameter.ONE_CLASS;

		svmParameter.kernel_type = AnomalyDetectionConfiguration.SVM_RANDOM_KERNEL_TYPE;
		svmParameter.cache_size = AnomalyDetectionConfiguration.SVM_CACHE_SIZE;
		svmParameter.eps = AnomalyDetectionConfiguration.SVM_EPS;
		svmParameter.gamma = AnomalyDetectionConfiguration.SVM_GAMMA;
		
		// TODO: Need to fix allCrossValidate for only one single SVM for both normal and anomaly. 
		// the library uses kfold
		//svmParameter.nu = allCrossValidate(svmProblem, svmParameter, nuValues, targetCrossTrainAccuracy, null, null, targetAnomalyAccuracy);
		//		if (svmParameter.nu == -1) {
		//			throw new RuntimeException("nu was not set");
		//		}
		//		System.out.println("YYY picked a nu of " + svmParameter.nu);
		//
		//		// I don't know what limits we should set for expanding but I just don't want to get stuck in an infinite loop
		//		// or somehow have so small a nu that it stops being relevant
		//		int expandTimes = 0;
		//		while (svmParameter.nu == nuValues.firstKey() && expandTimes < 5) {
		//			System.out.println("YYY expanding");
		//			for (double testNU : AnomalyDetectionConfiguration.NU_BASE_LIST) {
		//				for (int testPow = AnomalyDetectionConfiguration.NU_START_POW_LOW; testPow > AnomalyDetectionConfiguration.NU_START_POW_LOW - AnomalyDetectionConfiguration.NU_EXPAND_INCREMENT; testPow--) {
		//					nuValues.put(testNU * Math.pow(10, testPow), -1.0); // negative indicates that we still need to calculate it
		//				}
		//			}
		//
		//			// The previous nu could still be the best option. We set this to -1 so allCrossValidate reconsiders it
		//			// It is a hack because it causes us to re-do the work of calculating it. If this becomes a performance
		//			// problem we can do something smarter
		//			nuValues.put(svmParameter.nu, -1.0);
		//
		//			AnomalyDetectionConfiguration.NU_START_POW_LOW -= AnomalyDetectionConfiguration.NU_EXPAND_INCREMENT;
		//			svmParameter.nu = allCrossValidate(svmProblem, svmParameter, nuValues, targetCrossTrainAccuracy, histogramsAnomaly, svmProblemAnomaly, 0.0);
		//			expandTimes++;
		//		}

		svmParameter.nu = 0.1;
		System.out.println("YYY selected nu of " + svmParameter.nu);
		Pair <GaussianRandomFeatures, svm_model> gffSVMPair = new Pair<GaussianRandomFeatures, svm_model> (grf, svm.svm_train(svmProblem, svmParameter));
		return gffSVMPair;
	}
	
	@SuppressWarnings("unused")
	private static Pair<GaussianRandomFeatures, svm_model> 
	generateModelTwoClass(ArrayList<Pair<Integer, GenericPoint<Integer>>> histograms, 
			double targetCrossTrainAccuracy, ArrayList<Pair<Integer, 
			GenericPoint<Integer>>> histogramsAnomaly, double targetAnomalyAccuracy, int rn) {

		// For quiet SVM
		svm.svm_set_print_string_function(new QuietPrint());

		TreeMap<Double, Double> nuValues = new TreeMap<Double,Double>();
		System.out.println("YYY -------------------------");
		// generate a list of nu values to try (we can add to this later)
		for (double testNU : AnomalyDetectionConfiguration.NU_BASE_LIST) {
			for (int testPow = AnomalyDetectionConfiguration.NU_START_POW_LOW; testPow <= AnomalyDetectionConfiguration.NU_START_POW_HIGH; testPow++) {
				nuValues.put(testNU * Math.pow(10, testPow), -1.0); // negative indicates that we still need to calculate it
			}
		}

		int d = AnomalyDetectionConfiguration.SVM_D;
		if (rn > 0) d = rn;
		//Initialize before so we can pull out coefficiencts.
		SVMRandomGaussian svmrg = 
		new SVMRandomGaussian(	histograms, d,
								AnomalyDetectionConfiguration.SVM_GAMMA,
								AnomalyDetectionConfiguration.RFF_SINE,
								AnomalyDetectionConfiguration.NUM_THREADS);
		GaussianRandomFeatures grf = svmrg.getRandomFeatures();

		// fill in the svm_problem with the histogram data points
		svm_problem svmProblem = new svm_problem();

		if (histogramsAnomaly != null) {
			svmProblem.l = histograms.size() + histogramsAnomaly.size();

			double y_normal[] = new double[histograms.size()], y_anomaly[] = new double[histogramsAnomaly.size()];
			Arrays.fill(y_normal, 1.0);
			Arrays.fill(y_anomaly, -1.0); // set all of this data to anomalous
			svmProblem.y = append(y_normal, y_anomaly);

			svmProblem.x = append(svmrg.getData(),
								  (new SVMRandomGaussian(histogramsAnomaly, d, grf, 
										  				 AnomalyDetectionConfiguration.NUM_THREADS)).getData());
		}
		else {
			svmProblem.l = histograms.size();
			svmProblem.y = new double[histograms.size()];
			Arrays.fill(svmProblem.y, 1.0); // all of our training data is non-anomalous
			svmProblem.x = (svmrg.getData());
		}

		svm_parameter svmParameter = new svm_parameter();
		svmParameter.svm_type = svm_parameter.ONE_CLASS;
		svmParameter.kernel_type = AnomalyDetectionConfiguration.SVM_RANDOM_KERNEL_TYPE;
		svmParameter.cache_size = AnomalyDetectionConfiguration.SVM_CACHE_SIZE;
		svmParameter.eps = AnomalyDetectionConfiguration.SVM_EPS;
		svmParameter.gamma = AnomalyDetectionConfiguration.SVM_GAMMA;
		
		// TODO: Need to fix allCrossValidate for only one single SVM for both normal and anomaly. 
		// the library uses kfold
		//svmParameter.nu = allCrossValidate(svmProblem, svmParameter, nuValues, targetCrossTrainAccuracy, null, null, targetAnomalyAccuracy);
		//		if (svmParameter.nu == -1) {
		//			throw new RuntimeException("nu was not set");
		//		}
		//		System.out.println("YYY picked a nu of " + svmParameter.nu);
		//
		//		// I don't know what limits we should set for expanding but I just don't want to get stuck in an infinite loop
		//		// or somehow have so small a nu that it stops being relevant
		//		int expandTimes = 0;
		//		while (svmParameter.nu == nuValues.firstKey() && expandTimes < 5) {
		//			System.out.println("YYY expanding");
		//			for (double testNU : AnomalyDetectionConfiguration.NU_BASE_LIST) {
		//				for (int testPow = AnomalyDetectionConfiguration.NU_START_POW_LOW; testPow > AnomalyDetectionConfiguration.NU_START_POW_LOW - AnomalyDetectionConfiguration.NU_EXPAND_INCREMENT; testPow--) {
		//					nuValues.put(testNU * Math.pow(10, testPow), -1.0); // negative indicates that we still need to calculate it
		//				}
		//			}
		//
		//			// The previous nu could still be the best option. We set this to -1 so allCrossValidate reconsiders it
		//			// It is a hack because it causes us to re-do the work of calculating it. If this becomes a performance
		//			// problem we can do something smarter
		//			nuValues.put(svmParameter.nu, -1.0);
		//
		//			AnomalyDetectionConfiguration.NU_START_POW_LOW -= AnomalyDetectionConfiguration.NU_EXPAND_INCREMENT;
		//			svmParameter.nu = allCrossValidate(svmProblem, svmParameter, nuValues, targetCrossTrainAccuracy, histogramsAnomaly, svmProblemAnomaly, 0.0);
		//			expandTimes++;
		//		}

		svmParameter.nu = 0.1;

		System.out.println("YYY selected nu of " + svmParameter.nu);
		Pair <GaussianRandomFeatures, svm_model> gffSVMPair = new Pair<GaussianRandomFeatures, svm_model> (grf, svm.svm_train(svmProblem, svmParameter));
		return gffSVMPair;
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
	// TODO: Fix this function for SVM for both normal and anomaly
	@SuppressWarnings("unused")
	private static double allCrossValidate(svm_problem svmProblem, svm_parameter svmParameter, 
			TreeMap<Double, Double> nuValues, double targetCrossTrainAccuracy, 
			ArrayList<Pair<Integer, GenericPoint<Integer>>> histogramsAnomaly, 
			svm_problem svmProblemAnomaly, double targetAnomalyAccuracy) {
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
	public static StringBuilder runOneTestSVM(Integer trainID, GenericPoint<String> trainKey, GenericPoint<String> trainValue, Integer testID, GenericPoint<String> testKey, GenericPoint<String> testValue, Integer anomalyID, GenericPoint<String> anomalyKey, GenericPoint<String> anomalyValue, MultiValueMap results, int rn) {
	//public static StringBuilder runOneTestSVM(Integer trainID, GenericPoint<String> trainKey, Integer testID, GenericPoint<String> testKey, MultiValueMap results) {
		StringBuilder output = new StringBuilder();

		GaussianRandomFeatures grf = null;
		svm_model svmModel = null;
		//HashMap<GenericPoint<String>, Pair<GaussianRandomFeatures, svm_model>> allModels;


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
		boolean changed = false; //HistoTuple.upgradeWindowsDimensions(trainValue, DaemonService.allHistogramsMap.get(trainID).get(trainValue).get(trainKey), DaemonService.allHistogramsMap.get(testID).get(testValue).get(testKey), anomalyHistogram);

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
		    Pair<GaussianRandomFeatures, svm_model> grf_svm =
			SVMRandomCalc.generateModelOneClass(DaemonService.allHistogramsMap.get(trainID).get(trainValue).get(trainKey).getValue1(), .9, anomalyData, .9, rn);
			
		    grf = grf_svm.getValue0(); 
		    svmModel = grf_svm.getValue1();
		    System.out.println("ZZZ grf " + grf);
		    _svmModelsCacheLock.lock();
		    if (_svmModelsCache.get(trainID) == null) {
			_svmModelsCache.put(trainID, new HashMap<GenericPoint<String>, HashMap<GenericPoint<String>, Pair<GaussianRandomFeatures, svm_model>>>());
		    }
		    if (_svmModelsCache.get(trainID).get(trainValue) == null) {
			_svmModelsCache.get(trainID).put(trainValue, new HashMap<GenericPoint<String>, Pair<GaussianRandomFeatures, svm_model>>());
		    }
		    if (_svmModelsCache.get(trainID).get(trainValue).get(trainKey) == null) {
			_svmModelsCache.get(trainID).get(trainValue).put(trainKey, grf_svm);
		    }
		}
		else {
		    Pair<GaussianRandomFeatures, svm_model> grf_svm = _svmModelsCache.get(trainID).get(trainValue).get(trainKey);
		    grf = grf_svm.getValue0(); 
		    svmModel = grf_svm.getValue1();
		    System.out.println("SVM Model cache hit");

		}
		_svmModelsCacheLock.unlock();
		if (rn <= 0) rn = AnomalyDetectionConfiguration.SVM_D;
		// test the training set against itself to get a scaling factor
		double anomalyScale = DaemonService.allHistogramsMap.get(trainID).get(trainValue).get(trainKey).getValue0();
		if (anomalyScale <= 1e-3) {
			SVMRandomGaussian svmSRG = new SVMRandomGaussian(DaemonService.allHistogramsMap.get(trainID).get(trainValue).get(trainKey).getValue1(), rn, grf, AnomalyDetectionConfiguration.NUM_THREADS);
			svm_node[][] bar = svmSRG.getData();

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
				if (anomalyScale < prediction)
					anomalyScale = prediction;
				index++;
			}
			// this can happen if the data is very simliar or there isn't a lot of it.  all of the results end up as "Infinity"
			if (anomalyScale <= 1e-3) {
				System.out.println("Calculated scaling factor of " + anomalyScale);
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
		SVMRandomGaussian GFSTest = new SVMRandomGaussian(DaemonService.allHistogramsMap.get(testID).get(testValue).get(testKey).getValue1(), rn, grf, AnomalyDetectionConfiguration.NUM_THREADS);
		svm_node[][] testFeatures = GFSTest.getData(); 
		
		int index = 0;
		//		System.out.println("QUICK TESTS: -------------------");
		//		
		//		GenericPoint<Integer> p1 = DaemonService.allHistogramsMap.get(testID).get(testKey).get(2).getValue1();
		//		GenericPoint<Integer> p2 = DaemonService.allHistogramsMap.get(testID).get(testKey).get(15).getValue1();
		//
		//		System.out.println("RBF Kernel: " +gff.gaussianKernel(p1, p2));
		//		System.out.println("Linear Kernel: " + gff.linearKernel(gff.computeGaussianFourierFeatures(p1), gff.computeGaussianFourierFeatures(p2)));
//		ArrayList<Pair<Integer, GenericPoint<Integer>>> ahists = DaemonService.allHistogramsMap.get(trainID).get(trainValue).get(trainKey).getValue1();
		ArrayList<Pair<Integer, GenericPoint<Integer>>> bhists = DaemonService.allHistogramsMap.get(testID).get(testValue).get(testKey).getValue1();
//		// bhists = ahists;
//		
//		output.append("\n\nLinear Random Kernel\n");
//		double [] lkernel = new double[ahists.size()]; 
//		for (int i = 0; i < ahists.size(); ++i) {
//			for (int j = 0; j < bhists.size(); ++j) {
//				lkernel[i] = GaussianRandomFeatures.linearKernel(grf.computeGaussianFourierFeatures(ahists.get(i).getValue1()),
//												grf.computeGaussianFourierFeatures(bhists.get(j).getValue1()));
//				output.append(String.format("%.5f", lkernel[i]) + ' ');
//			}
//			output.append('\n');
//		}
//		output.append("\n\nRBF Kernel\n");
//		double [] rkernel = new double[ahists.size()]; 
//		for (int i = 0; i < ahists.size(); ++i) {
//			for (int j = 0; j < bhists.size(); ++j) {
//				rkernel[i] = grf.gaussianKernel(ahists.get(i).getValue1(), bhists.get(j).getValue1());
//				output.append(String.format("%.5f", rkernel[i]) + ' ');
//			}
//			output.append('\n');
//		}
//			
//		output.append("\n\n");

				//              getFak
		//		System.out.println("--------------------------------");
		//		System.out.println("--------------------------------");		
		int dim = bhists.get(0).getValue1().getDimensions();
		for (Pair<Integer, GenericPoint<Integer>> onePoint : DaemonService.allHistogramsMap.get(testID).get(testValue).get(testKey).getValue1()) {
			double[] values = new double[1];
//			double d = svm.svm_predict_values(svmModel, testFeatures[index], values);
			double[] bnode = grf.computeGaussianFourierFeatures(onePoint.getValue1());
			svm_node[] bnode_svm = new svm_node[bnode.length];
			for (int k = 0; k < bnode.length; ++k) {
				bnode_svm[k] = new svm_node();
				bnode_svm[k].index = k+1;
				bnode_svm[k].value = bnode[k];
			}

			double d = svm.svm_predict_values(svmModel, bnode_svm, values);

			double prediction = values[0];

			// this code returns a lower score for more anomalous so we flip it to match kdtree
			prediction *= -1;

			output.append(index + ": score " + String.format("%.3f",prediction) +  " and predicted " + d + " for " + onePoint.getValue1().toString()+ "\n");

			if (results != null) {
				results.put(prediction, onePoint);
			}
			index++;
		}

		// output.append("\n\nSVectors\n");
		// bhists = ahists;
//		int dim = bhists.get(0).getValue1().getDimensions();
		double[][] SV = new double[svmModel.l][svmModel.SV[0].length];
		for (int k = 0; k < svmModel.l; ++k) {
			for (int l = 0; l < svmModel.SV[k].length; ++l) {
				SV[k][l] = svmModel.SV[k][l].value;
//			 	if (l < 10)
//			 		output.append(String.format("%.4f",svmModel.SV[k][l].value) + " ");
			 }
//			 output.append('\n');
		}
//		output.append("\n\nSVSVKernel\n");
//		for (int j = 0; j < svmModel.l; ++j) {
//			for (int i = 0; i < svmModel.l; ++i) {
//				double rk = GaussianRandomFeatures.linearKernel(SV[i], SV[j]);
//				output.append(String.format("%.5f", rk) + ' ');
//				}
//			output.append('\n');
//		}
		output.append("\n\nRHO: " + svmModel.rho[0] + "\n");
		
		output.append("\n\nSV Kernel\n");
		for (int i = 0; i < testFeatures.length; ++i) {
//			for (int j = 0; j < svmModel.l; ++j) {
				// Constructing svm_node to pass into 
//				GenericPoint<Integer> bhist = bhists.get(i).getValue1();
				svm_node [] bhist = testFeatures[i]; 
//				double[] bnode = new double[dim];
				double[] bnode = new double[bhist.length];
				for (int k = 0; k < bhist.length; ++k)
					//bnode[k] = bhist.getCoord(k);
					bnode[k] = bhist[k].value;

				for (int j = 0; j < svmModel.l; ++j) {
	//				double rk = GaussianRandomFeatures.linearKernel(grf.computeGaussianFourierFeatures(bnode), SV[j]);
					double rk = GaussianRandomFeatures.linearKernel(bnode, SV[j]);
					output.append(String.format("%.5f", rk) + ", ");
				}
			output.append('\n');
		}
		output.append('\n' + testFeatures[0].length + '\n');

		output.append("\n\nSV Coeffs\n");
		for (int i = 0; i < svmModel.l; ++i)
			output.append(String.format("%.5f", svmModel.sv_coef[0][i]) + ", ");
		output.append("\n");
		output.append("\nSV Indices\n");
				for (int i = 0; i < svmModel.l; ++i)
			output.append(svmModel.sv_indices[i]+ " ");
		output.append("\n\n");

		return output;
	}

	public static void removeModelFromCache(int id) {
		_svmModelsCacheLock.lock();
		_svmModelsCache.remove(id);
		_svmModelsCacheLock.unlock();
	}

	/**
	 * Test every combination against every other combination
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
						runOneTestSVM(keyID, key, valueType, keyIDInner, keyInner, valueType, null, null, null, resultsHash, AnomalyDetectionConfiguration.SVM_D);

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
//	/**
//	 * Test every combination against every other combination
//	 */
//	public static StringBuilder runAllTestSVM() {
//		StringBuilder output = new StringBuilder();
//
//		for (Integer keyID : DaemonService.allHistogramsMap.keySet()) {
//			for (Integer keyIDInner : DaemonService.allHistogramsMap.keySet()) {
//				for (GenericPoint<String> key : DaemonService.allHistogramsMap.get(keyID).keySet()) {
//					for (GenericPoint<String> keyInner : DaemonService.allHistogramsMap.get(keyIDInner).keySet()) {
//						// A MultiValueMap is a HashMap where the value is an Collection of values (to handle duplicate keys)
//						MultiValueMap resultsHash = new MultiValueMap();
//
//						output.append("Highest 5 scores for ID " + keyID + " : <" + key.toString() + "> vs ID " + keyIDInner + " : <" + keyInner.toString() + ">\n");
//						runOneTestSVM(keyID, key, keyIDInner, keyInner, resultsHash);
//
//						List<Double> resultsHashList = new ArrayList<Double>(resultsHash.keySet());
//						Collections.sort(resultsHashList); // ascending order
//						Collections.reverse(resultsHashList); //descending order
//						int ii = 0;
//						for (Double score : resultsHashList) {
//							//if (ii == 0 && score == 0.0) {
//							//	output.append("[All scores are zero. Not printing]\n");
//							//	break;
//							//}
//							if (ii >= 5) {
//								break;
//							}
//							for (Integer timestamp : ((Collection<Integer>)resultsHash.getCollection(score))) {
//								output.append(score + " at time " + timestamp + "( " + ((Collection<Integer>)resultsHash.getCollection(score)).size() + " with this score)\n");
//								break;
//							}
//							ii++;
//						}
//					}
//				}
//			}
//		}
//		return output;
//	}
}