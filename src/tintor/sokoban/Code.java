package tintor.sokoban;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Code {
	public final char Box = '$';
	public final char Wall = '#';
	public final char BoxGoal = '*';
	public final char AgentGoal = '+';
	public final char Goal = '.';
	public final char Agent = '@';
	public final char Space = ' ';

	public final char Dead = ':';
	public final char AliveTunnel = 't';
	public final char DeadTunnel = 'o';
	public final char GoalRoomEntrance = 'b';

	private String emojify(char c) {
		if (c == ' ')
			return "🕸️";
		if (c == Wall)
			return "✴️";
		if (c == '\n')
			return "\n";
		if (c == Agent)
			return "😀";
		if (c == Box)
			return "🔴";
		if (c == Goal)
			return "🏳";
		if (c == BoxGoal)
			return "🔵";
		if (c == AgentGoal)
			return "😎";
		if (c == DeadTunnel)
			return "🔹";
		if (c == AliveTunnel)
			return "🔸";
		if (c == Dead) // dead cell
			return "🌀";
		if (c == GoalRoomEntrance)
			return "🚩";
		if (c == '0')
			return "0️⃣";
		if (c == '1')
			return "1️⃣";
		if (c == '2')
			return "2️⃣";
		if (c == '3')
			return "3️⃣";
		if (c == '4')
			return "4️⃣";
		if (c == '5')
			return "5️⃣";
		if (c == '6')
			return "6️⃣";
		if (c == '7')
			return "7️⃣";
		if (c == '8')
			return "️8️⃣";
		if (c == '9')
			return "9️⃣";
		if (c == 'A')
			return "🔟";
		if (c == 'w')
			return "⬆️";
		if (c == 'a')
			return "⬅️️";
		if (c == 's')
			return "⬇️️";
		if (c == 'd')
			return "➡️";
		assert false : "" + c;
		return "" + c;
	}

	public String emojify(char[] buffer) {
		boolean add_space = System.console() != null;
		StringBuilder sb = new StringBuilder();
		for (char c : buffer) {
			sb.append(emojify(c));
			if (add_space && c != '\n')
				sb.append(' ');
		}
		return sb.toString();
	}
}