package hw;

import static hw.Config.TIMEOUT_MSEC;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

// TODO.
public class StopAndWait extends TransportLayer {
  private Semaphore sem;
  private ScheduledFuture<?> timer;
  private ScheduledExecutorService scheduler;

  private int senderCount;
  private int senderAckSequence;
  private int receiverSequence;

  public StopAndWait(NetworkLayer networkLayer) {
    super(networkLayer);
    sem = new Semaphore(1);  // Guard to send 1 pkt at a time.
    scheduler = Executors.newScheduledThreadPool(1);
    this.senderCount = 0;
    this.senderAckSequence = -1;
    this.receiverSequence = -1;
  }

  @Override
  public void send(byte[] data) throws IOException {
    try {
      sem.acquire();
      int sequence = this.senderCount % 2;
      this.senderAckSequence = sequence;
      byte[] packet = Util.createDataPacket(Config.MSG_TYPE_DATA, sequence, data);
      networkLayer.send(Arrays.copyOf(packet, packet.length));
      System.out.println("Sender sent pkt" + sequence);
      timer = scheduler.scheduleAtFixedRate(new RetransmissionTask(packet),
          TIMEOUT_MSEC,
          TIMEOUT_MSEC,
          TimeUnit.MILLISECONDS);

    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    // Start thread to read ACK.
    new Thread(() -> {
      try {
        while (true) {
          byte[] ack = networkLayer.recv();
          if (Util.validAck(ack) && this.senderAckSequence == Util.getSequence(ack)) {
            System.out.println("Sender received ack" + Util.getSequence(ack));
            this.senderCount++;
            timer.cancel(true);
            sem.release();
            break;
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }).start();
  }

  @Override
  public byte[] recv() throws IOException {
    byte[] data = new byte[0];
    try {

      while (true) {
        data = networkLayer.recv();
        if (Util.validData(data)) {
          int sequence = Util.getSequence(data);
          System.out.println("Receiver received new packet: pkt" + sequence);
          byte[] packet = Util.createAckPacket(Config.MSG_TYPE_ACK, sequence);
          networkLayer.send(Arrays.copyOf(packet, packet.length));

          if (this.receiverSequence != sequence) {
            sem.acquire();
            this.receiverSequence = sequence;
            sem.release();
          } else {
            System.out.println("Receiver received duplicate packet: pk" + sequence + ", ignored");
            continue;
          }
          break;
        }
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    return Util.getPayLoad(data);
  }

  @Override
  public void close() throws IOException {
    try {
      sem.acquire();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    super.close();
  }

  private class RetransmissionTask implements Runnable {
    private byte[] data;

    public RetransmissionTask(byte[] data) {
      this.data = data;
    }

    @Override
    public void run() {
      try {
        networkLayer.send(Arrays.copyOf(data, data.length));
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }
}
