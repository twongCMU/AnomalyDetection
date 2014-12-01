package org.autonlab.anomalydetection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.javatuples.Pair;
import org.apache.commons.math3.distribution.UniformRealDistribution;

import com.savarese.spatial.GenericPoint;

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
	@Path("/getdb/{hostname}")
	@Produces(MediaType.TEXT_PLAIN)
	public Response getDb(@PathParam("hostname") String hostname) {
		StringBuilder output = new StringBuilder("ok\n");

		DataIOCassandraDB foo = new DataIOCassandraDB(hostname, "demo");
		allHistogramsMap.put(nextHistogramMapID, HistoTuple.mergeWindows(foo.getData(), AnomalyDetectionConfiguration.SAMPLE_WINDOW_SECS, AnomalyDetectionConfiguration.SLIDE_WINDOW_SECS));
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
		key.setCoord(0, "5.5.5.5");
		key.setCoord(1, "otherthing");
		fakeData.put(key, fullMatrix);
		output.append("Key: 5.5.5.5, otherthing (" + fullMatrix.size() + ")\n");

		// override this since we don't use the
		HistoTuple.setDimensions(2);

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
	@Path("/testSVMRandom")
	@Produces(MediaType.TEXT_PLAIN)
	public Response getDataSVMRandom(@QueryParam("trainID") Integer trainID,
			@QueryParam("trainKeyCSV") String trainKey,
			@QueryParam("testID") Integer testID,
			@QueryParam("testKeyCSV") String testKey) {
		StringBuilder output = new StringBuilder("Calculation method: SVM\n");

		output.append(SVMRandomCalc.runOneTestSVM(trainID, getPointFromCSV(trainKey), testID, getPointFromCSV(testKey), null));
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
		else if (AnomalyDetectionConfiguration.CALC_TYPE_TO_USE == AnomalyDetectionConfiguration.CALC_TYPE_SVM_RANDOM) {
			return getDataAllSVMRandom();
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
	@Path("/testallSVMRandom")
	@Produces(MediaType.TEXT_PLAIN)
	public Response getDataAllSVMRandom() {
		StringBuilder output = new StringBuilder("Calculation method: SVM\n");
		output.append(SVMRandomCalc.runAllTestSVM());
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

	@GET
	@Path("/customTest")
	@Produces(MediaType.TEXT_PLAIN)
	public Response customTest(@QueryParam("n") Integer n, @QueryParam("size") Integer s, @QueryParam("rn") Integer rn){//@QueryParam("id") Integer id,
		//@QueryParam("csvKey") String csvKey) {

		StringBuilder output = new StringBuilder("Custom Test\n\n");
		
		//output.append(getFakeData2(n,s,rn).getEntity());
		getFakeData2(n,s,rn);
//		String filename="/usr0/home/sibiv/Research/Data/GRE.out";
//		DataIOFile foo = new DataIOFile(filename);
//		allHistogramsMap.put(nextHistogramMapID, HistoTuple.mergeWindows(foo.getData(), AnomalyDetectionConfiguration.SAMPLE_WINDOW_SECS, AnomalyDetectionConfiguration.SLIDE_WINDOW_SECS));
//		System.out.println("Opened file.");

		int id = nextHistogramMapID-1;
		String csvKey1 = "a1,b1";
		String csvKey2 = "a2,b2";
		
		GenericPoint<String> trainKey = getPointFromCSV(csvKey1);
		GenericPoint<String> testKey = getPointFromCSV(csvKey1);

		output.append("\n\nSVM RANDOM:\n");
		long startTime1 = System.nanoTime();
		//output.append(SVMRandomCalc.runOneTestSVM(id, trainKey, id, testKey, null).toString());
		SVMRandomCalc.runOneTestSVM(id, trainKey, id, testKey, null).toString();
		long endTime1 = System.nanoTime();
		double duration = (double)(endTime1 - startTime1)/1000000000;
		output.append("\n\nTime taken:\n" + (duration));
		output.append("\n\nSVM:\n");
		long startTime2 = System.nanoTime();
		//output.append(SVMCalc.runOneTestSVM(id, trainKey, id, testKey, null));
		SVMCalc.runOneTestSVM(id, trainKey, id, testKey, null);
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
		
		HashMap<GenericPoint<String>, ArrayList<Pair<Integer, GenericPoint<Integer>>>> anomalyData = new HashMap();
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
		GenericPoint<String> aKey = new GenericPoint<String>(2);
		aKey.setCoord(0, "a");
		aKey.setCoord(1, "b");
		anomalyData.put(aKey, anomalyTraining);
		output.append("Key: a, b (" + anomalyTraining.size() + ")\n\n");
		anomalyID = -1;//nextHistogramMapID;
		anomalyKey = aKey;
		
		allHistogramsMap.put(nextHistogramMapID, anomalyData);
		nextHistogramMapID++;
		
		
		output.append("Train data:\n\n");
		HashMap<GenericPoint<String>, ArrayList<Pair<Integer, GenericPoint<Integer>>>> fakeData = new HashMap();
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
		GenericPoint<String> key = new GenericPoint<String>(2);
		key.setCoord(0, "a1");
		key.setCoord(1, "b1");
		fakeData.put(key, training);
		output.append("Key: a1, b1 (" + training.size() + ")\n\n");

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
		GenericPoint<String> key2 = new GenericPoint<String>(2);
		key2.setCoord(0, "a2");
		key2.setCoord(1, "b2");
		fakeData.put(key2, testing);
		output.append("Key: a2, b2 (" + testing.size() + ")\n");

		// override this since we don't use the ?????????????????????
		HistoTuple.setDimensions(s);
		
		allHistogramsMap.put(nextHistogramMapID, fakeData);
		nextHistogramMapID++;
		
		//output.append(allHistogramsMap.get(0).get(key2).get(0).toString());
		return Response.status(200).entity(output.toString()).build();
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
						output += ", ";
					}
				}
				output += "\t\t(datapoints: " + allHistogramsMap.get(id).get(keyFields).size() + ")";
				output += "</li>";
			}
			output += "</ul>";
		}
		return Response.status(200).entity(output).build();
	}
}