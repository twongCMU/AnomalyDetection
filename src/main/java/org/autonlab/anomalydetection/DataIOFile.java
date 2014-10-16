package org.autonlab.anomalydetection;

import java.io.*;
import java.util.*;
import org.javatuples.*; //Tuples, Pair

public class DataIOFile implements DataIO {
    private BufferedReader br = null;

    public DataIOFile(String filename) {
	try {
	    br = new BufferedReader(new FileReader(filename));
	} catch (IOException e) {
	    e.printStackTrace();
	    throw new RuntimeException("Failed to import data");
	}
    }

    /**
     * Read a csv file and return a HashMap of <Pair<ip, app name> -> <timestamp, histogram>>
     *
     */
    public HashMap<Pair<String, String>, ArrayList<HistoTuple>> getData() {
	HashMap<Pair<String, String>, ArrayList<HistoTuple>> trainMap = new HashMap();

	int count = 0;

	try {
	    int i;
 	    String sCurrentLine;

	    System.out.print("Importing data");
	    while ((sCurrentLine = br.readLine()) != null) {
		String[] sParts;

		if (count % 1000 == 0) {
		    System.out.print(".");
		}

		// expecting a line of the form <ip address>,<timestamp from epoch>,<msg type string>,<Application name>
		// The final two arguments may not exist but the first two must
		sParts = sCurrentLine.split(",");
		String ipAddress = sParts[0];
		Double timeStamp = Double.parseDouble(sParts[1]);
		String messageType = "";
		String applicationName = "";
		if (sParts.length >= 3) {
		    messageType = sParts[2];
		}
		if (sParts.length >= 4) {
		    applicationName = sParts[3];
		}

		Pair<String, String> keyPair = new Pair(ipAddress, applicationName);

		if (!trainMap.containsKey(keyPair)) {
		    trainMap.put(keyPair, new ArrayList<HistoTuple>());
		}
		trainMap.get(keyPair).add(new HistoTuple(timeStamp, messageType));

		count++;
	    }
	    System.out.println("");
	} catch (IOException e) {
	    e.printStackTrace();
	    throw new RuntimeException("Failed to import data");
	} finally {
	    try {
		if (br != null)br.close();
	    } catch (IOException ex) {
		ex.printStackTrace();
		throw new RuntimeException("Failed to import data");
	    }
	}

	return trainMap;
    }

    public void putData(int dateSecs, String ipAddress, String messageType, String appName) {
	throw new RuntimeException("putData for files not implemented");
    }

    public void close() {
	throw new RuntimeException("close for files is not implemented");
    }
}