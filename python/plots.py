from __future__ import division
import numpy as np, numpy.random as nr, numpy.linalg as nlg
import time

import SVM, SVMRandomGaussian as SRG
import visualize as vis
import matplotlib.pyplot as plt

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


def testGaussian (size, ntr, nts, rn, var =1.0 , c=1.0, gammak=1.0, sine=True):

	xs_train, ys_train = generateGaussian(size, ntr, var, c)
	print sum(ys_train)
	# print [nlg.norm(f) for f in xs_train]
	#raw_input()
	xs_test, ys_test = generateGaussian(size, nts, var, c)

	rfc = SRG.RandomFeaturesConverter(dim=size, rn=rn, gammak=gammak, sine=sine)

	params1 = SVM.SVMParam(ktype='rbf')
	params2 = SVM.SVMParam(ktype='linear')

	svm1 = SVM.SVM(params1)
	svm2 = SRG.SVMRandomGaussian(rfc, params2, svm_type='LinearSVM')

	tr_t1 = time.time()
	svm1.train(xs_train, ys_train)
	tr_t2 = time.time()
	svm2.train(xs_train, ys_train)
	tr_t3 = time.time()

	tr1 = (tr_t2-tr_t1)
	tr2 = (tr_t3-tr_t2)

	print("Training time for 1: %f"%tr1)
	print("Training time for 2: %f"%tr2)

	ts_t1 = time.time()
	ys1 = svm1.predict(xs_test)
	ts_t2 = time.time()
	ys2 = svm2.predict(xs_test)
	ts_t3 = time.time()

	ts1 = (ts_t2-ts_t1)
	ts2 = (ts_t3-ts_t2)

	print("Testing time for 1: %f"%ts1)
	print("Testing time for 2: %f"%ts2)

	agreement = (sum(ys1==ys2)*1.0/nts)
	acc1 = (sum(ys1==ys_test)*1.0/nts)
	acc2 = (sum(ys2==ys_test)*1.0/nts)

	print ("Agreement: %f"%agreement)
	print ("Accuracy 1: %f"%acc1)
	print ("Accuracy 2: %f"%acc2)

	return (tr1, tr2), (ts1, ts2), (agreement, acc1, acc2)

def testGaussianSineCos (size, ntr, nts, rn, var =1.0 , c=1.0, gammak=1.0):

	xs_train, ys_train = generateGaussian(size, ntr, var, c)
	print sum(ys_train)
	# print [nlg.norm(f) for f in xs_train]
	#raw_input()
	xs_test, ys_test = generateGaussian(size, nts, var, c)

	rfc1 = SRG.RandomFeaturesConverter(dim=size, rn=int(rn/2), gammak=gammak, sine=True)
	rfc2 = SRG.RandomFeaturesConverter(dim=size, rn=rn, gammak=gammak, sine=False)

	params1 = SVM.SVMParam(ktype='linear')
	params2 = SVM.SVMParam(ktype='linear')

	svm1 = SRG.SVMRandomGaussian(rfc1, params1, svm_type='LinearSVM')
	svm2 = SRG.SVMRandomGaussian(rfc2, params2, svm_type='LinearSVM')

	tr_t1 = time.time()
	svm1.train(xs_train, ys_train)
	tr_t2 = time.time()
	svm2.train(xs_train, ys_train)
	tr_t3 = time.time()

	tr1 = (tr_t2-tr_t1)
	tr2 = (tr_t3-tr_t2)

	print("Training time for Cos + Sine: %f"%tr1)
	print("Training time for Cos + Unif: %f"%tr2)

	ts_t1 = time.time()
	ys1 = svm1.predict(xs_test)
	ts_t2 = time.time()
	ys2 = svm2.predict(xs_test)
	ts_t3 = time.time()

	ts1 = (ts_t2-ts_t1)
	ts2 = (ts_t3-ts_t2)

	print("Testing time for Cos + Sine: %f"%ts1)
	print("Testing time for Cos + Unif: %f"%ts2)

	agreement = (sum(ys1==ys2)*1.0/nts)
	acc1 = (sum(ys1==ys_test)*1.0/nts)
	acc2 = (sum(ys2==ys_test)*1.0/nts)

	print ("Agreement: %f"%agreement)
	print ("Accuracy Cos + Sine: %f"%acc1)
	print ("Accuracy Cos + Unif: %f"%acc2)

	return (tr1, tr2), (ts1, ts2), (agreement, acc1, acc2)



def plotRangeN (size, ntr_range, nts_range, rn, var=1.0, c=1.0, gammak=1.0, sine=True):

	tr1s = []
	ts1s = []
	tr2s = []
	ts2s = []

	agg = 0.
	acc1 = 0.
	acc2 = 0.

	if sine:
		rn = int(rn/2)

	if not isinstance(ntr_range, list):
		ntr_range = [ntr_range]*(len(nts_range))
	if not isinstance(nts_range, list):
		nts_range = [nts_range]*(len(ntr_range))

	for ntr, nts in zip(ntr_range, nts_range):
		print '\n ntr:' , ntr, ' nts:', nts
		tr,ts,ag = testGaussian (size, ntr, nts, rn, var , c, gammak, sine)
		tr1s.append(tr[0])
		tr2s.append(tr[1])
		ts1s.append(ts[0])
		ts2s.append(ts[1])

		agg += ag[0]*nts
		acc1 += ag[1]*nts
		acc2 += ag[2]*nts

	agg /= sum(nts_range)
	acc1 /= sum(nts_range)
	acc2 /= sum(nts_range)

	print("Average agreement: %f"%agg)
	print("Average accuracy of 1: %f"%acc1)
	print("Average accuracy of 2: %f"%acc2)

	f1 = plt.figure()
	plt.plot(ntr_range, tr1s, label='svm1 tr')
	plt.plot(ntr_range, tr2s, label='svm2 tr')
	plt.title('Training: d = %d, rn = %d'%(size, rn))
	plt.legend()
	plt.xlabel('Training points')
	plt.xlabel('Time')
	f2 = plt.figure()
	plt.plot(nts_range, ts1s, label='svm1 ts')
	plt.plot(nts_range, ts2s, label='svm2 ts')
	plt.title('Testing: d = %d, rn = %d'%(size, rn))
	plt.legend()
	plt.xlabel('Testing points')
	plt.xlabel('Time')
	plt.show()


def plotAccuracyRNSineCos (size, ntr, nts, rn_range, var=1.0, c=1.0, gammak=1.0):

	agg = []
	acc1 = []
	acc2 = []

	tr1s = []
	ts1s = []
	tr2s = []
	ts2s = []

	for rn in rn_range:
		print '\n rn:', rn
		tr,ts,ag = testGaussianSineCos (size, ntr, nts, rn, var, c, gammak)

		agg.append(ag[0])
		acc1.append(ag[1])
		acc2.append(ag[2])

		tr1s.append(tr[0])
		tr2s.append(tr[1])
		ts1s.append(ts[0])
		ts2s.append(ts[1])

	print("Average agreement: %f"%(sum(agg)/len(rn_range)))
	print("Average accuracy of Cos + Sine: %f"%(sum(acc1)/len(rn_range)))
	print("Average accuracy of Cos + Unif: %f"%(sum(acc2)/len(rn_range)))

	f1 = plt.figure()
	plt.plot(	rn_range, agg, label='Agreement')
	plt.plot(rn_range, acc1, label='Acc cos/sin')
	plt.plot(rn_range, acc2, label='Acc cos/unif')
	plt.title('Accuracy: d = %d, ntr = %d, nts = %d'%(size, ntr, nts))
	plt.xlabel('Random features')
	plt.ylabel('Fraction')
	plt.legend(loc=4)
	f2 = plt.figure()
	plt.plot(rn_range, tr1s, label='sin/cos tr')
	plt.plot(rn_range, ts1s, label='sin/cos ts')
	plt.plot(rn_range, tr2s, label='cos/unif tr')
	plt.plot(rn_range, ts2s, label='cos/unif ts')
	plt.title('Time: d = %d, ntr = %d, nts = %d'%(size, ntr, nts))
	plt.legend(loc=4)
	plt.xlabel('Random features')
	plt.ylabel('Time')
	plt.show()



def plotAccuracyRN (size, ntr, nts, rn_range, var=1.0, c=1.0, gammak=1.0, sine=True):

	agg = []
	acc1 = []
	acc2 = []

	tr1s = []
	ts1s = []
	tr2s = []
	ts2s = []

	if sine:
		rn_range = [int(rn/2) for rn in rn_range]

	for rn in rn_range:
		print '\n rn:', rn
		tr,ts,ag = testGaussian (size, ntr, nts, rn, var, c, gammak, sine)

		agg.append(ag[0])
		acc1.append(ag[1])
		acc2.append(ag[2])

		tr1s.append(tr[0])
		tr2s.append(tr[1])
		ts1s.append(ts[0])
		ts2s.append(ts[1])

	print("Average agreement: %f"%(sum(agg)/len(rn_range)))
	print("Average accuracy of 1: %f"%(sum(acc1)/len(rn_range)))
	print("Average accuracy of 2: %f"%(sum(acc2)/len(rn_range)))

	f1 = plt.figure()
	plt.plot(rn_range, agg, label='Agreement')
	plt.plot(rn_range, acc1, label='Acc kernel')
	plt.plot(rn_range, acc2, label='Acc linear')
	plt.title('Accuracy: d = %d, ntr = %d, nts = %d'%(size, ntr, nts))
	plt.xlabel('Random features')
	plt.ylabel('Fraction')
	plt.legend(loc=4)
	f2 = plt.figure()
	plt.plot(rn_range, tr1s, label='svm1 tr')
	plt.plot(rn_range, ts1s, label='svm1 ts')
	plt.plot(rn_range, tr2s, label='svm2 tr')
	plt.plot(rn_range, ts2s, label='svm2 ts')
	plt.title('Time: d = %d, ntr = %d, nts = %d'%(size, ntr, nts))
	plt.legend(loc=4)
	plt.xlabel('Random features')
	plt.ylabel('Time')
	plt.show()




if __name__ == '__main__':
	ntr_min = 10
	ntr_max = 30000
	ntr = 25
	
	nts_min = 10
	nts_max = 30000
	nts = 25

	size = 8
	c = 2.5
	rn = 2000

	sine=True

	ntr_range = np.squeeze(np.linspace(ntr_min, ntr_max, ntr).astype(int)).tolist()
	nts_range = np.squeeze(np.linspace(nts_min, nts_max, nts).astype(int)).tolist()

	#plotRangeN(size, ntr_range, nts_range, rn = rn, c = c, sine=sine)
	#rn_range=[2, 5, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 150, 200, 300, 400, 500, 750, 1000, 2000, 5000]
	rn_range = np.squeeze(np.linspace(2, 5000, 15).astype(int)).tolist()
	#rn_range.extend([10000, 20000, 30000, 40000, 50000, 60000, 70000])
	#plotAccuracyRNSineCos(size=size, ntr=10000, nts=10000, rn_range=rn_range, c=c)