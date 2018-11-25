import java.net.*;
import java.io.*;
import java.util.logging.*;

public class GroupServer extends Thread {
  // Port that the server will listen on.
  private static final int PORT = 13231;
  // Private logger for this class.
  private static Logger LOGGER = Logger.getLogger(GroupServer.class.getName());
  // Server instance that will listen for any requests.
  private DatagramSocket socket;
  // Initial buffer to read into.
  private byte[] buffer = new byte[512];
  private String[] messages;


  /**
   * Creates a new server on the PORT constant.
   * @throws IOException
   */
  private GroupServer() throws IOException {
    // Starts server and waits for a connection.
    socket = new DatagramSocket(GroupServer.PORT);
  }

  /**
   * Implementation of the Thread run function.
   */
  public void run() {
    while (true) {
      try {
        // Accept any new requests to the server.
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);

        // Retrieve the address and port to send data back to and recreate the packet to send.
        InetAddress address = packet.getAddress();
        int port = packet.getPort();

        String input = new String(packet.getData());

        if (input.equals("fail")) {

        }

        // Split initial input from input stream into file arguments.
        String[] arguments = new String(packet.getData()).split(" ", 3);

        // Validate length of input stream arguments.
        if (arguments.length != 3) {
          LOGGER.warning("input stream does not contain 3 arguments");
          continue;
        }

        // Initialize input stream arguments.
        int payload;
        int timeout;

        // Attempt to parse integer values from first two input arguments.
        try {
          payload = Integer.parseInt(arguments[0]);
          timeout = Integer.parseInt(arguments[1]);
        } catch (NumberFormatException err) {
          LOGGER.warning("cannot parse integer arguments from input stream");
          continue;
        }

        // Pull out get request name.
        String name = arguments[2];

        // Retrieve the result from the GET request.
        String result;
        try {
          result = this.retrieve(name);
        } catch (IOException err) {
          LOGGER.warning(err.getMessage());
          continue;
        }

        messages = this.segment(result, payload);
        for (String message: messages) {
          byte[] converted = message.getBytes();
          packet = new DatagramPacket(converted, converted.length, address, port);
          socket.send(packet);
        }
      }
      catch(IOException err) {
        LOGGER.severe(err.getMessage());
      }
    }
  }

  private String[] segment(String original, int limit) {
    int length = original.length() / limit;
    String[] messages = new String[length];
    int offset = limit;
    int cursor;

    for (int i = 0; i < length; i++) {
      cursor = i * limit;
      offset = offset + limit;
      messages[i] = original.substring(cursor, offset);
    }

    return messages;
  }

  private String retrieve(String address) throws IOException {
    StringBuilder result = new StringBuilder();
    URL url = new URL(address);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("GET");
    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
    String line;
    while ((line = reader.readLine()) != null) {
      result.append(line);
    }
    return result.toString();
  }

  public static void main(String args[]) {
    try {
      Thread thread = new GroupServer();
      thread.start();
    } catch (IOException err) {
      LOGGER.severe(err.getMessage());
    }
  }
}
