package com.bowie.launcher;

import java.net.InetSocketAddress;

import com.bowie.app.Peer;
import com.bowie.app.PeerData;

public class PeerApp {

	public static void main(String[] args) {
		String hostname = "localhost";
		int port = 7676;
		
		if (args.length >= 1) {
			hostname = args[0];
		}
		if (args.length >= 2) {
			port = Integer.parseInt(args[1]);
		}
		
		// start peer here
		new Peer(hostname, port).startSession();
	}

}
