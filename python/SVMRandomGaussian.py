from __future__ import division
import numpy as np, numpy.random as nr, numpy.linalg as nlg

import gaussianRandomFeatures as grf
import SVM

class RandomFeaturesConverter:

	def __init__(self, dim, rn, gammak, feature_generator=None):
		"""
		dim 	--> dimension of input space
		rn  	--> number of random features
		gammak 	--> bandwidth of rbf kernel
		"""

		self.dim = dim
		self.rn = rn
		self.gammak = gammak

		if feature_generator is None:
			self.feature_generator = grf.GaussianRandomFeatures(self.dim, self.rn, self.gammak)
		else: self.feature_generator = feature_generator

	def getFeatureGenerator(self):
		"""
		Get stored feature generator.
		"""
		return self.feature_generator

	def getData (self, fs):
		"""
		Gets the projected features.
		"""
		assert len(fs[0]) == self.dim
		rfs = []
		for f in fs:
			rfs.append(self.feature_generator.computeRandomFeatures(f))

		return rfs

class SVMRandomGaussian:

	def __init__(self, rfc, params, svm_type='LinearSVM'):
		self.rfc = rfc

		self.svm = None
		if svm_type=='LinearSVM':
			self.svm = SVM.LinearSVM(params)
		elif svm_type=='SVM':
			self.svm = SVM.SVM(params)
		else:
			raise NotImplementedError('SVM type %s not implemented.'%svm_type)


	def setParam(self, param):
		self.svm.setParam(param)

	def train (self, X, Y):
		"""
		X --> list of features
		Y --> list of +1/-1

		Train SVM with X as input and Y as output.
		"""
		XR = self.rfc.getData(X)
		self.svm.train(XR,Y)

	def predict(self, X):
		"""
		Prediction for list of features X.
		"""
		XR = self.rfc.getData(X)
		return self.svm.predict(XR)

	def reset(self, param=None):
		self.svm.reset(param)