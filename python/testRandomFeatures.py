from __future__ import division
import numpy as np, numpy.random as nr, numpy.linalg as nlg

np.set_printoptions(suppress=True, precision=3)

ws = []
bs = []

def computeGaussianFourierFeatures (hist):
	"""
	Compute gaussian random features for hist.
	"""
	hist = np.atleast_1d(np.squeeze(hist))
	d = hist.shape[0]	

	assert ws and bs

	n = len(ws)
	f = [np.sqrt(2.0/n)*np.cos(w.dot(hist) + b) for w,b in zip(ws,bs)]

	return np.array(f)


def RBFKernel (x,y):
	"""
	Compute RBF Kernel between x and y.
	"""
	return np.exp(-nlg.norm(x-y)**2/(2*sigmak**2))


if __name__ == '__main__':

	s = 5
	d = 1
	n = 300

	sigmak = 5.0
	sigmap = 1/sigmak

	mean = np.zeros(d)
	cov = np.eye(d)*sigmap**2
	ws = [nr.multivariate_normal(mean, cov) for _ in range(n)]
	bs = [nr.random()*2*np.pi for _ in range(n)]

	x = nr.randint(s,size=d)
	y = nr.randint(s,size=d)
	test_n = 20
	mval = 0
	for _ in range(test_n):
		# x = nr.randn(10)
		# y = nr.randn(10)
		x = nr.randint(s,size=d)
		y = nr.randint(s,size=d)
		kval = RBFKernel (x,y)

		x_f = computeGaussianFourierFeatures(x)
		y_f = computeGaussianFourierFeatures(y)
		fval = x_f.dot(y_f)

		mval = max(mval, abs(kval-fval))

		print "kval: %.6f \tfval: %.6f\n"%(kval, fval)

	print "Max difference:", mval