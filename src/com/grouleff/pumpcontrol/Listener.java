package com.grouleff.pumpcontrol;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Listener {
	private ServerSocket serverSocket;

	public Listener(int port) throws IOException {
		init(port);
	}
	
	private void init(int port) throws IOException {
		serverSocket = new ServerSocket(port, 3);
	}

	public void listenForever() {
		while (true) {
			try {
				newConnection();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void newConnection() throws IOException {
		Socket clientSocket = serverSocket.accept();
		String name = "" + clientSocket.getRemoteSocketAddress();
		System.err.println("Client connected: " + name);
        new Client(clientSocket, name);
	}
}
