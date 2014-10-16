package org.autonlab.anomalydetection;

import com.savarese.spatial.*;
import java.util.*;
import org.apache.commons.collections.map.*;
import org.javatuples.*;

public class KDTreeCalc {
   /**
     * Convert the training data from a hash of (<IP, AppName> -> HistoTuple<date, message type>) to (<IP, AppName> -> KDTree)
     *
     * @param trainHashMap The HashMap to be converted to a mapping to KDTree
     * @param output this function will append the list all seen combinations of <IP, AppName>
     * @return HashMap of <IP, AppName>  to KDTree
     * 
     */
    public static HashMap<Pair<String, String>,KDTree<Integer, GenericPoint<Integer>, java.lang.Integer>> makeKDTree(HashMap<Pair<String, String>, ArrayList<Pair<Integer, GenericPoint<Integer>>>> trainHashMap, StringBuilder output) {
	HashMap<Pair<String, String>, KDTree<Integer, GenericPoint<Integer>, java.lang.Integer>> newMap = new HashMap();

	for (Pair<String, String> keyAddr : trainHashMap.keySet()) {
	    String ipAddress = keyAddr.getValue0();
	    String appName = keyAddr.getValue1();
	    if (output != null) {
		output.append(ipAddress + "," + appName + "\n");
	    }
	    System.out.println("ok ok " + ipAddress + " '" + appName + "' " + trainHashMap.get(keyAddr).size());
	    newMap.put(keyAddr, GetKDTree(trainHashMap.get(keyAddr)));
	}

	return newMap;
    }

    public static KDTree<Integer, GenericPoint<Integer>, java.lang.Integer> GetKDTree(ArrayList<Pair<Integer, GenericPoint<Integer>>> histograms) {
	KDTree<Integer, GenericPoint<Integer>, java.lang.Integer> treeKD = new KDTree<Integer, GenericPoint<Integer>, java.lang.Integer>(HistoTuple.getDimensions());

	for (Pair<Integer, GenericPoint<Integer>> dataPair : histograms) {
	    treeKD.put(dataPair.getValue1(), dataPair.getValue0());
	}
	return treeKD;
    }

   /**
     * @param trainID ID of the model to use to train on
     * @param trainIP IP address within the trainID model to consider
     * @param trainApp Application name within the trainID model to consider
     * @param testID ID of the model to use to test on
     * @param testIP IP address within the testID model to consider
     * @param testApp Application name within the testID model to consider
     * @param results If not null, every result will be recorded here as score->timestamp. We use a MultiValueMap so duplicate scores will still be recorded
     */
    public static String runOneTestKDTree(Integer trainID, String trainIP, String trainApp, Integer testID, String testIP, String testApp, MultiValueMap results) {
	NearestNeighbors<Integer, GenericPoint<Integer>, java.lang.Integer> neighbor = new NearestNeighbors<Integer, GenericPoint<Integer>, java.lang.Integer>();

	String output = new String();
	Pair<String, String> trainKey = new Pair<String, String>(trainIP, trainApp);
	Pair<String, String> testKey = new Pair<String, String>(testIP, testApp);

	KDTree<Integer, GenericPoint<Integer>, java.lang.Integer> trainTree = KDTreeCalc.GetKDTree(DaemonService.allHistogramsMap.get(trainID).get(trainKey));
	KDTree<Integer, GenericPoint<Integer>, java.lang.Integer> testTree = KDTreeCalc.GetKDTree(DaemonService.allHistogramsMap.get(testID).get(testKey));

	for (GenericPoint<Integer> myPoint : testTree.keySet()) {
	    // Note that the kdtree only stores unique points so if there are duplicate histograms, only one will be displayed here
	    // this tells us the anomolies but if we want to display a nice ordering of time windows we'll need to do something else
	    NearestNeighbors.Entry<Integer, GenericPoint<Integer>, java.lang.Integer>[] vals = neighbor.get(trainTree, myPoint, 1, false);

	    if (results != null) {
		results.put(vals[0].getDistance(), testTree.get(myPoint));
	    }
	    output += "distance for " + myPoint.toString() + " is " + vals[0].getDistance() + " at time " + testTree.get(myPoint) + "\n";
	}

	return output;
    }

    /**
     * Test every combination against every other combinatino
     */
    public static StringBuilder runAllTestKDTree() {
	StringBuilder output = new StringBuilder();

	for (Integer keyID : DaemonService.allHistogramsMap.keySet()) {
	    for (Integer keyIDInner : DaemonService.allHistogramsMap.keySet()) {
		HashMap<Pair<String, String>,KDTree<Integer, GenericPoint<Integer>, java.lang.Integer>> newMap = KDTreeCalc.makeKDTree(DaemonService.allHistogramsMap.get(keyID), null);
		HashMap<Pair<String, String>,KDTree<Integer, GenericPoint<Integer>, java.lang.Integer>> newMapInner = KDTreeCalc.makeKDTree(DaemonService.allHistogramsMap.get(keyIDInner), null);

		// iterate through all combinations of <IP, App names>
		for (Pair<String, String> keyPair : newMap.keySet()) {
		    for (Pair<String, String> keyPairInner : newMapInner.keySet()) {
			// track Score -> timestamp
			// A MultiValueMap is a HashMap where the value is an Collection of values (to handle duplicate keys)
			MultiValueMap resultsHash = new MultiValueMap();

			output.append("Highest 3 scores for ID " + keyID + " : <" + keyPair.getValue(0) + "," + keyPair.getValue(1) + "> vs ID " + keyIDInner + " : <" + keyPairInner.getValue(0) + "," + keyPairInner.getValue(1) + ">\n");
			// intentionally ignore the return string since we're generating our own display info later
			KDTreeCalc.runOneTestKDTree(keyID, keyPair.getValue0(), keyPair.getValue1(), keyIDInner, keyPairInner.getValue0(), keyPairInner.getValue1(), resultsHash);

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