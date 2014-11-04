package org.autonlab.anomalydetection;

import com.datastax.driver.core.*;
import com.savarese.spatial.*;
import java.util.*; 
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import org.javatuples.*;

@Path("/")
public class DaemonService {
    static HashMap<Integer, HashMap<GenericPoint<String>, ArrayList<Pair<Integer, GenericPoint<Integer>>>>> allHistogramsMap = new HashMap();

    static int nextHistogramMapID = 0;

    static int anomalyID = -1;
    static GenericPoint<String> anomalyKey;

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

	output = "" + HistoTuple.getDimensions() + "\n";
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
	    for (GenericPoint<String> keyFields : allHistogramsMap.get(id).keySet()) {
		output += "<li>";
		for (int ii = 0; ii < keyFields.getDimensions(); ii++) {
		    output += keyFields.getCoord(ii);
		    if (ii != keyFields.getDimensions() - 1) {
			output += ",";
		    }
		}
		output += "</li>";
	    }
	    output += "</ul>";
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
	StringBuilder output = new StringBuilder("Dataset ID: " + nextHistogramMapID + "\n");

	DataIOFile foo = new DataIOFile(filename);
	allHistogramsMap.put(nextHistogramMapID, HistoTuple.mergeWindows(foo.getData(), AnomalyDetectionConfiguration.SAMPLE_WINDOW_SECS, AnomalyDetectionConfiguration.SLIDE_WINDOW_SECS));
	for (GenericPoint<String> key : allHistogramsMap.get(nextHistogramMapID).keySet()) {
	    output.append("Key: " + key.toString());
	    output.append(" (datapoints: " + allHistogramsMap.get(nextHistogramMapID).get(key).size() + ")\n");
	}
	nextHistogramMapID++;
	return Response.status(200).entity(output.toString()).build();
    }

    /**
     *
     */
    @GET
    @Path("/getdb")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getDb(@QueryParam("hostname") String hostname,
			  @QueryParam("keyCSV") String keyCSV) {
	StringBuilder output = new StringBuilder("Dataset ID: " + nextHistogramMapID + "\n");
	    
	DataIOCassandraDB dbHandle = new DataIOCassandraDB(hostname, "demo");

	if (keyCSV != null) {
	    dbHandle.setKeyFields(keyCSV);
	}

	allHistogramsMap.put(nextHistogramMapID, HistoTuple.mergeWindows(dbHandle.getData(), AnomalyDetectionConfiguration.SAMPLE_WINDOW_SECS, AnomalyDetectionConfiguration.SLIDE_WINDOW_SECS));
	for (GenericPoint<String> key : allHistogramsMap.get(nextHistogramMapID).keySet()) {
	    output.append("Key: " + key.toString());
	    output.append(" (datapoints: " + allHistogramsMap.get(nextHistogramMapID).get(key).size() + ")\n");
	}
	nextHistogramMapID++;
	return Response.status(200).entity(output.toString()).build();
    }

    @GET
    @Path("/getfakedata")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getFakeData() {
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
	HistoTuple foo = new HistoTuple(1, "fake1");
	HistoTuple foo2 = new HistoTuple(2, "fake2");

	allHistogramsMap.put(nextHistogramMapID, fakeData);
	nextHistogramMapID++;
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
			    @QueryParam("testID") Integer testID,
			    @QueryParam("testKeyCSV") String testKey) {
	if (AnomalyDetectionConfiguration.CALC_TYPE_TO_USE == AnomalyDetectionConfiguration.CALC_TYPE_KDTREE) {
	    return getDataKDTree(trainID, trainKey, testID, testKey);
	}
	else if (AnomalyDetectionConfiguration.CALC_TYPE_TO_USE == AnomalyDetectionConfiguration.CALC_TYPE_SVM) {
	    return getDataSVM(trainID, trainKey, testID, testKey);
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
			    @QueryParam("testID") Integer testID,
  	                    @QueryParam("testKeyCSV") String testKey) {
	String output = "Calculation method: KDTree\n";

	output += KDTreeCalc.runOneTestKDTree(trainID, getPointFromCSV(trainKey), testID, getPointFromCSV(testKey), null);
	return Response.status(200).entity(output).build();
    }

    public GenericPoint<String> getPointFromCSV(String csv) {
	String[] sParts = csv.split(",");
	GenericPoint<String> point = new GenericPoint(sParts.length);

	for (int ii = 0; ii < sParts.length; ii++) {
	    point.setCoord(ii, sParts[ii]);
	}

	return point;
    }

    @GET
    @Path("/testSVM")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getDataSVM(@QueryParam("trainID") Integer trainID,
			       @QueryParam("trainKeyCSV") String trainKey,
			       @QueryParam("testID") Integer testID,
			       @QueryParam("testKeyCSV") String testKey) {
	StringBuilder output = new StringBuilder("Calculation method: SVM\n");

	output.append(SVMCalc.runOneTestSVM(trainID, getPointFromCSV(trainKey), testID, getPointFromCSV(testKey), null));
	return Response.status(200).entity(output.toString()).build();
    }

    @GET
    @Path("/testall")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getDataAll() {
	if (AnomalyDetectionConfiguration.CALC_TYPE_TO_USE == AnomalyDetectionConfiguration.CALC_TYPE_KDTREE) {
	    return getDataAllKDTree();
	}
	else if (AnomalyDetectionConfiguration.CALC_TYPE_TO_USE == AnomalyDetectionConfiguration.CALC_TYPE_SVM) {
	    return getDataAllSVM();
	}
	else {
	    throw new RuntimeException("unknown calculation type");
	}
    }

    @GET
    @Path("/testallKDTree")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getDataAllKDTree() {
	StringBuilder output = new StringBuilder("Calculation method: KDTree\n");
	output.append(KDTreeCalc.runAllTestKDTree());
	return Response.status(200).entity(output.toString()).build();
    }

    @GET
    @Path("/testallSVM")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getDataAllSVM() {
	StringBuilder output = new StringBuilder("Calculation method: SVM\n");
	output.append(SVMCalc.runAllTestSVM());
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

    @GET
    @Path("/setAnomalyKey")
    @Produces(MediaType.TEXT_HTML)
    public Response setAnomalyKey(@QueryParam("id") Integer id,
				  @QueryParam("csvKey") String csvKey) {
	String output = "ok";
	
	anomalyID = id;
	anomalyKey = getPointFromCSV(csvKey);

	return Response.status(200).entity(output).build();
    }
	
}