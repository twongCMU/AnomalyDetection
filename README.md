AnomalyDetection
================

AnomalyDetection is proof-of-concept code that demonstrates the ability to detect anomalies in Multispeak traffic and
suggest possible reasons for the anomalies based on past experiences. The anomaly detection is called the "unsupervised"
component as it operates on unlabeled data. The component that suggests possible reasons for the anomalies is called 
"supervised" because it draws on information previously labeled by an operator. Once the actual cause of an unsupervised
anomaly is known, the user annotates it as such and it becomes a new datapoint for the supervised component.

Anomaly detection can be accomplished using a plethora of algorithms. Each technique has strengths and weaknesses depending
on the nature of the input data. We are currently using Support Vector Machines with a Chi Squared kernel. However
we expect this to change as we gain a better understanding as to the nature of Multispeak traffic. 

AnomalyDetection currently interacts with several components. A REST API allows a user to load and manipulate data and 
run tests. There is also code to read Multispeak data from a remote database and interact with Essence to report anomalies
and retrieve information for the supervised decision making.

Once we have enough Multispeak traffic to tune the code, most of the REST interface will be removed and replaced with
a daemon that continually runs a predefined list of tests looking for abnormal behavior.

It should be noted that the word "anomaly" does not imply hostile or adversarial behavior. This project is designed to
detect that traffic patterns are different; it is up to the operator to decide if the alerts are acceptable or require
action.

# Running the code
The code is written in Java 7. Most dependencies are installed using Apache Maven. There is one exception:

  * Download the newest version of the library here: https://www.savarese.com/software/libssrckdtree-j/
  * Look for the precompiled jar file in lib/libssrckdtree-j-<version>.jar
  * Install the library into Maven (in this example we'll assume the version is 1.0.2. If your version is different, you should make sure the pom.xml file reflects that). Note that the parameter for -Dfile= is the relative path to your jar file
  * mvn install:install-file -Dfile=lib/libssrckdtree-j-1.0.2.jar -DgroupId=com.savarese.spatial -DartifactId=libssrckdtree
-Dversion=1.0.2 -Dpackaging=jar

To start the daemon:
mvn clean; mvn tomcat:run

# Python port
As this code is proof-of-concept, it has grown organically and has many shortcomings. Therefore, we have written
a Python port of the code. For more information, see 