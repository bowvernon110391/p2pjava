package com.bowie.app;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.HashMap;
import java.util.Map;

public class MatchMaker {
	private int port;
	private DatagramSocket socket;
	private DatagramChannel channel;
	
	// map of client based on address
	private Map<SocketAddress, PeerData> peers;
	
	// shared buffer for writing out message
	private ByteBuffer sendBuf;
	
	// server data
	protected long timelapsed = 0;
	
	public MatchMaker(int port) {
		this.port = port;
		// allocate peers
		peers = new HashMap<SocketAddress, PeerData>(15);
		// allocate send buffer
		sendBuf = ByteBuffer.allocateDirect(2048);
		sendBuf.clear();
	}
	
	// start the udp socket and listening
	public void startSession() {
		try {
			// create the channel first
			channel = DatagramChannel.open();
			
			// associate channel's socket
			socket = channel.socket();
			// bind to port
			InetSocketAddress sockAddr = new InetSocketAddress(port);
			socket.bind((SocketAddress) sockAddr);
			// make it nonblocking?
			channel.configureBlocking(false);
			
			// log it
			System.out.println(String.format("Started @ %s:%d", sockAddr.getHostString(), sockAddr.getPort()));
			
			// run the whole thing
			this.run();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	// sendSubConfirmation
	protected void sendSubConfirmation(PeerData pd) throws IOException {
		System.out.println(String.format("Sending sub confirm to %s", pd.getAddress()));
		sendBuf.clear();
		sendBuf.putInt(Shared.MSG_SUBBED);
		// tell the client what his public ip:port is
		InetSocketAddress addr = (InetSocketAddress) pd.getAddress();
		sendBuf.put(addr.getAddress().getAddress());
		sendBuf.putInt(addr.getPort());
		sendBuf.flip();
		
		channel.send(sendBuf, pd.getAddress());
	}
	
	// broadcastPeerInfo
	protected void broadcastPeerInfo() throws IOException {
		// build info first
		int numPeers = peers.size();
		
		sendBuf.clear();
		sendBuf.putInt(Shared.MSG_ADD_PEER);
		sendBuf.putInt(numPeers);
		// for every peers, send 4 tuples of ip + port
		for (PeerData pd : peers.values()) {
			InetSocketAddress addr = (InetSocketAddress) pd.getAddress();
			
			sendBuf.put(addr.getAddress().getAddress());
			sendBuf.putInt(addr.getPort());
		}
		sendBuf.flip();
		
		// now send it to everyone
		for (PeerData pd : peers.values()) {
			channel.send(sendBuf, pd.getAddress());
			sendBuf.flip();
		}
	}
	
	// send ping response
	protected void sendPingResponse(PeerData pd) throws IOException {
		sendBuf.clear();
		sendBuf.putInt(Shared.MSG_PONG);
		sendBuf.flip();
		
		channel.send(sendBuf, pd.getAddress());
	}
	
	// send ping request
	protected void sendPingRequest(PeerData pd) throws IOException {
		sendBuf.clear();
		sendBuf.putInt(Shared.MSG_PING);
		sendBuf.flip();
		
		channel.send(sendBuf, pd.getAddress());
		pd.startPing();
	}
	
	// handle socket message
	protected void handleDatagram(SocketAddress addr, ByteBuffer bb) {
		InetSocketAddress srcAddr = (InetSocketAddress) addr;
		
		// only log if it's new?
		if (!peers.containsKey(srcAddr)) {
			System.out.println(String.format("Got datagram from: %s:%d, %d bytes", srcAddr.getAddress().getHostAddress(), srcAddr.getPort(), bb.limit()));
		}
		
		// depending on message, we migth do something
		int msgId = bb.getInt();
		PeerData pd = null;
		
		try {
			switch (msgId) {
			case Shared.MSG_SUBSCRIBE:
				// init client, add to subscribed shit
				pd = peers.get(addr);
				if (pd == null) {
					// allocate it
					pd = new PeerData(addr);
					pd.setSubStatus(Shared.STAT_SUBSCRIBED);
					
					// add to list of clients
					peers.put(addr, pd);
					
					// log
					System.out.println(String.format("Got sub from %s", addr));
				} 
				System.out.println(String.format("Sub request from %s", addr));
				// always send confirmation (IDEMPOTENCY)
				sendSubConfirmation(pd);
				break;
				
			case Shared.MSG_PING:
				// send ping response
				pd = peers.get(addr);
				if (pd != null) {
					sendPingResponse(pd);
				}
				break;
				
			case Shared.MSG_PONG:
				// peer send ping response, measure RTT
				pd = peers.get(addr);
				if (pd != null) {
					pd.measureRTT();
				}
				break;

			default:
				break;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	// do whatever it usually does
	protected void onIdle(long timestepMS) throws IOException {
		// loop all over peer, send keep alive to em
		// if they're subbed
		
		// build ping message
		sendBuf.clear();
		sendBuf.putInt(Shared.MSG_PING);
		sendBuf.flip();
		
		for (PeerData pd : peers.values()) {
			// if it's subbed and not waiting for ping,
			// ping it
			if (pd.getSubStatus() == Shared.STAT_SUBSCRIBED && !pd.isWaitingForPong()) {
				channel.send(sendBuf, pd.getAddress());
				pd.startPing();
//				sendBuf.reset();
				sendBuf.flip();
			}
		}
		
		timelapsed += timestepMS;
		// once every 5 secs, print all client's rtt
		if (timelapsed >= 5000) {
			timelapsed = 0;
			System.out.print("\r=================RTT DUMP BEGIN=========================\n");
			for (PeerData pd : peers.values()) {
				if (pd.getSubStatus() == Shared.STAT_SUBSCRIBED) {
					System.out.println(String.format("RTT @ %s = %d ms", pd.getAddress(), pd.getRTT()));
				}
			}
			System.out.print("\r=================RTT DUMP END===========================\n");
			
			// broadcast peer info
			broadcastPeerInfo();
		}
	}
	
	// do the thing
	public void run() {
		boolean isRun = true;
		long dt = 200;
		
		// allocate byte buffer (2kb)
		ByteBuffer bb = ByteBuffer.allocateDirect(2048);
		bb.clear();
		
		while (isRun) {
			// poll for message?
			SocketAddress srcAddr = null;
			
			try {
				// keep reading until there's no more packet
				while ( (srcAddr = channel.receive(bb)) != null ) {
					// do something with it?
					// flip it
					bb.flip();
					this.handleDatagram(srcAddr, bb);
					
					// reset for next read
					bb.clear();
				}
				
				// do server logic
				onIdle(dt);
				
				// sleep?
				Thread.sleep(dt);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				isRun = false;
				
				System.out.println("Server interrupted.");
				
				Thread.currentThread().interrupt();
			}
		}
		
		System.out.println("Server stopped.");
	}
}
