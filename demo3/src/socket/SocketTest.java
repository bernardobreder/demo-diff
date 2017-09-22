package socket;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class SocketTest {

	public static void main(String[] args) throws IOException {
		final ServerSocket server = new ServerSocket(5000);
		for (int n = 0; n < 1024; n++) {
			System.out.println("Connection " + (n + 1));
			new Socket("localhost", 5000);
		}
		System.exit(0);
		new Thread() {
			public void run() {
				List<Socket> list = new ArrayList<Socket>();
				try {
					Socket socket = server.accept();
					list.add(socket);
				} catch (IOException e) {
				}
			}
		}.start();
	}

}
