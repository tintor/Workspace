package tintor.common;

import java.util.Arrays;

public final class BipartiteMatching {
	// A DFS based recursive function that returns true if a
	// matching for vertex u is possible
	private static boolean bpm(boolean bpGraph[][], int worker, boolean seen[], int matchR[]) {
		int N = bpGraph[0].length;
		for (int job = 0; job < N; job++) {
			// If applicant u is interested in job v and v
			// is not visited
			if (bpGraph[worker][job] && !seen[job]) {
				seen[job] = true;

				// If job V is not assigned to an applicant OR
				// previously assigned applicant for job v (which
				// is matchR[v]) has an alternate job available.
				// Since v is marked as visited in the above line,
				// matchR[v] in the following recursive call will
				// not get job V again
				if (matchR[job] < 0 || bpm(bpGraph, matchR[job], seen, matchR)) {
					matchR[job] = worker;
					return true;
				}
			}
		}
		return false;
	}

	// Returns maximum number of matching from M workers to N jobs
	public static int maxBPM(boolean bpGraph[][]) {
		int M = bpGraph.length;
		int N = bpGraph[0].length;

		// An array to keep track of the applicants assigned to
		// jobs. The value of matchR[i] is the applicant number
		// assigned to job i, the value -1 indicates nobody is
		// assigned.
		int matchR[] = Array.ofInt(N, -1);
		boolean seen[] = new boolean[N];
		int assigned = 0;
		for (int worker = 0; worker < M; worker++) {
			Arrays.fill(seen, false);
			// Find if the applicant U can get a job
			if (bpm(bpGraph, worker, seen, matchR))
				assigned += 1;
		}
		return assigned;
	}
}