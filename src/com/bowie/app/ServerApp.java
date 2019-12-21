package com.bowie.app;

public class ServerApp {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		final int DEFAULT_PORT = 7676;
		int port = DEFAULT_PORT;
		if (args.length > 1) {
			port = Integer.parseInt(args[1]);
		}
		
		new MatchMaker(port).startSession();
	}

}
