import java.net.*;
import java.io.*;
import java.util.logging.*;

/**
 * Represents a request from the client application.
 */
class TransmitRequest {
  // Target payload size of each returned request.
  public int size;
  // Port number to send results back to.
  public int port;
  // Timeout for the request.
  public int timeout;
  // Address to send request results back to.
  public InetAddress address;
  // Target URL to retrieve data from.
  public URL target;

  /**
   * Segments the given string into an array of strings that max out at the given limit.
   * @param payload String to segment into individual strings.
   * @return Segmented strings from the original string.
   */
  private String[] splitPayload(String payload) {
    int length = payload.length() / size;
    String[] messages = new String[length];
    int offset = size;
    int cursor;

    for (int i = 0; i < length; i++) {
      cursor = i * size;
      offset = offset + size;
      messages[i] = payload.substring(cursor, offset);
    }

    return messages;
  }

  /**
   * Transforms a string of data into packets that adhere to the maximum payload size of
   * the request.
   * @param payload String data received from the performed GET request.
   * @return Segmented datagram packets adhering to the maximum payload size.
   */
  private DatagramPacket[] createPackets(String payload) {
    String[] segments = this.splitPayload(payload);
    DatagramPacket[] packets = new DatagramPacket[segments.length];
    for (int i = 0; i < segments.length; i++) {
      byte[] converted = segments[i].getBytes();
      packets[i] = new DatagramPacket(converted, converted.length, address, port);
    }
    return packets;
  }

  /**
   * Creates a request from a given UDP packet.
   * @param packet UDP packet to pull request parameters from.
   * @throws IOException If the given data packet does not match the expected arguments.
   */
  public TransmitRequest(Packet packet) throws Exception {
    // Ensure that the given packet is a Transmit packet.
    if (packet.action != Action.Transmit) {
      throw new IllegalArgumentException("transmit requests can only be made from transmit action packets");
    }

    // Parse out attributes from packet.
    address = packet.address;
    port = packet.port;
    parse(packet.data);
  }

  /**
   * Parses packet string data into object's arguments.
   * @param data Packet string data to parse.
   * @throws Exception If the arguments cannot be parsed.
   */
  private void parse(String data) throws Exception {
    // Split initial input from input stream into file arguments.
    String[] arguments = data.split(" ", 3);

    // Validate length of input stream arguments.
    if (arguments.length != 3) {
      throw new Exception("input stream does not contain 3 arguments");
    }

    // Attempt to parse integer values from first two input arguments.
    try {
      size = Integer.parseInt(arguments[0]);
      timeout = Integer.parseInt(arguments[1]);
    } catch (NumberFormatException err) {
      throw new Exception("payload size or timeout are not integer values");
    }

    // Extract the target url from the last argument.
    target = new URL(arguments[2]);
  }

  /**
   * Performs the request and returns the appropriate data packets.
   * @return Segmented data packets.
   * @throws IOException
   */
  public DatagramPacket[] perform() throws IOException {
    HttpURLConnection conn = (HttpURLConnection) target.openConnection();
    conn.setRequestMethod("GET");

    // Turn the input stream into a buffered reader to stream all data into a singular string.
    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
    StringBuilder result = new StringBuilder();
    String line;
    while ((line = reader.readLine()) != null) {
      result.append(line);
    }
    return this.createPackets(result.toString());
  }
}

/**
 * Enum representation of what a given UDP request means.
 */
enum Action {
  Failure,
  Success,
  Transmit
}

/**
 * Specific packet class created from the generalized datagram packet.
 */
class Packet {
  private static Action parse(String data) {
    switch (data) {
      case "fail":
        return Action.Failure;
      case "file OK":
        return Action.Success;
      default:
        return Action.Transmit;
    }
  }

  public int port;
  public InetAddress address;
  public String data;
  public Action action;

  /**
   * Creates a server specific packet from a generalized UDP packet.
   * @param packet UDP packet to parse data from.
   */
  public Packet(DatagramPacket packet) {
    address = packet.getAddress();
    port = packet.getPort();
    data = new String(packet.getData());
    action = Packet.parse(data);
  }
}

class GroupServerThread extends Thread {
  // Private logger for this class.
  private static Logger LOGGER = Logger.getLogger(GroupServer.class.getName());
  // Server instance that will listen for any requests.
  private DatagramSocket socket;
  // Initial buffer to read into.
  private byte[] buffer = new byte[512];
  // Last known payload information.
  private DatagramPacket[] payload;
  private boolean active = true;

  /**
   * Creates a new server on the PORT constant.
   * @throws IOException If the socket cannot be created.
   */
  public GroupServerThread(String name) throws IOException {
    super(name);
    socket = new DatagramSocket(13231);
  }

  /**
   * Performs a transmit request and and sends the contents back to the client.
   * @param packet Packet to create the request from.
   * @throws Exception If an error occurs during transmission.
   */
  private void transmit(Packet packet) throws Exception {
    TransmitRequest request = new TransmitRequest(packet);
    payload = request.perform();
    send(payload);
  }

  /**
   * Implementation of the Thread run function.
   * Waits to receive a packet from the socket, then
   */
  public void run() {
    System.out.println("Server running on localhost:13231");
    while (active) {
      try {
        // Accept any new requests to the server.
        DatagramPacket datagram = new DatagramPacket(buffer, buffer.length);
        socket.receive(datagram);

        // Transform generalized datagram packet into server specific packet.
        Packet packet = new Packet(datagram);
        switch (packet.action) {
          case Failure:
            resend();
          case Success:
            payload = null;
          case Transmit:
            transmit(packet);
        }
      } catch (IOException error) {

      }
    }
  }

  /**
   * Sends an array of data packets back across the socket to the client.
   * @param packets Array of packets to send.
   * @throws IOException If an error occurred while sending the packet.
   */
  private void send(DatagramPacket[] packets) throws IOException {
    for (DatagramPacket packet: packets) {
      socket.send(packet);
    }
  }

  /**
   * Resends the last known payload.
   * @throws Exception If the last know payload does not exist.
   */
  private void resend() throws Exception {
    if (payload == null) {
      throw new Exception("payload to retransmit does not exist");
    }
    this.send(this.payload);
  }
}


/**
 * Server that waits for UDP packets, parses them for an external HTTP GET request,
 * then proxies the contents back to the client via UDP.
 */
public class GroupServer {
  public static void main(String args[]) throws IOException {
    new GroupServerThread("group_server").start();
  }
}
