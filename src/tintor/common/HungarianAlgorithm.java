package tintor.common;

import java.util.Arrays;

/**
 * An implementation of the Hungarian algorithm for solving the assignment
 * problem. An instance of the assignment problem consists of a number of
 * workers along with a number of jobs and a cost matrix which gives the cost of
 * assigning the i'th worker to the j'th job at position (i, j). The goal is to
 * find an assignment of workers to jobs so that no job is assigned more than
 * one worker and so that no worker is assigned to more than one job in such a
 * manner so as to minimize the total cost of completing the jobs.
 * <p>
 * 
 * An assignment for a cost matrix that has more workers than jobs will
 * necessarily include unassigned workers, indicated by an assignment value of
 * -1; in no other circumstance will there be unassigned workers. Similarly, an
 * assignment for a cost matrix that has more jobs than workers will necessarily
 * include unassigned jobs; in no other circumstance will there be unassigned
 * jobs. For completeness, an assignment for a square cost matrix will give
 * exactly one unique worker to each job.
 * <p>
 * 
 * This version of the Hungarian algorithm runs in time O(n^3), where n is the
 * maximum among the number of workers and the number of jobs.
 * 
 * @author Kevin L. Stern
 */
public final class HungarianAlgorithm {
	public final int[][] cost;
	private final int rows, cols, dim;
	private final int[] labelByWorker, labelByJob;
	private final int[] minSlackWorkerByJob;
	private final int[] minSlackValueByJob;
	private final int[] matchJobByWorker, matchWorkerByJob;
	private final int[] parentWorkerByCommittedJob;
	private final boolean[] committedWorkers;

	/**
	 * Construct an instance of the algorithm.
	 * 
	 * @param cost
	 *          the cost matrix, where matrix[i][j] holds the cost of assigning
	 *          worker i to job j, for all i, j. The cost matrix must not be
	 *          irregular in the sense that all rows must be the same length.
	 */
	public HungarianAlgorithm(int rows, int cols) {
		dim = Math.max(rows, cols);
		cost = new int[dim][dim];
		this.rows = rows;
		this.cols = cols;

		labelByWorker = new int[dim];
		labelByJob = new int[dim];
		minSlackWorkerByJob = new int[dim];
		minSlackValueByJob = new int[dim];
		committedWorkers = new boolean[dim];
		parentWorkerByCommittedJob = new int[dim];
		matchJobByWorker = new int[dim];
		matchWorkerByJob = new int[dim];
	}

	/**
	 * Compute an initial feasible solution by assigning zero labels to the
	 * workers and by assigning to each job a label equal to the minimum cost
	 * among its incident edges.
	 */
	protected void computeInitialFeasibleSolution() {
		Arrays.fill(labelByJob, Integer.MAX_VALUE);
		for (int w = 0; w < dim; w++)
			for (int j = 0; j < dim; j++)
				if (cost[w][j] < labelByJob[j])
					labelByJob[j] = cost[w][j];
	}

	/**
	 * Execute the algorithm.
	 * 
	 * @return the minimum cost matching of workers to jobs based upon the
	 *         provided cost matrix. A matching value of -1 indicates that the
	 *         corresponding worker is unassigned.
	 */
	public int[] execute() {
		Arrays.fill(labelByWorker, 0);
		Arrays.fill(labelByJob, 0);
		Arrays.fill(minSlackWorkerByJob, 0);
		Arrays.fill(minSlackValueByJob, 0);
		Arrays.fill(committedWorkers, false);
		Arrays.fill(parentWorkerByCommittedJob, 0);

		Arrays.fill(matchJobByWorker, -1);
		Arrays.fill(matchWorkerByJob, -1);

		/*
		 * Heuristics to improve performance: Reduce rows and columns by their
		 * smallest element, compute an initial non-zero dual feasible solution and
		 * create a greedy matching from workers to jobs of the cost matrix.
		 */
		reduce();
		computeInitialFeasibleSolution();
		greedyMatch();

		int w = fetchUnmatchedWorker();
		while (w < dim) {
			initializePhase(w);
			if (!executePhase())
				break;
			w = fetchUnmatchedWorker();
		}
		for (w = 0; w < rows; w++)
			if (matchJobByWorker[w] >= cols)
				matchJobByWorker[w] = -1;
		for (w = rows; w < dim; w++)
			matchJobByWorker[w] = -1;
		return matchJobByWorker;
	}

	/**
	 * Execute a single phase of the algorithm. A phase of the Hungarian algorithm
	 * consists of building a set of committed workers and a set of committed jobs
	 * from a root unmatched worker by following alternating unmatched/matched
	 * zero-slack edges. If an unmatched job is encountered, then an augmenting
	 * path has been found and the matching is grown. If the connected zero-slack
	 * edges have been exhausted, the labels of committed workers are increased by
	 * the minimum slack among committed workers and non-committed jobs to create
	 * more zero-slack edges (the labels of committed jobs are simultaneously
	 * decreased by the same amount in order to maintain a feasible labeling).
	 * <p>
	 * 
	 * The runtime of a single phase of the algorithm is O(n^2), where n is the
	 * dimension of the internal square cost matrix, since each edge is visited at
	 * most once and since increasing the labeling is accomplished in time O(n) by
	 * maintaining the minimum slack values among non-committed jobs. When a phase
	 * completes, the matching will have increased in size.
	 */
	protected boolean executePhase() {
		while (true) {
			int minSlackWorker = -1, minSlackJob = -1;
			int minSlackValue = Integer.MAX_VALUE;
			for (int j = 0; j < dim; j++)
				if (parentWorkerByCommittedJob[j] == -1)
					if (minSlackValueByJob[j] < minSlackValue) {
						minSlackValue = minSlackValueByJob[j];
						minSlackWorker = minSlackWorkerByJob[j];
						minSlackJob = j;
					}
			if (minSlackValue > 0)
				updateLabeling(minSlackValue);
			if (minSlackJob == -1) {
				for (int w = 0; w < rows; w++)
					matchJobByWorker[w] = -1;
				return false;
			}
			parentWorkerByCommittedJob[minSlackJob] = minSlackWorker;
			if (matchWorkerByJob[minSlackJob] == -1) {
				/*
				 * An augmenting path has been found.
				 */
				int committedJob = minSlackJob;
				int parentWorker = parentWorkerByCommittedJob[committedJob];
				while (true) {
					int temp = matchJobByWorker[parentWorker];
					match(parentWorker, committedJob);
					committedJob = temp;
					if (committedJob == -1)
						break;
					parentWorker = parentWorkerByCommittedJob[committedJob];
				}
				return true;
			} else {
				/*
				 * Update slack values since we increased the size of the committed
				 * workers set.
				 */
				int worker = matchWorkerByJob[minSlackJob];
				committedWorkers[worker] = true;
				for (int j = 0; j < dim; j++)
					if (parentWorkerByCommittedJob[j] == -1) {
						int slack = cost[worker][j] - labelByWorker[worker] - labelByJob[j];
						if (minSlackValueByJob[j] > slack) {
							minSlackValueByJob[j] = slack;
							minSlackWorkerByJob[j] = worker;
						}
					}
			}
		}
	}

	/**
	 * @return the first unmatched worker or {@link #dim} if none.
	 */
	protected int fetchUnmatchedWorker() {
		int w;
		for (w = 0; w < dim; w++)
			if (matchJobByWorker[w] == -1)
				break;
		return w;
	}

	/**
	 * Find a valid matching by greedily selecting among zero-cost matchings. This
	 * is a heuristic to jump-start the augmentation algorithm.
	 */
	protected void greedyMatch() {
		for (int w = 0; w < dim; w++)
			for (int j = 0; j < dim; j++)
				if (matchJobByWorker[w] == -1 && matchWorkerByJob[j] == -1
						&& cost[w][j] - labelByWorker[w] - labelByJob[j] == 0)
					match(w, j);
	}

	/**
	 * Initialize the next phase of the algorithm by clearing the committed
	 * workers and jobs sets and by initializing the slack arrays to the values
	 * corresponding to the specified root worker.
	 * 
	 * @param w
	 *          the worker at which to root the next phase.
	 */
	protected void initializePhase(int w) {
		Arrays.fill(committedWorkers, false);
		Arrays.fill(parentWorkerByCommittedJob, -1);
		committedWorkers[w] = true;
		for (int j = 0; j < dim; j++) {
			minSlackValueByJob[j] = cost[w][j] - labelByWorker[w] - labelByJob[j];
			minSlackWorkerByJob[j] = w;
		}
	}

	/**
	 * Helper method to record a matching between worker w and job j.
	 */
	protected void match(int w, int j) {
		matchJobByWorker[w] = j;
		matchWorkerByJob[j] = w;
	}

	/**
	 * Reduce the cost matrix by subtracting the smallest element of each row from
	 * all elements of the row as well as the smallest element of each column from
	 * all elements of the column. Note that an optimal assignment for a reduced
	 * cost matrix is optimal for the original cost matrix.
	 */
	protected void reduce() {
		for (int w = 0; w < dim; w++) {
			int min = Integer.MAX_VALUE;
			for (int j = 0; j < dim; j++)
				if (cost[w][j] < min)
					min = cost[w][j];
			for (int j = 0; j < dim; j++)
				cost[w][j] -= min;
		}
		int[] min = new int[dim];
		Arrays.fill(min, Integer.MAX_VALUE);
		for (int w = 0; w < dim; w++)
			for (int j = 0; j < dim; j++)
				if (cost[w][j] < min[j])
					min[j] = cost[w][j];
		for (int w = 0; w < dim; w++)
			for (int j = 0; j < dim; j++)
				cost[w][j] -= min[j];
	}

	/**
	 * Update labels with the specified slack by adding the slack value for
	 * committed workers and by subtracting the slack value for committed jobs. In
	 * addition, update the minimum slack values appropriately.
	 */
	protected void updateLabeling(int slack) {
		for (int w = 0; w < dim; w++)
			if (committedWorkers[w])
				labelByWorker[w] += slack;
		for (int j = 0; j < dim; j++)
			if (parentWorkerByCommittedJob[j] != -1)
				labelByJob[j] -= slack;
			else
				minSlackValueByJob[j] -= slack;
	}
}