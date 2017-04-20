package tintor.sokoban;

import tintor.common.Timer;
import tintor.common.Util;

class Monitor {
	OpenSet open;
	ClosedSet closed;
	Deadlock deadlock;
	Level level;

	final Timer timer = new Timer();
	final Timer model_timer = new Timer();
	int states_closed = 0;
	int states_per_report = 10000;
	int next_states_closed = states_per_report;
	double speed = 0;

	int branches = 0;
	int closes = 0;
	int model_deadlocks = 0;
	int model_non_deadlocks = 0;

	void report(StateBase a) {
		closes += 1;
		states_closed += 1;
		if (states_closed < next_states_closed)
			return;

		timer.stop();
		speed = (1e9 * states_per_report / timer.total);
		closed.timer_add.total /= closes;
		closed.timer_contains.total /= closes;
		model_timer.total /= closes;

		timer.total /= closes;
		timer.total -= closed.timer_add.total;
		timer.total -= closed.timer_contains.total;
		timer.total -= model_timer.total;

		System.out.printf("closed:%s %.2f [add:%s contains:%s]\n", Util.human(states_closed), closed.ratio(),
				closed.timer_add.clear(), closed.timer_contains.clear());
		timer.total -= open.report(closes);
		timer.total -= deadlock.report(closes);
		System.out.printf("dist:%d total_dist:%d dead:%s live:%s [%s]\n", a.dist, a.total_dist,
				Util.human(model_deadlocks), Util.human(model_non_deadlocks), model_timer.clear());

		System.out.printf("speed:%s [%s] ", Util.human((int) speed), timer.clear());
		System.out.printf("branch:%.2f ", (double) branches / closes);
		System.out.printf("memory:%s\n", Util.human(Runtime.getRuntime().freeMemory()));

		branches = closes = 0;

		level.print(a);
		states_per_report = (int) (10 * speed);
		next_states_closed = states_closed + states_per_report;
		timer.start();
	}
}