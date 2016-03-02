import numpy as np
from sklearn.svm import OneClassSVM
from sklearn.svm import SVC
from sklearn.svm import LinearSVC
from sklearn.multiclass import OneVsRestClassifier
from sklearn.metrics.pairwise import chi2_kernel
import time
from sklearn import cross_validation
from constants import *
from histograms import Histograms
from generic_calc import GenericCalc
from sklearn import preprocessing

class SVMCalc(GenericCalc):

    @staticmethod
    def test(train_h, test_h, train_start_sec=None, train_end_sec=None,
             train_from_start_min=None, train_from_end_min=None,
             train_drop_start_min=None, train_drop_end_min=None,
             test_start_sec=None, test_end_sec=None,
             test_drop_start_min=None, test_drop_end_min=None,
             test_from_start_min=None, test_from_end_min=None):
        """
        TODO: the edges of the dataspace should be dropped
        train_h and test_h are the Histogram() for training
        data and test data

        for start_sec, end_sec, from_start_min, from_end_min see
        histograms.get_histograms()
        """
        assert(isinstance(train_h, Histograms))
        assert(isinstance(test_h, Histograms))

        # These histograms might have different features or they're in 
        # different orders. Generate a union of them
        train_feat = train_h.get_features()
        test_feat = test_h.get_features()
        feat_uniq = dict()
        for f in train_feat + test_feat:
            feat_uniq[f] = 0
        sort_feat = sorted(feat_uniq.keys())

        train_m = train_h.get_histograms(features=sort_feat,
                                         start_sec = train_start_sec,
                                         end_sec = train_end_sec,
                                         from_start_min = train_from_start_min,
                                         from_end_min = train_from_end_min,
                                         drop_start_min = train_drop_start_min,
                                         drop_end_min = train_drop_end_min)
        test_m = test_h.get_histograms(features=sort_feat,
                                       start_sec = test_start_sec,
                                       end_sec = test_end_sec,
                                       from_start_min = test_from_start_min,
                                       from_end_min = test_from_end_min,
                                       drop_start_min = test_drop_start_min,
                                       drop_end_min = test_drop_end_min)


        scaler = preprocessing.MaxAbsScaler().fit(train_m)
        train_m_s = scaler.transform(train_m)
        test_m_s = scaler.transform(test_m)

        train_k = chi2_kernel(train_m_s)
        print "XYZ",test_m_s
        test_k = chi2_kernel(test_m_s, train_m_s)

        if train_h._nu == -1:
            best_nu = SVMCalc._cross_validate(train_k)
            assert best_nu > 0
            assert best_nu <= 1
            train_h._nu = best_nu

        clf = OneClassSVM(kernel="precomputed", nu=train_h._nu)

        clf.fit(train_k)

        #positive = normal, negative = anomaly
        res = clf.decision_function(test_k)
        return res

    @staticmethod
    def _generate_nu_list(nu_dict, nu_start_pow_low, nu_start_pow_high):
        for nu_base in NU_BASE_LIST:
            for nu_pow in range(nu_start_pow_low, nu_start_pow_high):
                new_nu = nu_base * pow(10, nu_pow)
                if new_nu not in nu_dict and \
                        new_nu > 0 and new_nu <=1:
                    nu_dict[new_nu] = -1
        return nu_dict

    @staticmethod
    def _cross_validate(train_k):
        """
        if using precomputed kernel, k_train is the precomputed output
        not the orignal data
        """
        target = np.zeros(train_k.shape[0])

        # mark all of these as normal data
        target.fill(1)

        best_nu = -1
        best_pct = -1

        nu_dict = dict()
        nu_start_pow_high = NU_START_POW_HIGH
        nu_start_pow_low = NU_START_POW_LOW

        # If the nu we select is on the edge of the list of possible nu values
        # we grow the list of nu values and try again. Since nu values are
        # bounded by (0,1] we can only grow so much. We break out if we run
        # out of useful nu so we don't get stuck in a loop
        while len(nu_dict.keys()) == 0 or best_nu == min(nu_dict.keys()) or \
                best_nu == max(nu_dict.keys()):

            nu_count = len(nu_dict.keys())
            nu_dict = SVMCalc._generate_nu_list(nu_dict, nu_start_pow_low,
                                            nu_start_pow_high)
            assert(len(nu_dict.keys()) > 0)
            # so future loops will expand generate_nu_list
            nu_start_pow_high += NU_EXPAND_INCREMENT
            nu_start_pow_low -= NU_EXPAND_INCREMENT

            if nu_count == len(nu_dict.keys()):
                # this means generate_nu_list did now grow the list any
                # more, probably because we hit the (0, 1] bound
                break
            for nu_try in nu_dict:
                if nu_dict[nu_try] != -1:
                    continue

                clf = OneClassSVM(kernel="precomputed", nu=nu_try)
                kfold = cross_validation.KFold(train_k.shape[1], n_folds=4)

                # this scoring param was arbitrarily selected
#http://scikit-learn.org/stable/modules/model_evaluation.html#scoring-parameter
                s = cross_validation.cross_val_score(clf, train_k,
                                                     target,
                                                     scoring="f1",
                                                     cv=kfold, n_jobs=-1)
                mean = s.mean()
                print "nu %0.5f %0.8f (+/- %0.2f)" % (nu_try, s.mean(),s.std()*2)
                if mean > best_pct:
                    best_pct  = mean
                    best_nu = nu_try

                nu_dict[nu_try] = mean


        for k in sorted(nu_dict):
            print "result:",k,nu_dict[k]
        print "best is ",best_nu
        # we don't want to ruin the user experience by asserting 
        # inappropriately but if cross validation returns 0% correct
        # then something is broken
        assert(nu_dict[best_nu] > 0.01) #cross validate returned 0% correct
        return best_nu

    @staticmethod
    def onevsall(anomalies, observed):
        dims = (0,0);
        all_h = None
        labels = None
        next_label = 0;
        # build a giant matrix of all anomaly data and make the labels
        # The labels matrix is samples x n_classes with a 1 if that sample
        # is in that class and a 0 if not. It is currently not sparse but
        # should eventually be
        label_count = len(anomalies.keys())

        for k in sorted(anomalies.keys()):
            if all_h is None:
                all_h = anomalies[k].get_histograms()
                for r in range(all_h.shape[0]):
                    row_label = np.zeros(label_count)
                    row_label[next_label] = 1
                    if labels is None:
                        labels = row_label
                    else:
                        labels = np.vstack((labels,row_label))
                next_label = 1
            else:
                #all_h.insert(anomalies[k].get_histograms(), axis=0)
                new_h = anomalies[k].get_histograms()
                all_h = np.vstack([all_h, new_h])
                for r in range(new_h.shape[0]):
                    row_label = np.zeros(label_count)
                    row_label[next_label] = 1
                    labels = np.vstack((labels,row_label))
                next_label += 1

        #SVC is quadratic with the number of samples and a dataset of 10k+ is hard
        #support vector classification
        #I used this because an example I found did. We should find out if there is
                # a better one
        clf = OneVsRestClassifier(SVC(probability=True), n_jobs = -1)
        scaler = preprocessing.MaxAbsScaler().fit(all_h)
        all_m_s = scaler.transform(all_h)

        ## fix this:
        # the input is an array of one data point but the algo
        # takes in an array of data points (array of array)
        # so either we need to call this functino that way
        # and allow for multiple datapoints to be tested at once
        # or know to wrap it in here
        # I think we'll want to do the former: when running a test, pass
        # all anomalies in here at once to save having to rebuild the
        # labels matrix
        observed_m_s = scaler.transform([observed])

        clf.fit(all_m_s, labels)

        # now test the new anomaly against the classifier
        # this seems to work but with this quirk: if two training classes have similar data
        # and is tested with an observed data that is similar to both, this reports neither as being matches
        # so the different classes influence each other  in that they split the 
        # vote if they're the same training data rather than simply maching both
        # as independent comparisons



        return clf.predict_proba(observed_m_s)

