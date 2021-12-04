package hw;

import static hw.Config.TIMEOUT_MSEC;
import static hw.Config.WINDOW_SIZE;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

// TODO.
public class GoBackN extends TransportLayer {
  private Semaphore sem;
  private Semaphore senderLock;
  private ScheduledFuture<?> timer;
  private ScheduledExecutorService scheduler;
  private LinkedList<byte[]> senderPacketList;
  private int base;
  private int nextSequenceNum;
  private int expectedSeqNum;


  public GoBackN(NetworkLayer networkLayer) {
    super(networkLayer);
    sem = new Semaphore(WINDOW_SIZE);  // Guard to send 1 pkt at a time.
    senderLock = new Semaphore(1);
    scheduler = Executors.newScheduledThreadPool(1);

    this.senderPacketList = new LinkedList<>();
    this.base = 0;
    this.nextSequenceNum = 0;
    this.expectedSeqNum = 0;
  }

  @Override
  public void send(byte[] data) throws IOException {
    try {
      sem.acquire();
      senderLock.acquire();
      int sequence = this.nextSequenceNum % Util.HASH;
      byte[] packet = Util.createDataPacket(Config.MSG_TYPE_DATA, sequence, data);
      networkLayer.send(Arrays.copyOf(packet, packet.length));
      this.senderPacketList.add(packet);
      System.out.println("Sender sent pkt" + sequence);
      if (this.base == this.nextSequenceNum) {
        timer = scheduler.scheduleAtFixedRate(new RetransmissionTask(senderPacketList),
            TIMEOUT_MSEC,
            TIMEOUT_MSEC,
            TimeUnit.MILLISECONDS);
      }
      this.nextSequenceNum++;
      senderLock.release();

      while (true) {
        byte[] ack = networkLayer.recv();
        if (Util.validAck(ack)) {
          System.out.println("Sender received ack" + Util.getSequence(ack));
          senderLock.acquire();
          this.base = Util.getSequence(ack) + 1;
          if (this.base == this.nextSequenceNum) {
            timer.cancel(true);
          } else {
            timer.cancel(true);
            timer = scheduler.scheduleAtFixedRate(new RetransmissionTask(senderPacketList),
                TIMEOUT_MSEC,
                TIMEOUT_MSEC,
                TimeUnit.MILLISECONDS);
          }
          senderLock.release();
          sem.release();
          break;
        }
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  @Override
  public byte[] recv() throws IOException {
    byte[] data;
    while (true) {
      data = networkLayer.recv();
      if (Util.validData(data)) {
        int sequence = Util.getSequence(data);
        if (this.expectedSeqNum == sequence) {
          System.out.println("Receiver received new packet: pkt" + this.expectedSeqNum);
          byte[] packet = Util.createAckPacket(Config.MSG_TYPE_ACK, this.expectedSeqNum);
          networkLayer.send(Arrays.copyOf(packet, packet.length));
          this.expectedSeqNum++;
          break;
        } else {
          System.out.println("Receiver received pkt" + sequence + ", discard");
          byte[] packet = Util.createAckPacket(Config.MSG_TYPE_ACK, this.expectedSeqNum - 1);
          networkLayer.send(Arrays.copyOf(packet, packet.length));
        }
      }
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
    private List<byte[]> packets;

    public RetransmissionTask(List<byte[]> packets) {
      this.packets = packets;
    }

    @Override
    public void run() {
      try {
        senderLock.acquire();
        for (int i = base; i < nextSequenceNum; i++) {
          byte[] data = packets.get(i);
          networkLayer.send(Arrays.copyOf(data, data.length));
        }
        senderLock.release();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }
}
