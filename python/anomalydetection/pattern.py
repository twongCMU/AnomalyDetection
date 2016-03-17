import numpy as np

class AnomalyPattern():

    @staticmethod
    def anomaly_pattern(observed, mean, stddev):

        assert(isinstance(mean, np.ndarray))
        assert(isinstance(stddev, np.ndarray))
        assert(isinstance(observed, np.ndarray))
        print "sizes",mean.size, observed.size
        assert(mean.size == stddev.size)
        assert(mean.size == observed.size)

        ret = list()

        # check if any histogram values are 2 or more stddev away from the mean
        for i in range(len(mean)):
            adj_stddev = stddev[i]
            if adj_stddev < 1.0:
                adj_stddev = 1.0

            if abs(observed[i]-mean[i]) >= 2*adj_stddev:
                ret.append(i)
        # if so, we return the pattern. If not, fall through to the next pattern
        if len(ret) > 0:
            return ret;

        # check if every single histogram value is between 1 stddev and 2 stddev
        all_are = True
        for i in range(len(mean)):
            adj_stddev = stddev[i]
            if adj_stddev < 1.0:
                adj_stddev = 1.0

            if abs(observed[i]-mean[i]) >= adj_stddev and \
                    abs(observed[i]-mean[i]) <= 2*adj_stddev:
                all_are = False
                
        if (all_are == True):
            ret.append(-1)
            return ret


        return ret
