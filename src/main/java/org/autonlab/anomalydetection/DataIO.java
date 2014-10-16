package org.autonlab.anomalydetection;

import java.util.*;
import org.javatuples.*; //Tuples, Pair

public interface DataIO {
    /**
     * Import the data from the source and assemble it into a HashMap of 
     * <Pair<IP Address, Application Name>> -> ArrayList<timestamp, message type>
     * This data will need to be processed using HistoTuple.mergeWindows
     */
    public HashMap<Pair<String, String>, ArrayList<HistoTuple>> getData();

    /**
     * Write one record to the data store. Not implemented by all subclasses
     *
     * @param dataSecs Timestamp of record in seconds since epoch
     * @param ipAddress Ip address of sender
     * @param messageType Sending message type
     * @param appName Sending application name
     */
    public void putData(int dateSecs, String ipAddress, String messageType, String appName);

    /**
     * Close data store
     */
    public void close();
}