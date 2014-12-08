package org.autonlab.anomalydetection;

import com.datastax.driver.core.*;
import com.savarese.spatial.*;
import java.util.*; 
import java.util.concurrent.locks.*;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import org.javatuples.*;

@Path("/")
public class DaemonService {
    static volatile HashMap<Integer, HashMap<GenericPoint<String>, HashMap<GenericPoint<String>, ArrayList<Pair<Integer, GenericPoint<Integer>>>>>> allHistogramsMap = new HashMap();
    static volatile HashMap<GenericPoint<String>, HashMap<GenericPoint<String>, Integer>> allHistogramsMapKeyRemap = new HashMap();
    // XYZ the lock should also protect reads/deletes to allHistogramsMap!

    static volatile int nextHistogramMapID = 0;
    static volatile Lock allHistogramsMapLock = new ReentrantLock();

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
	return Response.status(200).entity(output).build();
    }

    @GET
    @Path("/getHistograms")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getHistograms(@QueryParam("id") Integer id,
				  @QueryParam("keyCSV") String key,
				  @QueryParam("valueCSV") String value) {
    
	String output = new String();

	ArrayList<Pair<Integer, GenericPoint<Integer>>> histograms = allHistogramsMap.get(id).get(getPointFromCSV(value)).get(getPointFromCSV(key));
	output += "Number of datapoints: " + histograms.size() + " \n";
	for (Pair<Integer, GenericPoint<Integer>> tempPair : histograms) {
	    output += tempPair.getValue0() + " : " + tempPair.getValue1().toString() + "\n";
	}
	return Response.status(200).entity(output).build();
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
	    for (GenericPoint<String> key : allHistogramsMap.get(nextHistogramMapID).get(valueName).keySet()) {
		output.append("Key: " + key.toString() + ", Value: " + valueName);
		output.append(" (datapoints: " + allHistogramsMap.get(nextHistogramMapID).get(valueName).get(key).size() + ")\n");
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
			  @QueryParam("keyCSV") String keyCSV,
			  @QueryParam("value") String valueCSV,
			  @QueryParam("ageMins") Integer ageMins) {
	allHistogramsMapLock.lock();

	StringBuilder output = new StringBuilder("Dataset ID: " + nextHistogramMapID + "\n");
	    
	DataIOCassandraDB dbHandle = new DataIOCassandraDB(hostname, "demo2");

	if (keyCSV != null) {
	    dbHandle.setKeyFields(keyCSV);
	}
	if (valueCSV != null) {
	    dbHandle.setValueFields(valueCSV);
	}
	if (ageMins == null) {
	    ageMins = 0;
	}

	allHistogramsMap.put(nextHistogramMapID, HistoTuple.mergeWindows(dbHandle.getData(ageMins), AnomalyDetectionConfiguration.SAMPLE_WINDOW_SECS, AnomalyDetectionConfiguration.SLIDE_WINDOW_SECS));
	for (GenericPoint<String> valueName : allHistogramsMap.get(nextHistogramMapID).keySet()) {
	    for (GenericPoint<String> key : allHistogramsMap.get(nextHistogramMapID).get(valueName).keySet()) {
		output.append("Key: " + key.toString() + ", Value: " + valueName);
		output.append(" (datapoints: " + allHistogramsMap.get(nextHistogramMapID).get(valueName).get(key).size() + ")\n");
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
			    @QueryParam("trainKeyCSV") String trainKey,
			    @QueryParam("trainValue") String trainValue,
			    @QueryParam("testID") Integer testID,
			    @QueryParam("testKeyCSV") String testKey,
			    @QueryParam("testValue") String testValue,
			    @QueryParam("anomalyTrainID") Integer anomalyTrainID,
			    @QueryParam("anomalyTrainKeyCSV") String anomalyTrainKey,
			    @QueryParam("anomalyTrainValue") String anomalyTrainValue,
			    @QueryParam("autoReload") Integer autoReloadSec) {
	if (AnomalyDetectionConfiguration.CALC_TYPE_TO_USE == AnomalyDetectionConfiguration.CALC_TYPE_KDTREE) {
	    return getDataKDTree(trainID, trainKey, trainValue, testID, testKey, testValue);
	}
	else if (AnomalyDetectionConfiguration.CALC_TYPE_TO_USE == AnomalyDetectionConfiguration.CALC_TYPE_SVM) {
	    return getDataSVM(trainID, trainKey, trainValue, testID, testKey, testValue, anomalyTrainID, anomalyTrainKey, anomalyTrainValue, autoReloadSec);
	}
	else {
	    throw new RuntimeException("unknown calculation type");
	}
    }

    @GET
    @Path("/testKDTree")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getDataKDTree(@QueryParam("trainID") Integer trainID,
				  @QueryParam("trainKeyCSV") String trainKey,
				  @QueryParam("trainValue") String trainValue,
				  @QueryParam("testID") Integer testID,
				  @QueryParam("testKeyCSV") String testKey,
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

	if (trainKey == null) {
	    System.out.println("train Key not set");
	    error++;
	}

	if (testKey == null) {
	    System.out.println("test Key not set");
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

	output += KDTreeCalc.runOneTestKDTree(trainID, getPointFromCSV(trainKey), getPointFromCSV(trainValue), testID, getPointFromCSV(testKey), getPointFromCSV(testValue), null);
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
     * @param trainKeyCSV a CSV of keys
     * @param valueKeyCSV a CSV of histogram values
     * @param output function will add text to this if it makes a new mapping
     *
     * @return the ID that contains the data with indexes valueKeyCSV and trainKeyCSV (which may be different from the input id) or -1 if none
     */
    public int recalculateByKey(Integer id, String trainKeyCSV, String valueKeyCSV, StringBuilder output) {
	GenericPoint<String> newKey = getPointFromCSV(trainKeyCSV);
	GenericPoint<String> valueKey = getPointFromCSV(valueKeyCSV);

	if (allHistogramsMap.get(id) == null) {
	    throw new RuntimeException("Error: id " + id + " not found");
	}
	if (allHistogramsMap.get(id).get(valueKey) == null) {
	    throw new RuntimeException("Error: value key '" + valueKey + "' not found");
	}
	if (allHistogramsMap.get(id).get(valueKey).get(newKey) != null) {
	    return id;
	}

	if (allHistogramsMapKeyRemap.get(valueKey) != null &&
	    allHistogramsMapKeyRemap.get(valueKey).get(newKey) != null) {
	    return allHistogramsMapKeyRemap.get(valueKey).get(newKey);
	}

	// XYZ probably move this lock to beginning of function
	allHistogramsMapLock.lock();

	allHistogramsMap.put(nextHistogramMapID, new HashMap<GenericPoint<String>, HashMap<GenericPoint<String>, ArrayList<Pair<Integer, GenericPoint<Integer>>>>>());

	allHistogramsMap.get(nextHistogramMapID).put(valueKey, new HashMap<GenericPoint<String>, ArrayList<Pair<Integer, GenericPoint<Integer>>>>());
	allHistogramsMap.get(nextHistogramMapID).get(valueKey).put(newKey, new ArrayList<Pair<Integer, GenericPoint<Integer>>>());

	int subsetsFound = 0;
	// the histograms are in an arraylist so it's hard to add them together. Sum them in a hashmap then convert it to an arraymap
	TreeMap<Integer, GenericPoint<Integer>> newDataSum = new TreeMap(); //use TreeMap not HashMap so we get keys back in sorted orders
	for (GenericPoint<String> oneKey : allHistogramsMap.get(id).get(valueKey).keySet()) {
	    System.out.println("XYZ " + oneKey.toString() + " compare to " + newKey.toString());

	    if (isPointSubset(newKey, oneKey)) {
		System.out.println("counting " + newKey.toString() + " as subset as " + oneKey.toString() + "\n");
		subsetsFound++;
		for (Pair<Integer, GenericPoint<Integer>> oneHist : allHistogramsMap.get(id).get(valueKey).get(oneKey)) {

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
	    allHistogramsMap.get(nextHistogramMapID).get(valueKey).get(newKey).add(new Pair<Integer, GenericPoint<Integer>>(timeSec, newDataSum.get(timeSec)));
	}

	int newID = -1;
	if (subsetsFound > 0) {
	    newID = nextHistogramMapID;
	    nextHistogramMapID++;
	    output.append("Did not find appropriate data at ID " + id + " but was able to create it from existing data and put it in id " + newID + "\n");
	}
	allHistogramsMapLock.unlock();

	return newID;
    }

    /**
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
			       @QueryParam("trainKeyCSV") String trainKey,
			       @QueryParam("trainValue") String trainValue,
			       @QueryParam("testID") Integer testID,
			       @QueryParam("testKeyCSV") String testKey,
			       @QueryParam("testValue") String testValue,
			       @QueryParam("anomalyTrainID") Integer anomalyTrainID,
			       @QueryParam("anomalyTrainKeyCSV") String anomalyTrainKey,
			       @QueryParam("anomalyTrainValue") String anomalyTrainValue,
			       @QueryParam("autoReloadSec") Integer autoReloadSec) {

	StringBuilder output = new StringBuilder("<html>");
	if (autoReloadSec != null && autoReloadSec > 0) {
	    output.append("<head><meta http-equiv='refresh' content='" + autoReloadSec + "'></head>");
	}
	output.append("<body><pre>Calculation method: SVM\n");

	GenericPoint<String> trainKeyPoint = getPointFromCSV(trainKey);
	GenericPoint<String> trainValuePoint = getPointFromCSV(trainValue);
	GenericPoint<String> testKeyPoint = getPointFromCSV(testKey);
	GenericPoint<String> testValuePoint = getPointFromCSV(testValue);

	GenericPoint<String> anomalyTrainKeyPoint = null;
	GenericPoint<String> anomalyTrainValuePoint = null;
	if (anomalyTrainKey != null) {
	    anomalyTrainKeyPoint = getPointFromCSV(anomalyTrainKey);
	    anomalyTrainValuePoint = getPointFromCSV(anomalyTrainValue);
	}

	int newTrainID = recalculateByKey(trainID, trainKey, trainValue, output);
	if (newTrainID == -1) {
	    output.append("ERROR: trainKeyCSV (" + trainKey + ") was not found and could not be calculated from existing data\n");
	    return Response.status(200).entity(output.toString()).build();
	}
	else if (newTrainID != trainID) {
	    output.append("NOTE: trainKeyCSV (" + trainKey + ") was not found at id " + trainID + " but found it at ID " + newTrainID + "\n");
	    trainID = newTrainID;
	    if (allHistogramsMapKeyRemap.get(trainValuePoint) == null) {
		allHistogramsMapKeyRemap.put(trainValuePoint, new HashMap<GenericPoint<String>, Integer>());
	    }
	    allHistogramsMapKeyRemap.get(trainValuePoint).put(trainKeyPoint, trainID);
	}
	int newTestID = recalculateByKey(testID, testKey, testValue, output);
	if (newTestID == -1) {
	    output.append("ERROR: testKeyCSV  (" + testKey + ") was not found and could not be calculated from existing data\n");
	    return Response.status(200).entity(output.toString()).build();
	}
	else if (newTestID != testID) {
	    output.append("NOTE: testKeyCSV  (" + testKey + ") was not found at id " + testID + " but found it at ID " + newTestID + "\n");
	    testID = newTestID;
	    if (allHistogramsMapKeyRemap.get(testValuePoint) == null) {
		allHistogramsMapKeyRemap.put(testValuePoint, new HashMap<GenericPoint<String>, Integer>());
	    }
	    allHistogramsMapKeyRemap.get(testValuePoint).put(testKeyPoint, testID);
	}
	if (anomalyTrainID != null) {
	    int newAnomalyTrainID = recalculateByKey(anomalyTrainID, anomalyTrainKey, anomalyTrainValue, output);
	    if (newAnomalyTrainID == -1) {
		output.append("ERROR: anomalyTrainKeyCSV (" + anomalyTrainKey + ") was not found and could not be calculated from existing data\n");
		return Response.status(200).entity(output.toString()).build();
	    }
	    else if (newAnomalyTrainID != anomalyTrainID) {
		output.append("NOTE: anomalyTrainKeyCSV (" + anomalyTrainKey + ") was not found at id " + anomalyTrainID + " but found it at ID " + newAnomalyTrainID + "\n");
		anomalyTrainID = newAnomalyTrainID;
		if (allHistogramsMapKeyRemap.get(anomalyTrainValuePoint) == null) {
		    allHistogramsMapKeyRemap.put(anomalyTrainValuePoint, new HashMap<GenericPoint<String>, Integer>());
		}
		allHistogramsMapKeyRemap.get(anomalyTrainValuePoint).put(anomalyTrainKeyPoint, anomalyTrainID);
	    }
	}
	output.append(SVMCalc.runOneTestSVM(trainID, trainKeyPoint, trainValuePoint, testID, testKeyPoint, testValuePoint, anomalyTrainID, anomalyTrainKeyPoint, anomalyTrainValuePoint, null));


	return Response.status(200).entity(output.toString()).build();
    }

    @GET
    @Path("/testall/{valueCSV}")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getDataAll(@PathParam("valueCSV") String value) {
	if (AnomalyDetectionConfiguration.CALC_TYPE_TO_USE == AnomalyDetectionConfiguration.CALC_TYPE_KDTREE) {
	    return getDataAllKDTree(value);
	}
	else if (AnomalyDetectionConfiguration.CALC_TYPE_TO_USE == AnomalyDetectionConfiguration.CALC_TYPE_SVM) {
	    return getDataAllSVM(value);
	}
	else {
	    throw new RuntimeException("unknown calculation type");
	}
    }

    @GET
    @Path("/testallKDTree/{valueCSV}")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getDataAllKDTree(@PathParam("valueCSV") String value) {
	StringBuilder output = new StringBuilder("Calculation method: KDTree\n");
	output.append(KDTreeCalc.runAllTestKDTree(getPointFromCSV(value)));
	return Response.status(200).entity(output.toString()).build();
    }

    @GET
    @Path("/testallSVM/{valueCSV}")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getDataAllSVM(@PathParam("valueCSV") String value) {
	StringBuilder output = new StringBuilder("Calculation method: SVM\n");
	output.append(SVMCalc.runAllTestSVM(getPointFromCSV(value)));
	return Response.status(200).entity(output.toString()).build();
    }

    @GET
    @Path("/setSampleWindowSecs/{newVal}")
    @Produces(MediaType.TEXT_PLAIN)
    public Response setSampleWindowSecs(@PathParam("newVal") int newValue) {
	AnomalyDetectionConfiguration.SAMPLE_WINDOW_SECS = newValue;
	return Response.status(200).entity("ok").build();
    }

    @GET
    @Path("/getSampleWindowSecs")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getSampleWindowSecs() {
	String output = "" + AnomalyDetectionConfiguration.SAMPLE_WINDOW_SECS;
	return Response.status(200).entity(output).build();
    }

    @GET
    @Path("/setSlideWindowSecs/{newVal}")
    @Produces(MediaType.TEXT_PLAIN)
    public Response setSlideWindowSecs(@PathParam("newVal") int newValue) {
	AnomalyDetectionConfiguration.SLIDE_WINDOW_SECS = newValue;
	return Response.status(200).entity("ok").build();
    }

    @GET
    @Path("/getSlideWindowSecs")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getSlideWindowSecs() {
	String output = "" + AnomalyDetectionConfiguration.SLIDE_WINDOW_SECS;
	return Response.status(200).entity(output).build();
    }

    @GET
    @Path("/setCalcType/{newVal}")
    @Produces(MediaType.TEXT_HTML)
    public Response setCalcType(@PathParam("newVal") String newValue) {
	String output = "Ok. Calc type set to " + newValue;
	int newIndex = Arrays.asList(AnomalyDetectionConfiguration.CALC_TYPE_NAMES).indexOf(newValue);
	if (newIndex >= 0) {
	    AnomalyDetectionConfiguration.CALC_TYPE_TO_USE = newIndex;
	}
	else {
	    output = "Invalid type<br>\n";
	}
	output += AnomalyDetectionConfiguration.printCalcTypeNameLinksHTML("");
	return Response.status(200).entity(output).build();
    }

    @GET
    @Path("/getCalcType")
    @Produces(MediaType.TEXT_HTML)
    public Response getCalcType() {
	String output = "Calc type is " + AnomalyDetectionConfiguration.CALC_TYPE_NAMES[AnomalyDetectionConfiguration.CALC_TYPE_TO_USE];
	output += "<br>" + AnomalyDetectionConfiguration.printCalcTypeNameLinksHTML("setCalcType/");
	return Response.status(200).entity(output).build();
    }
	
}