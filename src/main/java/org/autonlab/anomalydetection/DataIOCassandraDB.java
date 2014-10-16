package org.autonlab.anomalydetection;

import com.datastax.driver.core.*; //cassandra
import java.util.*;
import org.javatuples.*; //Tuples, Pair

public class DataIOCassandraDB implements DataIO {
    private Session _session;
    private String _keyspace;
    private int _counter;
    public DataIOCassandraDB(String hostname, String keyspace) {
	Cluster cluster = Cluster.builder().addContactPoint(hostname).build();
	_session = cluster.connect();
	_keyspace = keyspace;
	_counter = 0;
    }

    public HashMap<Pair<String, String>, ArrayList<HistoTuple>> getData() {
	HashMap<Pair<String, String>, ArrayList<HistoTuple>> trainMap = new HashMap();

	ResultSet results = _session.execute("SELECT time_stamp, source_addr, text_values FROM " + _keyspace + ".packet");
	for (Row row: results) {
	    int dateSecs = (int)(row.getDate("time_stamp").getTime() / 1000);
	    String ipAddress = row.getString("source_addr");
	    Map<String, String> fieldMap = row.getMap("text_values", String.class, String.class);
	    String messageType = fieldMap.get("messagetype");
	    String appName = fieldMap.get("endpoint");
	    Pair keyPair = new Pair<String, String>(ipAddress, appName == null ? "" : appName);

	    if (!trainMap.containsKey(keyPair)) {
		trainMap.put(keyPair, new ArrayList<HistoTuple>());
	    }
	    trainMap.get(keyPair).add(new HistoTuple(dateSecs, messageType));
	}
	results = null;

	return trainMap;
    }

    public void putData(int dateSecs, String ipAddress, String messageType, String appName) {
	_session.execute("INSERT INTO " + _keyspace + ".packet (time_stamp, source_addr, text_values, dest_addr) VALUES (" + dateSecs + ",'" + ipAddress + "',{'messagetype' : '" + messageType + "', 'endpoint' : '" + appName + "'}, '" + _counter + "')");
	_counter++;
    }

    public void close() {
	_session.close();
	_session = null;
    }
}