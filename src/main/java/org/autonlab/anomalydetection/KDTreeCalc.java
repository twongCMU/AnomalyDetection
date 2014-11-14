package org.autonlab.anomalydetection;

import com.savarese.spatial.*;
import java.util.*;
import org.apache.commons.collections.map.*;
import org.javatuples.*;

public class KDTreeCalc {
   /**
     * Convert the training data from a hash of (<GenericPoint> -> HistoTuple<date, message type>) to (<GenericPoint> -> KDTree)
     *
     * @param trainHashMap The HashMap to be converted to a mapping to KDTree
     * @param output this function will append the list all seen combinations of <GenericPoint>
     * @return HashMap of <GenericPoint>  to KDTree
     * 
     */
    public static HashMap<GenericPoint<String>,KDTree<Integer, GenericPoint<Integer>, java.lang.Integer>> makeKDTree(String valueType, HashMap<GenericPoint<String>, ArrayList<Pair<Integer, GenericPoint<Integer>>>> trainHashMap, StringBuilder output) {
	HashMap<GenericPoint<String>, KDTree<Integer, GenericPoint<Integer>, java.lang.Integer>> newMap = new HashMap();

	for (GenericPoint<String> keyAddr : trainHashMap.keySet()) {
	    if (output != null) {
		output.append(keyAddr.toString());
	    }
	    newMap.put(keyAddr, GetKDTree(valueType, trainHashMap.get(keyAddr)));
	}
	
	return newMap;
    }

    public static KDTree<Integer, GenericPoint<Integer>, java.lang.Integer> GetKDTree(String valueType, ArrayList<Pair<Integer, GenericPoint<Integer>>> histograms) {
	KDTree<Integer, GenericPoint<Integer>, java.lang.Integer> treeKD = new KDTree<Integer, GenericPoint<Integer>, java.lang.Integer>(HistoTuple.getDimensions(valueType));

	for (Pair<Integer, GenericPoint<Integer>> dataPair : histograms) {
	    treeKD.put(dataPair.getValue1(), dataPair.getValue0());
	}
	return treeKD;
    }

   /**
     * @param trainID ID of the model to use to train on
     * @param trainKey Key index for the training set histograms
     * @param testID ID of the model to use to test on
     * @param testKey Key index for the test set histograms
     * @param results If not null, every result will be recorded here as score->timestamp. We use a MultiValueMap so duplicate scores will still be recorded
     */
    public static String runOneTestKDTree(Integer trainID, GenericPoint<String> trainKey, String trainValue, Integer testID, GenericPoint<String> testKey, String testValue, MultiValueMap results) {
	NearestNeighbors<Integer, GenericPoint<Integer>, java.lang.Integer> neighbor = new NearestNeighbors<Integer, GenericPoint<Integer>, java.lang.Integer>();

	String output = new String();

	HistoTuple.upgradeWindowsDimensions(trainValue, DaemonService.allHistogramsMap.get(trainID).get(trainValue).get(trainKey), DaemonService.allHistogramsMap.get(testID).get(testValue).get(testKey));

	KDTree<Integer, GenericPoint<Integer>, java.lang.Integer> trainTree = KDTreeCalc.GetKDTree(trainValue, DaemonService.allHistogramsMap.get(trainID).get(trainValue).get(trainKey));

	// The GenericPoints are often duplicated so we could add a cache here but I'm not sure if performace would improve by that much. KDTrees are already log time
	for (Pair<Integer, GenericPoint<Integer>> tempPair : DaemonService.allHistogramsMap.get(testID).get(testValue).get(testKey)) {
	    Integer histogramTime = (Integer)tempPair.getValue(0);
	    GenericPoint<Integer> histogramData = (GenericPoint<Integer>)tempPair.getValue(1);

	    NearestNeighbors.Entry<Integer, GenericPoint<Integer>, java.lang.Integer>[] vals = neighbor.get(trainTree, histogramData, 1, false);

	    if (results != null) {
		results.put(vals[0].getDistance(), histogramTime);
	    }
	    output += "time " + histogramTime + " distance for " + histogramData.toString() + " is " + vals[0].getDistance() + "\n";
	}

	return output;
    }

    /**
     * Test every combination against every other combinatino
     */
    public static StringBuilder runAllTestKDTree(String valueType) {
	StringBuilder output = new StringBuilder();

	for (Integer keyID : DaemonService.allHistogramsMap.keySet()) {
	    for (Integer keyIDInner : DaemonService.allHistogramsMap.keySet()) {
		if (!DaemonService.allHistogramsMap.get(keyID).containsKey(valueType) ||
		    !DaemonService.allHistogramsMap.get(keyIDInner).containsKey(valueType)) {
		    continue;
		}
		HashMap<GenericPoint<String>,KDTree<Integer, GenericPoint<Integer>, java.lang.Integer>> newMap = KDTreeCalc.makeKDTree(valueType, DaemonService.allHistogramsMap.get(keyID).get(valueType), null);
		HashMap<GenericPoint<String>,KDTree<Integer, GenericPoint<Integer>, java.lang.Integer>> newMapInner = KDTreeCalc.makeKDTree(valueType, DaemonService.allHistogramsMap.get(keyIDInner).get(valueType), null);

		// iterate through all combinations of <IP, App names>
		for (GenericPoint<String> key : newMap.keySet()) {
		    for (GenericPoint<String> keyInner : newMapInner.keySet()) {
			// track Score -> timestamp
			// A MultiValueMap is a HashMap where the value is an Collection of values (to handle duplicate keys)
			MultiValueMap resultsHash = new MultiValueMap();

			output.append("Highest 3 scores for ID " + keyID + " : <" + key.toString() + "> vs ID " + keyIDInner + " : <" + keyInner.toString() + ">\n");
			// intentionally ignore the return string since we're generating our own display info later
			KDTreeCalc.runOneTestKDTree(keyID, key, valueType, keyIDInner, keyInner, valueType, resultsHash);

			List<Double> resultsHashList = new ArrayList<Double>(resultsHash.keySet());
			Collections.sort(resultsHashList);
			Collections.reverse(resultsHashList);
			int ii = 0;
			for (Double score : resultsHashList) {
			    if (ii == 0 && score == 0.0) {
				output.append("[All scores are zero. Not printing]\n");
				break;
			    }
			    if (ii >= 3) {
				break;
			    }
			    for (Integer timestamp : ((Collection<Integer>)resultsHash.getCollection(score))) {
				output.append(score + " at time " + timestamp + "\n");
			    }
			    ii++;
			}
		    }
		}
	    }
	}
	return output;
    }
}