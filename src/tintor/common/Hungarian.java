package tintor.common;

import java.util.Arrays;

/**
 * An implementation of the O(n^3) Hungarian method for the minimum cost assignment problem
 * (maximum value matching can be computed by subtracting each value from the minimum value).
 *
 * It is assumed that the matrix is SQUARE. Code to ensure this could be easily added to the constructor.
 *
 * new Hungarian(costMatrix).execute() returns a 2d array,
 * with result[i][0] being the row index assigned to the result[i][1] column index (for assignment i).
 *
 * This method uses O(n^3) time (or at least, it should) and O(n^2) memory; it is
 * probably possible to reduce both computation and memory usage by constant factors using a few more tricks.
 */
public final class Hungarian {
	private final int dim;
	public int[][] costs;

	private boolean[][] primes;
	private boolean[][] stars;
	private boolean[] rowsCovered;
	private boolean[] colsCovered;
	private int[] result;
	private int[] primeLocations;
	private int[] starLocations;

	// Note: costs must be range [0, Integer.MAX_VALUE / 2]
	public Hungarian(int dim) {
		this.dim = dim;
		if (dim > Short.MAX_VALUE)
			throw new IllegalArgumentException();
		costs = new int[dim][dim];

		primes = new boolean[dim][dim];
		stars = new boolean[dim][dim];
		rowsCovered = new boolean[dim];
		colsCovered = new boolean[dim];
		result = new int[dim];
		primeLocations = new int[dim * 2];
		starLocations = new int[dim * 2];
	}

	static int makeLocation(int row, int col) {
		return (row << 16) | col;
	}

	static int rowFromLocation(int loc) {
		return loc >> 16;
	}

	static int colFromLocation(int loc) {
		return (loc << 16) >> 16;
	}

	public int[] execute() {
		resetPrimes();
		subtractRowColMins();
		findStars(); // O(n^2)
		resetCovered(); // O(n);
		coverStarredZeroCols(); // O(n^2)

		while (!allColsCovered()) {
			int primedLocation = primeUncoveredZero(); // O(n^2)

			// It's possible that we couldn't find a zero to prime, so we have to induce some zeros so we can find one to prime
			if (primedLocation == makeLocation(-1, -1)) {
				minUncoveredRowsCols(); // O(n^2)
				primedLocation = primeUncoveredZero(); // O(n^2)
			}

			// is there a starred 0 in the primed zeros row?
			int primedRow = rowFromLocation(primedLocation);
			int starCol = findStarColInRow(primedRow);
			if (starCol != -1) {
				// cover the row of the primedLocation and uncover the star column
				rowsCovered[primedRow] = true;
				colsCovered[starCol] = false;
			} else { // otherwise we need to find an augmenting path and start over.
				augmentPathStartingAtPrime(primedLocation);
				resetCovered();
				resetPrimes();
				coverStarredZeroCols();
			}
		}

		return starsToAssignments(); // O(n^2)

	}

	// the starred 0's in each column are the assignments. O(n^2)
	public int[] starsToAssignments() {
		for (int j = 0; j < dim; j++)
			result[j] = findStarRowInCol(j); // O(n)
		return result;
	}

	public void resetPrimes() {
		for (int i = 0; i < dim; i++)
			Arrays.fill(primes[i], false);
	}

	public void resetCovered() {
		Arrays.fill(rowsCovered, false);
		Arrays.fill(colsCovered, false);
	}

	// get the first zero in each column, star it if there isn't already a star in that row
	// cover the row and column of the star made, and continue to the next column. O(n^2)
	public void findStars() {
		resetCovered();
		for (int i = 0; i < dim; i++)
			Arrays.fill(stars[i], false);

		for (int j = 0; j < dim; j++) {
			for (int i = 0; i < dim; i++)
				if (costs[i][j] == 0 && !rowsCovered[i] && !colsCovered[j]) {
					stars[i][j] = true;
					rowsCovered[i] = true;
					colsCovered[j] = true;
					break;
				}
		}
	}

	/*
	* Finds the minimum uncovered value, and adds it to all the covered rows then
	* subtracts it from all the uncovered columns. This results in a cost matrix with
	* at least one more zero.
	*/
	private void minUncoveredRowsCols() {
		// find min uncovered value
		int minUncovered = Integer.MAX_VALUE;
		for (int i = 0; i < dim; i++)
			if (!rowsCovered[i])
				for (int j = 0; j < dim; j++)
					if (!colsCovered[j])
						if (costs[i][j] < minUncovered)
							minUncovered = costs[i][j];

		// add that value to all the COVERED rows.
		for (int i = 0; i < dim; i++)
			if (rowsCovered[i])
				for (int j = 0; j < dim; j++)
					if (costs[i][j] + minUncovered < costs[i][j])
						throw new Error(costs[i][j] + " " + minUncovered);

		for (int i = 0; i < dim; i++)
			if (rowsCovered[i])
				for (int j = 0; j < dim; j++)
					costs[i][j] += minUncovered;

		// subtract that value from all the UNcovered columns
		for (int j = 0; j < dim; j++)
			if (!colsCovered[j])
				for (int i = 0; i < dim; i++)
					costs[i][j] -= minUncovered;
	}

	/*
	* Finds an uncovered zero, primes it, and returns an array
	* describing the row and column of the newly primed zero.
	* If no uncovered zero could be found, returns -1 in the indices.
	* O(n^2)
	*/
	private int primeUncoveredZero() {
		for (int i = 0; i < dim; i++)
			if (!rowsCovered[i])
				for (int j = 0; j < dim; j++)
					if (!colsCovered[j]) {
						if (costs[i][j] == 0) {
							primes[i][j] = true;
							return makeLocation(i, j);
						}
					}
		return makeLocation(-1, -1);
	}

	/*
	* Starting at a given primed location[0=row,1=col], we find an augmenting path
	* consisting of a primed , starred , primed , ..., primed. (note that it begins and ends with a prime)
	* We do this by starting at the location, going to a starred zero in the same column, then going to a primed zero in
	* the same row, etc, until we get to a prime with no star in the column.
	* O(n^2)
	*/
	private void augmentPathStartingAtPrime(int location) {
		int primeLocationsSize = 0;
		int starLocationsSize = 0;
		primeLocations[primeLocationsSize++] = location;

		int currentRow = rowFromLocation(location);
		int currentCol = colFromLocation(location);
		while (true) { // add stars and primes in pairs
			int starRow = findStarRowInCol(currentCol);
			// at some point we won't be able to find a star. if this is the case, break.
			if (starRow == -1)
				break;
			starLocations[starLocationsSize++] = makeLocation(starRow, currentCol);
			currentRow = starRow;

			int primeCol = findPrimeColInRow(currentRow);
			primeLocations[primeLocationsSize++] = makeLocation(currentRow, primeCol);
			currentCol = primeCol;
		}

		unStarLocations(starLocations, starLocationsSize);
		starLocations(primeLocations, primeLocationsSize);
	}

	private void starLocations(int[] locations, int size) {
		for (int k = 0; k < size; k++) {
			int row = rowFromLocation(locations[k]);
			int col = colFromLocation(locations[k]);
			stars[row][col] = true;
		}
	}

	private void unStarLocations(int[] locations, int size) {
		for (int k = 0; k < size; k++) {
			int row = rowFromLocation(locations[k]);
			int col = colFromLocation(locations[k]);
			stars[row][col] = false;
		}
	}

	// Given a row index, finds a column with a prime. returns -1 if this isn't possible
	private int findPrimeColInRow(int row) {
		for (int j = 0; j < dim; j++)
			if (primes[row][j])
				return j;
		return -1;
	}

	// Given a column index, finds a row with a star. returns -1 if this isn't possible
	public int findStarRowInCol(int col) {
		for (int i = 0; i < dim; i++)
			if (stars[i][col])
				return i;
		return -1;
	}

	public int findStarColInRow(int row) {
		for (int j = 0; j < dim; j++)
			if (stars[row][j])
				return j;
		return -1;
	}

	// looks at the colsCovered array, and returns true if all entries are true, false otherwise
	private boolean allColsCovered() {
		for (int j = 0; j < dim; j++)
			if (!colsCovered[j])
				return false;
		return true;
	}

	// sets the columns covered if they contain starred zeros O(n^2)
	private void coverStarredZeroCols() {
		for (int j = 0; j < dim; j++) {
			boolean covered = false;
			for (int i = 0; i < dim; i++)
				if (stars[i][j]) {
					covered = true;
					break;
				}
			colsCovered[j] = covered;
		}
	}

	private void subtractRowColMins() {
		for (int i = 0; i < dim; i++) { //for each row
			int rowMin = Integer.MAX_VALUE;
			for (int j = 0; j < dim; j++) // grab the smallest element in that row
				if (costs[i][j] < rowMin)
					rowMin = costs[i][j];
			for (int j = 0; j < dim; j++) // subtract that from each element
				costs[i][j] -= rowMin;
		}

		for (int j = 0; j < dim; j++) {
			int colMin = Integer.MAX_VALUE;
			for (int i = 0; i < dim; i++) // grab the smallest element in that column
				if (costs[i][j] < colMin)
					colMin = costs[i][j];
			for (int i = 0; i < dim; i++) // subtract that from each element
				costs[i][j] -= colMin;
		}
	}
}