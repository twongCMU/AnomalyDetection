package org.autonlab.anomalydetection;

import com.savarese.spatial.*;

import java.util.*; 
import java.util.concurrent.locks.*;

import javax.ws.rs.*;
import javax.ws.rs.core.*;

import org.apache.commons.collections.map.*;
import org.javatuples.*;
import org.apache.commons.math3.distribution.UniformRealDistribution;

@Path("/")
public class DaemonService {
	static volatile HashMap<Integer, HashMap<GenericPoint<String>, HashMap<GenericPoint<String>, ArrayList<Pair<Integer, GenericPoint<Integer>>>>>> allHistogramsMap = new HashMap();
	static volatile HashMap<GenericPoint<String>, HashMap<GenericPoint<String>, HashMap<Pair<Integer, Integer>, Integer>>> allHistogramsMapKeyRemap = new HashMap();
	// XYZ the lock should also protect reads/deletes to allHistogramsMap!

	static volatile int nextHistogramMapID = 0;
	static volatile ReentrantLock allHistogramsMapLock = new ReentrantLock();

	/**
	 * Get the histogram's number of dimensions and their names. This is so that calls to /evaluate can be constructed properly
	 *
	 * @return the number of dimensions in the histogram, -1 if there's no matching IP, 0 if there's no data for that IP then a list of <index> <name> pairs, one per line
	 */
	@GET
	@Path("/getdimensions")
	@Produces(MediaType.TEXT_PLAIN)
	public Response getDimensions() {
		String output = new String("-1");

		output = "";
		output += "" + HistoTuple.getDimensionNames();

		return Response.status(200).entity(output).build();
	}


	@GET
	@Path("/getDatasetKeys")
	@Produces(MediaType.TEXT_HTML)
	public Response getDatasetKeys() {
		String output = new String();

		for (Integer id : allHistogramsMap.keySet()) {
			output += "ID " + id + "<ul>";
			for (GenericPoint<String> valueName : allHistogramsMap.get(id).keySet()) {
				for (GenericPoint<String> categoryFields : allHistogramsMap.get(id).get(valueName).keySet()) {
					output += "<li>";
					for (int ii = 0; ii < categoryFields.getDimensions(); ii++) {
						output += categoryFields.getCoord(ii);
						if (ii != categoryFields.getDimensions() - 1) {
							output += "(" + valueName + ",";
						}
					}
					output += "</li>";
				}
			}
			output += "</ul>";
		}
		return Response.status(200).entity(output).build();
	}

	@GET
	@Path("/getHistograms")
	@Produces(MediaType.TEXT_PLAIN)
	public Response getHistograms(@QueryParam("id") Integer id,
			@QueryParam("categoryCSV") String category,
			@QueryParam("valueCSV") String value) {

		StringBuilder output = new StringBuilder();

		int newID = recalculateByCategory(id, category, value, output);
		if (newID != id) {
			output.append("Didn't find the data at id " + id + " but found it at id " + newID);
			id = newID;
		}
		System.out.println("ZZZ id is now " + id + "\n");
		ArrayList<Pair<Integer, GenericPoint<Integer>>> histograms = allHistogramsMap.get(id).get(getPointFromCSV(value)).get(getPointFromCSV(category));
		output.append("Number of datapoints: " + histograms.size() + " \n");
		for (Pair<Integer, GenericPoint<Integer>> tempPair : histograms) {
			output.append(tempPair.getValue0() + " : " + tempPair.getValue1().toString() + "\n");
		}
		return Response.status(200).entity(output.toString()).build();
	}

	/**
	 * 
	 */
	@GET
	@Path("/getfile")
	@Produces(MediaType.TEXT_PLAIN)
	public Response getFile(@QueryParam("filename") String filename) {
		allHistogramsMapLock.lock();
		StringBuilder output = new StringBuilder("Dataset ID: " + nextHistogramMapID + "\n");

		DataIOFile foo = new DataIOFile(filename);
		allHistogramsMap.put(nextHistogramMapID, HistoTuple.mergeWindows(foo.getData(0), AnomalyDetectionConfiguration.SAMPLE_WINDOW_SECS, AnomalyDetectionConfiguration.SLIDE_WINDOW_SECS));
		for (GenericPoint<String> valueName : allHistogramsMap.get(nextHistogramMapID).keySet()) {
			for (GenericPoint<String> category : allHistogramsMap.get(nextHistogramMapID).get(valueName).keySet()) {
				output.append("Category: " + category.toString() + ", Value: " + valueName);
				output.append(" (datapoints: " + allHistogramsMap.get(nextHistogramMapID).get(valueName).get(category).size() + ")\n");
			}
		}
		nextHistogramMapID++;
		allHistogramsMapLock.unlock();
		return Response.status(200).entity(output.toString()).build();
	}

	/**
	 *
	 * @param ageMins The number of minutes into the past from the current time to include data. Everything else is excluded
	 *                 NOTE: as of this writing we're hard coding the current time to be 2014-05-12T13:54:12.000-04:00 since
	 *                       that is the most recent time of our test data and we're not using live data
	 */
	@GET
	@Path("/getdb")
	@Produces(MediaType.TEXT_PLAIN)
	public Response getDb(@QueryParam("hostname") String hostname,
			@QueryParam("categoryCSV") String categoryCSV,
			@QueryParam("value") String valueCSV,
			@QueryParam("ageMins") Integer ageMins) {
		allHistogramsMapLock.lock();

		StringBuilder output = new StringBuilder("Dataset ID: " + nextHistogramMapID + "\n");

		DataIOCassandraDB dbHandle = new DataIOCassandraDB(hostname, "demo2");

		if (categoryCSV != null) {
			dbHandle.setCategoryFields(categoryCSV);
		}
		if (valueCSV != null) {
			dbHandle.setValueFields(valueCSV);
		}
		if (ageMins == null) {
			ageMins = 0;
		}

		allHistogramsMap.put(nextHistogramMapID, HistoTuple.mergeWindows(dbHandle.getData(ageMins), AnomalyDetectionConfiguration.SAMPLE_WINDOW_SECS, AnomalyDetectionConfiguration.SLIDE_WINDOW_SECS));
		for (GenericPoint<String> valueName : allHistogramsMap.get(nextHistogramMapID).keySet()) {
			for (GenericPoint<String> category : allHistogramsMap.get(nextHistogramMapID).get(valueName).keySet()) {
				output.append("Category: " + category.toString() + ", Value: " + valueName);
				output.append(" (datapoints: " + allHistogramsMap.get(nextHistogramMapID).get(valueName).get(category).size() + ")\n");
			}
		}
		nextHistogramMapID++;
		allHistogramsMapLock.unlock();
		return Response.status(200).entity(output.toString()).build();
	}

	@GET
	@Path("/getfakedata")
	@Produces(MediaType.TEXT_PLAIN)
	public Response getFakeData() {
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
		GenericPoint<String> category = new GenericPoint(2);
		category.setCoord(0, "1.1.1.1");
		category.setCoord(1, "myapp");
		fakeData.put(category, lowerHalf);
		output.append("Category: 1.1.1.1, myapp (" + lowerHalf.size() + ")\n");

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
		GenericPoint<String> category2 = new GenericPoint(2);
		category2.setCoord(0, "5.5.5.5");
		category2.setCoord(1, "otherthing");
		fakeData.put(category2, fullMatrix);
		output.append("Category: 5.5.5.5, otherthing (" + fullMatrix.size() + ")\n");

		// generate some fake HistoTuples. these are unused but the code would crash without them
		HistoTuple foo = new HistoTuple(1, "fake1", valueType);
		HistoTuple foo2 = new HistoTuple(2, "fake2", valueType);

		HashMap<GenericPoint<String>, HashMap<GenericPoint<String>, ArrayList<Pair<Integer, GenericPoint<Integer>>>>> fakeDataFinal = new HashMap();
		fakeDataFinal.put(valueType, fakeData);
		allHistogramsMap.put(nextHistogramMapID, fakeDataFinal);
		nextHistogramMapID++;
		allHistogramsMapLock.unlock();
		return Response.status(200).entity(output.toString()).build();
	}

	/**
	 * Delete the mapping for one ID -> newMap
	 *
	 * Return "ok" on success, something else on error
	 */
	@GET
	@Path("/deleteone/{id}")
	@Produces(MediaType.TEXT_PLAIN)
	public Response deleteOne(@PathParam("id") Integer id) {
		String output = "ok";
		if (!allHistogramsMap.containsKey(id)) {
			output = "No data matching id " + id;
		}
		else {
			allHistogramsMap.remove(id);
		}
		SVMCalc.removeModelFromCache(id);
		return Response.status(200).entity(output).build();
	}	    

	@GET
	@Path("/test")
	@Produces(MediaType.TEXT_PLAIN)
	public Response getData(@QueryParam("trainID") Integer trainID,
			@QueryParam("trainCategoryCSV") String trainCategory,
			@QueryParam("trainValue") String trainValue,
			@QueryParam("testID") Integer testID,
			@QueryParam("testCategoryCSV") String testCategory,
			@QueryParam("testValue") String testValue,
			@QueryParam("anomalyTrainID") Integer anomalyTrainID,
			@QueryParam("anomalyTrainCategoryCSV") String anomalyTrainCategory,
			@QueryParam("anomalyTrainValue") String anomalyTrainValue,
			@QueryParam("autoReload") Integer autoReloadSec) {
		if (AnomalyDetectionConfiguration.CALC_TYPE_TO_USE == AnomalyDetectionConfiguration.CALC_TYPE_KDTREE) {
			return getDataKDTree(trainID, trainCategory, trainValue, testID, testCategory, testValue);
		}
		else if (AnomalyDetectionConfiguration.CALC_TYPE_TO_USE == AnomalyDetectionConfiguration.CALC_TYPE_SVM) {
			return getDataSVM(trainID, trainCategory, trainValue, testID, testCategory, testValue, anomalyTrainID, anomalyTrainCategory, anomalyTrainValue, autoReloadSec);
		}
		else {
			throw new RuntimeException("unknown calculation type");
		}
	}

	@GET
	@Path("/testKDTree")
	@Produces(MediaType.TEXT_PLAIN)
	public Response getDataKDTree(@QueryParam("trainID") Integer trainID,
			@QueryParam("trainCategoryCSV") String trainCategory,
			@QueryParam("trainValue") String trainValue,
			@QueryParam("testID") Integer testID,
			@QueryParam("testCategoryCSV") String testCategory,
			@QueryParam("testValue") String testValue) {
		String output = "Calculation method: KDTree\n";

		int error = 0;
		if (trainID == null) {
			System.out.println("train ID not set");
			error++;
		}

		if (testID == null) {
			System.out.println("test ID not set");
			error++;
		}

		if (trainCategory == null) {
			System.out.println("train Category not set");
			error++;
		}

		if (testCategory == null) {
			System.out.println("test Category not set");
			error++;
		}

		if (trainValue == null) {
			System.out.println("train Value not set");
			error++;
		}

		if (testValue == null) {
			System.out.println("test Value not set");
			error++;
		}

		if (error > 0) {
			throw new RuntimeException("error in inputs");
		}

		output += KDTreeCalc.runOneTestKDTree(trainID, getPointFromCSV(trainCategory), getPointFromCSV(trainValue), testID, getPointFromCSV(testCategory), getPointFromCSV(testValue), null);
		return Response.status(200).entity(output).build();
	}

	public GenericPoint<String> getPointFromCSV(String csv) {
		String[] sParts = csv.split(",");
		Arrays.sort(sParts);
		GenericPoint<String> point = new GenericPoint(sParts.length);

		for (int ii = 0; ii < sParts.length; ii++) {
			point.setCoord(ii, sParts[ii]);
		}

		return point;
	}

	/**
	 * @param id The data index ID returned when the data was read in
	 * @param categoryCSV a CSV of category
	 * @param valueCSV a CSV of histogram values
	 * @param output function will add text to this if it makes a new mapping
	 *
	 * @return the ID that contains the data with indexes valueCSV and categoryCSV (which may be different from the input id) or -1 if none
	 */
	public int recalculateByCategory(Integer id, String categoryCSV, String valueCSV, StringBuilder output) {
		GenericPoint<String> categoryPoint = getPointFromCSV(categoryCSV);
		GenericPoint<String> valuePoint    = getPointFromCSV(valueCSV);

		// If the data we want is already there
		if (allHistogramsMap.get(id) == null) {
			throw new RuntimeException("Error: id " + id + " not found");
		}
		if (allHistogramsMap.get(id).get(valuePoint) == null) {
			throw new RuntimeException("Error: value key '" + valuePoint + "' not found");
		}
		if (allHistogramsMap.get(id).get(valuePoint).get(categoryPoint) != null) {
			return id;
		}
		// XYZ need to calculate the time range from any existing data even if it doesn't match key and value since all data in ID has the same ranges
		Pair<Integer, Integer> startAndEnd = getStartAndEndTime(id);
		if (allHistogramsMapKeyRemap.get(valuePoint) != null &&
				allHistogramsMapKeyRemap.get(valuePoint).get(categoryPoint) != null &&
				startAndEnd != null &&
				allHistogramsMapKeyRemap.get(valuePoint).get(categoryPoint).get(startAndEnd) != null) {
			return allHistogramsMapKeyRemap.get(valuePoint).get(categoryPoint).get(startAndEnd);
		}
		// XYZ probably move this lock to beginning of function
		allHistogramsMapLock.lock();

		allHistogramsMap.put(nextHistogramMapID, new HashMap<GenericPoint<String>, HashMap<GenericPoint<String>, ArrayList<Pair<Integer, GenericPoint<Integer>>>>>());

		allHistogramsMap.get(nextHistogramMapID).put(valuePoint, new HashMap<GenericPoint<String>, ArrayList<Pair<Integer, GenericPoint<Integer>>>>());
		allHistogramsMap.get(nextHistogramMapID).get(valuePoint).put(categoryPoint, new ArrayList<Pair<Integer, GenericPoint<Integer>>>());

		int subsetsFound = 0;
		// the histograms are in an arraylist so it's hard to add them together. Sum them in a hashmap then convert it to an arraymap
		TreeMap<Integer, GenericPoint<Integer>> newDataSum = new TreeMap(); //use TreeMap not HashMap so we get keys back in sorted orders
		for (GenericPoint<String> oneKey : allHistogramsMap.get(id).get(valuePoint).keySet()) {
			System.out.println("XYZ " + oneKey.toString() + " compare to " + categoryPoint.toString());

			if (isPointSubset(categoryPoint, oneKey)) {
				System.out.println("counting " + categoryPoint.toString() + " as subset as " + oneKey.toString() + "\n");
				subsetsFound++;
				for (Pair<Integer, GenericPoint<Integer>> oneHist : allHistogramsMap.get(id).get(valuePoint).get(oneKey)) {

					GenericPoint<Integer> sumData = newDataSum.get(oneHist.getValue0());
					if (sumData == null) {
						sumData = new GenericPoint<Integer>(oneHist.getValue1().getDimensions());
					}
					int copyIndex = 0;
					for (copyIndex = 0; copyIndex < oneHist.getValue1().getDimensions(); copyIndex++) {
						if (sumData.getCoord(copyIndex) == null) {
							sumData.setCoord(copyIndex, oneHist.getValue1().getCoord(copyIndex));
						}
						else {
							sumData.setCoord(copyIndex, sumData.getCoord(copyIndex) + oneHist.getValue1().getCoord(copyIndex));
						}
					}
					newDataSum.put(oneHist.getValue0(), sumData);
				}
			}
		}

		// convert the newdataSum TreeMap to an ArrayList
		for (Integer timeSec : newDataSum.keySet()) {
			allHistogramsMap.get(nextHistogramMapID).get(valuePoint).get(categoryPoint).add(new Pair<Integer, GenericPoint<Integer>>(timeSec, newDataSum.get(timeSec)));
		}

		int newID = -1;
		if (subsetsFound > 0) {
			newID = nextHistogramMapID;
			nextHistogramMapID++;
			output.append("Did not find appropriate data at ID " + id + " but was able to create it from existing data and put it in id " + newID + "\n");

			if (allHistogramsMapKeyRemap.get(valuePoint) == null) {
				allHistogramsMapKeyRemap.put(valuePoint, new HashMap<GenericPoint<String>, HashMap<Pair<Integer, Integer>, Integer>>());
			}
			if (allHistogramsMapKeyRemap.get(valuePoint).get(categoryPoint) == null) {
				allHistogramsMapKeyRemap.get(valuePoint).put(categoryPoint, new HashMap<Pair<Integer, Integer>, Integer>());
			}
			startAndEnd = getStartAndEndTime(newID);
			allHistogramsMapKeyRemap.get(valuePoint).get(categoryPoint).put(startAndEnd, newID);
		}
		allHistogramsMapLock.unlock();

		return newID;
	}

	/**
	 * Compare the string names in a GenericPoint. This is for the category names only and
	 * not the histogram values
	 *
	 * @param testPoint the point that may be a subset
	 * @param bigPoint the point that may be a superset
	 *
	 * @return true if every coord in testPoint also exists in bigPoint, false otherwise
	 */
	public Boolean isPointSubset(GenericPoint<String> testPoint, GenericPoint<String> bigPoint) {
		if (bigPoint.getDimensions() <  testPoint.getDimensions()) {
			return false;
		}

		int bigPointCoord = 0;
		int testPointCoord = 0;

		while (bigPointCoord < bigPoint.getDimensions() && testPointCoord < testPoint.getDimensions()) {
			if (testPoint.getCoord(testPointCoord).equals(bigPoint.getCoord(bigPointCoord))) {
				bigPointCoord++;
				testPointCoord++;
			}
			else {
				bigPointCoord++;
			}
		}
		if (testPointCoord == testPoint.getDimensions()) {
			return true;
		}

		return false;
	}

	@GET
	@Path("/testSVM")
	@Produces(MediaType.TEXT_HTML)
	public Response getDataSVM(@QueryParam("trainID") Integer trainID,
			@QueryParam("trainCategoryCSV") String trainCategory,
			@QueryParam("trainValue") String trainValue,
			@QueryParam("testID") Integer testID,
			@QueryParam("testCategoryCSV") String testCategory,
			@QueryParam("testValue") String testValue,
			@QueryParam("anomalyTrainID") Integer anomalyTrainID,
			@QueryParam("anomalyTrainCategoryCSV") String anomalyTrainCategory,
			@QueryParam("anomalyTrainValue") String anomalyTrainValue,
			@QueryParam("autoReloadSec") Integer autoReloadSec) {

		StringBuilder output = new StringBuilder("<html>");
		if (autoReloadSec != null && autoReloadSec > 0) {
			output.append("<head><meta http-equiv='refresh' content='" + autoReloadSec + "'></head>");
		}
		output.append("<body><pre>Calculation method: SVM\n");

		GenericPoint<String> trainCategoryPoint = getPointFromCSV(trainCategory);
		GenericPoint<String> trainValuePoint = getPointFromCSV(trainValue);
		GenericPoint<String> testCategoryPoint = getPointFromCSV(testCategory);
		GenericPoint<String> testValuePoint = getPointFromCSV(testValue);

		GenericPoint<String> anomalyTrainCategoryPoint = null;
		GenericPoint<String> anomalyTrainValuePoint = null;
		if (anomalyTrainCategory != null) {
			anomalyTrainCategoryPoint = getPointFromCSV(anomalyTrainCategory);
			anomalyTrainValuePoint = getPointFromCSV(anomalyTrainValue);
		}

		int newTrainID = recalculateByCategory(trainID, trainCategory, trainValue, output);
		if (newTrainID == -1) {
			output.append("ERROR: trainCategoryCSV (" + trainCategory + ") was not found and could not be calculated from existing data\n");
			return Response.status(200).entity(output.toString()).build();
		}
		else if (newTrainID != trainID) {
			output.append("NOTE: trainCategoryCSV (" + trainCategory + ") was not found at id " + trainID + " but found it at ID " + newTrainID + "\n");
			trainID = newTrainID;
		}
		int newTestID = recalculateByCategory(testID, testCategory, testValue, output);
		if (newTestID == -1) {
			output.append("ERROR: testCategoryCSV  (" + testCategory + ") was not found and could not be calculated from existing data\n");
			return Response.status(200).entity(output.toString()).build();
		}
		else if (newTestID != testID) {
			output.append("NOTE: testCategoryCSV  (" + testCategory + ") was not found at id " + testID + " but found it at ID " + newTestID + "\n");
			testID = newTestID;
		}
		if (anomalyTrainID != null) {
			int newAnomalyTrainID = recalculateByCategory(anomalyTrainID, anomalyTrainCategory, anomalyTrainValue, output);
			if (newAnomalyTrainID == -1) {
				output.append("ERROR: anomalyTrainCategoryCSV (" + anomalyTrainCategory + ") was not found and could not be calculated from existing data\n");
				return Response.status(200).entity(output.toString()).build();
			}
			else if (newAnomalyTrainID != anomalyTrainID) {
				output.append("NOTE: anomalyTrainCategoryCSV (" + anomalyTrainCategory + ") was not found at id " + anomalyTrainID + " but found it at ID " + newAnomalyTrainID + "\n");
				anomalyTrainID = newAnomalyTrainID;
			}
		}
		System.out.println("ZZZ " + trainID + " : " + testID + " : " + anomalyTrainID + "\n");
		MultiValueMap resultsHash = new MultiValueMap();
		output.append(SVMCalc.runOneTestSVM(trainID, trainCategoryPoint, trainValuePoint, testID, testCategoryPoint, testValuePoint, anomalyTrainID, anomalyTrainCategoryPoint, anomalyTrainValuePoint, resultsHash));

		List<Double> resultsHashList = new ArrayList<Double>(resultsHash.keySet());
		Collections.sort(resultsHashList); // ascending order
		Collections.reverse(resultsHashList); //descending order
		int ii = 0;
		Double score = resultsHashList.get(0);

		Pair<Integer, Integer> trainTime = getStartAndEndTime(trainID);
		Pair<Integer, Integer> anomalyTrainTime = getStartAndEndTime(anomalyTrainID);
		Pair<Integer, Integer> testTime = getStartAndEndTime(testID);


		for (Pair<Integer, GenericPoint<Integer>> onePoint : ((Collection<Pair<Integer, GenericPoint<Integer>>>)resultsHash.getCollection(score))) {
			Integer timestamp = onePoint.getValue0();
			output.append("\n====== Anomaly Detected Info =====\n"); //right now we just say the highest scoring point is anomaly just to make sure we can print the info
			output.append("Anomaly " + score + " at time " + timestamp + "( " + ((Collection<Integer>)resultsHash.getCollection(score)).size() + " with this score)\n");
			output.append(" * anomaly datapoint: " + onePoint.getValue1() + "\n");
			output.append(" * Training data: " + trainID + "," + trainCategoryPoint.toString() + "," + trainValuePoint.toString() + " time range: " + trainTime.getValue0() + " to " + trainTime.getValue1() + "\n"); 
			output.append(" * Anomaly training data: " + anomalyTrainID + "," + anomalyTrainCategoryPoint.toString() + "," + anomalyTrainValuePoint.toString() + " time range: " + anomalyTrainTime.getValue0() + " to " + anomalyTrainTime.getValue1() + "\n"); 
			output.append(" * Testing data: " + testID + "," + testCategoryPoint.toString() + "," + testValuePoint.toString() + " time range: " + testTime.getValue0() + " to " + testTime.getValue1() + "\n");
			output.append("<a href=http://localhost:8080/AnomalyDetection/rest/getHistograms?id=" + trainID + "&categoryCSV=" + trainCategory + "&valueCSV=" + trainValue + ">link to training dataset</a>\n");
			output.append("<a href=http://localhost:8080/AnomalyDetection/rest/getHistograms?id=" + anomalyTrainID + "&categoryCSV=" + anomalyTrainCategory + "&valueCSV=" + anomalyTrainValue + ">link to anomaly training dataset</a>\n");
			output.append("<a href=http://localhost:8080/AnomalyDetection/rest/getHistograms?id=" + testID + "&categoryCSV=" + testCategory + "&valueCSV=" + testValue + ">link to testing dataset</a>\n");
			break;
		}

		return Response.status(200).entity(output.toString()).build();
	}

	@GET
	@Path("/testSVMRandom")
	@Produces(MediaType.TEXT_HTML)
	public Response getDataSVMRandom(@QueryParam("trainID") Integer trainID,
			@QueryParam("trainCategoryCSV") String trainCategory,
			@QueryParam("trainValue") String trainValue,
			@QueryParam("testID") Integer testID,
			@QueryParam("testCategoryCSV") String testCategory,
			@QueryParam("testValue") String testValue,
			@QueryParam("anomalyTrainID") Integer anomalyTrainID,
			@QueryParam("anomalyTrainCategoryCSV") String anomalyTrainCategory,
			@QueryParam("anomalyTrainValue") String anomalyTrainValue,
			@QueryParam("autoReloadSec") Integer autoReloadSec) {

		StringBuilder output = new StringBuilder("<html>");
		if (autoReloadSec != null && autoReloadSec > 0) {
			output.append("<head><meta http-equiv='refresh' content='" + autoReloadSec + "'></head>");
		}
		output.append("<body><pre>Calculation method: SVM\n");

		GenericPoint<String> trainCategoryPoint = getPointFromCSV(trainCategory);
		GenericPoint<String> trainValuePoint = getPointFromCSV(trainValue);
		GenericPoint<String> testCategoryPoint = getPointFromCSV(testCategory);
		GenericPoint<String> testValuePoint = getPointFromCSV(testValue);

		GenericPoint<String> anomalyTrainCategoryPoint = null;
		GenericPoint<String> anomalyTrainValuePoint = null;
		if (anomalyTrainCategory != null) {
			anomalyTrainCategoryPoint = getPointFromCSV(anomalyTrainCategory);
			anomalyTrainValuePoint = getPointFromCSV(anomalyTrainValue);
		}

		int newTrainID = recalculateByCategory(trainID, trainCategory, trainValue, output);
		if (newTrainID == -1) {
			output.append("ERROR: trainCategoryCSV (" + trainCategory + ") was not found and could not be calculated from existing data\n");
			return Response.status(200).entity(output.toString()).build();
		}
		else if (newTrainID != trainID) {
			output.append("NOTE: trainCategoryCSV (" + trainCategory + ") was not found at id " + trainID + " but found it at ID " + newTrainID + "\n");
			trainID = newTrainID;
		}
		int newTestID = recalculateByCategory(testID, testCategory, testValue, output);
		if (newTestID == -1) {
			output.append("ERROR: testCategoryCSV  (" + testCategory + ") was not found and could not be calculated from existing data\n");
			return Response.status(200).entity(output.toString()).build();
		}
		else if (newTestID != testID) {
			output.append("NOTE: testCategoryCSV  (" + testCategory + ") was not found at id " + testID + " but found it at ID " + newTestID + "\n");
			testID = newTestID;
		}
		if (anomalyTrainID != null) {
			int newAnomalyTrainID = recalculateByCategory(anomalyTrainID, anomalyTrainCategory, anomalyTrainValue, output);
			if (newAnomalyTrainID == -1) {
				output.append("ERROR: anomalyTrainCategoryCSV (" + anomalyTrainCategory + ") was not found and could not be calculated from existing data\n");
				return Response.status(200).entity(output.toString()).build();
			}
			else if (newAnomalyTrainID != anomalyTrainID) {
				output.append("NOTE: anomalyTrainCategoryCSV (" + anomalyTrainCategory + ") was not found at id " + anomalyTrainID + " but found it at ID " + newAnomalyTrainID + "\n");
				anomalyTrainID = newAnomalyTrainID;
			}
		}
		System.out.println("ZZZ " + trainID + " : " + testID + " : " + anomalyTrainID + "\n");
		MultiValueMap resultsHash = new MultiValueMap();
		output.append(SVMRandomCalc.runOneTestSVM(trainID, trainCategoryPoint, trainValuePoint, testID, testCategoryPoint, testValuePoint, anomalyTrainID, anomalyTrainCategoryPoint, anomalyTrainValuePoint, resultsHash, AnomalyDetectionConfiguration.SVM_D));

		List<Double> resultsHashList = new ArrayList<Double>(resultsHash.keySet());
		Collections.sort(resultsHashList); // ascending order
		Collections.reverse(resultsHashList); //descending order
		int ii = 0;
		Double score = resultsHashList.get(0);

		Pair<Integer, Integer> trainTime = getStartAndEndTime(trainID);
		Pair<Integer, Integer> anomalyTrainTime = getStartAndEndTime(anomalyTrainID);
		Pair<Integer, Integer> testTime = getStartAndEndTime(testID);


		for (Pair<Integer, GenericPoint<Integer>> onePoint : ((Collection<Pair<Integer, GenericPoint<Integer>>>)resultsHash.getCollection(score))) {
			Integer timestamp = onePoint.getValue0();
			output.append("\n====== Anomaly Detected Info =====\n"); //right now we just say the highest scoring point is anomaly just to make sure we can print the info
			output.append("Anomaly " + score + " at time " + timestamp + "( " + ((Collection<Integer>)resultsHash.getCollection(score)).size() + " with this score)\n");
			output.append(" * anomaly datapoint: " + onePoint.getValue1() + "\n");
			output.append(" * Training data: " + trainID + "," + trainCategoryPoint.toString() + "," + trainValuePoint.toString() + " time range: " + trainTime.getValue0() + " to " + trainTime.getValue1() + "\n"); 
			output.append(" * Anomaly training data: " + anomalyTrainID + "," + anomalyTrainCategoryPoint.toString() + "," + anomalyTrainValuePoint.toString() + " time range: " + anomalyTrainTime.getValue0() + " to " + anomalyTrainTime.getValue1() + "\n"); 
			output.append(" * Testing data: " + testID + "," + testCategoryPoint.toString() + "," + testValuePoint.toString() + " time range: " + testTime.getValue0() + " to " + testTime.getValue1() + "\n");
			output.append("<a href=http://localhost:8080/AnomalyDetection/rest/getHistograms?id=" + trainID + "&categoryCSV=" + trainCategory + "&valueCSV=" + trainValue + ">link to training dataset</a>\n");
			output.append("<a href=http://localhost:8080/AnomalyDetection/rest/getHistograms?id=" + anomalyTrainID + "&categoryCSV=" + anomalyTrainCategory + "&valueCSV=" + anomalyTrainValue + ">link to anomaly training dataset</a>\n");
			output.append("<a href=http://localhost:8080/AnomalyDetection/rest/getHistograms?id=" + testID + "&categoryCSV=" + testCategory + "&valueCSV=" + testValue + ">link to testing dataset</a>\n");
			break;
		}

		return Response.status(200).entity(output.toString()).build();
	}

	
	/**
	 * This function does not lock the allHistogramsMap. It assumes the caller will use it and therefore
	 * will handle the locking
	 *
	 * I am not sure how useful this function is; functions that need this information seem to do
	 * different things that defy 
	 * @param id 
	 * @param category
	 * @param value
	 *
	 * @return true if there is a histogram for the input values, false if not
	 */
	public boolean verifyHistogram(Integer id, GenericPoint<String> category, GenericPoint<String> value) {
		boolean ret = true;

		if (allHistogramsMapLock.isHeldByCurrentThread() == false) {
			throw new RuntimeException("verifyHistogram called without lock being held");
		}
		if (allHistogramsMap.get(id) == null ||
				allHistogramsMap.get(id).get(value) == null ||
				allHistogramsMap.get(id).get(value).get(category) == null) {
			ret = false;
		}

		return ret;
	}

	/**
	 * Get the times for the first and last histograms for a given ID. All data within one ID has the same time windows
	 *
	 * @param id
	 */
	public Pair<Integer, Integer> getStartAndEndTime(Integer id) {

		allHistogramsMapLock.lock();

		if (allHistogramsMap.get(id) == null) {
			allHistogramsMapLock.unlock();
			return null;
		}

		for (GenericPoint<String> category : allHistogramsMap.get(id).keySet()) {
			for (GenericPoint<String> key : allHistogramsMap.get(id).get(category).keySet()) {
				int size = allHistogramsMap.get(id).get(category).get(key).size();
				if (size == 0) {
					continue;
				}
				Integer firstTime = allHistogramsMap.get(id).get(category).get(key).get(0).getValue0();
				Integer lastTime = allHistogramsMap.get(id).get(category).get(key).get(size - 1).getValue0();
				Pair<Integer, Integer> ret = new Pair(firstTime, lastTime);

				allHistogramsMapLock.unlock();
				return ret;
			}
		}
		allHistogramsMapLock.unlock();
		return null;
	}

	@GET
	@Path("/testall/{categoryCSV}")
	@Produces(MediaType.TEXT_PLAIN)
	public Response getDataAll(@PathParam("categoryCSV") String category) {
		if (AnomalyDetectionConfiguration.CALC_TYPE_TO_USE == AnomalyDetectionConfiguration.CALC_TYPE_KDTREE) {
			return getDataAllKDTree(category);
		}
		else if (AnomalyDetectionConfiguration.CALC_TYPE_TO_USE == AnomalyDetectionConfiguration.CALC_TYPE_SVM) {
			return getDataAllSVM(category);
		}
		else {
			throw new RuntimeException("unknown calculation type");
		}
	}

	@GET
	@Path("/testallKDTree/{categoryCSV}")
	@Produces(MediaType.TEXT_PLAIN)
	public Response getDataAllKDTree(@PathParam("categoryCSV") String category) {
		StringBuilder output = new StringBuilder("Calculation method: KDTree\n");
		output.append(KDTreeCalc.runAllTestKDTree(getPointFromCSV(category)));
		return Response.status(200).entity(output.toString()).build();
	}

	@GET
	@Path("/testallSVM/{categoryCSV}")
	@Produces(MediaType.TEXT_PLAIN)
	public Response getDataAllSVM(@PathParam("categoryCSV") String category) {
		StringBuilder output = new StringBuilder("Calculation method: SVM\n");
		output.append(SVMCalc.runAllTestSVM(getPointFromCSV(category)));
		return Response.status(200).entity(output.toString()).build();
	}
	
	@GET
	@Path("/testallSVMRandom/{categoryCSV}")
	@Produces(MediaType.TEXT_PLAIN)
	public Response getDataAllSVMRandom(@PathParam("categoryCSV") String category) {
		StringBuilder output = new StringBuilder("Calculation method: SVM\n");
		output.append(SVMCalc.runAllTestSVM(getPointFromCSV(category)));
		return Response.status(200).entity(output.toString()).build();
	}

	// XYZ this should nullify the caches
	@GET
	@Path("/setSampleWindowSecs/{newVal}")
	@Produces(MediaType.TEXT_PLAIN)
	public Response setSampleWindowSecs(@PathParam("newVal") int newValue) {

		allHistogramsMapLock.lock();

		AnomalyDetectionConfiguration.SAMPLE_WINDOW_SECS = newValue;

		allHistogramsMapLock.unlock();

		return Response.status(200).entity("ok").build();
	}

	@GET
	@Path("/getSampleWindowSecs")
	@Produces(MediaType.TEXT_PLAIN)
	public Response getSampleWindowSecs() {

		allHistogramsMapLock.lock();

		String output = "" + AnomalyDetectionConfiguration.SAMPLE_WINDOW_SECS;

		allHistogramsMapLock.unlock();

		return Response.status(200).entity(output).build();
	}

	// XYZ this should nullify the caches
	@GET
	@Path("/setSlideWindowSecs/{newVal}")
	@Produces(MediaType.TEXT_PLAIN)
	public Response setSlideWindowSecs(@PathParam("newVal") int newValue) {

		allHistogramsMapLock.lock();

		AnomalyDetectionConfiguration.SLIDE_WINDOW_SECS = newValue;

		allHistogramsMapLock.unlock();

		return Response.status(200).entity("ok").build();
	}

	@GET
	@Path("/getSlideWindowSecs")
	@Produces(MediaType.TEXT_PLAIN)
	public Response getSlideWindowSecs() {

		allHistogramsMapLock.lock();

		String output = "" + AnomalyDetectionConfiguration.SLIDE_WINDOW_SECS;

		allHistogramsMapLock.unlock();

		return Response.status(200).entity(output).build();
	}

	@GET
	@Path("/setCalcType/{newVal}")
	@Produces(MediaType.TEXT_HTML)
	public Response setCalcType(@PathParam("newVal") String newValue) {

		allHistogramsMapLock.lock();

		String output = "Ok. Calc type set to " + newValue;
		int newIndex = Arrays.asList(AnomalyDetectionConfiguration.CALC_TYPE_NAMES).indexOf(newValue);
		if (newIndex >= 0) {
			AnomalyDetectionConfiguration.CALC_TYPE_TO_USE = newIndex;
		}
		else {
			output = "Invalid type<br>\n";
		}
		output += AnomalyDetectionConfiguration.printCalcTypeNameLinksHTML("");

		allHistogramsMapLock.unlock();

		return Response.status(200).entity(output).build();
	}

	@GET
	@Path("/getCalcType")
	@Produces(MediaType.TEXT_HTML)
	public Response getCalcType() {

		allHistogramsMapLock.lock();

		String output = "Calc type is " + AnomalyDetectionConfiguration.CALC_TYPE_NAMES[AnomalyDetectionConfiguration.CALC_TYPE_TO_USE];
		output += "<br>" + AnomalyDetectionConfiguration.printCalcTypeNameLinksHTML("setCalcType/");

		allHistogramsMapLock.unlock();

		return Response.status(200).entity(output).build();
	}
	
	
	// CUSTOM TESTS
	//_______________________________________________________________________//
	
	@GET
	@Path("/customTest")
	@Produces(MediaType.TEXT_PLAIN)
	public Response customTest(@QueryParam("n") Integer n, @QueryParam("size") Integer s, @QueryParam("rn") Integer rn){//@QueryParam("id") Integer id,
		//@QueryParam("csvKey") String csvKey) {

		StringBuilder output = new StringBuilder("Custom Test\n\n");
		
		output.append(getFakeData2(n,s,rn).getEntity());
		getFakeData2(n,s,rn);
//		String filename="/usr0/home/sibiv/Research/Data/GRE.out";
//		DataIOFile foo = new DataIOFile(filename);
//		allHistogramsMap.put(nextHistogramMapID, HistoTuple.mergeWindows(foo.getData(), AnomalyDetectionConfiguration.SAMPLE_WINDOW_SECS, AnomalyDetectionConfiguration.SLIDE_WINDOW_SECS));
//		System.out.println("Opened file.");

		int id = nextHistogramMapID-1;
		String csvKeyA = "a";
		String csvValueA = "b";
		String csvKey1 = "a1";
		String csvValue1 = "b1";
		String csvKey2 = "a2";
		String csvValue2 = "b2";
		
		GenericPoint<String> trainKeyA = getPointFromCSV(csvKeyA);
		GenericPoint<String> trainValueA = getPointFromCSV(csvValueA);
		GenericPoint<String> trainKey = getPointFromCSV(csvKey1);
		GenericPoint<String> trainValue = getPointFromCSV(csvValue1);
		GenericPoint<String> testKey = getPointFromCSV(csvKey2);
		GenericPoint<String> testValue = getPointFromCSV(csvValue2);

		output.append("\n\nSVM RANDOM:\n");
		long startTime1 = System.nanoTime();
		output.append(SVMRandomCalc.runOneTestSVM(id, trainKey, trainValue, id, testKey, testValue, id-1, trainKeyA, trainValueA, null, -1).toString());
		long endTime1 = System.nanoTime();
		double duration = (double)(endTime1 - startTime1)/1000000000;
		output.append("\n\nTime taken:\n" + (duration));
		output.append("\n\nSVM:\n");
		long startTime2 = System.nanoTime();
		output.append(SVMCalc.runOneTestSVM(id, trainKey, trainValue, id, testKey, testValue, id-1, trainKeyA, trainValueA, null).toString());
		long endTime2 = System.nanoTime();
		duration = (double)(endTime2 - startTime2)/1000000000;
		output.append("\n\nTime taken:\n" + (duration));
		
		return Response.status(200).entity(output.toString()).build();
	}

	
	@GET
	@Path("/getfakedata2")
	@Produces(MediaType.TEXT_PLAIN)
	public Response getFakeData2(@QueryParam("n") Integer n, @QueryParam("size") Integer s, @QueryParam("rn") Integer rn) {
		
		//allHistogramsMap.clear();
		
		StringBuilder output = new StringBuilder("Dataset ID: " + nextHistogramMapID + "\n");
	
		UniformRealDistribution urd = new UniformRealDistribution (0.0, 1.0);

		
		output.append("Anomaly data:\n\n");
		int hs = (int) s/2; // half size
		int qs = (int) s/2; // quarter size
		
		HashMap<GenericPoint<String> ,HashMap<GenericPoint<String>, ArrayList<Pair<Integer, GenericPoint<Integer>>>>> anomalyData = new HashMap();
		ArrayList<Pair<Integer, GenericPoint<Integer>>> anomalyTraining = new ArrayList<Pair<Integer, GenericPoint<Integer>>>();
		int fakeTime = 1;
		for (int i = 0; i < n; i += 1) {
			GenericPoint<Integer> fakePoint = new GenericPoint<Integer>(s);
			for (int j = 0; j < s; j += 1) { // Randomly fill first quarter with 0s and 1s (anomalies)
				if (j < qs && urd.sample() > 0.5)
					fakePoint.setCoord(j, 1);
				else
					fakePoint.setCoord(j, 0);
				
			}
			output.append(fakePoint.toString() + "\n");
			Pair<Integer, GenericPoint<Integer>> fakePair = new Pair<Integer, GenericPoint<Integer>>(fakeTime, fakePoint);
			anomalyTraining.add(fakePair);
			fakeTime++;
		}
		GenericPoint<String> aKey = new GenericPoint<String>(1);
		aKey.setCoord(0, "a");
		GenericPoint<String> aValue = new GenericPoint<String>(1);
		aValue.setCoord(0, "b");
		anomalyData.put(aValue, new HashMap<GenericPoint<String>, ArrayList<Pair<Integer, GenericPoint<Integer>>>>());
		anomalyData.get(aValue).put(aKey, anomalyTraining);
		output.append("Key: a, Value: b (" + anomalyTraining.size() + ")\n\n");
		
		allHistogramsMap.put(nextHistogramMapID, anomalyData);
		nextHistogramMapID++;
		
		
		output.append("Train data:\n\n");
		HashMap<GenericPoint<String> ,HashMap<GenericPoint<String>, ArrayList<Pair<Integer, GenericPoint<Integer>>>>> fakeData = new HashMap();
		ArrayList<Pair<Integer, GenericPoint<Integer>>> training = new ArrayList<Pair<Integer, GenericPoint<Integer>>>();
		
		fakeTime = 1;
		for (int i = 0; i < n; i += 1) {
			GenericPoint<Integer> fakePoint = new GenericPoint<Integer>(s);
			for (int j = 0; j < s; j += 1) { // Randomly fill second half with 1s and 0s
				if (j < hs)
					fakePoint.setCoord(j, 0);
				else if (urd.sample() > 0.5)
					fakePoint.setCoord(j, 1);
				else
					fakePoint.setCoord(j, 0);
				
			}
			output.append(fakePoint.toString() + "\n");
			Pair<Integer, GenericPoint<Integer>> fakePair = new Pair<Integer, GenericPoint<Integer>>(fakeTime, fakePoint);
			training.add(fakePair);
			fakeTime++;
		}
		GenericPoint<String> key = new GenericPoint<String>(1);
		key.setCoord(0, "a1");
		GenericPoint<String> value = new GenericPoint<String>(1);
		value.setCoord(0, "b1");
		fakeData.put(value, new HashMap<GenericPoint<String>, ArrayList<Pair<Integer, GenericPoint<Integer>>>>());
		fakeData.get(value).put(key, training);
		output.append("Key: a1, Value: b1 (" + training.size() + ")\n\n");

		output.append("Test data:\n\n");
		// generate a dense full matrix. This will be test data used to run against the lower half matrix
		int normal_n = (int) 3*n/4;
		fakeTime = 1;
		ArrayList<Pair<Integer, GenericPoint<Integer>>> testing = new ArrayList<Pair<Integer, GenericPoint<Integer>>>();
		for (int i = 0; i < n; i += 1) {
			GenericPoint<Integer> fakePoint = new GenericPoint<Integer>(s);
			if (i < normal_n) {
				for (int j = 0; j < s; j += 1) { // Randomly fill second half with 1s and 0s
					if (j < hs || urd.sample() < 0.5)
						fakePoint.setCoord(j, 0);
					else
						fakePoint.setCoord(j, 1);
				}
			} else {
				for (int j = 0; j < s; j += 1) { // Randomly fill first quarter with 0s and 1s (anomalies)
					if (j < qs && urd.sample() > 0.5)
						fakePoint.setCoord(j, 1);
					else
						fakePoint.setCoord(j, 0);
					
				}
			}
			output.append(fakePoint.toString() + "\n");
			Pair<Integer, GenericPoint<Integer>> fakePair = new Pair<Integer, GenericPoint<Integer>>(fakeTime, fakePoint);
			testing.add(fakePair);
			fakeTime++;
		}
		GenericPoint<String> key2 = new GenericPoint<String>(1);
		key2.setCoord(0, "a2");
		GenericPoint<String> value2 = new GenericPoint<String>(1);
		value2.setCoord(0, "b2");
		fakeData.put(value2, new HashMap<GenericPoint<String>, ArrayList<Pair<Integer, GenericPoint<Integer>>>>());
		fakeData.get(value2).put(key2, testing);
		output.append("Key: a2, b2 (" + testing.size() + ")\n");
		
		// generate some fake HistoTuples. these are unused but the code would crash without them
		HistoTuple foo = new HistoTuple(1, "fake1", value);
		HistoTuple foo2 = new HistoTuple(2, "fake2", value);
		
		
		allHistogramsMap.put(nextHistogramMapID, fakeData);
		nextHistogramMapID++;
		
		//output.append(allHistogramsMap.get(0).get(key2).get(0).toString());
		return Response.status(200).entity(output.toString()).build();
	}
	
	public double norm(double[] v) {
		double n = 0.0;
		for (int i = 0; i < v.length; i++)
			n += v[i]*v[i];
		return Math.sqrt(n);
	}

}