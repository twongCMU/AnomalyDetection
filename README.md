AnomalyDetection
================
To run the code, make sure you have Apache Maven installed then install the kdtree lib into your maven library cache:

 * Download the newest version of the library here: https://www.savarese.com/software/libssrckdtree-j/
 * Look for the precompiled jar file in lib/libssrckdtree-j-<version>.jar
 * Install the library into Maven (in this example we'll assume the version is 1.0.2. If your version is different, you should make sure the pom.xml file reflects that). Note that the parameter for -Dfile= is the relative path to your jar file
  * mvn install:install-file -Dfile=lib/libssrckdtree-j-1.0.2.jar -DgroupId=com.savarese.spatial -DartifactId=libssrckdtree
-Dversion=1.0.2 -Dpackaging=jar

Then you're ready to run:
mvn clean; mvn tomcat:run
