package org.autonlab.anomalydetection;

import com.datastax.driver.core.*; //cassandra
import com.savarese.spatial.*;
import java.util.*;

public class DataIOCassandraDB implements DataIO {
    private Session _session;
    private String _keyspace;
    private int _counter;
    private ArrayList<String> _keyFieldsList;
    private ArrayList<String> _dataFieldsList;

    public DataIOCassandraDB(String hostname, String keyspace) {
	Cluster cluster = Cluster.builder().addContactPoint(hostname).build();
	_session = cluster.connect();
	_keyspace = keyspace;
	_counter = 0;
	_keyFieldsList = new ArrayList<String>();
	_keyFieldsList.add("source_addr");
	_keyFieldsList.add("text_values.endpoint");

	_dataFieldsList = new ArrayList<String>();
	// this part isn't quite implemented but we want to make the code similar to the key stuff
	_dataFieldsList.add("time_stamp"); // timestamp must be first
	_dataFieldsList.add("text_values.messagetype");
	//	_dataFieldsList.add("dest_addr");
    }

    public void setKeyFields(String keyFieldsCSV) {
	String[] sParts = keyFieldsCSV.split(",");
	_keyFieldsList = new ArrayList<String>();

	for (int ii = 0; ii < sParts.length; ii++) {
	    _keyFieldsList.add(sParts[ii]);
	}
    }

    public void setValueFields(String dataField) {
	_dataFieldsList = new ArrayList<String>();

	_dataFieldsList.add("time_stamp");
	_dataFieldsList.add(dataField);
    }

    /* histogram data name -> key names -> histograms */
    public HashMap<String, HashMap<GenericPoint<String>, ArrayList<HistoTuple>>> getData() {
	HashMap<String, HashMap<GenericPoint<String>, ArrayList<HistoTuple>>> trainMap = new HashMap();

	// build a SELECT statement that pulls out all of the database columns that we want
	String selectStatement = "SELECT ";
	HashMap<String, Integer> fieldsSeen = new HashMap();
	// get the column names for our data's keys
	for (String oneField : _keyFieldsList) {
	    int oneFieldEnd = oneField.indexOf(".");
	    if (oneFieldEnd == -1) {
		oneFieldEnd = oneField.length();
	    }
	    String oneFieldName = oneField.substring(0, oneFieldEnd);

	    // XML fields are all stored as a hash inside one DB column so we want to be sure not to request duplicate column names
	    if (!fieldsSeen.containsKey(oneFieldName)) {
		selectStatement += oneFieldName + ",";
		fieldsSeen.put(oneFieldName, 1);
	    }
	}
	// get the column names for our data's histogram values
	for (String oneField : _dataFieldsList) {
	    int oneFieldEnd = oneField.indexOf(".");
	    if (oneFieldEnd == -1) {
		oneFieldEnd = oneField.length();
	    }
	    String oneFieldName = oneField.substring(0, oneFieldEnd);

	    // XML fields are all stored as a hash inside one DB column so we want to be sure not to request duplicate column names
	    if (!fieldsSeen.containsKey(oneFieldName)) {
		selectStatement += oneFieldName + ",";
		fieldsSeen.put(oneFieldName, 1);
	    }
	}

	selectStatement = selectStatement.substring(0, selectStatement.length() - 1); // drop the trailing comma from above
	selectStatement += " FROM " + _keyspace + ".packet";
	System.out.println("Statement is " + selectStatement);
	ResultSet results = _session.execute(selectStatement);
	// get the data we want out of what the database returned
	// some of the db columns are hashses of more values, so we have to be smart about pulling those out too
	for (Row row: results) {
	    GenericPoint<String> key = new GenericPoint<String>(_keyFieldsList.size());
	    Map<String, String> fieldMap = row.getMap("text_values", String.class, String.class);

	    int ii = 0;
	    for (String oneField : _keyFieldsList) {
		if (oneField.equals("time_stamp")) {
		    key.setCoord(ii, "" + (row.getDate("time_stamp").getTime() / 1000));
		}
		else if (oneField.matches("^text_values.*$")) {
		    int oneFieldStart = oneField.indexOf(".");
		    if (oneFieldStart == -1) {
			throw new RuntimeException("did not find a period in field " + oneField);
		    }
		    String oneFieldTrailing = oneField.substring(oneFieldStart + 1, oneField.length());
		    key.setCoord(ii, fieldMap.get(oneFieldTrailing));
		}
		else {
		    String fieldValue = row.getString(oneField);
		    key.setCoord(ii, fieldValue == null ? "" : fieldValue);
		}
		ii++;
	    }
	   
	    if (_dataFieldsList.size() != 2) {
		throw new RuntimeException("Data field list did not have exactly two elements");
	    }
	    if (!_dataFieldsList.get(0).equals("time_stamp")) {
		throw new RuntimeException("First data field was not time_stamp");
	    }
	    double dateSecs = (row.getDate("time_stamp").getTime() / 1000);
	    for (String valueName : _dataFieldsList) {
		String value = "";
		if (valueName.equals("time_stamp")) {
		    continue;
		}
		if (valueName.matches("^text_values.*$")) {
		int oneFieldStart = valueName.indexOf(".");
		if (oneFieldStart == -1) {
		    throw new RuntimeException("did not find a period in field " + valueName);
		}
		String oneFieldTrailing = valueName.substring(oneFieldStart + 1, valueName.length());
		value = fieldMap.get(oneFieldTrailing);
		}
		else {
		    value = row.getString(valueName);
		}
		if (value == null) {
		    throw new RuntimeException("Failed to get value for column " + valueName);
		}
		// When we get to the point where we can customize the values we save, we might need to overwrite this cached value
		
		if (!trainMap.containsKey(valueName)) {
		    trainMap.put(valueName, new HashMap<GenericPoint<String>, ArrayList<HistoTuple>>());
		}
		if (!trainMap.get(valueName).containsKey(key)) {
		    trainMap.get(valueName).put(key, new ArrayList<HistoTuple>());
		}
		trainMap.get(valueName).get(key).add(new HistoTuple(dateSecs, value, valueName));
	    }
	}
	results = null;

	return trainMap;
    }

    public void putData(String keyCSV, int dateSecs, String messageType) {
    // from group meeting, we're going to write to a different db not the cassandra one so we won't reimplement this in a dynamic key friendly way 
    // until we know more details
    //	_session.execute("INSERT INTO " + _keyspace + ".packet (time_stamp, source_addr, text_values, dest_addr) VALUES (" + dateSecs + ",'" + ipAddress + "',{'messagetype' : '" + messageType + "', 'endpoint' : '" + appName + "'}, '" + _counter + "')");
    //_counter++;
    }

    public void close() {
	_session.close();
	_session = null;
    }
}