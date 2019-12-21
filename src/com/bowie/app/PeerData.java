package com.bowie.app;

import java.net.SocketAddress;

public class PeerData {
	// states
	private long lastPing;
	private boolean waitForPong;
	private long rtt;
	private int subStatus;
	// socket data
	private SocketAddress srcAddr;
	
	// ctor
	public PeerData(SocketAddress addr) {
		subStatus = Shared.STAT_UNSUBSCRIBED;
		lastPing = 0;
		rtt = 0;
		waitForPong = false;
		srcAddr = addr;
	}
	
	public int getSubStatus() {
		return subStatus;
	}
	
	public void setSubStatus(int s) {
		subStatus = s;
	}
	
	// get peer's address
	public SocketAddress getAddress() {
		return srcAddr;
	}
	
	public long getLastPingTime() {
		return lastPing;
	}
	
	public void setLastPingTime() {
		// just record it
		lastPing = System.currentTimeMillis();
	}
	
	public long timeSinceLastPing() {
		return System.currentTimeMillis() - lastPing;
	}
	
	public void measureRTT() {
		// compute rtt, and reset last ping time so
		// it can ping again
		rtt = timeSinceLastPing();
		setLastPingTime();
		waitForPong = false;
	}
	
	public long getRTT() {
		return rtt;
	}
	
	public boolean isWaitingForPong() {
		return waitForPong;
	}
	
	public void startPing() {
		setLastPingTime();
		waitForPong = true;
	}
}
