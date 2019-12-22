package com.bowie.app;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PortUnreachableException;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;
import java.util.List;

public class Peer {
	private SocketAddress addr;
	private SocketAddress publicAddr;
	
	private String host;
	private int port;
	
	private DatagramChannel channel;
	
//	private ByteBuffer bb;
	private ByteBuffer sendBuf;
	private PeerData pd;
	
	private List<SocketAddress> peerAddresses;
//	private int shiftBreadth = 3;	// shift 3 at a time
//	private int shiftCount = 0;		// start at 0
	
	public Peer(String host, int port) {
		this.host = host;
		this.port = port;
		
		sendBuf = ByteBuffer.allocateDirect(2048);
		sendBuf.clear();
		
		peerAddresses = new ArrayList<SocketAddress>(15);
		peerAddresses.clear();
	}
	
	protected void handleDatagram(SocketAddress addr, ByteBuffer bb) throws IOException {
//		if (bb.limit() < 4) {
//			System.out.println("Abnormal message size. skipping");
//			return;
//		}
		
		int msgId = bb.getInt();
		byte[] ipAddress = new byte[4];
		int peerPort;
		
		switch (msgId) {
		case Shared.MSG_SUBBED:
			// read address next (4 bytes)
			bb.get(ipAddress, 0, 4);
			
			String publicIp = String.format("%d.%d.%d.%d", ipAddress[0] & 0xff, ipAddress[1] & 0xff, ipAddress[2] & 0xff, ipAddress[3] & 0xff);
			int publicPort = bb.getInt();
			
			// server says we're subbed
			System.out.println(String.format("Subbed to server! @ %s:%d", publicIp, publicPort));
			pd.setSubStatus(Shared.STAT_SUBSCRIBED);
			
			// save public address
			try {
				publicAddr = new InetSocketAddress(InetAddress.getByAddress(ipAddress), publicPort);
			} catch (UnknownHostException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			break;
			
		case Shared.MSG_PING:
			sendPingResponse();
			break;
			
		case Shared.MSG_PONG:
			pd.measureRTT();
			break;
		
		case Shared.MSG_ADD_PEER:
			// if we're not subbed yet, bail
			// got peers!!
			if (pd.getSubStatus() == Shared.STAT_SUBSCRIBED) {
				int numPeers = bb.getInt();
				System.out.println(String.format("Get %d peer infos", numPeers));
				
				peerAddresses.clear();
				
				if (numPeers <= 64) {
					// read em all
					for (int i=0; i<numPeers; i++) {
						bb.get(ipAddress, 0, 4);
						peerPort = bb.getInt();
						
						String peerHost = String.format("%d.%d.%d.%d", ipAddress[0]&0xff, ipAddress[1]&0xff, ipAddress[2]&0xff, ipAddress[3]&0xff);
						
						SocketAddress peerAddr = new InetSocketAddress(InetAddress.getByAddress(ipAddress), peerPort);
						
						if (peerAddr.equals(publicAddr)) {
							System.out.println("Our address is in peer list!");
							// attempt to add self into list too (DEBUG)
//							peerAddresses.add(peerAddr);
						} else {
							peerAddresses.add(peerAddr);
							System.out.println(peerAddr);
						}
					}
				}
			} else {
				System.out.println("Ignoring peers since we're not subbed yet...");
			}
			break;
			
		case Shared.MSG_PEER_HI:
			System.out.println(String.format("Get Hello from peer %s :)", addr));
			// send hello back?
			sendHo(addr);
			break;
			
		case Shared.MSG_PEER_HO:
			System.out.println(String.format("Peer greets you back from %s :)", addr));
			break;

		default:
			System.out.println(String.format("Got something else from %s: %d", addr, msgId));
			break;
		}
	}
	
	protected void onIdle(long timestepMS) throws IOException {
		// if we're not subbed, request it
		if (pd.getSubStatus() == Shared.STAT_UNSUBSCRIBED) {
			sendSubRequest();
		}
		
		// if we're subbed, then send to peer
		if (pd.getSubStatus() == Shared.STAT_SUBSCRIBED) {
			// do we have peers?
			
			// send ping so server knows we're alive
			if (!pd.isWaitingForPong()) {
				sendPingRequest();
				pd.startPing();
			}
			
			// broadcast hello to peers?
			broadcastHiToPeer();
		}
		
	}
	
	public void sendHo(SocketAddress target) throws IOException {
		sendBuf.clear();
		sendBuf.putInt(Shared.MSG_PEER_HO);
		sendBuf.flip();
		
		channel.send(sendBuf, target);
	}
	
	public void broadcastHiToPeer() throws IOException {
		if (peerAddresses.size() < 1) {
			return;
		}
		
		System.out.println(String.format("Sending broadcast to %d peers", peerAddresses.size()));
		
		sendBuf.clear();
		sendBuf.putInt(Shared.MSG_PEER_HI);
		sendBuf.flip();
		
		// send to all
		for (SocketAddress socketAddress : peerAddresses) {
//			DatagramChannel peerConnection = DatagramChannel.open();
//			peerConnection.connect(socketAddress);
//			peerConnection.send(sendBuf, socketAddress);
			channel.send(sendBuf, socketAddress);
//			peerConnection.close();
			sendBuf.flip();
		}
	}
	
	public void sendSubRequest() throws IOException {
		sendBuf.clear();
		sendBuf.putInt(Shared.MSG_SUBSCRIBE);
		sendBuf.flip();
		
		channel.send(sendBuf, pd.getAddress());
	}
	
	public void sendPingResponse() throws IOException {
		sendBuf.clear();
		sendBuf.putInt(Shared.MSG_PONG);
		sendBuf.flip();
		
		channel.send(sendBuf, pd.getAddress());
	}
	
	public void sendPingRequest() throws IOException {
		sendBuf.clear();
		sendBuf.putInt(Shared.MSG_PING);
		sendBuf.flip();
		
		channel.send(sendBuf, pd.getAddress());
	}
	
	public void startSession() {
		// first, try connecting.
		System.out.println(String.format("Connecting to %s:%d", host, port));
		
		SocketAddress serverAddr;
		
		try {
			serverAddr = new InetSocketAddress(InetAddress.getByName(host), port);
			
			channel = DatagramChannel.open();
//			channel.connect(serverAddr);
			channel.configureBlocking(false);
			
			pd = new PeerData(serverAddr);
			
			System.out.println("No problem detected, moving to main session code...");
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			return ;
		}
		
		ByteBuffer bb = ByteBuffer.allocateDirect(2048);
		bb.clear();
		
		SocketAddress srcAddr;
		boolean isRunning = true;
		while (isRunning) {
			try {
				// read all available packet
				while ( (srcAddr = channel.receive(bb)) != null ) {
					// always receive, but process discriminately
					if (true) {
						bb.flip();
						
						if (srcAddr.equals(serverAddr) && pd.getSubStatus() == Shared.STAT_UNSUBSCRIBED) {
							System.out.println(String.format("Got packet From server: %d bytes!", bb.remaining()));
						} else if (!srcAddr.equals(serverAddr)) {
							System.out.println(String.format("Got from %s", srcAddr));
						}
						// handle it
						
						
						handleDatagram(srcAddr, bb);
						
						// clear for next read
						bb.clear();
					}
				}
				
				// do logic thing
				onIdle(200);
				
				// sleep for 200 ms
				Thread.sleep(200);
			} catch (PortUnreachableException e) {
				System.out.println("Server address unresolved....");
				isRunning =  false;
				
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}
