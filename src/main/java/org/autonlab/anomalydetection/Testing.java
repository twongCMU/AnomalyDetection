package org.autonlab.anomalydetection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.javatuples.Pair;

import com.savarese.spatial.GenericPoint;

public class Testing {

	static volatile HashMap<Integer, HashMap<GenericPoint<String>, HashMap<GenericPoint<String>, ArrayList<Pair<Integer, GenericPoint<Integer>>>>>> allHistogramsMap = new HashMap();
	// XYZ the lock should also protect reads/deletes to allHistogramsMap!

	static volatile int nextHistogramMapID = 0;
	static volatile Lock allHistogramsMapLock = new ReentrantLock();

	static int anomalyID = -1;
	static GenericPoint<String> anomalyKey;
	static String anomalyValue;

	/**
	 * Get the histogram's number of dimensions and their names. This is so that calls to /evaluate can be constructed properly
	 *
	 * @return the number of dimensions in the histogram, -1 if there's no matching IP, 0 if there's no data for that IP then a list of <index> <name> pairs, one per line
	 */
	public String getDimensions() {
		String output = new String("-1");

		output = "";
		output += "" + HistoTuple.getDimensionNames();
		
		return output;
	}

	
	public String getDatasetKeys() {
		String output = new String();

		for (Integer id : allHistogramsMap.keySet()) {
			output += "ID " + id + "<ul>";
			for (GenericPoint<String> valueName : allHistogramsMap.get(id).keySet()) {
				for (GenericPoint<String> keyFields : allHistogramsMap.get(id).get(valueName).keySet()) {
					output += "<li>";
					for (int ii = 0; ii < keyFields.getDimensions(); ii++) {
						output += keyFields.getCoord(ii);
						if (ii != keyFields.getDimensions() - 1) {
							output += "(" + valueName + ",";
						}
					}
					output += "</li>";
				}
			}
			output += "</ul>";
		}
		return output;
	}

	public String getHistograms(Integer id, String key, String value) {

		String output = new String();

		ArrayList<Pair<Integer, GenericPoint<Integer>>> histograms = allHistogramsMap.get(id).get(getPointFromCSV(value)).get(getPointFromCSV(key));
		for (Pair<Integer, GenericPoint<Integer>> tempPair : histograms) {
			output += tempPair.getValue0() + " : " + tempPair.getValue1().toString() + "\n";
		}
		return output;
	}

	/**
	 * 
	 */
	public void getFile(String filename) {
		allHistogramsMapLock.lock();
		StringBuilder output = new StringBuilder("Dataset ID: " + nextHistogramMapID + "\n");

		DataIOFile foo = new DataIOFile(filename);
		allHistogramsMap.put(nextHistogramMapID, HistoTuple.mergeWindows(foo.getData(), AnomalyDetectionConfiguration.SAMPLE_WINDOW_SECS, AnomalyDetectionConfiguration.SLIDE_WINDOW_SECS));
		for (GenericPoint<String> valueName : allHistogramsMap.get(nextHistogramMapID).keySet()) {
			for (GenericPoint<String> key : allHistogramsMap.get(nextHistogramMapID).get(valueName).keySet()) {
				output.append("Key: " + key.toString() + ", Value: " + valueName);
				output.append(" (datapoints: " + allHistogramsMap.get(nextHistogramMapID).get(valueName).get(key).size() + ")\n");
			}
		}
		nextHistogramMapID++;
		allHistogramsMapLock.unlock();
		System.out.print(output.toString());
	}

	public void getDb(String hostname, String keyCSV, String valueCSV) {
		allHistogramsMapLock.lock();

		StringBuilder output = new StringBuilder("Dataset ID: " + nextHistogramMapID + "\n");

		DataIOCassandraDB dbHandle = new DataIOCassandraDB(hostname, "demo2");

		if (keyCSV != null) {
			dbHandle.setKeyFields(keyCSV);
		}
		if (valueCSV != null) {
			dbHandle.setValueFields(valueCSV);
		}

		allHistogramsMap.put(nextHistogramMapID, HistoTuple.mergeWindows(dbHandle.getData(), AnomalyDetectionConfiguration.SAMPLE_WINDOW_SECS, AnomalyDetectionConfiguration.SLIDE_WINDOW_SECS));
		for (GenericPoint<String> valueName : allHistogramsMap.get(nextHistogramMapID).keySet()) {
			for (GenericPoint<String> key : allHistogramsMap.get(nextHistogramMapID).get(valueName).keySet()) {
				output.append("Key: " + key.toString() + ", Value: " + valueName);
				output.append(" (datapoints: " + allHistogramsMap.get(nextHistogramMapID).get(valueName).get(key).size() + ")\n");
			}
		}
		nextHistogramMapID++;
		allHistogramsMapLock.unlock();
		System.out.print(output.toString());
	}

	public void getFakeData() {
		GenericPoint<String> valueType = new GenericPoint(1);
		valueType.setCoord(0, "messagetype");

		allHistogramsMapLock.lock();

		StringBuilder output = new StringBuilder("Dataset ID: " + nextHistogramMapID + "\n");

		HashMap<GenericPoint<String>, ArrayList<Pair<Integer, GenericPoint<Integer>>>> fakeData = new HashMap();

		// generate a sparse-ish lower half of a matrix. This will be our training data
		ArrayList<Pair<Integer, GenericPoint<Integer>>> lowerHalf = new ArrayList();
		int fakeTime = 1;
		for (int i = 10; i < 50; i += 10) {
			for (int j = 10; j <= (50-i); j += 10) {
				GenericPoint<Integer> fakePoint = new GenericPoint(i, j);
				output.append(fakePoint.toString() + "\n");
				Pair<Integer, GenericPoint<Integer>> fakePair = new Pair(fakeTime, fakePoint);
				lowerHalf.add(fakePair);
				fakeTime++;
			}
		}
		GenericPoint<String> key = new GenericPoint(2);
		key.setCoord(0, "1.1.1.1");
		key.setCoord(1, "myapp");
		fakeData.put(key, lowerHalf);
		output.append("Key: 1.1.1.1, myapp (" + lowerHalf.size() + ")\n");

		// generate a dense full matrix. This will be test data used to run against the lower half matrix
		ArrayList<Pair<Integer, GenericPoint<Integer>>> fullMatrix = new ArrayList();
		for (int i = 10; i < 100; i += 10) {
			for (int j = 10; j < 100; j += 10) {
				GenericPoint<Integer> fakePoint = new GenericPoint(i, j);
				Pair<Integer, GenericPoint<Integer>> fakePair = new Pair(fakeTime, fakePoint);
				fullMatrix.add(fakePair);
				fakeTime++;
			}
		}
		GenericPoint<String> key2 = new GenericPoint(2);
		key2.setCoord(0, "5.5.5.5");
		key2.setCoord(1, "otherthing");
		fakeData.put(key2, fullMatrix);
		output.append("Key: 5.5.5.5, otherthing (" + fullMatrix.size() + ")\n");

		// generate some fake HistoTuples. these are unused but the code would crash without them
		HistoTuple foo = new HistoTuple(1, "fake1", valueType);
		HistoTuple foo2 = new HistoTuple(2, "fake2", valueType);

		HashMap<GenericPoint<String>, HashMap<GenericPoint<String>, ArrayList<Pair<Integer, GenericPoint<Integer>>>>> fakeDataFinal = new HashMap();
		fakeDataFinal.put(valueType, fakeData);
		allHistogramsMap.put(nextHistogramMapID, fakeDataFinal);
		nextHistogramMapID++;
		allHistogramsMapLock.unlock();
		System.out.print(output.toString());
	}

	/**
	 * Delete the mapping for one ID -> newMap
	 *
	 * Return "ok" on success, something else on error
	 */
	public String deleteOne(Integer id) {
		String output = "ok";
		if (!allHistogramsMap.containsKey(id)) {
			output = "No data matching id " + id;
		}
		else {
			allHistogramsMap.remove(id);
		}
		SVMCalc.removeModelFromCache(id);
		SVMRandomCalc.removeModelFromCache(id);
		return output;
	}	    

	public String getData(Integer trainID, String trainKey, String trainValue,
						  Integer testID, String testKey, String testValue) {
		if (AnomalyDetectionConfiguration.CALC_TYPE_TO_USE == AnomalyDetectionConfiguration.CALC_TYPE_KDTREE) {
			return getDataKDTree(trainID, trainKey, trainValue, testID, testKey, testValue);
		}
		else if (AnomalyDetectionConfiguration.CALC_TYPE_TO_USE == AnomalyDetectionConfiguration.CALC_TYPE_SVM) {
			return getDataSVM(trainID, trainKey, trainValue, testID, testKey, testValue);
		}
		else if (AnomalyDetectionConfiguration.CALC_TYPE_TO_USE == AnomalyDetectionConfiguration.CALC_TYPE_SVM_RANDOM) {
			return getDataSVMRandom(trainID, trainKey, trainValue, testID, testKey, testValue);
		}
		else {
			throw new RuntimeException("unknown calculation type");
		}
	}


	public String getDataKDTree(Integer trainID, String trainKey, String trainValue,
								Integer testID, String testKey, String testValue) {
		String output = "Calculation method: KDTree\n";

		output += KDTreeCalc.runOneTestKDTree(trainID, getPointFromCSV(trainKey), getPointFromCSV(trainValue), testID, getPointFromCSV(testKey), getPointFromCSV(testValue), null);
		return output;
	}

	public GenericPoint<String> getPointFromCSV(String csv) {
		String[] sParts = csv.split(",");
		Arrays.sort(sParts); // Why is there sorting going on here?
		GenericPoint<String> point = new GenericPoint<String>(sParts.length);

		for (int ii = 0; ii < sParts.length; ii++) {
			point.setCoord(ii, sParts[ii]);
		}

		return point;
	}

	public String getDataSVM(Integer trainID, String trainKey, String trainValue,
			  				   Integer testID, String testKey, String testValue) {
		StringBuilder output = new StringBuilder("Calculation method: SVM\n");

		output.append(SVMCalc.runOneTestSVM(trainID, getPointFromCSV(trainKey), getPointFromCSV(trainValue), testID, getPointFromCSV(testKey), getPointFromCSV(testValue), null));
		return output.toString();
	}

	public String getDataSVMRandom(Integer trainID, String trainKey, String trainValue,
			  						 Integer testID, String testKey, String testValue) {
		StringBuilder output = new StringBuilder("Calculation method: SVM Random\n");

		output.append(SVMRandomCalc.runOneTestSVM(trainID, getPointFromCSV(trainKey), getPointFromCSV(trainValue), testID, getPointFromCSV(testKey), getPointFromCSV(testValue), null));
		return output.toString();
	}


	public String getDataAll(String value) {
		if (AnomalyDetectionConfiguration.CALC_TYPE_TO_USE == AnomalyDetectionConfiguration.CALC_TYPE_KDTREE) {
			return getDataAllKDTree(value);
		}
		else if (AnomalyDetectionConfiguration.CALC_TYPE_TO_USE == AnomalyDetectionConfiguration.CALC_TYPE_SVM) {
			return getDataAllSVM(value);
		}
		else if (AnomalyDetectionConfiguration.CALC_TYPE_TO_USE == AnomalyDetectionConfiguration.CALC_TYPE_SVM_RANDOM) {
			return getDataAllSVMRandom(value);
		}
		else {
			throw new RuntimeException("unknown calculation type");
		}
	}

	public String getDataAllKDTree(String value) {
		StringBuilder output = new StringBuilder("Calculation method: KDTree\n");
		output.append(KDTreeCalc.runAllTestKDTree(getPointFromCSV(value)));
		return output.toString();
	}

	public String getDataAllSVM(String value) {
		StringBuilder output = new StringBuilder("Calculation method: SVM\n");
		output.append(SVMCalc.runAllTestSVM(getPointFromCSV(value)));
		return output.toString();
	}

	public String getDataAllSVMRandom(String value) {
		StringBuilder output = new StringBuilder("Calculation method: SVM\n");
		output.append(SVMRandomCalc.runAllTestSVM(getPointFromCSV(value)));
		return output.toString();
	}

	public void setSampleWindowSecs(int newValue) {
		AnomalyDetectionConfiguration.SAMPLE_WINDOW_SECS = newValue;
		System.out.println("ok");
	}

	public int getSampleWindowSecs() {
		return AnomalyDetectionConfiguration.SAMPLE_WINDOW_SECS;
	}

	public void setSlideWindowSecs(int newValue) {
		AnomalyDetectionConfiguration.SLIDE_WINDOW_SECS = newValue;
		System.out.println("ok");
	}

	public int getSlideWindowSecs() {
		return AnomalyDetectionConfiguration.SLIDE_WINDOW_SECS;
	}

	public String setCalcType(String newValue) {
		String output = "Ok. Calc type set to " + newValue;
		int newIndex = Arrays.asList(AnomalyDetectionConfiguration.CALC_TYPE_NAMES).indexOf(newValue);
		if (newIndex >= 0) {
			AnomalyDetectionConfiguration.CALC_TYPE_TO_USE = newIndex;
		}
		else {
			output = "Invalid type<br>\n";
		}
		output += AnomalyDetectionConfiguration.printCalcTypeNameLinksHTML("");
		return output;
	}

	public String getCalcType() {
		String output = "Calc type is " + AnomalyDetectionConfiguration.CALC_TYPE_NAMES[AnomalyDetectionConfiguration.CALC_TYPE_TO_USE];
		output += "\n" + AnomalyDetectionConfiguration.printCalcTypeNameLinksHTML("setCalcType/");
		return output;
	}

	public void setAnomalyKey(Integer id, String csvKey) {

		anomalyID = id;
		anomalyKey = getPointFromCSV(csvKey);

		System.out.println("ok");
	}
	
}