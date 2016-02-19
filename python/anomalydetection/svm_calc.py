import numpy as np
from sklearn.svm import OneClassSVM
from sklearn.metrics.pairwise import chi2_kernel
import time
from sklearn import cross_validation
from constants import *
from histograms import Histograms
from generic_calc import GenericCalc

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

        train_k = chi2_kernel(train_m)
        test_k = chi2_kernel(test_m, train_m)

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
