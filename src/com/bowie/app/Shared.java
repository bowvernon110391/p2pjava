package com.bowie.app;

public class Shared {
	// message IDs
	public static final int MSG_SUBSCRIBE = 0;	// peer request subscribe to server
	public static final int MSG_SUBBED = 1;		// server confirmation
	public static final int MSG_PING = 3;		// ping
	public static final int MSG_PONG = 4;		// pong
	
	public static final int MSG_PEER_HI = 5;	// p2p client specific
	public static final int MSG_PEER_HO = 6;
	
	// peer state
	public static final int STAT_UNSUBSCRIBED = 0;	// either disconnected or is in session
	public static final int STAT_SUBSCRIBED = 1;		// if subscribed, keep sending keep alive
	public static final int STAT_IN_SESSION = 2;		// is in p2p session
}
