package org.autonlab.anomalydetection;

import com.datastax.driver.core.*; //cassandra
import com.savarese.spatial.*;
import java.util.*;
import org.joda.time.*;
import org.javatuples.*;

public class DataIOCassandraDB implements DataIO {
    /*
     * if open a new session with every call, the code crashes
     * after 20 or so opens saying that we're out of threads. I don't know why and it
     * persists even if we close the session and null stuff out. I think it is a bug
     * in the driver. Having this cache allows us to reuse the sessions and avoid the
     * crash
     */
    private static HashMap<Pair<String,String>, Session> _sessionHash = new HashMap();

    private Session _session = null;
    private String _keyspace;
    private String _hostname;
    private int _counter;
    private ArrayList<String> _categoryFieldsList;
    private ArrayList<String> _valueFieldsList;

    public DataIOCassandraDB(String hostname, String keyspace) {
	_session = _sessionHash.get(new Pair<String, String>(hostname, keyspace));
	if (_session == null) {
	    Cluster cluster = Cluster.builder().addContactPoint(hostname).build();
	    _session = cluster.connect();
	    _sessionHash.put(new Pair<String, String>(hostname, keyspace), _session);
	}
	else {
	    System.out.println("Session cache hit");
	}

	_keyspace = keyspace;
	_hostname = hostname;
	_counter = 0;
	_categoryFieldsList = new ArrayList<String>();
	_categoryFieldsList.add("source_addr");
	_categoryFieldsList.add("text_values.endpoint");

	_valueFieldsList = new ArrayList<String>();
	_valueFieldsList.add("time_stamp"); // timestamp must be first
	_valueFieldsList.add("text_values.messagetype");
	_valueFieldsList.add("dest_addr");
    }

    /*
     * @param categoryFieldsCSV a CSV of DB column names to categorize the histograms by
     */
    public void setCategoryFields(String categoryFieldsCSV) {
	String[] sParts = categoryFieldsCSV.split(",");
	Arrays.sort(sParts);
	_categoryFieldsList = new ArrayList<String>();

	for (int ii = 0; ii < sParts.length; ii++) {
	    _categoryFieldsList.add(sParts[ii]);
	}
    }

    /*
     * @param valueFieldsCSV a CSV of DB column names to generate histograms on
     */
    public void setValueFields(String valueFieldsCSV) {
	String[] sParts = valueFieldsCSV.split(",");
	Arrays.sort(sParts);
	_valueFieldsList = new ArrayList<String>();

	_valueFieldsList.add("time_stamp");

	for (int ii = 0; ii < sParts.length; ii++) {
	    _valueFieldsList.add(sParts[ii]);
	}
    }

    /* histogram value names -> category names -> histograms */
    public HashMap<GenericPoint<String>, HashMap<GenericPoint<String>, ArrayList<HistoTuple>>> getData(int minutesBack) {
	HashMap<GenericPoint<String>, HashMap<GenericPoint<String>, ArrayList<HistoTuple>>> trainMap = new HashMap();

	// use a value relevant to the test data. In the future, use empty constructor for current time
	DateTime endTime = new DateTime("2014-05-12T13:54:12.000-04:00");
	DateTime startTime = endTime.minusMinutes(minutesBack);

	int getTextValues = 0;

	// build a SELECT statement that pulls out all of the database columns that we want
	String selectStatement = "SELECT ";
	HashMap<String, Integer> fieldsSeen = new HashMap();
	// get the column names for our data's keys
	for (String oneField : _categoryFieldsList) {
	    int oneFieldEnd = oneField.indexOf(".");
	    if (oneFieldEnd == -1) {
		oneFieldEnd = oneField.length();
	    }
	    else {
		getTextValues = 1;
	    }
	    String oneFieldName = oneField.substring(0, oneFieldEnd);

	    // XML fields are all stored as a hash inside one DB column so we want to be sure not to request duplicate column names
	    if (!fieldsSeen.containsKey(oneFieldName)) {
		selectStatement += oneFieldName + ",";
		fieldsSeen.put(oneFieldName, 1);
	    }
	}
	// get the column names for our data's histogram values
	for (String oneField : _valueFieldsList) {
	    int oneFieldEnd = oneField.indexOf(".");
	    if (oneFieldEnd == -1) {
		oneFieldEnd = oneField.length();
	    }
	    else {
		getTextValues = 1;
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
	if (minutesBack > 0) {
	    selectStatement += " WHERE time_stamp > '" + startTime + "' AND time_stamp < '" + endTime + "' ALLOW FILTERING";
	}
	System.out.println("Statement is " + selectStatement);
	ResultSet results = _session.execute(selectStatement);

	// get the data we want out of what the database returned
	// some of the db columns are hashses of more values, so we have to be smart about pulling those out too 
	int rowCount = 0;
	int categoryFieldsListSize = _categoryFieldsList.size();//cache these outside of the loops to save recalculation
	int valueFieldsListSize = _valueFieldsList.size() - 1; // -1 because  we don't want to count time_stamp

	for (Row row: results) {
	    GenericPoint<String> category = new GenericPoint<String>(categoryFieldsListSize);
	    Map<String, String> fieldMap = new HashMap(); // initializing this isn't needed but Java complains
	    if (getTextValues == 1) {
		fieldMap = row.getMap("text_values", String.class, String.class);
	    }

	    int ii = 0;
	    for (String oneField : _categoryFieldsList) {
		String categoryString = oneField + ";";
		if (oneField.equals("time_stamp")) {
		    categoryString += (row.getDate("time_stamp").getTime() / 1000);
		}
		else if (oneField.matches("^text_values.*$")) {
		    if (getTextValues == 0) {
			throw new RuntimeException("didn't retrieve text_values from DB but need it");
		    }
		    int oneFieldStart = oneField.indexOf(".");
		    if (oneFieldStart == -1) {
			throw new RuntimeException("did not find a period in field " + oneField);
		    }
		    String oneFieldTrailing = oneField.substring(oneFieldStart + 1, oneField.length());
		    categoryString += (fieldMap.get(oneFieldTrailing));
		}
		else {
		    String fieldValue = row.getString(oneField);
		    categoryString += (fieldValue == null ? "" : fieldValue);
		}
		category.setCoord(ii, categoryString);
		ii++;
	    }
	   
	    if (!_valueFieldsList.get(0).equals("time_stamp")) {
		throw new RuntimeException("First value field was not time_stamp");
	    }
	    double dateSecs = (row.getDate("time_stamp").getTime() / 1000);
	    String value = "";
	    GenericPoint<String> valueNamePoint = new GenericPoint<String>(valueFieldsListSize);
	    ii = 0;
	    for (String valueName : _valueFieldsList) {
		if (valueName.equals("time_stamp")) {
		    continue;
		}
		valueNamePoint.setCoord(ii, valueName);
		if (valueName.matches("^text_values.*$")) {
		    if (getTextValues == 0) {
			throw new RuntimeException("didn't retrieve text_values from DB but need it");
		    }
		    int oneFieldStart = valueName.indexOf(".");
		    if (oneFieldStart == -1) {
			throw new RuntimeException("did not find a period in field " + valueName);
		    }
		    String oneFieldTrailing = valueName.substring(oneFieldStart + 1, valueName.length());
		    value += fieldMap.get(oneFieldTrailing) + ",";
		}
		else {
		    value += row.getString(valueName) + ",";
		}
		if (value == null) {
		    throw new RuntimeException("Failed to get value for column " + valueName);
		}
		// When we get to the point where we can customize the values we save, we might need to overwrite this cached value
		ii++;
	    }
	    value = value.substring(0, value.length() - 1); // to drop the trailing comma appended above

	    if (!trainMap.containsKey(valueNamePoint)) {
		trainMap.put(valueNamePoint, new HashMap<GenericPoint<String>, ArrayList<HistoTuple>>());
	    }
	    if (!trainMap.get(valueNamePoint).containsKey(category)) {
		trainMap.get(valueNamePoint).put(category, new ArrayList<HistoTuple>());
	    }
	    trainMap.get(valueNamePoint).get(category).add(new HistoTuple(dateSecs, value, valueNamePoint));
	    rowCount++;
	    if ((rowCount % 1000) == 0) {
		System.out.println("Read in " + rowCount + " rows");
	    }
	}
	results = null;

	return trainMap;
    }

    public void putData(String categoryCSV, int dateSecs, String messageType) {
    // from group meeting, we're going to write to a different db not the cassandra one so we won't reimplement this in a dynamic category friendly way 
    // until we know more details
    //	_session.execute("INSERT INTO " + _keyspace + ".packet (time_stamp, source_addr, text_values, dest_addr) VALUES (" + dateSecs + ",'" + ipAddress + "',{'messagetype' : '" + messageType + "', 'endpoint' : '" + appName + "'}, '" + _counter + "')");
    //_counter++;
    }

    public void close() {
	_sessionHash.remove(new Pair<String, String>(_hostname, _keyspace));
	_session.close();
	_session = null;
    }
}