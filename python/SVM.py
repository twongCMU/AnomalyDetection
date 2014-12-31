import sklearn.svm as svm
import numpy as np
"""
One class SVMs from sci-kit learn.
"""

class SVMParam:
	def __init__(self, 	ktype='linear', nu=0.2, gamma=1.0, 
						C=1.0, dual=False, loss='l2', penalty='l2'):
		self.kernel_type = ktype
		self.nu = nu
		self.gamma = gamma

		self.C = C
		self.dual = dual
		self.loss = loss
		self.penalty = penalty


class SVM(object):
	def __init__ (self, param=None):
		if param is None:
			param = SVMParam()

		self.setParam(param)

	def setParam(self, param):
		self.param = param
		self.model = svm.NuSVC(	kernel=self.param.kernel_type, 
						nu=self.param.nu,
						gamma=self.param.gamma)

		self.trained = False

	def train (self, X, Y):
		"""
		X --> list of features
		Y --> list of +1/-1

		Train SVM with X as input and Y as output.
		"""
		self.n_samples = len(X)
		self.n_features = len(X[0])

		X = np.array(X)
		Y = np.array(Y)
		assert (Y**2 == 1).all()

		self.model.fit(X, Y)
		self.trained = True

	def predict(self, X):
		"""
		Prediction for list of features X.
		"""
		if self.trained == False:
			print("Warning: SVM is untrained.")
			return None

		return self.model.predict(np.atleast_2d(X))

	def reset(self, param=None):
		if param is None:
			param = self.param

		self.setParam(param)


class LinearSVM(SVM):
	def __init__ (self, param=None):
		if param is None:
			param = SVMParam(ktype='linear')
	
		super(SVM, self).__init__(param)
		

	def setParam(self, param):
		assert param.kernel_type == 'linear'

		self.param = param
		self.model = svm.LinearSVC(	C=self.param.C, 
									dual=self.param.dual,
									gamma=self.param.gamma,
									loss=self.param.loss,
									penalty=self.param.penalty)
		self.trained = False	