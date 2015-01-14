import csv

def generateHistograms(data, all_mtypes, wsize, wsep):
	"""
	"""
	all_mtypes.sort()

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

				if next is False and t >= tbegin + wsep:
					tbegin = tbegin + wsep
					sidx = idx
					next = True

				if t > tend or idx == n-1:
					if idx == n-1:
						hist[m] += 1

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

def generateHistogramsFromFile(file_name, wsize, wsep):

	fh = open(file_name,'r')
	r = csv.reader(fh)

	data = {}
	all_mtypes = []

	start_t = -1

	for row in r:
		if len(r) != 4: continue

		ip, t, mtype, app = r
		if start_t == -1: start_t = t

		if ip not in data:
			data[ip] = {}
		if app not in data[ip]:
			data[ip][app] = []

		if m not in all_mtypes:
			all_mtypes.append(m)
		data[ip][app].append((t, mtype))
	fh.close()

	return generateHistograms(data, all_mtypes, wsize, wsep)


if __name == '__main__':
	