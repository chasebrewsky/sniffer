import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;

class ClientRequest {
  private int timeout;
  private int size;
  private String filename;
  private URL target;

  public ClientRequest(String input) throws Exception {
    String[] arguments =  input.split(" ", 4);
    if (arguments.length < 4) {
      throw new Exception("input must contain four separate arguments");
    }
    size = Integer.parseInt(arguments[0]);
    timeout = Integer.parseInt(arguments[1]);
    filename = arguments[2];
    target = new URL(arguments[3]);
  }

  public int getSize() {
    return size;
  }

  public URL getTarget() {
    return target;
  }

  public int getTimeout() {
    return timeout;
  }

  public String getFilename() {
    return filename;
  }
}

class UDPTimeoutThread extends Thread {
  private DatagramSocket socket;
  private DatagramPacket packet;
  private int timeout;

  public UDPTimeoutThread(DatagramSocket socket, String filename, int size, int timeout) throws UnknownHostException {
    byte[] buffer = (size + " " + filename).getBytes();
    this.packet = new DatagramPacket(buffer, buffer.length, InetAddress.getLocalHost(), 13231);
    this.timeout = timeout;
    this.socket = socket;
  }
  public void run() {
    try { process(); } catch(Exception error) { error.printStackTrace(); }
  }

  private void process() throws Exception {
    UDPThread thread = new UDPThread(socket, packet);
    System.out.println(" UDP: START");
    thread.start();
    Thread.sleep(timeout);
    thread.shutdown();

    if (!thread.successful()) {
      System.out.println(" UDP: FAILED Retrying");
      packet.setData("fail".getBytes());
      thread = new UDPThread(socket, packet);
      thread.start();
      Thread.sleep(timeout * 2);
      thread.shutdown();
      if (!thread.successful()) {
        socket.send(packet);
        System.out.println(" UDP: QUIT");
      }
    }
  }
}

class UDPThread extends Thread {
  private DatagramSocket socket;
  private DatagramPacket packet;
  private TreeMap<Integer, String> packets = new TreeMap<>();
  private int max;
  private boolean success = false;
  private byte[] buffer = new byte[1400];
  private boolean active = false;

  public UDPThread(DatagramSocket socket, DatagramPacket packet) {
    this.packet = packet;
    this.socket = socket;
  }
  public void run() {
    try { listen(); } catch (Exception error) { error.printStackTrace(); }
  }

  public boolean successful() {
    return success;
  }

  public void shutdown() {
    this.active = false;
  }

  // Wait and listen for packets.
  private void listen() throws Exception {
    this.active = true;
    this.socket.send(this.packet);
    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
    boolean done = false;
    while (!done && active) {
      System.out.println(" UDP: WAITING");
      socket.receive(packet);
      if (active) { done = process(packet); }
    }
    if (!active) { return; }
    String value = payload();
    success = true;
    System.out.println(" UDP: SUCCESS: " + value);
  }

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
    // Pull the rest of the payload as the
    String value = new String(buffer.array(), 0, buffer.limit());
    packets.put(index, value);
    System.out.println(" UDP: RECEIVED " + index.toString() + " " + value.length() + " bytes");
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
    System.out.println("HTTP: START");
    long start = System.currentTimeMillis();
    HttpURLConnection connection = (HttpURLConnection) target.openConnection();
    connection.setRequestMethod("GET");
    InputStream stream = connection.getInputStream();
    byte[] data = stream.readAllBytes();
    long elapsed = System.currentTimeMillis() - start;
    System.out.println("HTTP: SUCCESS " + elapsed + " ms " + data.length + " bytes");
  }
}

//class ClientThread extends Thread {
//  public void run() {
//    try { process(); } catch (Exception error) { error.printStackTrace(); }
//  }
//
//  private void process() throws Exception {
//    DatagramSocket socket = new DatagramSocket();
//
//    System.out.println("Client");
//    System.out.println("-------");
//    System.out.println("Please enter the payload size, request timeout, filename, and web server URL separated by spaces:");
//
//    ClientRequest request = null;
//    boolean reading = true;
//    while (reading) {
//      System.out.print("(payload, timeout, filename, URL) > ");
//      Scanner scanner = new Scanner(System.in);
//      String input = scanner.nextLine();
//      try {
//        request = new ClientRequest(input);
//      } catch (Exception error) {
//        System.out.println("Incorrect format: " + error.getMessage());
//        System.out.println("Please try again:");
//        continue;
//      }
//      reading = false;
//    }
//
//    Thread[] threads = new Thread[2];
//    threads[0] = new HTTPThread(request.getTarget());
//    threads[1] = new UDPTimeoutThread(socket, request.getFilename(), request.getSize(), request.getTimeout());
//
//    for (Thread thread : threads) {
//      thread.start();
//    }
//  }
//}

public class JeanClient {
  public static void main(String args[]) {
    while (true) {
      try {
        System.out.println();
        System.out.println("Configure");
        System.out.println("---------");

        DatagramSocket socket = new DatagramSocket();
        System.out.println("Please enter the payload size, request timeout, filename, and web server URL separated by spaces:");

        ClientRequest request = null;
        boolean reading = true;
        while (reading) {
          System.out.print("(payload, timeout, filename, URL) > ");
          Scanner scanner = new Scanner(System.in);
          String input = scanner.nextLine();
          try {
            request = new ClientRequest(input);
          } catch (Exception error) {
            System.out.println("Incorrect format: " + error.getMessage());
            System.out.println("Please try again:");
            continue;
          }
          reading = false;
        }

        System.out.println();
        System.out.println("Process");
        System.out.println("-------");

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
