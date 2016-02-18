#!/usr/bin/python2.7
from flask import Flask
from flask import Response
from flask import request
import numpy as np
import sys
import re
from cassandraIO import CassandraIO
from svm_calc import SVMCalc

##
# To run this, make sure the permissions are right:
# chmod a+x daemon_service.py 
#
# Then run it:
# ./daemon_service.py
##

app = Flask(__name__)

next_id = 0
hist_dict = dict()

@app.route('/getdb')
def getdb():
    global next_id
    global hist_dict

    host = request.args.get('hostname')
    keyspace = request.args.get('keyspace')
    table = request.args.get('table')
    filter_name = request.args.get('filter')
    filter_value = request.args.get('filter_val')
    features = request.args.get('featuresCSV')

    c = CassandraIO(keyspace, table, hostname=host)
    print "ok",filter_name,filter_value,features
    hist = c.get_histogram(60, 10, filter_name, filter_value, features)
    c.close()

    hist_dict[next_id] = hist

    output = "Dataset ID: " + str(next_id) + "\nFeatures (" + features + "):\n"
    for f in hist.get_features():
        output += f + "\n"

    next_id += 1

    return Response(output,  mimetype='text/plain')

@app.route('/test')
def test():
    global hist_dict

    train = int(request.args.get('train_id'))
    test = int(request.args.get('test_id'))

    output = ""
    error = 0
    if train not in hist_dict:
        output += "Error: training ID not found: " + train
        error += 1
    if test not in hist_dict:
        output += "Error: test ID not found: " + test
        error += 1
    if error == 0:
        ret = SVMCalc.test(hist_dict[train], hist_dict[test])
        count = 0
        for r in ret:
            output += str(count) + ":" +str(r) + "\n"
            count += 1

    return Response(output,  mimetype='text/plain')

if __name__ == '__main__':
    app.run(debug=True)
