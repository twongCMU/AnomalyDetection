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

public class DataIOWriteAnomaly {

    Client client;

    public DataIOWriteAnomaly()
    {
	client = Client.create();
    }

    public void closeConnection()
    {
	client.destroy();
    }

    public String writeFakeAnomalies() 
    {
	String output = new String();

	for (int predictionValue = 0; predictionValue < 4; predictionValue++) {
	    for (int i = 0; i < 1; i++) {
		JSONObject obj = new JSONObject();
		obj.put("detectionTimeWindowStart", new Long(1399912491000L));
		obj.put("detectionTimeWindowEnd", new Long(1399912451000L));
		obj.put("trainingTimeWindowStart", new Long(1399830841000L));
		obj.put("trainingTimeWindowEnd", new Long(1399917252000L));
		obj.put("sourceType", new Integer(1));
		// 10.90.94.9 or 10.80.1.148
		obj.put("sourceValue", "10.90.94.9");
		obj.put("targetType", new Integer(1));
		obj.put("algorithm", "svm_chi_squared_1.0");
		obj.put("score", new Double(100.0));
		obj.put("patternIndex", 1);

		JSONObject normalEntries0 = new JSONObject();
		normalEntries0.put("sequenceNumber", new Integer(0));
		normalEntries0.put("targetValue", "10.90.94.9");
		normalEntries0.put("minCount", new Integer(320 + (predictionValue * 20)));
		normalEntries0.put("maxCount", new Integer(320 + (predictionValue * 20)));
		normalEntries0.put("meanCount", new Integer(320 + (predictionValue * 20)));
		normalEntries0.put("standardDeviation", new Double(0.0));

		JSONObject normalEntries1 = new JSONObject();
		normalEntries1.put("sequenceNumber", new Integer(1));
		normalEntries1.put("targetValue", "10.80.1.148");
		normalEntries1.put("minCount", new Integer(0));
		normalEntries1.put("maxCount", new Integer(0));
		normalEntries1.put("meanCount", new Integer(0));
		normalEntries1.put("standardDeviation", new Double(0.0));

		JSONArray normalEntriesArray = new JSONArray();
		normalEntriesArray.add(normalEntries0);
		normalEntriesArray.add(normalEntries1);

		obj.put("normalEntries", normalEntriesArray);

		JSONObject anomalyEntries0 = new JSONObject();
		anomalyEntries0.put("sequenceNumber", new Integer(0));
		anomalyEntries0.put("targetValue", "10.90.94.9");
		anomalyEntries0.put("count", new Integer(320 + (predictionValue * 20)));

		JSONObject anomalyEntries1 = new JSONObject();
		anomalyEntries1.put("sequenceNumber", new Integer(0));
		anomalyEntries1.put("targetValue", "10.80.1.148");
		anomalyEntries1.put("count", new Integer(0));

		JSONArray anomalyEntriesArray = new JSONArray();
		anomalyEntriesArray.add(anomalyEntries0);
		anomalyEntriesArray.add(anomalyEntries1);
		obj.put("anomalyEntries", anomalyEntriesArray);

		JSONArray predictedCausesArray = new JSONArray();
		predictedCausesArray.add(new Integer(predictionValue & 0x0001));
		obj.put("predictedCauses", predictedCausesArray);

		JSONArray predictedStatesArray = new JSONArray();
		predictedStatesArray.add(new Integer((predictionValue & 0x0002)>>1));
		obj.put("predictedStates", predictedStatesArray);
		System.out.println(obj.toString() + "\n");
		WebResource webResource = client.resource(AnomalyDetectionConfiguration.ANOMALY_REST_URL_PREFIX + "/anomaly");
		ClientResponse response = webResource.type("application/json").post(ClientResponse.class, obj.toString());
		output += response.getEntity(String.class) + "\n\n";
	    }
	}
	return output;
    }

    public String writeAnomaly(String testStart, String testEnd, String trainStart, String trainEnd,
			       Integer sourceType, String sourceValue, Integer targetType,
			       String algorithm, Double score, Integer[] patternIndex,
			       String[] trainingTargetValue, Integer[] trainingMinCount,
			       Integer[] trainingMaxCount, Integer[] trainingMeanCount,
			       Double[] trainingStandardDeviation, String[] anomalyValue,
			       String[] anomalyCount, Integer[] predictedCauses,
			       Integer[] predictedStates) {

	JSONObject obj = new JSONObject();
	obj.put("detectionTimeWindowStart", testStart);
	obj.put("detectionTimeWindowEnd", testEnd);
	obj.put("trainingTimeWindowStart", trainStart);
	obj.put("trainingTimeWindowEnd", trainEnd);
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
	    predictedCausesArray.add(oneCause);
	}
	obj.put("predictedCauses",predictedCausesArray);

	JSONArray predictedStatesArray = new JSONArray();
	for (Integer oneState : predictedStates) {
	    predictedStatesArray.add(oneState);
	}
	obj.put("predictedStates",predictedStatesArray);
	String output = new String(obj.toString());
	output += "\n";

	return output;
    }
    // "svm_chi_squared_1.0"
    public HashMap<Pair<Integer, Integer>, ArrayList<Pair<Integer, GenericPoint<Integer>>>> getAnomalies(
			String testStart, String testEnd, String trainStart, String trainEnd,
			String sourceValue, Integer targetType, String algorithm,
			Integer userState, Integer userCause)
    {
	String ret = new String();
	/*
	JSONObject obj = new JSONObject();
	obj.put("detectionTimeWindowStart", testStart);
	obj.put("detectionTimeWindowEnd", testEnd);
	obj.put("trainingTimeWindowStart", trainStart);
	obj.put("trainingTimeWindowEnd", trainEnd);
	obj.put("sourceType", sourceType);
	obj.put("targetType", targetType);
	obj.put("algorithm", algorithm);
	obj.put("userState", userState);
	obj.put("userCause", userCause);


	WebResource webResource = client.resource("states/" + obj.toString());
	String ret = webResource.accept("text/plain").get(String.class);
	*/

	String testState = "[{\"id\":1,\"state\":\"1\"}]";
	Object retObj = JSONValue.parse(testState);
	JSONArray retArray = (JSONArray)retObj;


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
}
