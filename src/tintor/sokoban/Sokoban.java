package tintor.sokoban;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;

import com.sun.net.httpserver.HttpExchange;

import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import tintor.common.Flags;
import tintor.common.KeepAwake;

@UtilityClass
public class Sokoban {
	// TODO allow it to modify Flags at runtime!
	public void handleFlag(HttpExchange t) throws IOException {
		URI u = t.getRequestURI();
		String response = "This is the response";
		t.sendResponseHeaders(200, response.getBytes().length);
		OutputStream os = t.getResponseBody();
		os.write(response.getBytes());
		os.close();
	}

	public void handleTest(HttpExchange t) throws IOException {
		String response = "This is the response";
		t.sendResponseHeaders(200, response.getBytes().length);
		OutputStream os = t.getResponseBody();
		os.write(response.getBytes());
		os.close();
	}

	public void handleAbort(HttpExchange t) throws IOException {
		System.err.println("abort request!");
		System.exit(0);
	}

	@SneakyThrows
	public String[] init(String[] args, int min, int max) {
		//HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
		//server.createContext("/flag", Sokoban::handleFlag);
		//server.createContext("/test", Sokoban::handleTest);
		//server.createContext("/abort", Sokoban::handleAbort);
		//server.start();

		for (Class<?> c : new Class<?>[] { Deadlock.class, OpenSet.class, ClosedSet.class, PatternIndex.class,
				Heuristic.class, State.class, AStarSolver.class, Level.class, Code.class })
			Class.forName(c.getName());
		KeepAwake.enable();
		return Flags.parse(args, min, max);
	}
}
