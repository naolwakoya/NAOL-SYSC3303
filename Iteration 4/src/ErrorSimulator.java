<<<<<<< HEAD

import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ErrorSimulator {
	// instance variables
	private DatagramSocket sendReceiveSocket, receiveSocket;
	private DatagramPacket receivePacket, sendPacket;

	boolean running = true;
	boolean lastAck, lastData, losePacket;

	InetAddress clientAddress, serverAddress;
	int clientPort, serverPort;
	int proxyPort = 23;
	int serverRequestPort = 69;

	private int blockNumber, newBlockNumber, operation;
	private int delay;

	public ErrorSimulator() {
		// create new datagram sockets for the client and server
		try {
			receiveSocket = new DatagramSocket(proxyPort, InetAddress.getLocalHost());
			serverAddress = InetAddress.getLocalHost();
		} catch (IOException se) {
			se.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * Forwards and modifies TFTP packets during file transfer between the
	 * client/server
	 * 
	 * @param operation
	 * @param blockNumber
	 * @param newBlockNumber
	 */
	public void run(int operation, int blockNumber, int newBlockNumber) {
		// Reset boolean values
		lastAck = false;
		lastData = false;
		running = true;
		losePacket = true;
		// Set the sendReceive socket
		try {
			sendReceiveSocket = new DatagramSocket();
		} catch (SocketException e) {
		}
		this.blockNumber = blockNumber;
		this.newBlockNumber = newBlockNumber;
		this.operation = operation;
		// Receive request from client
		this.receiveRequest();
		System.out.println("Received packet from client on port " + receivePacket.getPort());
		printPacketInformation(receivePacket);
		// Set the source TID
		clientPort = receivePacket.getPort();
		clientAddress = receivePacket.getAddress();
		// Create packet to forward to the server
		sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), serverAddress,
				serverRequestPort);
		System.out.println("Forwarding packet to server on port " + sendPacket.getPort());
		this.forwardRequestPacket();

		// Receive response from server
		this.receive();
		System.out.println("Received packet from server on port " + receivePacket.getPort());
		printPacketInformation(receivePacket);
		// Set the destination TID
		serverPort = receivePacket.getPort();
		// forward packet to client
		sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), clientAddress, clientPort);
		System.out.println("Forwarding packet to client on port " + sendPacket.getPort());
		this.forwardPacket();

		// Will run for the entire connection file transfer unless an error
		// occurs
		while (!(lastData && lastAck) && running) {
			// Receive packet from client
			this.receive();
			System.out.println("Received packet from client on port " + receivePacket.getPort());
			printPacketInformation(receivePacket);
			// Forward the packet
			sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), serverAddress,
					serverPort);
			System.out.println("Forwarding packet to server on port " + sendPacket.getPort());
			this.forwardPacket();
			if (!running || (lastAck && lastData)) {
				sendReceiveSocket.close();
				return;
			}
			// Receive packet
			this.receive();
			System.out.println("Received packet from server on port " + receivePacket.getPort());
			printPacketInformation(receivePacket);
			// Forward the packet
			sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), clientAddress,
					clientPort);
			System.out.println("Forwarding packet to client on port " + sendPacket.getPort());
			this.forwardPacket();
		}
		sendReceiveSocket.close();
	}

	/**
	 * Forwards the request packet to the client/server with packet modifications by
	 * the error simulator
	 */
	private void forwardRequestPacket() {
		// Modify request to have invalid opcode
		if (operation == 1) {
			this.send(invalidOpcode(sendPacket));
		}
		// Modify request to have invalid mode
		else if (operation == 2) {
			this.send(invalidMode(sendPacket));
		}
		// Modify request to have invalid format
		else if (operation == 3) {
			this.send(invalidRequestFormat(sendPacket));
		}
		// Lose the request packet
		else if (operation == 10 && losePacket) {
			losePacket = false;
			//Wait for the host to resend the request packet
			this.receive();
			// Create packet to forward to the server
			sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), serverAddress,
					serverRequestPort);
			System.out.println("Forwarding packet to server on port " + sendPacket.getPort());
			this.forwardRequestPacket();
		}
		// Normal operation
		else {
			this.send(sendPacket);
		}
	}

	/**
	 * Forwards the packet to the client/server with packet modifications by the
	 * error simulator
	 */
	private void forwardPacket() {
		// Check if it is the last data packet
		if (receivePacket.getData()[1] == 3 && receivePacket.getLength() < 516) {
			lastData = true;
		}
		// Check if it is the last ack packet
		if (receivePacket.getData()[1] == 4 && lastData) {
			lastAck = true;
		}
		// If the packet is an error packet forward the packet and do not run
		// the file transfer
		if (receivePacket.getData()[1] == 5) {
			this.send(sendPacket);
			sendReceiveSocket.close();
			running = false;
			return;
		}
		// Check if packet is a data packet that we want to modify
		else if (receivePacket.getData()[1] == 3 && receivePacket.getData()[3] == blockNumber) {
			// Modify data to have invalid opcode
			if (operation == 4) {
				this.send(invalidOpcode(sendPacket));
			}
			// Modify data to have invalid format (>516 bytes)
			else if (operation == 5) {
				this.send(invalidFormat(sendPacket));
			}
			// Change block number of data packet
			else if (operation == 6) {
				this.send(changeBlockNumber(sendPacket));
			}
			// Lose the data packet
			else if (operation == 11 && losePacket) {
				losePacket = false;
				//Wait for the host to resend the data packet
				this.receive();
				while (receivePacket.getData()[1]!=3) {
					this.receive();
				}
				System.out.println("Received packet from server on port " + receivePacket.getPort());
				// Forward the packet
				if(receivePacket.getPort()==clientPort) {
				sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), serverAddress,
						serverPort);
				}
				else if (receivePacket.getPort()==serverPort) {
					sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), clientAddress,
							clientPort);
				}
				System.out.println("Forwarding packet to client on port " + sendPacket.getPort());
				this.forwardPacket();
			}
			// Normal operation
			else {
				this.send(sendPacket);
			}
		}
		// Check if packet is an ack packet that we want to modify
		else if (receivePacket.getData()[1] == 4 && receivePacket.getData()[3] == blockNumber) {
			// Modify ack to have invalid opcode
			if (operation == 7) {
				this.send(invalidOpcode(sendPacket));
			}
			// Modify ack to have invalid format (>4 bytes)
			else if (operation == 8) {
				this.send(invalidFormat(sendPacket));
			}
			// Change block number of ack packet
			else if (operation == 9) {
				this.send(changeBlockNumber(sendPacket));
			}
			// Lose the ack packet
			else if (operation == 12 && losePacket) {
				losePacket = false;
				//Wait for the host to resend the data packet
				this.receive();
				while (receivePacket.getData()[1]!=3) {
					this.receive();
				}
				System.out.println("Received packet on port " + receivePacket.getPort());
				// Forward the packet
				if(receivePacket.getPort()==clientPort) {
				sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), serverAddress,
						serverPort);
				}
				else if (receivePacket.getPort()==serverPort) {
					sendPacket = new DatagramPacket(receivePacket.getData(), receivePacket.getLength(), clientAddress,
							clientPort);
				}
				System.out.println("Forwarding packet on port " + sendPacket.getPort());
				this.forwardPacket();
			}
			// Normal operation
			else {
				this.send(sendPacket);
			}
		}
		// Normal operation
		else {
			this.send(sendPacket);
		}

	}

	/**
	 * Changes the opcode of the packet to an invalid TFTP opcode
	 * 
	 * @param packet
	 * @return
	 */
	private DatagramPacket invalidOpcode(DatagramPacket packet) {
		byte[] data = packet.getData();
		data[1] = 9;
		return new DatagramPacket(data, data.length, packet.getAddress(), packet.getPort());
	}

	/**
	 * Changes the format of a request packet to an invalid format
	 * 
	 * @param packet
	 * @return
	 */
	private DatagramPacket invalidRequestFormat(DatagramPacket packet) {
		return new DatagramPacket(packet.getData(), packet.getLength() - 1, packet.getAddress(), packet.getPort());
	}

	/**
	 * Changes the mode of the request packet to an invalid mode
	 * 
	 * @param packet
	 * @return
	 */
	private DatagramPacket invalidMode(DatagramPacket packet) {
		byte[] data = packet.getData();
		int x = 1;
		while (data[++x] != 0 && x < packet.getLength())
			;

		byte[] mode = ("invalid").getBytes();
		x++;
		for (int i = 0; i < mode.length; i++) {
			data[x + i] = mode[i];
		}
		return new DatagramPacket(data, data.length, packet.getAddress(), packet.getPort());
	}

	/**
	 * Makes the TFTP packet larger than the maximum size
	 * 
	 * @param packet
	 * @return
	 */
	private DatagramPacket invalidFormat(DatagramPacket packet) {
		byte[] data = packet.getData();
		byte[] data2 = packet.getData();
		byte[] newData = new byte[data.length + data2.length];
		System.arraycopy(data, 0, newData, 0, data.length);
		System.arraycopy(data2, 0, newData, data.length, data2.length);

		return new DatagramPacket(newData, newData.length, packet.getAddress(), packet.getPort());
	}

	/**
	 * Changes the block number of the TFTP packet
	 * 
	 * @param packet
	 * @return
	 */
	private DatagramPacket changeBlockNumber(DatagramPacket packet) {
		byte[] data = packet.getData();
		data[2] = (byte) ((newBlockNumber >> 8) & 0xFF);
		data[3] = (byte) (newBlockNumber & 0xFF);

		return new DatagramPacket(data, packet.getLength(), packet.getAddress(), packet.getPort());
	}

	/**
	 * method to send packet
	 * 
	 * @param data
	 */
	private void send(DatagramPacket packet) {
		printPacketInformation(packet);
		try {
			sendReceiveSocket.send(packet);
		} catch (IOException ioe) {
			ioe.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * Waits to receive a packet from the sendReceiveSocket with random port
	 */
	public void receive() {
		// Create a DatagramPacket for receiving packets
		byte receive[] = new byte[516];
		receivePacket = new DatagramPacket(receive, receive.length);

		try {
			// Block until a datagram is received via sendReceiveSocket.
			sendReceiveSocket.receive(receivePacket);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * Waits to receive a request from the receiveSocket on port 23
	 */
	public void receiveRequest() {
		// Create a DatagramPacket for receiving packets
		byte receive[] = new byte[516];
		receivePacket = new DatagramPacket(receive, receive.length);

		try {
			// Block until a datagram is received via receiveSocket.
			receiveSocket.receive(receivePacket);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * Processes the received Datagram and Prints the packet information onto the
	 * console
	 */
	public void printPacketInformation(DatagramPacket packet) {
		System.out.println("Host: " + packet.getAddress());
		System.out.println("Packet length: " + packet.getLength());
		// Request packet
		if (packet.getData()[1] == 1 || packet.getData()[1] == 2) {
			System.out.println("Request packet");
		}
		// Data packet
		else if (packet.getData()[1] == 3) {
			System.out.println("Data packet");
			System.out.println("Block#: " + packet.getData()[2] + packet.getData()[3]);
		}
		// Ack packet
		else if (packet.getData()[1] == 4) {
			System.out.println("ACK packet");
			System.out.println("Block#: " + packet.getData()[2] + packet.getData()[3]);
		}
		// Error packet
		else if (packet.getData()[1] == 5) {
			System.out.println("Error packet");
		}
	}

	public void setDelay(int delay) {
		this.delay = delay;
	}

	public static void main(String[] args) {
		Scanner s = new Scanner(System.in);
		ErrorSimulator er = new ErrorSimulator();
		System.out.println("Error Simulator");
		int input, operation, delay;
		int blockNumber = 0;
		int newBlockNumber = 0;

		while (true) {
			System.out.println("What would you like to change?");
			System.out.println("(0): normal operation");
			System.out.println("(1): request packet");
			System.out.println("(2): data packet");
			System.out.println("(3): ack packet");
			System.out.println("(10): lose a packet");
			System.out.println("(11): delay a packet");
			System.out.println("(12): duplicate a packet");
			System.out.println("(13): Invalid TID");
			System.out.println("(any other value): quit");

			input = s.nextInt();

			if (input == 0) {
				// do nothing
				System.out.println("(0): Confirm do nothing");
				operation = s.nextInt();
				if (operation == 0) {
					System.out.println("Performing normal operation");
					er.run(operation, blockNumber, newBlockNumber);
					System.out.println("Operation complete...");
				} else {
					System.out.println("Invalid command");
				}
			} else if (input == 1) {
				System.out.println("(1)Request packets chosen.");
				System.out.println("What would you like to do to the request packet?");
				System.out.println("(1): invalid opcode");
				System.out.println("(2): invalid mode");
				System.out.println("(3): invalid format (missing 0 after mode/missing 0 after filename)");
				operation = s.nextInt();
				if (operation == 1 || operation == 2 || operation == 3) {
					System.out.println("Performing the modified operation");
					er.run(operation, blockNumber, newBlockNumber);
					System.out.println("Operation complete...");
				} else {
					System.out.println("Invalid command");
				}
			} else if (input == 2) {
				System.out.println("(2)Data Packets chosen.");
				System.out.println("Which DATA packet would you like to change (block#)");
				blockNumber = s.nextInt();
				System.out.println("What would you like to do to the Data packet?");
				System.out.println("(4): invalid opcode");
				System.out.println("(5): invalid data format (>516)");
				System.out.println("(6): change block number");
				operation = s.nextInt();
				if (operation == 6) {
					System.out.println("Change to what block number?");
					newBlockNumber = s.nextInt();
					System.out.println("Performing operation with changed block number");
					er.run(operation, blockNumber, newBlockNumber);
					System.out.println("Operation complete...");
				} else if (operation == 4 || operation == 5) {
					System.out.println("Performing the modified operation");
					er.run(operation, blockNumber, newBlockNumber);
					System.out.println("Operation complete...");
				} else {
					System.out.println("Invalid command");
				}
			} else if (input == 3) {
				System.out.println("(3)Acknowledgement Packets chosen.");
				System.out.println("Which ACK packet would you like to change (block#)");
				blockNumber = s.nextInt();
				System.out.println("What would you like to do to the Ack packets?");
				System.out.println("(7): invalid opcode");
				System.out.println("(8): invalid ack format (>4)");
				System.out.println("(9): change block number");
				operation = s.nextInt();
				if (operation == 9) {
					System.out.println("Change to what block number?");
					newBlockNumber = s.nextInt();
					System.out.println("Performing operation with changed block number");
					er.run(operation, blockNumber, newBlockNumber);
					System.out.println("Operation complete...");
				} else if (operation == 7 || operation == 8) {
					System.out.println("Performing the modified operation");
					er.run(operation, blockNumber, newBlockNumber);
					System.out.println("Operation complete...");
				} else {
					System.out.println("Invalid command");
				}
			} else if (input == 10) {
				System.out.println("(10)Lose a packet chosen.");
				System.out.println("What type of packet do you want to lose?");
				System.out.println("(10): Request");
				System.out.println("(11): DATA");
				System.out.println("(12): ACK");
				operation = s.nextInt();
				if (operation == 10) {
					System.out.println("Performing operation to lose packet");
					er.run(operation, blockNumber, newBlockNumber);
					System.out.println("Operation complete...");
				} else if (operation == 11 || operation == 12) {
					System.out.println("Which packet would you like to lose (block#)");
					blockNumber = s.nextInt();
					System.out.println("Performing operation to lose packet");
					er.run(operation, blockNumber, newBlockNumber);
					System.out.println("Operation complete...");
				} else {
					System.out.println("Invalid command");
				}
			} else if (input == 11) {
				System.out.println("(11)Delay a packet chosen.");
				System.out.println("What type of packet do you want to delay?");
				System.out.println("(13): Request");
				System.out.println("(14): DATA");
				System.out.println("(15): ACK");
				operation = s.nextInt();
				if (operation == 13) {
					System.out.println("How long of a delay? (ms)");
					delay = s.nextInt();
					er.setDelay(delay);
					System.out.println("Performing operation to delay packet");
					er.run(operation, blockNumber, newBlockNumber);
					System.out.println("Operation complete...");
				} else if (operation == 14 || operation == 15) {
					System.out.println("Which packet would you like to delay (block#)");
					blockNumber = s.nextInt();
					System.out.println("How long of a delay? (ms)");
					delay = s.nextInt();
					er.setDelay(delay);
					System.out.println("Performing operation to delay packet");
					er.run(operation, blockNumber, newBlockNumber);
					System.out.println("Operation complete...");
				} else {
					System.out.println("Invalid command");
				}
			} else if (input == 12) {
				System.out.println("(12)Duplicate a packet chosen.");
				System.out.println("What type of packet do you want to duplicate?");
				System.out.println("(16): Request");
				System.out.println("(17): DATA");
				System.out.println("(18): ACK");
				operation = s.nextInt();
				if (operation == 16) {
					System.out.println("How much of a space between duplicates? (ms)");
					delay = s.nextInt();
					er.setDelay(delay);
					System.out.println("Performing operation to duplicate packet");
					er.run(operation, blockNumber, newBlockNumber);
					System.out.println("Operation complete...");
				} else if (operation == 17 || operation == 18) {
					System.out.println("Which packet would you like to duplicate (block#)");
					blockNumber = s.nextInt();
					System.out.println("How much of a space between duplicates? (ms)");
					delay = s.nextInt();
					er.setDelay(delay);
					System.out.println("Performing operation to duplicate packet");
					er.run(operation, blockNumber, newBlockNumber);
					System.out.println("Operation complete...");
				} else {
					System.out.println("Invalid command");
				}
			} else if (input == 13) {
				System.out.println("(13)Invalid TID chosen.");
				System.out.println("What type of packet do you want to have an invalid TID?");
				System.out.println("(19): DATA");
				System.out.println("(20): ACK");
				operation = s.nextInt();
				if (operation == 19 || operation == 20) {
					System.out.println("Which packet would you like to have an invalid TID (block#)");
					blockNumber = s.nextInt();
					System.out.println("Performing operation to for invalid TID");
					er.run(operation, blockNumber, newBlockNumber);
					System.out.println("Operation complete...");
				} else {
					System.out.println("Invalid command");
				}
			} else if (input > 13) {
				System.out.println("Closing the error simulator");
				System.exit(0);
			}

		}
	}

=======
import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ErrorSimulator{
	// instance variables
	private DatagramSocket sendReceiveSocket;
	private DatagramPacket receivePacket, sendPacket;

	private boolean isConnected = false;

	private boolean readFinished = false;
	private boolean writeFinished = false;

	private InetAddress clientAddress;
	private int clientPort;

	private int proxyPort = 8080;
	private int server1Port = 8081;

	private boolean requestReceived = false;

	private Scanner input;

	private void connect() {
		// scanner to receive user input from prompts
		input = new Scanner(System.in);

		// create new datagram sockets for the client and server
		try {
			sendReceiveSocket = new DatagramSocket(proxyPort, InetAddress.getLocalHost());
			System.out.println("Connected to client on port: " + sendReceiveSocket.getLocalPort());
		} catch (IOException se) {
			se.printStackTrace();
			System.exit(1);
		}
		isConnected = true;
	}


	private void run() {
		//if no connection has been established, the connect method will run
		if (!isConnected) {
			connect();
		}
		while(true){
			int operation = getOperation();
			if(operation == 0){
				//branch for normal operation
				while(true){
					byte[] data;
					data = receiveFromClient();

					String packetType;
					packetType = getPacketType(data);

					//if data packet received from client is less than 516 bytes, then that is the last data packet
					if(packetType.equals("data") && receivePacket.getLength()< 516){
						writeFinished = true;
						System.out.println("Last Packet received, terminating write transaction. PacketLength: " + receivePacket.getLength() + " " + writeFinished);
					}

					sendToServer(data);

					//check to see if transaction is finished after ack packet sent to server in read situation
					if(readFinished){
						server1Port = 8081;
						System.out.println("Breaking Loop");
						break;
					}

					data = receiveFromServer();

					//check if transaction is finished after packet forwarded to server
					//last data packet has been received in a read request or write request
					packetType = getPacketType(data);
					if(packetType.equals("data") && receivePacket.getLength()< 516){
						readFinished = true;
						System.out.println("Last Packet received, terminating read Transaction. PacketLength: " + receivePacket.getLength() + " " + readFinished);
					}

					sendToClient(data);

					if(writeFinished){
						server1Port = 8081;
						System.out.println("Breaking Loop");
						break;
					}

				}

				readFinished = false;
				writeFinished = false;
			}else if(operation == 4){
				//branch for changing request packets opcode
				byte[] data;
				data = receiveFromClient();

				System.out.println("Altering opcode of request packet...");
				data[0] = 9;
				data[1] = 9;
				System.out.println("OPCODE changed to: " + data[0] + data[1]);

				sendToServer(data);

				data = receiveFromServer();

				sendToClient(data);

				server1Port = 8081;

			} else if (operation == 5) {
				//branch for changing filename in the the request packet
				// takes the request packet and alters the filename
				byte[] data;

				data = receiveFromClient();

				System.out.println("Altering filename of request packet...");

				String fileName = extractFileName(data, data.length);

				System.out.println("Original filename: " + fileName);
				System.out.println("Changing fileName to: wrongFileName.txt");

				// create new byteArray with different fileName
				fileName = "randomFileName";
				String mode = "ascii";
				ByteArrayOutputStream request = new ByteArrayOutputStream();
				// hardcode request bytearraystream to be later converted to byte
				// array
				request.write(data[0]);
				request.write(data[1]);
				request.write(fileName.getBytes(), 0, fileName.getBytes().length);
				request.write(0);
				request.write(mode.getBytes(), 0, mode.getBytes().length);
				request.write(0);

				byte[] msg = request.toByteArray();

				sendToServer(msg);

				data = receiveFromServer();

				sendToClient(data);

				server1Port = 8081;
			}else if(operation == 6){
				//branch for changing the mode in the request packet
				//takes the request packet and alters the mode

				byte[] data;

				data = receiveFromClient();

				System.out.println("Altering mode of request packet...");
				System.out.println("Changing mode to: wrongMode");

				// create new byteArray with different fileName
				String fileName = extractFileName(data, data.length);
				String mode = "wrongMode";
				ByteArrayOutputStream request = new ByteArrayOutputStream();
				// hardcode request bytearraystream to be later converted to byte
				// array
				request.write(data[0]);
				request.write(data[1]);
				request.write(fileName.getBytes(), 0, fileName.getBytes().length);
				request.write(0);
				request.write(mode.getBytes(), 0, mode.getBytes().length);
				request.write(0);

				byte[] msg = request.toByteArray();

				sendToServer(msg);

				data = receiveFromServer();

				sendToClient(data);

				server1Port = 8081;

			}else if(operation == 7){
				//branch for changing the opcode in the data packet

				System.out.println("Changing Data packet's opcode...");
				byte[] data;

				data = receiveFromClient();

				if(data[1] == 1) {
					//if data[1] equals to 1, then it is a read request
					//data packet will be received from the server
					sendToServer(data);

					data = receiveFromServer();

					System.out.println("Original opcode: " + data[0] + data[1]);

					//alter the data's opcode
					data[0] = 9;
					data[1] = 9;

					System.out.println("New changed to: " + data[0] + data[1]);

					sendToClient(data);

					server1Port = 8081;

				}else if(data[1] == 2){
					//if data[1] equals to 2, then it is a write request
					//data packet will be received from the client
					//this branch will change the data packet sent from client and forward it to server
					sendToServer(data);

					data = receiveFromServer(); //server sends ack

					sendToClient(data); //forward ack to client

					data = receiveFromClient(); //receive data packet from client

					//alter the data's opcode to generate error
					data[0] = 9;
					data[1] = 9;

					sendToClient(data); //send data packet to server with altered opcode

					data = receiveFromServer(); //should receive an error packet

					sendToClient(data); //forward error packet back to client

					server1Port = 8081;
				}

			}else if(operation == 8){
				//branch for changing the block number in the data packet

				System.out.println("Changing Data packet's block number...");
				byte[] data;

				data = receiveFromClient(); //receive packet from client, should be a request packet

				if(data[1] == 1) {
					//if data[1] equals to 1, then it is a read request
					//data packet will be received from the server
					sendToServer(data); //forward request packet to client

					data = receiveFromServer();  // should be the first data packet from server

					System.out.println("Original Block Number: " + data[2] + data[3]);

					//alter the data's block number
					data[2] = 9;
					data[3] = 9;

					System.out.println("New Block Number: " + data[2] + data[3]);

					sendToClient(data); //sending the data packet with the changed block number to client

					server1Port = 8081;

				}else if(data[1] == 2){
					//if data[1] equals to 2, then it is a write request
					//data packet will be received from the client

					sendToServer(data);

					data = receiveFromServer(); //server sends ack for write request

					sendToClient(data); //forward ack to client

					data = receiveFromClient(); //receive data packet from client

					System.out.println("Original Block Number: " + data[2] + data[3]);

					//alter the data's opcode to generate error
					data[2] = 9;
					data[3] = 9;

					System.out.println("New Block Number changed to: " + data[2] + data[3]);

					sendToClient(data); //send data packet to server with altered opcode

					data = receiveFromServer(); //should receive an error packet

					sendToClient(data); //forward error packet back to client

					server1Port = 8081;
				}

			}else if(operation == 9){
				//branch for changing the opcode in the ack packet

				System.out.println("Changing Ack packet's opcode...");
				byte[] data;


				data = receiveFromClient(); //receive packet from client, should be a request packet

				if(data[1] == 1){
					//read request, first ack packet will be send from client

					sendToServer(data); //forwarding request packet to server

					data = receiveFromServer();  //receive first data packet from server

					sendToClient(data); //forward first data packet back to client

					data = receiveFromClient(); //receive the first ack packet from the client

					System.out.println("Original Opcode of Ack packet: " + data[0] + data[1]);

					data[0] = 9;
					data[1] = 9;

					System.out.println("New Opcode of Ack packet: " + data[0] + data[1]);

					System.out.println("Sending ack packet with altered opcode to server");

					sendToServer(data);

					server1Port = 8081;

				}else if(data[1] == 2){
					//write request, first ack packet will be sent by server

					sendToServer(data); //forwarding request packet to server

					data = receiveFromServer(); //receive first ack packet from server

					//change the ack packets opcode
					System.out.println("Original Opcode of Ack packet: " + data[0] + data[1]);

					data[0] = 9;
					data[1] = 9;

					System.out.println("New Opcode of Ack packet: " + data[0] + data[1]);

					sendToClient(data);

					server1Port = 8081;

				}


			}else if(operation == 10){
				//branch for changing the block number in the ack packet

				System.out.println("Changing the ack packets block number");
				byte[] data;

				data = receiveFromClient(); // receive packet from client, should be request packet

				if(data[1] == 1){
					//read request, first ack packet will be sent by client

					sendToServer(data); //forward request packet to server

					data = receiveFromServer(); //receive first data packet from server

					sendToClient(data); //forward first data packet back to client

					data = receiveFromClient(); //receive the first ack packet from client

					System.out.println("Original block number of Ack packet: " + data[2] + data[3]);

					data[2] = 9;
					data[3] = 9;

					System.out.println("New block number of Ack packet: " + data[2] + data[3]);

					sendToServer(data);

					server1Port = 8081;

				}

			}else if(operation == 11){
				//branch for delaying a request packet

				System.out.println("Delaying the request packet for 1000 milliseconds.");

				boolean packetDelayed = false;

				while(true){

					byte[] data;

					data = receiveFromClient();

					String packetType;
					packetType = getPacketType(data);

					if(packetType.equals("data") && receivePacket.getLength() < 516){
						writeFinished = true;
						System.out.println("Last Packet received, terminating write transaction. PacketLength: " + receivePacket.getLength() + " " + writeFinished);
					}

					//the first packet received from the client should be a request packet. after receiving it, sleep
					//for 1000 milliseconds then continue to send to server
					if(!packetDelayed) {
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
							e.printStackTrace();
							System.exit(1);
						}
						packetDelayed = true;
					}

					if(!requestReceived){
						requestReceived = true;
					}

					sendToServer(data);

					//check to see if transaction is finished after ack packet sent to server in read situation
					if(readFinished ){
						server1Port = 8081;
						System.out.println("Breaking Loop");
						break;
					}

					data = receiveFromServer();

					//check if transaction is finished after packet forwarded to server
					//last data packet has been received in a read request or write request
					packetType = getPacketType(data);
					if(packetType.equals("data") && receivePacket.getLength() < 516){
						readFinished = true;
						System.out.println("Last Packet received, terminating read Transaction. PacketLength: " + receivePacket.getLength() + " " + readFinished);
					}

					sendToClient(data);
					if(writeFinished ){
						server1Port = 8081;
						System.out.println("Breaking Loop");
						break;
					}

				}

				readFinished = false;
				writeFinished = false;


			}else if(operation == 12){
				//branch for delaying a data packet
				System.out.println("Delaying the first data packet for 1000 milliseconds.");

				boolean packetDelayed = false;

				while(true){

					byte[] data;
					String packetType;

					data = receiveFromClient();

					//checks to see if a request packet has been received
					if(!requestReceived){
						requestReceived = true;
					}

					packetType = getPacketType(data);
					if(packetType.equals("data") && receivePacket.getLength() < 516){
						writeFinished = true;
						System.out.println("Last Packet received, terminating write transaction. PacketLength: " + receivePacket.getLength() + " " + writeFinished);
					}


					//checks to see if packet from client is a data packet
					if(data[1] == 3){
						//if data[1] equals 3, then it is a data packet
						if(!packetDelayed) {
							try {
								Thread.sleep(1000);
							} catch (InterruptedException e) {
								e.printStackTrace();
								System.exit(1);
							}
							packetDelayed = true;
						}
					}

					sendToServer(data);

					//check to see if transaction is finished after ack packet sent to server in read situation
					if(readFinished){
						server1Port = 8081;
						System.out.println("Breaking Loop");
						break;
					}

					data = receiveFromServer();

					//checks to see if packet from client is a data packet
					if(data[1] == 3){
						//if data[1] equals 3, then it is a data packet
						if(!packetDelayed) {
							try {
								Thread.sleep(1000);
							} catch (InterruptedException e) {
								e.printStackTrace();
								System.exit(1);
							}
							packetDelayed = true;
						}
					}

					//check if transaction is finished after packet forwarded to server
					//last data packet has been received in a read request or write request
					packetType = getPacketType(data);
					if(packetType.equals("data") && receivePacket.getLength() < 516){
						readFinished = true;
						System.out.println("Last Packet received, terminating read Transaction. PacketLength: " + receivePacket.getLength() + " " + readFinished);
					}

					sendToClient(data);

					if(writeFinished){
						server1Port = 69;
						System.out.println("Breaking Loop");
						break;
					}

				}

				readFinished = false;
				writeFinished = false;
			}else if(operation == 13){
				//branch for delaying an ack packet
				System.out.println("Delaying the first ack packet for 1000 milliseconds.");

				boolean packetDelayed = false;

				while(true){

					byte[] data;
					String packetType;

					data = receiveFromClient();

					if(!requestReceived){
						requestReceived = true;
					}

					packetType = getPacketType(data);
					if(packetType.equals("data") && receivePacket.getLength() < 516){
						writeFinished = true;
						System.out.println("Last Packet received, terminating write transaction. PacketLength: " + receivePacket.getLength() + " " + writeFinished);
					}


					//checks to see if packet from client is an ack packet
					if(data[1] == 4){
						//if data[1] equals 4, then it is a ack packet
						if(!packetDelayed) {
							try {
								Thread.sleep(1000);
							} catch (InterruptedException e) {
								e.printStackTrace();
								System.exit(1);
							}
							packetDelayed = true;
						}
					}


					sendToServer(data);

					//check to see if transaction is finished after ack packet sent to server in read situation
					if(readFinished){
						server1Port = 8081;
						System.out.println("Breaking Loop");
						break;
					}

					data = receiveFromServer();

					//checks to see if packet from server is an ack packet
					if(data[1] == 4){
						//if data[1] equals 4, then it is a ack packet
						if(!packetDelayed) {
							try {
								Thread.sleep(1000);
							} catch (InterruptedException e) {
								e.printStackTrace();
								System.exit(1);
							}
							packetDelayed = true;
						}
					}

					//check if transaction is finished after packet forwarded to server
					//last data packet has been received in a read request or write request
					packetType = getPacketType(data);
					if(packetType.equals("data") && receivePacket.getLength() < 516) {
						readFinished = true;
						System.out.println("Last Packet received, terminating read Transaction. PacketLength: " + receivePacket.getLength() + " " + readFinished);
					}

					sendToClient(data);

					//check to see if transaction is finished after ack packet sent to server in read situation
					if(writeFinished){
						server1Port = 8081;
						System.out.println("Breaking Loop");
						break;
					}


				}

				readFinished = false;
				writeFinished = false;
				requestReceived = false;

			}else if(operation == 14){
				Scanner reader = new Scanner(System.in);  // Reading from System.in

				//branch for duplicating a request packet
				if (getPacketType(data).equals("request") {
					System.out.println("How many duplicates to send? ");
					int numberDuplicates = reader.nextInt();

					System.out.println("How much space between duplicates in ms ");
					int spaceBetweenDuplicates = reader.nextInt();

					for (int numDuplicates=0; numDuplicates < numberDuplicates; numDuplicates++) {
						try {
							Thread.sleep(spaceBetweenDuplicates);
						} catch (InterruptedException e) {
							e.printStackTrace();
							System.exit(1);
						}
						sendPacket(data);
					}
				}
			}else if(operation == 15){
				//branch for duplicating a data packet
				Scanner reader = new Scanner(System.in);  // Reading from System.in

				//branch for duplicating a request packet
				if (getPacketType(data).equals("data") {
					System.out.println("How many duplicates to send? ");
					int numberDuplicates = reader.nextInt();

					System.out.println("How much space between duplicates in ms ");
					int spaceBetweenDuplicates = reader.nextInt();

					for (int numDuplicates=0; numDuplicates < numberDuplicates; numDuplicates++) {
						try {
							Thread.sleep(spaceBetweenDuplicates);
						} catch (InterruptedException e) {
							e.printStackTrace();
							System.exit(1);
						}
						sendPacket(data);
					}
				}
			}else if(operation == 16){
				//branch for duplicating an ack packet
				Scanner reader = new Scanner(System.in);  // Reading from System.in

				//branch for duplicating a request packet
				if (getPacketType(data).equals("ack") {
					System.out.println("How many duplicates to send? ");
					int numberDuplicates = reader.nextInt();

					System.out.println("How much space between duplicates in ms ");
					int spaceBetweenDuplicates = reader.nextInt();

					for (int numDuplicates=0; numDuplicates < numberDuplicates; numDuplicates++) {
						try {
							Thread.sleep(spaceBetweenDuplicates);
						} catch (InterruptedException e) {
							e.printStackTrace();
							System.exit(1);
						}
						sendPacket(data);
					}
				}
			}else if(operation == 17){
				//branch for losing the request packet
			}else if(operation == 18){
				//branch for losing the first data packet
			}else if(operation == 19){
				//branch for losing an ack packet
			}

		}
	}

	/*
	Asks the user what operation they would like to do to the packets.
	 */
	private int getOperation() {
		int response = 9;
		System.out.println("What would you like do?");
		System.out.println("(0): normal operation");
		System.out.println("(1): change request packets");
		System.out.println("(2): change data packets");
		System.out.println("(3): change ack packets");
		System.out.println("(4): Delay a packet");
		System.out.println("(5): Send duplicate packet");
		System.out.println("(6): Lose a packet");
		System.out.println("(20): Close the ErrorSimulator");

		response = input.nextInt();

		System.out.println("\n");

		if (response == 0) {
			// do nothing
			System.out.println("(0): Confirm do nothing");
		} else if (response == 1) {
			System.out.println("(1)Request packets chosen.");
			System.out.println("What would you like to do to the request packets?");
			System.out.println("(4): change opcode");
			System.out.println("(5): change fileName");
			System.out.println("(6): change mode");
		} else if (response == 2) {
			System.out.println("(2)Data Packets chosen.");
			System.out.println("What would you like to do to the Data packets?");
			System.out.println("(7): change opcode");
			System.out.println("(8): change block number");
		} else if (response == 3) {
			System.out.println("(3)Acknowledgement Packets chosen.");
			System.out.println("What would you like to do to the Ack packets?");
			System.out.println("(9):  change opcode");
			System.out.println("(10): change block number");
		}else if(response == 4){
			System.out.println("(4)Delay Packet chosen");
			System.out.println("Which packet would you like to delay?");
			System.out.println("(11): Request Packet");
			System.out.println("(12): Data packet");
			System.out.println("(13): Ack packet");
		}else if(response ==5){
			System.out.println("Which packet would you like to duplicate?");
			System.out.println("(14): Request Packet");
			System.out.println("(15): Data Packet");
			System.out.println("(16): Ack Packet");
		}else if(response == 6){
			System.out.println("Which packet would you like to lose?");
			System.out.println("(17): Request Packet");
			System.out.println("(18): Data Packet");
			System.out.println("(19): Ack Packet");
		}

		else if(response == 20){
			System.out.println("ErrorSimulator Closing.");
			System.out.println("Goodbye.");
			System.exit(1);
		}

		response = input.nextInt();

		return response;
	}

	// method to return the type of packet received
	private String getPacketType(byte[] data) {

		if (data[1] == 1 || data[1] == 2) {
			System.out.println("Packet type: Request");
			return "request";
		} else if (data[1] == 3) {
			System.out.println("Packet type: data");
			return "data";
		} else if (data[1] == 4) {
			System.out.println("Packet type: ack");
			return "ack";
		}
		return "error";
	}

	private String getRequestType(byte[] data){
		if (data[1] == 1 ) {
			return "read";
		} else if (data[1] == 2) {
			return "write";
		}

		return null;
	}

	//method to receive packet from client
	private byte[] receiveFromClient(){
		// create byte array to hold packet to be received
		byte[] data = new byte[516];

		// create packet to receive data from client
		receivePacket = new DatagramPacket(data, data.length);

		try {
			// receive packet from client
			// receive() method blocks until datagram is received, data is now
			// populated with recievd packet
			System.out.println("Receiving from Client...");
			sendReceiveSocket.receive(receivePacket);
			clientAddress = receivePacket.getAddress();
			clientPort = receivePacket.getPort();

			System.out.println("Packet received from client");
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
		return data;
	}

	//method to receive packet from server
	private void sendToServer(byte[] data){
		System.out.println("Forwarding packet...");
		try {
			sendPacket = new DatagramPacket(data, receivePacket.getLength(), InetAddress.getLocalHost(), server1Port);
			System.out.println("Forwarding packet to server on port " + sendPacket.getPort());
			sendReceiveSocket.send(sendPacket);
			System.out.println("Packet forwarded.");


		} catch (IOException ioe) {
			ioe.printStackTrace();
			System.exit(1);
		}
	}

	private byte[] receiveFromServer(){
		System.out.println("\n");

		// receive response from server
		byte[] data = new byte[516];
		receivePacket = new DatagramPacket(data, data.length);
		System.out.println("Receiving from server...");

		try{
			sendReceiveSocket.receive(receivePacket);
			System.out.println("Packet received from server on port " + receivePacket.getPort());
			System.out.println("Packet size from server: " + receivePacket.getLength());
			server1Port = receivePacket.getPort();

		}catch(IOException e){
			e.printStackTrace();
		}

		return data;
	}

	private void sendToClient(byte[] data){
		// forward packet to client
		try{
			sendPacket = new DatagramPacket(data, receivePacket.getLength(), clientAddress, clientPort);
			System.out.println("Forwarding packet back to client...");
			sendReceiveSocket.send(sendPacket);
			System.out.println("Packet forwarded. \n");
		}catch(IOException e){
			e.printStackTrace();
		}
	}


	private String extractFileName(byte[] data, int dataLength) {
		int i = 1;
		StringBuilder sb = new StringBuilder();
		while (data[++i] != 0 && i < dataLength) {
			sb.append((char) data[i]);
		}

		return sb.toString();
	}


	public static void main(String[] args) {
		ErrorSimulator er1 = new ErrorSimulator();
		er1.run();

	}

>>>>>>> e26efaa4d647ab972c8e801f9d76fe0508b48a2b
}