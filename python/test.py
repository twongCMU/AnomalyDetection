from __future__ import division
import numpy as np, numpy.random as nr, numpy.linalg as nlg

import SVM, SVMRandomGaussian as SRG
import visualize as vis

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
	params2 = SVM.SVMParam(ktype='linear')

	svm1 = SVM.SVM(params1)#SRG.SVMRandomGaussian(rgf, params1, svm_type='SVM')
	svm2 = SRG.SVMRandomGaussian(rfc, params2, svm_type='SVM')

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


if __name__ == '__main__':
	#testBinary()
	testGaussian()
	#testKernel()