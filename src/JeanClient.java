import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * Class representing a client request from the given input.
 */
class ClientRequest {
  private int timeout;
  private int size;
  private String filename;
  private URL target;

  /**
   * Creates a ClientRequest from a string input.
   * @param input Input read from the CLI.
   * @throws Exception If the input is incorrect.
   */
  public ClientRequest(String input) throws Exception {
    String[] arguments =  input.split(" ", 4);
    if (arguments.length < 4) {
      throw new Exception("Input must contain four separate arguments");
    }
    size = Integer.parseInt(arguments[0]);
    // Throw an error if the packet size is larger or equal to 1400.
    if (size >= 1400) {
      throw new Exception("Packet size must be lower than 1400");
    }
    timeout = Integer.parseInt(arguments[1]);
    filename = arguments[2];
    target = new URL("http://" + arguments[3]);
  }

  /**
   * Getter for the payload size parameter.
   * @return UDP payload size.
   */
  public int getSize() {
    return size;
  }

  /**
   * Getter for the target URL for the HTTP request.
   * @return UTL HTTP GET target.
   */
  public URL getTarget() {
    return target;
  }

  /**
   * Getter for the UDP request timeout.
   * @return UDP request timeout in milliseconds.
   */
  public int getTimeout() {
    return timeout;
  }

  /**
   * Getter for the filename to retrieve in the UDP request.
   * @return Filename of local server to retrieve.
   */
  public String getFilename() {
    return filename;
  }
}

/**
 * Thread that handles timing out and retrying the UDP thread.
 */
class UDPTimeoutThread extends Thread {
  private DatagramSocket socket;
  private DatagramPacket packet;
  private int timeout;

  /**
   * Creates a UDPTimeoutThread.
   * @param socket DatagramSocket to send and receive requests on.
   * @param filename Filename to retrieve on the local UDP server.
   * @param size Payload size each UDP packet should have.
   * @param timeout Timeout in milliseconds for the request to complete.
   * @throws UnknownHostException If the localhost setting cannot be found on this machine.
   */
  public UDPTimeoutThread(DatagramSocket socket, String filename, int size, int timeout) throws UnknownHostException {
    byte[] buffer = (size + " " + filename).getBytes();
    this.packet = new DatagramPacket(buffer, buffer.length, InetAddress.getLocalHost(), 13231);
    this.timeout = timeout;
    this.socket = socket;
  }

  /**
   * Implementation of the Thread.run function.
   */
  public void run() {
    try { process(); } catch(Exception error) { error.printStackTrace(); }
  }

  /**
   * Starts the UDPThread and retries the thread if the timeout occurs before all bytes area read.
   * @throws Exception If anything bad happens.
   */
  private void process() throws Exception {
    UDPThread thread = new UDPThread(socket, packet);
    System.out.println("[UDP] start");
    thread.start();
    Thread.sleep(timeout);
    thread.shutdown();

    if (!thread.successful()) {
      System.out.println("[UDP] timeout");
      System.out.println("[UDP] retry");
      packet.setData("fail".getBytes());
      thread = new UDPThread(socket, packet);
      thread.start();
      Thread.sleep(timeout * 2);
      thread.shutdown();
      if (!thread.successful()) {
        socket.send(packet);
        System.out.println("[UDP] quit");
      }
    }
  }
}

/**
 * Thread that is responsible for making UDP requests to the local server.
 */
class UDPThread extends Thread {
  private DatagramSocket socket;
  private DatagramPacket packet;
  private TreeMap<Integer, String> packets = new TreeMap<>();
  private int max;
  private boolean success = false;
  private byte[] buffer = new byte[1400];
  private boolean active = false;

  /**
   * Creates a UDPThread.
   * @param socket DatagramSocket to send and received requests.
   * @param packet Initial packet to send over the socket.
   */
  public UDPThread(DatagramSocket socket, DatagramPacket packet) {
    this.packet = packet;
    this.socket = socket;
  }

  /**
   * Implementation of Thread.run function.
   */
  public void run() {
    try { listen(); } catch (Exception error) { error.printStackTrace(); }
  }

  /**
   * Returns if the thread has completed successfully.
   * @return If the thread completed successfully.
   */
  public boolean successful() {
    return success;
  }

  /**
   * Shuts down execution of this thread.
   */
  public void shutdown() {
    this.active = false;
  }

  /**
   * Sends the initial packet over the socket, waits for all data from the server, and replies with
   * a "file OK" if everything was successful.
   * @throws Exception If anything bad happened.
   */
  private void listen() throws Exception {
    this.active = true;
    // Send initial data packet.
    this.socket.send(this.packet);
    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
    boolean done = false;
    while (!done && active) {
      socket.receive(packet);
      // Each process returns a done flag to determine if all packets came in successfully.
      if (active) { done = process(packet); }
    }
    if (!active) { return; }
    success = true;
    // Send a "file OK" message back to the server.
    packet.setData("file OK".getBytes());
    socket.send(packet);
    String value = payload();
    // Print the resulting file.
    System.out.println("[UDP] success file:\n----------\n" + value + "\n----------");
  }

  /**
   * Returns the complete payload in the correct packet order.
   * @return Payload as a string.
   */
  private String payload() {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < packets.size(); i++) {
      builder.append(packets.get(i + 1));
    }
    return builder.toString();
  }

  /**
   * Process UDP packets as they come in and add them to the current packet map tracker.
   * @param packet Packet to process
   * @return If the instance has received all possible packets.
   * @throws Exception if the received packet does not match the expected byte structure.
   */
  private boolean process(DatagramPacket packet) throws Exception {
    // Wrap the packet data in a byte buffer for easier operations.
    ByteBuffer buffer = ByteBuffer.wrap(packet.getData(), 0, packet.getLength());
    if (buffer.limit() < 5) {
      throw new Exception("Packet data must have at least 5 bytes");
    }
    // Retrieve integer value of packet number from the first four byte values.
    Integer index = buffer.getInt();
    // Retrieve flag determining if this packet is considered the last packet.
    boolean last;
    switch (buffer.get()) {
      case 0b0000000: last = false;
        break;
      case 0b1111111: last = true;
        break;
      default: throw new Exception("Packet end flag byte must be 0000000 or 1111111");
    }
    // Pull the rest of the payload as data contents. Copy the underlying buffer array to avoid
    // accidental copying of the complete array.
    String value = new String(Arrays.copyOfRange(buffer.array(), 5, buffer.limit()));
    packets.put(index, value);
    System.out.println("[UDP] received packet " + index.toString() + " " + buffer.limit() + " bytes");
    // Set the max packet count if the last flag is set.
    if (last) { max = index; }
    // Only return true if the max packet count is set and the current packet map is the same size.
    return max != 0 && max == packets.size();
  }
}

/**
 * Thread that performs an HTTP request to a given URL and transmits data to a given QueryResult.
 */
class HTTPThread extends Thread {
  private URL target;

  /**
   * Creates a new HTTPThread.
   * @param target URL to send GET request to.
   */
  public HTTPThread(URL target) {
    this.target = target;
  }

  /**
   * Implementation of the Thread.run method. Runs the main process method. If an error occurred during
   * processing, then set the HTTP thread as failed on the given query result.
   */
  public void run() {
    try { process(); } catch (Exception error) { error.printStackTrace(); }
  }

  /**
   * Performs an HTTP GET request to the stored URL target and sends the result to the given result query.
   * @throws Exception If anything occurs during connection setup, reading, or transforming.
   */
  private void process() throws Exception {
    System.out.println("[HTTP] start");
    long start = System.currentTimeMillis();
    HttpURLConnection connection = (HttpURLConnection) target.openConnection();
    connection.setRequestMethod("GET");
    InputStream stream = connection.getInputStream();
    byte[] data = stream.readAllBytes();
    long elapsed = System.currentTimeMillis() - start;
    System.out.println("[HTTP] success " + target.getHost() + " " + data.length + " bytes " + elapsed + " ms" );
  }
}

/**
 * Main method for the client portion of the application.
 */
public class JeanClient {
  public static void main(String args[]) {
    // Perform until a signal kill command is sent. This is useful for testing many different inputs.
    while (true) {
      try {
        DatagramSocket socket = new DatagramSocket();
        System.out.println(
          "Please enter the payload size, request timeout, filename, and web server URL separated by spaces " +
          "(payload_size timeout filename URL):"
        );

        ClientRequest request = null;
        boolean reading = true;
        // Read input from the command line.
        while (reading) {
          System.out.print("> ");
          Scanner scanner = new Scanner(System.in);
          String input = scanner.nextLine();
          try {
            request = new ClientRequest(input);
          } catch (Exception error) {
            System.out.println("Incorrect format: " + error.getMessage());
            continue;
          }
          reading = false;
        }

        // Start each thread and join them to the main one to wait for all input.
        Thread[] threads = new Thread[2];
        threads[0] = new HTTPThread(request.getTarget());
        threads[1] = new UDPTimeoutThread(socket, request.getFilename(), request.getSize(), request.getTimeout());

        for (Thread thread : threads) {
          thread.start();
        }
        for (Thread thread : threads) {
          thread.join();
        }
      } catch (Exception error) {
        error.printStackTrace();
      }
    }
  }
}
