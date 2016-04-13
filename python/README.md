AnomalyDetection python
=======================

For more about this project, see the [parent doc](https://github.com/twongCMU/AnomalyDetection)

As of this writing, this code is tested internally under both supervised and
unsupervised learning. It has also been run against an existing
dataset but the anomaly reporting to Essence has not.

To run the most recent release with the REST service exported
to port 5000, install Docker, then simply run:
 * docker run -p 5000:5000 twongcmu/anomalydetection
(This is a private repo so you will need access permissions)

To build and run the Docker container:
 * cd AnomalyDetection/docs
 * docker build --force-rm -t anom-detect .   <--note the trailing period
 * docker run -p 5000:5000 anom-detect

To run the code without Docker, install the dependencies listed
in docs/Dockerfile and python/setup.py
 * <use your package manager to install the dependencies in Dockerfile>
 * sudo python python/setup.py install
 * cd AnomalyDetection/python/anomalydetection
 * ./daemon_service.py

To exercise the API once the code is running:
http://127.0.0.1:5000/getfakedata
http://localhost:5000/test?train_id=0&test_id=1

To run the tests:
 * cd AnomalyDetection/python/anomalydetection
 * nosetests-2.7 (nose is one of the dependencies in setup.py so this should be installed)

The original Java implementation had a few shortcomings:
 * Required 3rd party library that had to be manually installed (not in Maven repo)
 * Codebase grew organically due to unknown requirements
 * Codebase not well documented
 * No automated tests
 
Java was not the best language to use for the project. This
code is a python reimplementation which tries to address all
of the problems:
 * Optionally uses a Docker container to deliver a pre-packaged running system
 * Codebase tries to reimplement the same external API as the Java code
   * The REST API is different but the interface with Essence follows the documented API
 * The Python code allows the same functionality in just 20% as much code
 * Codebase is better documented
 * Uses nosetest to verify functionality and confirm code coverage
 * Uses TeamCity to automatically run tests when repo changes


