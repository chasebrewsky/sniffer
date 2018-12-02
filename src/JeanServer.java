import java.net.*;
import java.io.*;
import java.util.HashMap;

/**
 * Coordinates streaming bytes from an InputStream into packets on a DatagramSocket.
 * The first four bytes indicate the packet order and the fifth byte indicates
 * if that packet is the last packet in the sequence.
 */
class ServerRequestStream extends Thread {
  private static final int HEADER_LENGTH = 5;
  private DatagramSocket socket;
  private InputStream stream;
  private InetAddress address;
  private boolean active = false;
  private int limit;
  private int port;

  /**
   * Creates a new StreamCoordinator.
   * @param socket Socket to stream packets over.
   * @param stream Stream to read bytes from.
   * @param limit Packet size limit for each sent packet.
   * @param address IP address to send packets to.
   * @param port Destination port to send packets to.
   */
  public ServerRequestStream(InputStream stream, DatagramSocket socket, InetAddress address, int port, int limit) {
    this.socket = socket;
    this.stream = stream;
    this.limit = limit;
    this.address = address;
    this.port = port;
  }

  /**
   * Implementation of the Thread.run method.
   */
  public void run() {
    try { stream(); } catch (IOException error) { error.printStackTrace(); }
  }

  /**
   * Stops the currently running thread.
   */
  public void shutdown() {
    active = false;
  }

  /**
   * Streams bytes from the given input stream as packets to the given socket connection.
   * @throws IOException If a socket error occurs.
   */
  private void stream() throws IOException {
    active = true;
    // Max is the maximum amount of bytes that can be read into the buffer.
    int count = 1, max = limit - HEADER_LENGTH;
    while (active) {
      int read, position = HEADER_LENGTH;
      byte[] buffer = new byte[limit];
      // Add the index identifier to the byte buffer array.
      buffer[0] = (byte) (count >> 24);
      buffer[1] = (byte) (count >> 16);
      buffer[2] = (byte) (count >> 8);
      buffer[3] = (byte) (count);
      // Loop over the read until the buffer limit or EOF is reached, otherwise there is a chance that
      // there will be less packets in the buffer than the intended limit.
      while ((read = stream.read(buffer, position, limit - position)) != -1 && (position += read) < max) {}
      DatagramPacket packet = new DatagramPacket(buffer, position, address, port);
      // When amount is -1, that means that EOF is reached.
      // Flip the bits of the fifth byte to indicate that this is the last send packet
      // and let the parent loop exit.
      if (read == -1 || position < limit) {
        buffer[4] = 0b1111111;
        active = false;
      }
      System.out.println("[SEND] " + address.getHostAddress() + ":" + port + " packet " + count + " " + packet.getLength() + " bytes");
      socket.send(packet);
      count++;
    }
    System.out.println("[STOP] " + address.getHostAddress() + ":" + port);
  }
}

/**
 * Controller for a currently processing request. This facilitates the ability for UDP requests to be
 */
class ServerRequestController {
  private DatagramSocket socket;
  private ServerRequestStream thread;
  private InetAddress address;
  private String filename;
  private boolean retried;
  private int limit;
  private int port;

  /**
   * Creates a new ServerRequestController from a ServerRequest and a DatagramSocket
   * @param request Request to perform a transmit action on.
   * @param socket Socket to stream UDP packets to.
   * @throws Exception If the ServerRequest command is incorrect.
   */
  public ServerRequestController(ServerRequest request, DatagramSocket socket) throws Exception {
    this.address = request.getAddress();
    this.port = request.getPort();
    this.socket = socket;
    String command = request.getCommand();
    String[] args = command.split(" ", 2);
    if (args.length < 2) {
      throw new Exception("Server request requires two arguments: (packet_size, filename)");
    }
    this.limit = Integer.parseInt(args[0]);
    this.filename = args[1];
  }

  /**
   * Starts the internal ServerRequestStream.
   * @throws Exception IF anything bad happens.
   */
  public void start() throws Exception {
    thread = stream();
    thread.start();
  }

  public String ID() {
    return address.getHostAddress() + ":" + port;
  }

  /**
   * Returns the IP address attached to this controller.
   * @return IP address
   */
  public InetAddress getAddress() {
    return address;
  }

  /**
   * Creates a ServerRequestStream from the attributes on this instance.
   * @return ServerRequestStream
   * @throws FileNotFoundException If the given file cannot be found.
   */
  private ServerRequestStream stream() throws FileNotFoundException {
    return new ServerRequestStream(new FileInputStream(filename), socket, address, port, limit);
  }

  /**
   * Retries the stream again. Returns if the stream was successful in retrying. Returns false if
   * the stream was already retried.
   * @return True if it was successfully retried, False if it was already retried.
   * @throws FileNotFoundException If the file to stream was not found.
   */
  public boolean retry() throws FileNotFoundException {
    if (retried) { return false; }
    if (thread != null) { thread.shutdown(); }
    thread = stream();
    thread.start();
    return retried = true;
  }

  private void shutdown() {
    if (this.thread != null) {
      this.thread.shutdown();
      this.thread = null;
    }
  }
}

/**
 * Representation of any of the possible server requests.
 */
class ServerRequest {
  private InetAddress address;
  private String command;
  private Action action;
  private int port;

  /**
   * Possible actions of the server request.
   */
  public enum Action {
    Failure,
    Success,
    Transmit,
  }

  /**
   * Creates a new server request instance from a DatagramPacket.
   * @param packet Packet to pull request information from.
   */
  public ServerRequest(DatagramPacket packet) {
    this.port = packet.getPort();
    this.address = packet.getAddress();
    this.command = new String(packet.getData(), 0, packet.getLength());

    switch (command) {
      case "fail": this.action = Action.Failure; break;
      case "file OK": this.action = Action.Success; break;
      default: this.action = Action.Transmit;
    }
  }

  /**
   * Returns the parsed Action type.
   * @return The parsed action type.
   */
  public Action getAction() {
    return action;
  }

  /**
   * Returns a string that uniquely identifies this server request.
   * @return Unique identifier.
   */
  public String ID() {
    return this.address.getHostAddress() + ":" + this.port;
  }

  /**
   * Returns the parsed command string from the UDP packet.
   * @return Parsed command string.
   */
  public String getCommand() {
    return command;
  }

  /**
   * Returns the destination port of the remote node that made the request.
   * @return Remote ports node.
   */
  public int getPort() {
    return port;
  }

  /**
   * Returns the destination IP of the remote node that made the request.
   * @return Remote node's IP address.
   */
  public InetAddress getAddress() {
    return address;
  }
}

/**
 * Thread that runs the server process.
 */
class ServerThread extends Thread {
  private DatagramSocket socket;
  private HashMap<String, ServerRequestController> controllers = new HashMap<>();
  private byte[] buffer = new byte[256];
  private boolean running = true;

  /**
   * Creates a new server on the PORT constant.
   * @throws IOException If the socket cannot be created.
   */
  public ServerThread() throws IOException {
    socket = new DatagramSocket(13231);
  }

  /**
   * Starts the server listening on port 13231 so it can accept any requests that make it into the server.
   */
  public void run() {
    System.out.println("Server running on localhost:13231");
    while (running) {
      try {
        // Accept any new requests to the server.
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);
        ServerRequest request = new ServerRequest(packet);
        System.out.println("[RECV] " + request.ID() + " " + request.getCommand());
        switch (request.getAction()) {
          case Success: this.success(request);
            break;
          case Failure: this.failure(request);
            break;
          case Transmit: this.transmit(request);
        }
      } catch (Exception error) {
        error.printStackTrace();
      }
    }
  }

  /**
   * Handler for a failure server request.
   * Attempts to retry the past connection. If that's not possible, it removes the current controller
   * and prints a QUIT log to the console.
   * @param request Request that was marked as a failure.
   * @throws Exception If anything bad happened.
   */
  private void failure(ServerRequest request) throws Exception {
    String identifier = request.ID();
    ServerRequestController controller = controllers.get(identifier);
    if (controller != null && !controller.retry()) {
      System.out.println("[QUIT] " + controller.ID());
      controllers.remove(identifier);
    }
  }

  /**
   * Handler for a success server request.
   * Prints an OK log to the console and removes the controller from the current list of controllers.
   * @param request Request that was marked as a success.
   */
  private void success(ServerRequest request) {
    System.out.println("[SUCC] " + request.ID() + " address OK");
    controllers.remove(request.ID());
  }

  /**
   * Handler for a transmit server request.
   * Creates a new ServerRequestController and starts the controller threads.
   * @param request Request that was marked as a success.
   * @throws Exception If anything bad happened.
   */
  private void transmit(ServerRequest request) throws Exception {
    ServerRequestController controller = new ServerRequestController(request, socket);
    controllers.put(request.ID(), controller);
    controller.start();
  }
}


/**
 * Server that waits for UDP packets, parses them for an external HTTP GET request,
 * then proxies the contents back to the client via UDP.
 */
public class JeanServer {
  public static void main(String args[]) throws IOException {
    new ServerThread().start();
  }
}
