package org.autonlab.anomalydetection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import libsvm.svm_node;

import org.javatuples.Pair; //Tuples, Pair
import org.apache.commons.math3.distribution.MultivariateNormalDistribution;
import org.apache.commons.math3.distribution.UniformRealDistribution;

import com.savarese.spatial.GenericPoint;


/**
 * @author sibiv
 * 
 * This class is for creating random Gaussian Fourier Features to approximate the RBF Kernel.
 * Given a list of histograms, this returns a list of svm_nodes which have been randomly created.
 * Eventually, a linear SVM will be used given these features. 
 *
 */
public class GaussianFourierFeatures implements Runnable {
	// Setting this to 1 could make things really slow
	// but it's useful to disable occasionally to see if
	// it produces similar results
	// Note that there is randomness in cross-validation
	// so it might not produce identical results every time
	static final int DISABLE_CACHE = 0;

	int _threadCount = 0;
	Thread[] _threadArray = null;

	// These are used by all related threads. Volatile variables are modified by threads
	ArrayList<Pair<Integer, GenericPoint<Integer>>> _histograms = null;
	ArrayList<Pair<Integer, GenericPoint<Integer>>> _histogramsB = null;
	int _D = 0; // The number of random features 
	volatile svm_node _retNode[][] = null;
	volatile Lock _retNodeLock = null;

	// cache a GenericPoint histogram to its pre-computed feature map as an Integer index into _retNode
	// We want to ensure that each histogram gets mapped to the same feature vector -- I think.
	volatile HashMap<GenericPoint<Integer>, Integer> _retNodeRowCache = null;

	/**
	 * Convert an ArrayList of histograms into a matrix of svm_nodes that can be passed into the svm library
	 * This is done in parallel. This library can also be instantiated multiple times in parallel
	 *
	 * @param histograms The ArrayList of histograms that we're 
	 * @param threadCount The number of threads to use to perform the computation
	 * @param D The number of random fourier features
	 * @param threadCount if svm_type is svm_parameter.PRECOMPUTED, use this many threads to apply kernel. Otherwise ignore value
	 */
	public GaussianFourierFeatures(ArrayList<Pair<Integer, GenericPoint<Integer>>> histograms, int D, int threadCount) {
		_threadCount = threadCount;
		_threadArray = new Thread[_threadCount];
		_histograms = histograms;
		_D = D;
		_retNode = new svm_node[histograms.size()][];
		_retNodeRowCache = new HashMap();

//		if (_svm_type != svm_parameter.PRECOMPUTED) {
//			_threadCount = 1;
//		}

		for (svm_node[] svmNodeArr : _retNode) {
			svmNodeArr = null;
		}
		_retNodeLock = new ReentrantLock();

		for (int i = 0; i < _threadCount; i++) {
			_threadArray[i] = new Thread(new GaussianFourierFeatures(_retNodeLock, _retNode, _histograms, _D, _retNodeRowCache));
			_threadArray[i].start();
		}   
	}

	public GaussianFourierFeatures(Lock retNodeLock, svm_node[][] retNode, ArrayList<Pair<Integer, GenericPoint<Integer>>> histograms, int _D, HashMap<GenericPoint<Integer>, Integer> rowCache) {
		_retNodeLock = retNodeLock;
		_retNode = retNode;
		_histograms = histograms;
		_retNodeRowCache = rowCache;
	}

	/**
	 * ??? otherwise we simply reformat the data to D+1xN so that libsvm can handle it
	 * (D = number of dimensions of each histogram, N = number of histograms)
	 */
	public void run() {
		int index = 0;
		int cache_hit = 0;
		int cache_work = 0;
		/* 
		 * _retNode contains the processed data so we synchronize on
		 *  that.  The rows of _retNode represent the processed output
		 *  of the _histograms at the same index. Any row that is null
		 *  has yet to be processed
		 */
		while (index < _histograms.size()) {
			GenericPoint<Integer> oneHist = null;

			// look for the first unprocessed output row or decide that we're done
			_retNodeLock.lock();
			while (index < _histograms.size() && _retNode[index] != null) {
				index++;
			}
			if (index < _histograms.size() && _retNode[index] == null) {
				oneHist = _histograms.get(index).getValue1();

				Integer cacheIndex = _retNodeRowCache.get(oneHist);
				if (cacheIndex != null) {
					cache_hit++;
					// We've already computed this feature vector before
					_retNode[index] = _retNode[cacheIndex].clone();  

					_retNodeLock.unlock();
					continue;
				}
				else {
					_retNode[index] = new svm_node[_D];
				}

			}
			_retNodeLock.unlock();

			if (index >= _histograms.size()) {
				break;
			}

			// TODO: create random fourier features
			for (int j = 0; j < oneHist.getDimensions(); j++) {
				_retNode[index][j] = new svm_node();
				_retNode[index][j].index = j+1;
				_retNode[index][j].value = oneHist.getCoord(j);
			}

			index++;
		}

		System.out.println("Cache stats: hit: " + cache_hit + " work: " + cache_work);
	}

	/**
	 * Wait for all threads to finish then return the data
	 *
	 * @return the svm_node matrix representing the kernel-processed histograms
	 */
	public svm_node[][] getData() {
		int i;
		for (i = 0; i < _threadCount; i++) {
			try {
				_threadArray[i].join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		return _retNode;
	}

	
	/**
	 * Generate the random Fourier features to approximate the Gaussian Kernel
	 * For approximating with low-dim features: http://www.eecs.berkeley.edu/~brecht/papers/07.rah.rec.nips.pdf
	 *
	 * Assuming the Gaussian kernel has gamma=1.
	 *
	 * @param histX the histogram
	 * @param D the number of features
	 * 
	 * @return the feature array
	 */
	public double gaussianFourierFeatures(GenericPoint<Integer> hist, int D) {
		double res = 0.0;

		for (int i = 0; i < hist.getDimensions(); i++) {
			res += Math.pow(hist.getCoord(i) - hist.getCoord(i), 2);
		}
		return Math.exp(-D*res);
	}

}
