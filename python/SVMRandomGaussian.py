from __future__ import division
import numpy as np, numpy.random as nr, numpy.linalg as nlg

import gaussianRandomFeatures as grf

class SVMRandomGaussian:

	def __init__(self, fs, rn, gammak, feature_generator=None):
		"""
		Assumes fs is a list of features to be projected into fourier space.
		"""

		self.dim = len(fs[0])
		self.rn = rn
		self.fs = fs

		self.rfs = []

		if feature_generator is None:
			self.feature_generator = gff.gaussianRandomFeatures(self.dim, self.rn, self.gammak)
		else: self.feature_generator = feature_generator

	def getFeatureGenerator(self):
		"""
		Get stored feature generator.
		"""
		return self.feature_generator

	def getData (self):
		"""
		Gets the projected features.
		"""
		if not self.rfs:
			for f in self.fs:
				self.rfs.append(self.feature_generator.computeRandomFeatures(f))

		return self.rfs