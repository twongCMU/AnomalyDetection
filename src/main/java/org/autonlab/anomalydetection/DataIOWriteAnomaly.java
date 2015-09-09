package org.autonlab.anomalydetection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.ListIterator;

import java.net.URI;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.JSONValue;

import org.javatuples.*;
import com.savarese.spatial.*;

import org.joda.time.*;

public class DataIOWriteAnomaly {

    Client client;
    HashMap<Integer, String> causes;

    public DataIOWriteAnomaly()
    {
	client = Client.create();
	causes = null;
    }

    public void closeConnection()
    {
	client.destroy();
    }

    public String writeFakeAnomalies() 
    {
	String output = new String();
	int numToCreate = 1;

	for (int predictionValue = 0; predictionValue < 4; predictionValue++) {
	    for (int i = 0; i < numToCreate; i++) {
		Long testStart = new Long(1399912491000L);
		Long testEnd = new Long(1399912451000L);
		Long trainStart = new Long(1399830841000L);
		Long trainEnd = new Long(1399917252000L);
		Integer sourceType = 1;
		String sourceValue = "10.90.94.9";
		Integer targetType = 1;
		String algorithm ="svm_chi_squared_1.0";
		Double score = new Double(100.0);
		Integer patternIndex = 1;
		String[] trainingTargetValue = { "10.90.94.9", "10.80.1.148" };
		Integer[] trainingMinCount = { 320 + (predictionValue * 20), 0 };
		Integer[] trainingMaxCount = { 320 + (predictionValue * 20), 0 };
		Integer[] trainingMeanCount = { 320 + (predictionValue * 20), 0 };
		Double[] trainingStandardDeviation = { 0.0, 0.0 };
		String[] anomalyValue = { "10.90.94.9", "10.80.1.148" };
		Integer[] anomalyCount = { 320 + (predictionValue * 20), 0 };
		Integer[] predictedCauses = { (1 + predictionValue) };
		Integer[] predictedStates = { 2 };
		output += writeAnomaly(testStart, testEnd, trainStart, trainEnd,
				       sourceType, sourceValue, targetType,
				       algorithm,  score,  patternIndex,
				       trainingTargetValue, trainingMinCount,
				       trainingMaxCount,  trainingMeanCount,
				       trainingStandardDeviation,  anomalyValue,
				       anomalyCount,  predictedCauses,
				       predictedStates);
	    }
	    output += "Sent " + numToCreate + " anomalies with data [" + (320 + (predictionValue * 20)) + ",0] and cause " + (1 + predictionValue) + "\n";
	}
	return output;
    }

    public String writeAnomaly(Long testStart, Long testEnd, Long trainStart, Long trainEnd,
			       Integer sourceType, String sourceValue, Integer targetType,
			       String algorithm, Double score, Integer patternIndex,
			       String[] trainingTargetValue, Integer[] trainingMinCount,
			       Integer[] trainingMaxCount, Integer[] trainingMeanCount,
			       Double[] trainingStandardDeviation, String[] anomalyValue,
			       Integer[] anomalyCount, Integer[] predictedCauses,
			       Integer[] predictedStates) {

	JSONObject obj = new JSONObject();
	DateTime testStartDateTime = new DateTime(testStart);
	obj.put("detectionTimeWindowStart", testStartDateTime.toString("EEE, dd MMM yyyy HH:mm:ss Z"));
	DateTime testEndDateTime = new DateTime(testEnd);
	obj.put("detectionTimeWindowEnd", testEndDateTime.toString("EEE, dd MMM yyyy HH:mm:ss Z"));
	DateTime trainingStartDateTime = new DateTime(trainStart);
	obj.put("trainingTimeWindowStart", trainingStartDateTime.toString("EEE, dd MMM yyyy HH:mm:ss Z"));
	DateTime trainingEndDateTime = new DateTime(trainEnd);
	obj.put("trainingTimeWindowEnd", trainingEndDateTime.toString("EEE, dd MMM yyyy HH:mm:ss Z"));
	obj.put("sourceType", sourceType);
	// 10.90.94.9 or 10.80.1.148
	obj.put("sourceValue", sourceValue);
	obj.put("targetType", targetType);
	obj.put("algorithm", algorithm);
	obj.put("score", score);
	obj.put("patternIndex", patternIndex);

	JSONArray normalEntriesArray = new JSONArray();
	for (int i = 0; i < trainingTargetValue.length; i++) {
	    JSONObject normalEntries = new JSONObject();
	    normalEntries.put("sequenceNumber", new Integer(i));
	    normalEntries.put("targetValue", trainingTargetValue[i]);
	    normalEntries.put("minCount", trainingMinCount[i]);
	    normalEntries.put("maxCount", trainingMaxCount[i]);
	    normalEntries.put("meanCount", trainingMeanCount[i]);
	    normalEntries.put("standardDeviation", trainingStandardDeviation[i]);
	    normalEntriesArray.add(normalEntries);
	}
	obj.put("normalEntries", normalEntriesArray);

	JSONArray anomalyEntriesArray = new JSONArray();
	for (int i = 0; i < anomalyValue.length; i++) {
	    JSONObject anomalyEntries = new JSONObject();
	    anomalyEntries.put("sequenceNumber",new Integer(i));
	    anomalyEntries.put("targetValue", anomalyValue[i]);
	    anomalyEntries.put("count", anomalyCount[i]);
	    anomalyEntriesArray.add(anomalyEntries);
	}
	obj.put("anomalyEntries", anomalyEntriesArray);

	JSONArray predictedCausesArray = new JSONArray();
	for (Integer oneCause : predictedCauses) {
	    JSONObject predictedCausesEntry = new JSONObject();
	    predictedCausesEntry.put("id", new Integer(oneCause));
	    predictedCausesArray.add(predictedCausesEntry);
	}
	obj.put("predictedCauses",predictedCausesArray);

	JSONArray predictedStatesArray = new JSONArray();
	for (Integer oneState : predictedStates) {
	    JSONObject predictedStatesEntry = new JSONObject();
	    predictedStatesEntry.put("id", oneState);
	    predictedStatesArray.add(predictedStatesEntry);
	}
	obj.put("predictedStates",predictedStatesArray);
	String output = new String();
	//output += obj.toString());
	//output += "\n";

	WebResource webResource = client.resource(AnomalyDetectionConfiguration.ANOMALY_REST_URL_PREFIX + "/anomaly");
	ClientResponse response = webResource.type("application/json").post(ClientResponse.class, obj.toString());
	String writeRes = response.getEntity(String.class);
	if (!writeRes.equals("A new anomaly and alert have been created")) {
	    output += writeRes + "\n\n\n";
	}
	
	return output;
    }

    public String getAnomaliesTest()
    {
	Long testStart = 1399912491000L;
	Long testEnd = 1399912451000L;
	Long trainStart = 1399830841000L;
	Long trainEnd = 1399917252000L;
	String sourceValue = "10.90.94.9";
	Integer targetType = 1;
	String algorithm = "svm_chi_squared_1.0";
	Integer userState = -1;
	Integer userCause = -1;

	HashMap<Pair<Integer, Integer>, ArrayList<Pair<Integer, GenericPoint<Integer>>>> anomalies;
	anomalies = getAnomalies(testStart, testEnd, trainStart, trainEnd, sourceValue, targetType, algorithm, userState, userCause);

	String output = new String();
	for (Pair<Integer, Integer> bar : anomalies.keySet()) {
	    output += bar.getValue0() + ", " + bar.getValue1();
	}

	return output;
    }

    public HashMap<Pair<Integer, Integer>, ArrayList<Pair<Integer, GenericPoint<Integer>>>> getAnomalies(
			Long testStart, Long testEnd, Long trainStart, Long trainEnd,
			String sourceValue, Integer targetType, String algorithm,
			Integer userState, Integer userCause)
    {
	String ret = new String();
	String arg = new String();

	if (testStart > 0) {
	    arg += "&detectionTimeWindowStart=" + testStart;
	}
	if (testEnd > 0) {
	    arg += "&detectionTimeWindowEnd=" + testEnd;
	}
	if (trainStart > 0) {
	    arg += "&trainingTimeWindowStart=" + trainStart;
	}
	if (trainEnd > 0) {
	    arg += "&trainingTimeWindowEnd=" + trainEnd;
	}
	if (sourceValue.length() > 0) {
	    arg += "&sourceValue=" + sourceValue;
	}
	if (targetType > 0) {
	    arg += "&targetType=" + targetType;
	}
	if (algorithm.length() > 0) {
	    arg += "&algorithm=" + algorithm;
	}
	
	if (userCause > 0) {
	    arg += "&userCause=" + userCause;
	}

	if (userState > 0) {
	    arg += "&userState=" + userState;
	}

	WebResource webResource = client.resource(AnomalyDetectionConfiguration.ANOMALY_REST_URL_PREFIX + "/anomaly/query/?" + arg);
	ret = webResource.accept("application/json").get(String.class);
	System.out.println(ret + "\n\n");

	JSONArray retArray = new JSONArray();
	retArray = (JSONArray)JSONValue.parse(ret);

	//	String testState = "[{\"id\":1,\"state\":\"1\"}]";
	//Object retObj = JSONValue.parse(testState);
	//JSONArray retArray = (JSONArray)retObj;


	HashMap<Pair<Integer, Integer>, ArrayList<Pair<Integer, GenericPoint<Integer>>>> anomalyData = new HashMap();

	for (int i = 0; i < retArray.size(); i++) {
	    Object oneObj = retArray.get(i);
	    JSONObject oneJObj = (JSONObject)oneObj;

	    Integer cause = Integer.parseInt(oneJObj.get("userCause").toString());
	    Integer state = Integer.parseInt(oneJObj.get("userState").toString());

	    Pair<Integer, Integer> key = new Pair(cause, state);
	    if (!anomalyData.containsKey(key)) {
		anomalyData.put(key, new ArrayList());
	    }

	    JSONArray anomalyEntries =(JSONArray)oneJObj.get("anomalyEntries");
	    GenericPoint<Integer> point = new GenericPoint(anomalyEntries.size());
	    for (int j = 0; j < anomalyEntries.size(); j++) {
		JSONObject oneAnomaly = (JSONObject)anomalyEntries.get(i);
		Integer count = Integer.parseInt(oneAnomaly.get("count").toString());
		point.setCoord(i, count);
	    }
	    // Using timestamps of 0 for now. Not sure if it matters yet
	    anomalyData.get(key).add(new Pair(0, point));
	}
	return anomalyData;
    }

    /* We should do some sort of caching here but we don't have a mechanism for invalidating the cache so for now we just retrieve the info every time */
    public String getCause(Integer lookupID)
    {
	if (causes != null) {
	    return this.causes.get(lookupID);
	}

	WebResource webResource = client.resource(AnomalyDetectionConfiguration.ANOMALY_REST_URL_PREFIX + "/anomaly/causes/");
	String ret = webResource.accept("application/json").get(String.class);
	JSONArray retArray = new JSONArray();
	retArray = (JSONArray)JSONValue.parse(ret);
	
	this.causes = new HashMap();

	for (int i = 0; i < retArray.size(); i++) {
	    Object oneObj = retArray.get(i);
	    JSONObject oneJObj = (JSONObject)oneObj;

	    
	    Integer id = Integer.parseInt(oneJObj.get("id").toString());
	    String cause = oneJObj.get("cause").toString();
	    this.causes.put(id, cause);
	}
	return this.causes.get(lookupID);
    }
}
