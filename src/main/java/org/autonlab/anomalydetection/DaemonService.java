package org.autonlab.anomalydetection;

import com.datastax.driver.core.*;
import com.savarese.spatial.*;
import java.util.*; 
import java.util.concurrent.locks.*;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import org.apache.commons.collections.map.*;
import org.javatuples.*;

@Path("/")
public class DaemonService {
    //                        ID                value                          category              scaling                 time       histogram S
    static volatile HashMap<Integer, HashMap<GenericPoint<String>, HashMap<GenericPoint<String>, Pair<Double, ArrayList<Pair<Integer, GenericPoint<Integer>>>>>>> allHistogramsMap = new HashMap();
    static volatile HashMap<GenericPoint<String>, HashMap<GenericPoint<String>, HashMap<Pair<Integer, Integer>, Integer>>> allHistogramsMapKeyRemap = new HashMap();

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

	allHistogramsMapLock.lock();

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

	allHistogramsMapLock.unlock();

	return Response.status(200).entity(output).build();
    }

    @GET
    @Path("/getHistograms")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getHistograms(@QueryParam("id") Integer id,
				  @QueryParam("categoryCSV") String category,
				  @QueryParam("valueCSV") String value) {

	GenericPoint<String> categoryPoint = getPointFromCSV(category);
	GenericPoint<String> valuePoint = getPointFromCSV(value);

	allHistogramsMapLock.lock();

	StringBuilder output = new StringBuilder();
	int newID = recalculateByCategory(id, categoryPoint, valuePoint, output);
	if (newID != id) {
	    output.append("Didn't find the data at id " + id + " but found it at id " + newID);
	    id = newID;
	}

	ArrayList<Pair<Integer, GenericPoint<Integer>>> histograms = allHistogramsMap.get(id).get(valuePoint).get(categoryPoint).getValue1();
	output.append("Number of datapoints: " + histograms.size() + " \n");
	for (Pair<Integer, GenericPoint<Integer>> tempPair : histograms) {
	    output.append(tempPair.getValue0() + " : " + tempPair.getValue1().toString() + "\n");
	}

	allHistogramsMapLock.unlock();

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
		output.append(" (datapoints: " + allHistogramsMap.get(nextHistogramMapID).get(valueName).get(category).getValue1().size() + ")\n");
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
			  @QueryParam("ageMins") Integer ageMins,
			  @QueryParam("keySpace") String keySpace) {

	allHistogramsMapLock.lock();

	StringBuilder output = new StringBuilder("Dataset ID: " + nextHistogramMapID + "\n");
	DataIOCassandraDB dbHandle = new DataIOCassandraDB(hostname, keySpace);
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
		output.append(" (datapoints: " + allHistogramsMap.get(nextHistogramMapID).get(valueName).get(category).getValue1().size() + ")\n");
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

	HashMap<GenericPoint<String>, Pair<Double, ArrayList<Pair<Integer, GenericPoint<Integer>>>>> fakeData = new HashMap();

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
	fakeData.put(category, new Pair<Double, ArrayList<Pair<Integer, GenericPoint<Integer>>>>(0.0, lowerHalf));
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
	fakeData.put(category2, new Pair<Double, ArrayList<Pair<Integer, GenericPoint<Integer>>>>(0.0, fullMatrix));
	output.append("Category: 5.5.5.5, otherthing (" + fullMatrix.size() + ")\n");

	// generate some fake HistoTuples. these are unused but the code would crash without them
	HistoTuple foo = new HistoTuple(1, "fake1", valueType);
	HistoTuple foo2 = new HistoTuple(2, "fake2", valueType);

	HashMap<GenericPoint<String>, HashMap<GenericPoint<String>, Pair<Double, ArrayList<Pair<Integer, GenericPoint<Integer>>>>>> fakeDataFinal = new HashMap();
	fakeDataFinal.put(valueType, fakeData);
	allHistogramsMap.put(nextHistogramMapID, fakeDataFinal);
	nextHistogramMapID++;

	allHistogramsMapLock.unlock();

	return Response.status(200).entity(output.toString()).build();
    }

    /**
     * Delete the mapping for one ID -> newMap
     *
     * @return "ok" on success, something else on error
     */
    @GET
    @Path("/deleteone/{id}")
    @Produces(MediaType.TEXT_PLAIN)
    public Response deleteOne(@PathParam("id") Integer id) {

	allHistogramsMapLock.lock();

	String output = "ok";
	if (!allHistogramsMap.containsKey(id)) {
	    output = "No data matching id " + id;
	}
	else {
	    allHistogramsMap.remove(id);
	}
	SVMCalc.removeModelFromCache(id);

	allHistogramsMapLock.unlock();

	return Response.status(200).entity(output).build();
    }	    

    /**
     * Delete all mappingings for ID -> newMap
     *
     * @return "ok" and the number if elements removed
     */
    @GET
    @Path("/deleteall/{id}")
    @Produces(MediaType.TEXT_PLAIN)
    public Response deleteAll() {
	String output = "ok";

	allHistogramsMapLock.lock();
	allHistogramsMap = new HashMap();
	nextHistogramMapID = 0;
	allHistogramsMapLock.unlock();

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

	int calcTypeToUse;

	allHistogramsMapLock.lock();
	calcTypeToUse = AnomalyDetectionConfiguration.CALC_TYPE_TO_USE;
	allHistogramsMapLock.unlock();

	if (calcTypeToUse == AnomalyDetectionConfiguration.CALC_TYPE_KDTREE) {
	    return getDataKDTree(trainID, trainCategory, trainValue, testID, testCategory, testValue);
	}
	else if (calcTypeToUse == AnomalyDetectionConfiguration.CALC_TYPE_SVM) {
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

	GenericPoint<String> trainCategoryPoint = getPointFromCSV(trainCategory);
	GenericPoint<String> trainValuePoint =  getPointFromCSV(trainValue);
	GenericPoint<String> testCategoryPoint = getPointFromCSV(testCategory);
	GenericPoint<String> testValuePoint = getPointFromCSV(testValue);

	allHistogramsMapLock.lock();

	output += KDTreeCalc.runOneTestKDTree(trainID, trainCategoryPoint, trainValuePoint, testID, testCategoryPoint, testValuePoint, null);

	allHistogramsMapLock.unlock();

	return Response.status(200).entity(output).build();
    }

    /**
     * Simply convert a CSV of strings into a GenericPoint where each dimension was one of the CSV strings
     *
     * Relative to the rest of the code, this is not an expensive operation but when possible we call
     * this outside of the allHistogramsMapLock
     *
     * @param csv
     * @return GenericPoint
     */
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
     * @param categoryPoint category
     * @param valuePoint histogram values
     * @param output function will add text to this if it makes a new mapping
     *
     * @return the ID that contains the data with indexes valueCSV and categoryCSV (which may be different from the input id) or -1 if none
     */
    public int recalculateByCategory(Integer id, GenericPoint<String> categoryPoint, GenericPoint<String> valuePoint, StringBuilder output) {

	if (allHistogramsMapLock.isHeldByCurrentThread() == false) {
	    throw new RuntimeException("Error: did not hold lock in recalculateByCategory");
	}

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

	Pair<Integer, Integer> startAndEnd = getStartAndEndTime(id);
	if (allHistogramsMapKeyRemap.get(valuePoint) != null &&
	    allHistogramsMapKeyRemap.get(valuePoint).get(categoryPoint) != null &&
	    startAndEnd != null &&
	    allHistogramsMapKeyRemap.get(valuePoint).get(categoryPoint).get(startAndEnd) != null) {
	    return allHistogramsMapKeyRemap.get(valuePoint).get(categoryPoint).get(startAndEnd);
	}

	allHistogramsMap.put(nextHistogramMapID, new HashMap<GenericPoint<String>, HashMap<GenericPoint<String>, Pair<Double, ArrayList<Pair<Integer, GenericPoint<Integer>>>>>>());
	allHistogramsMap.get(nextHistogramMapID).put(valuePoint, new HashMap<GenericPoint<String>, Pair<Double, ArrayList<Pair<Integer, GenericPoint<Integer>>>>>());
	allHistogramsMap.get(nextHistogramMapID).get(valuePoint).put(categoryPoint, new Pair<Double, ArrayList<Pair<Integer, GenericPoint<Integer>>>>(0.0, new ArrayList()));

	int subsetsFound = 0;
	// the histograms are in an arraylist so it's hard to add them together. Sum them in a hashmap then convert it to an arraymap
	TreeMap<Integer, GenericPoint<Integer>> newDataSum = new TreeMap(); //use TreeMap not HashMap so we get keys back in sorted orders
	for (GenericPoint<String> oneKey : allHistogramsMap.get(id).get(valuePoint).keySet()) {
	    if (isPointSubset(categoryPoint, oneKey)) {
		System.out.println("counting " + categoryPoint.toString() + " as subset as " + oneKey.toString() + "\n");
		subsetsFound++;
		for (Pair<Integer, GenericPoint<Integer>> oneHist : allHistogramsMap.get(id).get(valuePoint).get(oneKey).getValue1()) {

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
	    allHistogramsMap.get(nextHistogramMapID).get(valuePoint).get(categoryPoint).getValue1().add(new Pair<Integer, GenericPoint<Integer>>(timeSec, newDataSum.get(timeSec)));
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
	if (allHistogramsMapLock.isHeldByCurrentThread() == false) {
	    throw new RuntimeException("Error: did not hold lock in isPointSubset");
	}

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
    @Path("/demo")
    @Produces(MediaType.TEXT_HTML)
    public Response demo(@QueryParam("hostname") String hostname,
			  @QueryParam("categoryCSV") String categoryCSV,
			  @QueryParam("valueCSV") String valueCSV,
			  @QueryParam("ageMins") Integer ageMins,
           		  @QueryParam("refreshSec") Integer refreshSec,
			  @QueryParam("keySpace") String keySpace) {

	allHistogramsMapLock.lock();
	deleteAll(); //invalidate the existing data
	allHistogramsMapLock.unlock();

	//this will go into ID 0
	getDb(hostname, categoryCSV, valueCSV, null, keySpace);

	//this will go into ID 1
	getDb(hostname, categoryCSV, valueCSV, ageMins, keySpace);

	for (GenericPoint<String> oneValuePoint: allHistogramsMap.get(0).keySet()) {
	    for (GenericPoint<String> oneCategoryPoint : allHistogramsMap.get(0).get(oneValuePoint).keySet()) {
		System.out.println(oneCategoryPoint.getCoord(0));
		return getDataSVM(0, oneCategoryPoint.getCoord(0), valueCSV, 1, oneCategoryPoint.getCoord(0), valueCSV, null, null, null, refreshSec);
	    }
	}
	return null;
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

	allHistogramsMapLock.lock();

	int newTrainID = recalculateByCategory(trainID, trainCategoryPoint, trainValuePoint, output);
	if (newTrainID == -1) {
	    output.append("ERROR: trainCategoryCSV (" + trainCategory + ") was not found and could not be calculated from existing data\n");
	    allHistogramsMapLock.unlock();
	    return Response.status(200).entity(output.toString()).build();
	}
	else if (newTrainID != trainID) {
	    output.append("NOTE: trainCategoryCSV (" + trainCategory + ") was not found at id " + trainID + " but found it at ID " + newTrainID + "\n");
	    trainID = newTrainID;
	}
	int newTestID = recalculateByCategory(testID, testCategoryPoint, testValuePoint, output);
	if (newTestID == -1) {
	    output.append("ERROR: testCategoryCSV  (" + testCategory + ") was not found and could not be calculated from existing data\n");
	    allHistogramsMapLock.unlock();
	    return Response.status(200).entity(output.toString()).build();
	}
	else if (newTestID != testID) {
	    output.append("NOTE: testCategoryCSV  (" + testCategory + ") was not found at id " + testID + " but found it at ID " + newTestID + "\n");
	    testID = newTestID;
	}
	if (anomalyTrainID != null) {
	    int newAnomalyTrainID = recalculateByCategory(anomalyTrainID, anomalyTrainCategoryPoint, anomalyTrainValuePoint, output);
	    if (newAnomalyTrainID == -1) {
		output.append("ERROR: anomalyTrainCategoryCSV (" + anomalyTrainCategory + ") was not found and could not be calculated from existing data\n");
		allHistogramsMapLock.unlock();
		return Response.status(200).entity(output.toString()).build();
	    }
	    else if (newAnomalyTrainID != anomalyTrainID) {
		output.append("NOTE: anomalyTrainCategoryCSV (" + anomalyTrainCategory + ") was not found at id " + anomalyTrainID + " but found it at ID " + newAnomalyTrainID + "\n");
		anomalyTrainID = newAnomalyTrainID;
	    }
	}

	MultiValueMap resultsHash = new MultiValueMap();
	output.append(SVMCalc.runOneTestSVM(trainID, trainCategoryPoint, trainValuePoint, testID, testCategoryPoint, testValuePoint, anomalyTrainID, anomalyTrainCategoryPoint, anomalyTrainValuePoint, resultsHash));

	List<Double> resultsHashList = new ArrayList<Double>(resultsHash.keySet());
	Collections.sort(resultsHashList); // ascending order
	Collections.reverse(resultsHashList); //descending order
	int ii = 0;
	Double score = resultsHashList.get(0);

	Pair<Integer, Integer> trainTime = getStartAndEndTime(trainID);
	Pair<Integer, Integer> anomalyTrainTime = null;
	if (anomalyTrainID != null) {
	    anomalyTrainTime = getStartAndEndTime(anomalyTrainID);
	}
	Pair<Integer, Integer> testTime = getStartAndEndTime(testID);


	for (Pair<Integer, GenericPoint<Integer>> onePoint : ((Collection<Pair<Integer, GenericPoint<Integer>>>)resultsHash.getCollection(score))) {
	    Integer timestamp = onePoint.getValue0();
	    output.append("\n====== Anomaly Detected Info =====\n"); //right now we just say the highest scoring point is anomaly just to make sure we can print the info
	    output.append("Anomaly " + score + " at time " + timestamp + "( " + ((Collection<Integer>)resultsHash.getCollection(score)).size() + " with this score)\n");
	    output.append(" * anomaly datapoint: " + onePoint.getValue1() + "\n");
	    output.append(" * Training data: " + trainID + "," + trainCategoryPoint.toString() + "," + trainValuePoint.toString() + " time range: " + trainTime.getValue0() + " to " + trainTime.getValue1() + "\n"); 
	    if (anomalyTrainID != null) {
		output.append(" * Anomaly training data: " + anomalyTrainID + "," + anomalyTrainCategoryPoint.toString() + "," + anomalyTrainValuePoint.toString() + " time range: " + anomalyTrainTime.getValue0() + " to " + anomalyTrainTime.getValue1() + "\n");
	    }
	    output.append(" * Testing data: " + testID + "," + testCategoryPoint.toString() + "," + testValuePoint.toString() + " time range: " + testTime.getValue0() + " to " + testTime.getValue1() + "\n");
	    output.append("<a href=http://localhost:8080/AnomalyDetection/rest/getHistograms?id=" + trainID + "&categoryCSV=" + trainCategory + "&valueCSV=" + trainValue + ">link to training dataset</a>\n");
	    if (anomalyTrainID != null) {
		output.append("<a href=http://localhost:8080/AnomalyDetection/rest/getHistograms?id=" + anomalyTrainID + "&categoryCSV=" + anomalyTrainCategory + "&valueCSV=" + anomalyTrainValue + ">link to anomaly training dataset</a>\n");
	    }
	    output.append("<a href=http://localhost:8080/AnomalyDetection/rest/getHistograms?id=" + testID + "&categoryCSV=" + testCategory + "&valueCSV=" + testValue + ">link to testing dataset</a>\n");
	    break;
	}

	allHistogramsMapLock.unlock();

	return Response.status(200).entity(output.toString()).build();
    }

    /**
     * This function does not lock the allHistogramsMap. It assumes the caller will use it and therefore
     * will handle the locking
     *
     * I am not sure how useful this function is; functions that need this information seem to do
     * different things that defy lumping them all into a do-it-all function
     *
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
		int size = allHistogramsMap.get(id).get(category).get(key).getValue1().size();
		if (size == 0) {
		    continue;
		}
		Integer firstTime = allHistogramsMap.get(id).get(category).get(key).getValue1().get(0).getValue0();
		Integer lastTime = allHistogramsMap.get(id).get(category).get(key).getValue1().get(size - 1).getValue0();
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
	int calcTypeToUse;

	allHistogramsMapLock.lock();
	calcTypeToUse = AnomalyDetectionConfiguration.CALC_TYPE_TO_USE;
	allHistogramsMapLock.unlock();

	if (calcTypeToUse == AnomalyDetectionConfiguration.CALC_TYPE_KDTREE) {
	    return getDataAllKDTree(category);
	}
	else if (calcTypeToUse == AnomalyDetectionConfiguration.CALC_TYPE_SVM) {
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
	GenericPoint<String> categoryPoint = getPointFromCSV(category);

	allHistogramsMapLock.lock();
	
	output.append(KDTreeCalc.runAllTestKDTree(categoryPoint));

	allHistogramsMapLock.unlock();

	return Response.status(200).entity(output.toString()).build();
    }

    @GET
    @Path("/testallSVM/{categoryCSV}")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getDataAllSVM(@PathParam("categoryCSV") String category) {
	StringBuilder output = new StringBuilder("Calculation method: SVM\n");
	GenericPoint<String> categoryPoint = getPointFromCSV(category);

	allHistogramsMapLock.lock();

	output.append(SVMCalc.runAllTestSVM(categoryPoint));

	allHistogramsMapLock.unlock();

	return Response.status(200).entity(output.toString()).build();
    }

    /**
     * Set a new value for the size of the window to use when calculating histograms
     * WARNING: if the value is different from the current value, this function will
     *  delete all existing data from the system
     *
     * @param new value in seconds
     */
    @GET
    @Path("/setSampleWindowSecs/{newVal}")
    @Produces(MediaType.TEXT_PLAIN)
    public Response setSampleWindowSecs(@PathParam("newVal") int newValue) {

	allHistogramsMapLock.lock();

	if (newValue != AnomalyDetectionConfiguration.SAMPLE_WINDOW_SECS) {
	    deleteAll(); //invalidate the existing data
	}
	AnomalyDetectionConfiguration.SAMPLE_WINDOW_SECS = newValue;

	allHistogramsMapLock.unlock();

	return Response.status(200).entity("Changes enacted. All data deleted.").build();
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

    /**
     * Set a new value for how far to slide the window when calculating histograms
     * WARNING: if the value is different from the current value, this function will
     *  delete all existing data from the system
     *
     * @param new value in seconds
     */
    @GET
    @Path("/setSlideWindowSecs/{newVal}")
    @Produces(MediaType.TEXT_PLAIN)
    public Response setSlideWindowSecs(@PathParam("newVal") int newValue) {

	allHistogramsMapLock.lock();

	if (newValue != AnomalyDetectionConfiguration.SAMPLE_WINDOW_SECS) {
	    deleteAll(); //invalidate the existing data
	}
	AnomalyDetectionConfiguration.SLIDE_WINDOW_SECS = newValue;

	allHistogramsMapLock.unlock();

	return Response.status(200).entity("Change enacted. All data deleted.").build();
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

    
    @GET
    @Path("/setNumThreads/{newVal}")
    @Produces(MediaType.TEXT_HTML)
    public Response setNumThreads(@PathParam("newVal") Integer newValue) {
	String output = "Number of threads was " + AnomalyDetectionConfiguration.NUM_THREADS + " and is now " + newValue;

	if (AnomalyDetectionConfiguration.NUM_THREADS == newValue) {
	    output = "Number of threads is already " + newValue + ". No change";
	}
	else {
	    // grab the lock in the event that an SVM calculation is in progress
	    allHistogramsMapLock.lock();

	    AnomalyDetectionConfiguration.NUM_THREADS = newValue;

	    allHistogramsMapLock.unlock();
	}

	return Response.status(200).entity(output).build();
    }

}