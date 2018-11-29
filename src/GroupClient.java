import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.util.Scanner;

class Request {
  public int timeout;
  public int size;
  public URL target;
  public Request(String input) throws Exception {
    String[] arguments =  input.split(" ", 3);
    if (arguments.length < 3) {
      throw new Exception("input must contain three separate arguments");
    }
    size = Integer.parseInt(arguments[0]);
    timeout = Integer.parseInt(arguments[1]);
    target = new URL(arguments[2]);
  }

  public String toString() {
    return size + " " + timeout + " " + target.toString();
  }
}

class Result {
  private String HTTP = null;
  private String UDP = null;
  private boolean failed = false;

  public void setHTTP(String HTTP) { this.HTTP = HTTP; }

  public void setUDP(DatagramPacket[] packets) {
    StringBuilder builder = new StringBuilder();
    for (DatagramPacket packet : packets) {
      builder.append(packet.toString());
    }
    UDP = builder.toString();
  }

  public void timeout() { failed = true; }

  public boolean timedout() { return failed; }

  public boolean equals() {
    if (HTTP == null || UDP == null) { return false; }
    return HTTP.equals(UDP);
  }
}

class UDPThread extends Thread {
  private Result result;
  public UDPThread(DatagramSocket socket, Request request, Result result) throws IOException {

  }
}

class HTTPThread extends Thread {
  private URL target;
  private Result result;

  public HTTPThread(Request request, Result result) {
    this.target = request.target;
    this.result = result;
  }

  public void run() {
    try { process(); } catch (Exception error) { error.printStackTrace(); }
  }

  public void process() throws Exception {
    HttpURLConnection conn = (HttpURLConnection) target.openConnection();
    conn.setRequestMethod("GET");

    // Turn the input stream into a buffered reader to stream all data into a singular string.
    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
    StringBuilder result = new StringBuilder();
    String line;
    while ((line = reader.readLine()) != null) {
      result.append(line);
    }
    this.result.setUDP(this.createPackets(result.toString()));
  }

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
}

class GroupClientThread extends Thread {
  private DatagramSocket socket;
  private Result result = new Result();

  public void run() {
    try { process(); } catch (Exception error) { error.printStackTrace(); }
  }

  private void process() throws Exception {
    socket = new DatagramSocket();

    System.out.println("UDP Client");
    System.out.println("----------");
    System.out.println("Please enter the payload size, request timeout, and web server URL separated by spaces:");

    Request request = read();
    spawn(request);

    if (result.timedout() && !retryUDP(request)) {
      System.out.println("quit");
      return;
    }

    if (!result.equals()) {
      System.out.println("Results match!");
    } else {
      System.out.println("Results do not match!");
    }
  }

  private void spawn(Request request) throws IOException, InterruptedException {
    Thread[] threads = new Thread[2];
    threads[0] = processUDP(request);
    threads[1] = processHTTP(request);
    for (Thread thread : threads) {
      thread.start();
    }
    for (Thread thread : threads) {
      thread.join();
    }
  }

  private boolean retryUDP(Request request) throws IOException, InterruptedException {
    send("fail");
    request.timeout = request.timeout * 2;
    Thread thread = processUDP(request);
    thread.start();
    thread.join();
    if (result.timedout()) {
      send("fail");
      return false;
    }
    return true;
  }

  private UDPThread processUDP(Request request) throws IOException {
    return new UDPThread(socket, request, result);
  }

  private HTTPThread processHTTP(Request request) {
    return new HTTPThread(request, result);
  }

  private void send(String data) throws IOException {
    byte[] message = data.getBytes();
    DatagramPacket packet = new DatagramPacket(message, message.length, InetAddress.getLocalHost(), 13231);
    socket.send(packet);
  }

  private void fail() throws IOException {
    send("fail");
  }

  private static Request read() {
    Request request = null;
    boolean reading = true;
    while (reading) {
      System.out.print("(payload, timeout, URL) > ");
      Scanner scanner = new Scanner(System.in);
      String input = scanner.nextLine();
      try {
        request = new Request(input);
      } catch (Exception error) {
        System.out.println("Incorrect format: " + error.getMessage());
        System.out.println("Please try again:");
        continue;
      }
      reading = false;
    }
    return request;
  }
}

public class GroupClient {
  private DatagramSocket socket;
  public static void main(String args[]) throws IOException {
    new GroupClientThread().run();
    socket = new DatagramSocket();

    System.out.println("UDP Client");
    System.out.println("----------");
    System.out.println("Please enter the payload size, request timeout, and web server URL separated by spaces:");


    byte[] buffer = request.toString().getBytes();
    DatagramPacket packet = new DatagramPacket(buffer, buffer.length, InetAddress.getLocalHost(), 13231);
    socket.send(packet);

    Result result = new Result();
    UDPThread udp = new UDPThread(socket, packet, request.timeout, result);
    HTTPThread http = new HTTPThread(request.target, result);

    udp.start();
    http.start();
    udp.join();
    http.join();

    if (result.timedout()) {

      udp = new UDPThread(socket, packet, request.timeout * 2, result);
      udp.start();
      udp.join();

      if (result.timedout()) {
        buffer = new String("")
        packet = new Data
        socket
      }
    }

    if (result.equals()) {
      System.out.println("Result match!");
      return;
    }


  }
}
