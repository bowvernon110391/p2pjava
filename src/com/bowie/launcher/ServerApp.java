package com.bowie.launcher;

import com.bowie.app.MatchMaker;

public class ServerApp {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		final int DEFAULT_PORT = 7676;
		int port = DEFAULT_PORT;
		if (args.length >= 1) {
			port = Integer.parseInt(args[0]);
		}
		
		new MatchMaker(port).startSession();
	}

}
