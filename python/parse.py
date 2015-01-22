import csv

def generateHistograms(data, all_mtypes, start_t, wsize, wsep, vals_only=False):
	"""
	"""

	hists = {}
	for ip in data:
		hists[ip] = {}
		for app in data[ip]:
			hists[ip][app] = []
			n = len(data[ip][app])

			tbegin = start_t
			tend = start_t + wsize
			sidx = 0

			idx = sidx
			next = False

			hist = {m:0 for m in all_mtypes}

			while True:
				t, m = data[ip][app][idx]

				if (next is False) and (t >= tbegin + wsep):
					tbegin = tbegin + wsep
					sidx = idx
					next = True

				if t > tend or idx == n-1:
					if idx == n-1 and t <= tend:
						hist[m] += 1

					if vals_only:
						hists[ip][app].append([hist[mt] for mt in all_mtypes])
					else:
						hists[ip][app].append(hist)
					if not next:
						break
					else:
						tend = tbegin + wsize
						idx = sidx
						hist = {m:0 for m in all_mtypes}
						next = False
				else:
					hist[m] += 1
					idx += 1


	return hists

def generateHistogramsFromFile(file_name, wsize, wsep, vals_only=False):

	fh = open(file_name,'r')
	r = csv.reader(fh)

	data = {}
	all_mtypes = []

	start_t = -1

	for row in r:
		if len(row) != 4: continue

		ip, t, m, app = row
		if start_t == -1: start_t = float(t)

		if ip not in data:
			data[ip] = {}
		if app not in data[ip]:
			data[ip][app] = []

		if m not in all_mtypes:
			all_mtypes.append(m)
		data[ip][app].append((float(t), m))
	fh.close()

	all_mtypes.sort()
	return generateHistograms(data, all_mtypes, start_t, wsize, wsep, vals_only), all_mtypes


if __name__ == '__main__':
	import os, os.path as osp
	#file_name = osp.join(os.getenv('HOME'), 'Research/AnomalyDetection/python/tmp.txt')
	file_name = osp.join(os.getenv('HOME'), 'Research/Data/GRE.out')
	hists, all_mtypes = generateHistogramsFromFile(file_name, 2., 1., vals_only=True)