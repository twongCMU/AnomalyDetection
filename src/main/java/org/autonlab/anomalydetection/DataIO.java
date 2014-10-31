package org.autonlab.anomalydetection;

import com.savarese.spatial.*;
import java.util.*;

public interface DataIO {
    /**
     * Import the data from the source and assemble it into a HashMap of 
     * <GenericPoint key> -> ArrayList<timestamp, message type>
     * This data will need to be processed using HistoTuple.mergeWindows
     */
    public HashMap<GenericPoint<String>, ArrayList<HistoTuple>> getData();

    /**
     * Write one record to the data store. Not implemented by all subclasses
     *
     * @param keyCSV Key index for the histogram
     * @param dataSecs Timestamp of record in seconds since epoch
     * @param messageType Sending message type
     */
    public void putData(String keyCSV, int dateSecs, String messageType);

    /**
     * Close data store
     */
    public void close();
}