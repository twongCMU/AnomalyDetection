from __future__ import division
import numpy as np, numpy.random as nr, numpy.linalg as nlg
import time

import SVM, SVMRandomGaussian as SRG
import visualize as vis
import parse

np.set_printoptions(precision=3, suppress=True)


def generateGaussian(size, n, var, c):
	"""
	Generates a zero-mean gaussian data set of given dimension.
	Anomalies are a distance > c away from origin.
	"""

	mean = np.zeros(size)
	cov = np.eye(size)*var

	fs = [nr.multivariate_normal(mean, cov).tolist() for _ in range(n)]
	ys = [-1 if nlg.norm(f) > c else 1 for f in fs]

	return fs, ys

def generateBinaryDataset(size, n, anomaly_n):

	fs = []
	ys = []

	s2 = int(size/2)
	s4 = int(size/4)

	for _ in range(n):
		f = [0]*s2 + nr.randint(0,2,(size-s2)).tolist()
		y = 1

		fs.append(f)
		ys.append(y)

	for _ in range(anomaly_n):
		f = nr.randint(0,2,(s4)).tolist() + [0]*(size-s4)
		y = -1

		fs.append(f)
		ys.append(y)

	return fs, ys

def testBinary ():
	size = 12
	n = 10
	anomaly_n = 5

	rn = 10000
	gammak=1.0

	xs_train, ys_train = generateBinaryDataset(size, n, anomaly_n)
	xs_test, ys_test = generateBinaryDataset(size, n, anomaly_n)

	rfc = SRG.RandomFeaturesConverter(dim=size, rn=rn, gammak=gammak)

	params1 = SVM.SVMParam(ktype='rbf')
	params2 = SVM.SVMParam(ktype='linear', dual=True)

	svm1 = SVM.SVM(params1)#SRG.SVMRandomGaussian(rgf, params1, svm_type='SVM')
	svm2 = SRG.SVMRandomGaussian(rfc, params2, svm_type='LinearSVM')

	svm1.train(xs_train, ys_train)
	svm2.train(xs_train, ys_train)

	ys1 = svm1.predict(xs_test)
	ys2 = svm2.predict(xs_test)

	vis.visualize2d(rfc.getData(xs_train[:n]), rfc.getData(xs_train[n:]), show=False)
	vis.visualize2d(xs_train[:n], xs_train[n:])


	import IPython
	IPython.embed()


def testGaussian ():
	size = 2
	n = 20
	var = 1.0
	c = 1

	rn = 10000
	gammak=1.0

	xs_train, ys_train = generateGaussian(size, n, var, c)
	xs_test, ys_test = generateGaussian(size, n, var, c)

	rfc = SRG.RandomFeaturesConverter(dim=size, rn=rn, gammak=gammak)

	params1 = SVM.SVMParam(ktype='rbf')
	params2 = SVM.SVMParam(ktype='linear')

	svm1 = SVM.SVM(params1)#SRG.SVMRandomGaussian(rgf, params1, svm_type='SVM')
	svm2 = SRG.SVMRandomGaussian(rfc, params2, svm_type='SVM')

	svm1.train(xs_train, ys_train)
	svm2.train(xs_train, ys_train)

	ys1 = svm1.predict(xs_test)
	ys2 = svm2.predict(xs_test)

	xtrain_normal = [f for f,y in zip(xs_train, ys_train) if y == 1]
	xtrain_anomaly = [f for f,y in zip(xs_train, ys_train) if y == -1]

	vis.visualize2d(rfc.getData(xtrain_normal), rfc.getData(xtrain_anomaly), show=False)
	vis.visualize2d(xtrain_normal, xtrain_anomaly, show=False)
	vis.drawCircle((0,0), c)

	import IPython
	IPython.embed()

def testGaussian2 ():
	size = 2
	n = 30000
	var = 1.0
	c = 1

	rn = 1000
	gammak=1.0

	xs_train, ys_train = generateGaussian(size, n, var, c)
	xs_test, ys_test = generateGaussian(size, n, var, c)

	rfc = SRG.RandomFeaturesConverter(dim=size, rn=rn, gammak=gammak)

	params1 = SVM.SVMParam(ktype='rbf')
	params2 = SVM.SVMParam(ktype='linear')

	svm1 = SVM.SVM(params1)#SRG.SVMRandomGaussian(rgf, params1, svm_type='SVM')
	svm2 = SRG.SVMRandomGaussian(rfc, params2, svm_type='LinearSVM')

	tr_t1 = time.time()
	svm1.train(xs_train, ys_train)
	tr_t2 = time.time()
	svm2.train(xs_train, ys_train)
	tr_t3 = time.time()

	print("Training time for 1: %f"%(tr_t2-tr_t1))
	print("Training time for 2: %f"%(tr_t3-tr_t2))

	ts_t1 = time.time()
	ys1 = svm1.predict(xs_test)
	ts_t2 = time.time()
	ys2 = svm2.predict(xs_test)
	ts_t3 = time.time()

	print("Testing time for 1: %f"%(ts_t2-ts_t1))
	print("Testing time for 2: %f"%(ts_t3-ts_t2))

	xtrain_normal = [f for f,y in zip(xs_train, ys_train) if y == 1]
	xtrain_anomaly = [f for f,y in zip(xs_train, ys_train) if y == -1]

	# vis.visualize2d(rfc.getData(xtrain_normal), rfc.getData(xtrain_anomaly), show=False)
	# vis.visualize2d(xtrain_normal, xtrain_anomaly, show=False)
	# vis.drawCircle((0,0), c)
	grf = rfc.getFeatureGenerator()

	# K = np.zeros((n,n))
	# for i in range(n):
	# 	for j in range(n):
	# 		K[i,j] = K[j,i] = grf.RBFKernel(xs_train[i], xs_train[j])

	# XRF = np.array(rfc.getData(xs_train))
	# K2 = np.dot(XRF, XRF.T)

	# print K
	# print K2

	print ("Agreement: %f"%(sum(ys1==ys2)*1.0/len(ys1)))
	print ("Accuracy 1: %f"%(sum(ys1==ys_test)*1.0/len(ys1)))
	print ("Accuracy 2: %f"%(sum(ys2==ys_test)*1.0/len(ys1)))

	#print np.abs(K-K2).max()
	# import IPython
	# IPython.embed()


def testKernel():
	size = 1
	n = 20
	var = 1.0
	c = 1.5

	rn = 50000
	gammak=1.0

	xs_train, ys_train = generateGaussian(size, n, var, c)
	xs_test, ys_test = generateGaussian(size, n, var, c)

	rfc = SRG.RandomFeaturesConverter(dim=size, rn=rn, gammak=gammak)

	grf = rfc.getFeatureGenerator()

	K = np.zeros((n,n))
	for i in range(n):
		for j in range(n):
			K[i,j] = K[j,i] = grf.RBFKernel(xs_train[i], xs_train[j])

	XRF = np.array(rfc.getData(xs_train))
	K2 = np.dot(XRF, XRF.T)

	print K
	print K2

	print np.abs(K-K2).max()

def testOneClass():
	import os, os.path as osp
	#file_name = osp.join(os.getenv('HOME'), 'Research/AnomalyDetection/python/tmp.txt')
	file_name = osp.join(os.getenv('HOME'), 'Research/Data/GRE.out')
	hists, all_mtypes = parse.generateHistogramsFromFile(file_name, 2., 1., vals_only=True)

	k1,k2 = hists.keys()
	v1,v2 = hists[k1].keys()
	v3 = hists[k2].keys()[0]

	xs_train = hists[k1][v1]
	xs_test = hists[k2][v3]

	dim = len(xs_train[0])
	gammak = 1.0
	rn = 500

	rfc = SRG.RandomFeaturesConverter(dim=dim, rn=rn, gammak=gammak)

	params1 = SVM.SVMParam(ktype='rbf', verbose=True)
	params2 = SVM.SVMParam(ktype='linear', verbose=True)

	svm1 = SVM.OneClassSVM(params1)#SRG.SVMRandomGaussian(rgf, params1, svm_type='SVM')
	svm2 = SRG.SVMRandomGaussian(rfc, params2, svm_type='OneClassSVM')

	print ("Training")

	tr_t1 = time.time()
	svm1.train(xs_train, None)
	tr_t2 = time.time()
	svm2.train(xs_train, None)
	tr_t3 = time.time()

	print("Training time for 1: %f"%(tr_t2-tr_t1))
	print("Training time for 2: %f"%(tr_t3-tr_t2))

	ts_t1 = time.time()
	ys1 = svm1.predict(xs_test)
	ts_t2 = time.time()
	ys2 = svm2.predict(xs_test)
	ts_t3 = time.time()

	print("Testing time for 1: %f"%(ts_t2-ts_t1))
	print("Testing time for 2: %f"%(ts_t3-ts_t2))

	print ("Agreement: %f"%(sum(ys1==ys2)*1.0/len(ys1)))

	# xtrain_normal = [f for f,y in zip(xs_train, ys_train) if y == 1]
	# xtrain_anomaly = [f for f,y in zip(xs_train, ys_train) if y == -1]

	# vis.visualize2d(rfc.getData(xtrain_normal), rfc.getData(xtrain_anomaly), show=False)
	# vis.visualize2d(xtrain_normal, xtrain_anomaly, show=False)
	# vis.drawCircle((0,0), c)
	grf = rfc.getFeatureGenerator()

	import IPython
	IPython.embed()



def testRFFSine ():
	size = 2
	n = 1000
	var = 1.0
	c = 1

	rn = 1000
	gammak=1.0

	xs_train, ys_train = generateGaussian(size, n, var, c)
	xs_test, ys_test = generateGaussian(size, n, var, c)

	rfc1 = SRG.RandomFeaturesConverter(dim=size, rn=rn, gammak=gammak, sine=False)
	rfc2 = SRG.RandomFeaturesConverter(dim=size, rn=int(rn/2), gammak=gammak, sine=True)

	params1 = SVM.SVMParam(ktype='rbf')
	params2 = SVM.SVMParam(ktype='linear')
	params3 = SVM.SVMParam(ktype='linear')

	svm1 = SVM.SVM(params1)#SRG.SVMRandomGaussian(rgf, params1, svm_type='SVM')
	svm2 = SRG.SVMRandomGaussian(rfc1, params2, svm_type='LinearSVM')
	svm3 = SRG.SVMRandomGaussian(rfc2, params3, svm_type='LinearSVM')

	tr_t1 = time.time()
	svm1.train(xs_train, ys_train)
	tr_t2 = time.time()
	svm2.train(xs_train, ys_train)
	tr_t3 = time.time()
	svm3.train(xs_train, ys_train)
	tr_t4 = time.time()

	print("Training time for 1: %f"%(tr_t2-tr_t1))
	print("Training time for 2: %f"%(tr_t3-tr_t2))
	print("Training time for 2: %f"%(tr_t4-tr_t3))

	ts_t1 = time.time()
	ys1 = svm1.predict(xs_test)
	ts_t2 = time.time()
	ys2 = svm2.predict(xs_test)
	ts_t3 = time.time()
	ys3 = svm3.predict(xs_test)
	ts_t4 = time.time()

	print("Testing time for 1: %f"%(ts_t2-ts_t1))
	print("Testing time for 2: %f"%(ts_t3-ts_t2))
	print("Testing time for 2: %f"%(ts_t4-ts_t3))

	print ("Agreement 1,2: %f"%(sum(ys1==ys2)*1.0/len(ys1)))
	print ("Agreement 2,3: %f"%(sum(ys3==ys2)*1.0/len(ys1)))
	print ("Agreement 1,3: %f"%(sum(ys3==ys1)*1.0/len(ys1)))
	print ("Accuracy 1: %f"%(sum(ys1==ys_test)*1.0/len(ys1)))
	print ("Accuracy 2: %f"%(sum(ys2==ys_test)*1.0/len(ys1)))
	print ("Accuracy 3: %f"%(sum(ys3==ys_test)*1.0/len(ys1)))

	grf1 = rfc1.getFeatureGenerator()
	grf2 = rfc2.getFeatureGenerator()

	K1 = np.zeros((n,n))
	for i in range(n):
		for j in range(n):
			K1[i,j] = K1[j,i] = grf1.RBFKernel(xs_train[i], xs_train[j])

	XRF1 = np.array(rfc1.getData(xs_train))
	XRF2 = np.array(rfc2.getData(xs_train))
	K2 = np.dot(XRF1, XRF1.T)
	K3 = np.dot(XRF2, XRF2.T)

	print "Original/cos+unif:", np.abs(K1-K2).max()
	print "Cos+unif/cos+sin:", np.abs(K2-K3).max()
	print "Cos+sin/original:", np.abs(K1-K3).max()

	import IPython
	IPython.embed()


if __name__ == '__main__':
	#testBinary()
	# testGaussian()
	# testGaussian2()
	#testKernel()
	# testOneClass()
	testRFFSine()

	# import cProfile
	# cProfile.run('testGaussian2()')