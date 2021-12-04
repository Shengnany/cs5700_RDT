package hw;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

public class Util {
  public static final int HASH = 65536; // 2^16

  public static void log(String msg) {
    System.out.println(msg);
  }

  public static void log(byte[] msg) {
    StringBuilder sb = new StringBuilder();
    for (byte b : msg) {
      sb.append(String.format("%02X", b));
    }
    log(sb.toString());
  }

  // Introduce random bit error to data.
  public static byte[] randomBitError(byte[] data) {
    int i = ThreadLocalRandom.current().nextInt(data.length);
    data[i] = (byte) ~data[i];
    return data;
  }

  private static byte[] createHeader(int type, int sequence) {
    byte[] header = new byte[6];
    header[0] = (byte) ((type >> 8) & 0xFF);
    header[1] = (byte) (type & 0xFF);
    header[2] = (byte) ((sequence >> 8) & 0xFF);
    header[3] = (byte) (sequence & 0xFF);
    return header;
  }

  private static int payLoadSum(byte[] data) {
    int sum = 0;
    for (byte b : data) {
      sum += b & 0xFF;
    }
    return sum;
  }

  public static byte[] createDataPacket(int type, int sequence, byte[] data) {
    byte[] packet = new byte[Config.BUFFER_SIZE];
    byte[] header = createHeader(type, sequence);
    int checksum = (type + sequence + payLoadSum(data)) % HASH;
    header[4] = (byte) ((checksum >> 8) & 0xFF);
    header[5] = (byte) (checksum & 0xFF);
    System.arraycopy(header, 0, packet, 0, header.length);
    System.arraycopy(data, 0, packet, header.length, data.length);
    return packet;
  }

  public static byte[] createAckPacket(int type, int sequence) {
    byte[] header = createHeader(type, sequence);
    int checksum = type + sequence;
    header[4] = (byte) ((checksum >> 8) & 0xFF);
    header[5] = (byte) (checksum & 0xFF);
    return header;
  }

  public static boolean validAck(byte[] data) {
    int type = (data[0] & 0xFF) << 8;
    type += data[1] & 0xFF;
    int sequence = (data[2] & 0xFF) << 8;
    sequence += data[3] & 0xFF;
    int checksum = (data[4] & 0xFF) << 8;
    checksum += data[5] & 0xFF;
    return type == Config.MSG_TYPE_ACK && checksum == type + sequence;
  }

  public static boolean validData(byte[] data) {
    int type = (data[0] & 0xFF) << 8;
    type += data[1] & 0xFF;
    int sequence = (data[2] & 0xFF) << 8;
    sequence += data[3] & 0xFF;
    int checksum = (data[4] & 0xFF) << 8;
    checksum += data[5] & 0xFF;
    int payLoad = payLoadSum(Arrays.copyOfRange(data, 6, data.length));
    return type == Config.MSG_TYPE_DATA && checksum == (type + sequence + payLoad) % HASH;
  }

  public static int getSequence(byte[] data) {
    int sequence = (data[2] & 0xFF) << 8;
    sequence += data[3] & 0xFF;
    return sequence;
  }

  public static byte[] getPayLoad(byte[] data) {
    return Arrays.copyOfRange(data, 6, data.length);
  }
}
