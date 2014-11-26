package org.autonlab.anomalydetection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.javatuples.Pair;

import com.savarese.spatial.GenericPoint;

@Path("/")
public class DaemonService {

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
		allHistogramsMap.put(nextHistogramMapID, HistoTuple.mergeWindows(foo.getData(), AnomalyDetectionConfiguration.SAMPLE_WINDOW_SECS, AnomalyDetectionConfiguration.SLIDE_WINDOW_SECS));
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
	 */
	@GET
	@Path("/getdb")
	@Produces(MediaType.TEXT_PLAIN)
	public Response getDb(@QueryParam("hostname") String hostname,
			@QueryParam("keyCSV") String keyCSV,
			@QueryParam("value") String valueCSV) {
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
		SVMRandomCalc.removeModelFromCache(id);
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
			@QueryParam("testValue") String testValue) {
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

	@GET
	@Path("/testSVM")
	@Produces(MediaType.TEXT_PLAIN)
	public Response getDataSVM(@QueryParam("trainID") Integer trainID,
			@QueryParam("trainKeyCSV") String trainKey,
			@QueryParam("trainValue") String trainValue,
			@QueryParam("testID") Integer testID,
			@QueryParam("testKeyCSV") String testKey,
			@QueryParam("testValue") String testValue) {
		StringBuilder output = new StringBuilder("Calculation method: SVM\n");

		output.append(SVMCalc.runOneTestSVM(trainID, getPointFromCSV(trainKey), getPointFromCSV(trainValue), testID, getPointFromCSV(testKey), getPointFromCSV(testValue), null));
		return Response.status(200).entity(output.toString()).build();
	}

	@GET
	@Path("/testSVMRandom")
	@Produces(MediaType.TEXT_PLAIN)
	public Response getDataSVMRandom(@QueryParam("trainID") Integer trainID,
			@QueryParam("trainKeyCSV") String trainKey,
			@QueryParam("trainValue") String trainValue,
			@QueryParam("testID") Integer testID,
			@QueryParam("testKeyCSV") String testKey,
			@QueryParam("testValue") String testValue) {
		StringBuilder output = new StringBuilder("Calculation method: SVM\n");

		output.append(SVMRandomCalc.runOneTestSVM(trainID, getPointFromCSV(trainKey), getPointFromCSV(trainValue), testID, getPointFromCSV(testKey), getPointFromCSV(testValue), null));
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
		else if (AnomalyDetectionConfiguration.CALC_TYPE_TO_USE == AnomalyDetectionConfiguration.CALC_TYPE_SVM_RANDOM) {
			return getDataAllSVMRandom(value);
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
	@Path("/testallSVMRandom/{valueCSV}")
	@Produces(MediaType.TEXT_PLAIN)
	public Response getDataAllSVMRandom(@PathParam("valueCSV") String value) {
		StringBuilder output = new StringBuilder("Calculation method: SVM Random");
		output.append(SVMRandomCalc.runAllTestSVM(getPointFromCSV(value)));
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