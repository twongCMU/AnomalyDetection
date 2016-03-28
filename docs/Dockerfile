FROM ubuntu:15.10

# The basic ubuntu install has an outdated version of sklearn so we have to upgrade
RUN apt-get -y update 
RUN apt-get -y install git python-cassandra python-flask python-coverage python-nose python-scipy python-sklearn python-requests python-numpy
RUN apt-get -y install libblas-dev liblapack-dev libatlas-base-dev gfortran python-pip 
RUN easy_install -U pip
RUN pip2 install --upgrade requests scikit-learn scipy coverage flask cassandra-driver flask-restful
RUN cd /tmp && /usr/bin/git clone https://08d86aa9ca967566177315f1d0286064e5aea4b1@github.com/twongCMU/AnomalyDetection.git


RUN cd /tmp/AnomalyDetection && /usr/bin/git checkout twong/python
WORKDIR /tmp/AnomalyDetection/python/anomalydetection
#CMD /usr/bin/nosetests-2.7 -x -s
CMD ./daemon_service.py --docker_mode
