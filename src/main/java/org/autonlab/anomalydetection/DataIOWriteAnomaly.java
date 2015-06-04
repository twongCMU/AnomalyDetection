package org.autonlab.anomalydetection;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.ListIterator;

import java.net.URI;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

public class DataIOWriteAnomaly {

    Client client;

    public DataIOWriteAnomaly()
    {
	client = Client.create();
    }

    public void firstEmail(int email, int mode)
    {
	WebResource webResource = client.resource("firstemail/" + email + "/" + mode);
	String ret = webResource.accept("text/plain").get(String.class);
	if (!ret.equals("ok")) {
	    throw new RuntimeException("firstEmail " + ret);
	}
    }

    public int emailInteresting()
    {
	WebResource webResource = client.resource("emailinteresting");
	return Integer.parseInt(webResource.accept("text/plain").get(String.class));
    }

    public int emailBoring()
    {
	WebResource webResource = client.resource("emailboring");
	return Integer.parseInt(webResource.accept("text/plain").get(String.class));
    }

    public void setAlpha(double newAlpha)
    {
	WebResource webResource = client.resource("setalpha/" + newAlpha);
	String ret = webResource.accept("text/plain").get(String.class);
	if (!ret.equals("ok")) {
	    throw new RuntimeException("set alpha returned " + ret);
	}
    }

    public int getStartPoint()
    {
	WebResource webResource = client.resource("getStartPoint");
	return Integer.parseInt(webResource.accept("text/plain").get(String.class));
    }

    public double resetLabel(int index, int value)
    {
	WebResource webResource = client.resource("resetLabel/" + index + "/" + value);
	return Double.parseDouble(webResource.accept("text/plain").get(String.class));
    }

    public void setLabel(int value)
    {
	WebResource webResource = client.resource("setLabelCurrent/" + value);
	String ret = webResource.accept("text/plain").get(String.class);
	if (!ret.equals("ok")) {
	    throw new RuntimeException("setLabelCurrent returned " + ret);
	}
    }

    public int getNextEmail()
    {
	WebResource webResource = client.resource("getNextEmail");
	return Integer.parseInt(webResource.accept("text/plain").get(String.class));
    }

    public int pickRandomLabeledEmail()
    {
	WebResource webResource = client.resource("pickRandomLabeledEmail");
	return Integer.parseInt(webResource.accept("text/plain").get(String.class));
    }

    public double getLabel(int email)
    {
	WebResource webResource = client.resource("getLabel/" + email);
	return Double.parseDouble(webResource.accept("text/plain").get(String.class));
    }

    /**
     * Translate from a user ID to a username string
     *
     * @param userID The user ID to look up
     *
     * @return the username
     */
    public String getUserNameFromID(int userID)
    {
	WebResource webResource = client.resource("getUserNameFromID/" + userID);
	return webResource.accept("text/plain").get(String.class);
    }
 
    /**
     * Get a linked list of message subjects sent from one user ID to another user ID
     *
     * @param userIDFrom User ID of the user who sent the emails
     * @param userIDTo User ID of the user who received the emails
     *
     * @return Linked list of email message subjects sorted by timestamp of the form <messageid> : <timestamp> : <subject>
     */
    public LinkedList<String> getMessagesFromUserToUser(int userIDFrom, int userIDTo)
    {
	WebResource webResource = client.resource("getMessagesFromUserToUser/" + userIDFrom + "/" + userIDTo);

	String[] messages = webResource.accept("text/plain").get(String.class).split("[\\r\\n]+");

	LinkedList<String> retList = new LinkedList<String>();
	for (int i = 0; i < messages.length; i++) {
	    retList.add(messages[i]);
	}

	return retList;
    }

    /**
     * Get a message subject from a message ID
     *
     * @param messageID ID of the message to retrieve
     *
     * @return Message subject
     */
    public String getEmailSubjectFromMessageID(int messageID)
    {
	WebResource webResource = client.resource("getEmailSubjectFromMessageID/" + messageID);
	return webResource.accept("text/plain").get(String.class);
    }

    /**
     * Get a message body from a message ID
     *
     * @param messageID ID of the message to retrieve
     *
     * @return Message body contents with some other email info prepended to it
     */
    public String getEmailBodyFromMessageID(int messageID)
    {
	WebResource webResource = client.resource("getEmailBodyFromMessageID/" + messageID);
	return webResource.accept("text/plain").get(String.class);
    }

    /**
     * Initialize total number of email messages. Call this before functions that use it
     *
     * @return total number of email messages
     */
    public int getTotalEmailCount()
    {
	WebResource webResource = client.resource("getTotalEmailCount");
	return Integer.parseInt(webResource.accept("text/plain").get(String.class));
    }


    /**
     * Get arrays of email timestamps and senderIDs
     * The index of the array is the email message ID. The timestamp is seconds from epoch
     *
     * @return 2 item array with the first item being the array of timestamps and the second the array of senderIDs
     */
    public int[][] getEmailTimesAndSenders()
    {
	int emailCount = this.getTotalEmailCount();
	int[][] returnVals = new int[2][];
	int[] emailTime = new int[emailCount];
	 int[] emailSender = new int[emailCount];

	WebResource webResource = client.resource("getEmailTimesAndSenders");
	String[] messages = webResource.accept("text/plain").get(String.class).split("[\\r\\n]+");

	for (int i = 0; i < messages.length; i++) {
	    // format: messageID messageTime senderID
	    String[] retColumns = messages[i].split(" ");
	    emailTime[Integer.parseInt(retColumns[0])] = Integer.parseInt(retColumns[1]);
	    emailSender[Integer.parseInt(retColumns[0])] = Integer.parseInt(retColumns[2]);
	}

	return returnVals;
    }

    public LinkedList<Integer> getUsersByEmail(int email)
    {
	WebResource webResource = client.resource("getUsersByEmail/" + email);
	String[] messages = webResource.accept("text/plain").get(String.class).split("[\\r\\n]+");

	LinkedList<Integer> retList = new LinkedList<Integer>();
	for (int i = 0; i < messages.length; i++) {
	    retList.add(Integer.parseInt(messages[i]));
	}

	return retList;
    }

    public int getSenderByEmail(int email)
    {
	WebResource webResource = client.resource("getSenderByEmail/" + email);
	return Integer.parseInt(webResource.accept("text/plain").get(String.class));
    }

    public String getTimeByEmail(int email)
    {
	WebResource webResource = client.resource("getTimeByEmail/" + email);
	return webResource.accept("text/plain").get(String.class);
    }

    public String getSubjectByEmail(int email)
    {
	WebResource webResource = client.resource("getSubjectByEmail/" + email);
	return webResource.accept("text/plain").get(String.class);
    }

    public LinkedList<String> getEmailsByKeyword(String word)
    {
	WebResource webResource = client.resource("getEmailsByKeyword/" + word);
	String[] messages = webResource.accept("text/plain").get(String.class).split("[\\r\\n]+");

	LinkedList<String> retList = new LinkedList<String>();
	for (int i = 0; i < messages.length; i++) {
	    retList.add(messages[i]);
	}

	return retList;
    }

    public LinkedList<String> getEmailsByKeywordSubject(String word)
    {
	WebResource webResource = client.resource("getEmailsByKeywordSubject/" + word);
	String[] messages = webResource.accept("text/plain").get(String.class).split("[\\r\\n]+");

	LinkedList<String> retList = new LinkedList<String>();
	for (int i = 0; i < messages.length; i++) {
	    retList.add(messages[i]);
	}

	return retList;
    }

    /**
     * Get an ArrayList of Integer Linked Lists. The array index is the messageID, and the linked list of
     * integers is the variable number of unique and sorted recipient user IDs for that message
     *
     * An ArrayList was used rather than a generic array due to compiler issues with the LinkedList<Integer> type
     *
     * @return ArrayList of LinkedList of recipient user IDs
     */
    public ArrayList<LinkedList<Integer>> getEmailRecipients()
    {
	int emailCount = this.getTotalEmailCount();
	ArrayList<LinkedList<Integer>> emailRecipients = new ArrayList<LinkedList<Integer>>(emailCount);

	int i;
	for (i = 0; i < emailCount; i++) {
	    emailRecipients.add(null);
	    LinkedList<Integer> tempList = getEmailRecipientsByEmail(i);
	    emailRecipients.add(i, tempList);
	}
	return emailRecipients;
    }

    public LinkedList<Integer> getEmailRecipientsByEmail(int email)
    {
	WebResource webResource = client.resource("getEmailRecipientsByEmail/" + email);
	String[] recipients = webResource.accept("text/plain").get(String.class).split("[\\r\\n]+");

	LinkedList<Integer> retList = new LinkedList<Integer>();
	for (int i = 0; i < recipients.length; i++) {
	    if (!recipients[i].isEmpty()) {
		retList.add(Integer.parseInt(recipients[i]));
	    }
	}

	return retList;
    }
}
