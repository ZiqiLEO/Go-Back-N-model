import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.LinkedList;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.util.concurrent.Semaphore;

public class sender
{
  public static int Window_size;
  public static int base;
  public static int nextSeq;
  public static String EmulAddr;
  public static int EmulPort;
  public static int portNum;
  public static String targetFile;
  public static DatagramSocket senderSocket;
  public static DatagramSocket receiveSocket;
  private static Semaphore lock;
  public static boolean snd_done;
  public static boolean rcv_done;
  public static BufferedWriter seqnumLog;
  public static BufferedWriter ackLog;
  public static LinkedList < packet > unackPkts;
  public static ACKListener rcv;
  public static LinkedList < packet > packets;
  public static int packet_sum;
  public static Timer timer;
  public static Sender snd;

  public static void init () throws IOException
  {
    Window_size = 10;
    base = 0;
    nextSeq = 0;
    EmulAddr = "";
    EmulPort = 0;
    portNum = 0;
    targetFile = "";
    senderSocket = null;
    receiveSocket = null;
    snd_done = false;
    rcv_done = false;
    rcv = new ACKListener ();
    snd = new Sender ();
    timer = new Timer ();
    seqnumLog = new BufferedWriter (new FileWriter ("seqnum.log"));
    ackLog = new BufferedWriter (new FileWriter ("ack.log"));



  }

  private static boolean IsWindowFull (int base, int nextSeq)
  {
    int gap = nextSeq - base;
    if (gap >= 0 && gap < Window_size)
      {
	return false;
      }
    else if (gap < 0 && (gap + 32) < Window_size)
      {
	return false;
      }
    else
      {
	return true;

      }


  }
  public static void sendPacket (packet p, int EmulPort, String EmulAddr,
				 DatagramSocket SenderSocket) throws Exception
  {
    try
    {
      byte[]Data = p.getUDPdata ();
      InetAddress EmulIP = InetAddress.getByName (EmulAddr);
      DatagramPacket pkt =
	new DatagramPacket (Data, Data.length, EmulIP, EmulPort);
        SenderSocket.send (pkt);
    } catch (Exception e)
    {
      SenderSocket.close ();
    }

  }

  public static packet receivePacket (DatagramSocket receiveSocket) throws
    Exception
  {

    byte[] Data = new byte[512];
    DatagramPacket ack = new DatagramPacket (Data, Data.length);
      receiveSocket.receive (ack);
    packet ackPkt = packet.parseUDPdata (Data);
      return ackPkt;

  }

  public static class Timeout extends TimerTask
  {
    public void run ()
    {
      try
      {
	lock.acquire ();
	for (int i = 0; i < unackPkts.size (); ++i)
	  {
	    packet p = unackPkts.get (i);
	      sendPacket (p, EmulPort, EmulAddr, senderSocket);

	    if (p.getType () != 2)
	      {
		int seqnum = p.getSeqNum ();
		  seqnumLog.write (String.valueOf (seqnum));
		  seqnumLog.newLine ();

	      }
	  }
	lock.release ();
      } catch (Exception e)
      {

      }
    }

  }

  static class ACKListener extends Thread
  {
    public void run ()
    {
      try
      {
	while (!rcv_done)
	  {
	    packet ack = receivePacket (receiveSocket);

	    if (ack.getType () == 2)
	      {
		rcv_done = true;
		timer.cancel ();
		break;
	      }
	    else
	      {
		int seqnum = ack.getSeqNum ();
		ackLog.write (String.valueOf (seqnum));
		ackLog.newLine ();

		//duplicate ack
		/*if(seqnum == base){
		   continue;
		   } */
		int gap = seqnum - base;
		if (gap >= 0 && gap < 10)
		  {
		    base = (seqnum + 1) % packet.SeqNumModulo;
		  }
		else if (seqnum < 9 && base > 22)
		  {
		    base = seqnum + 1;
		  }
		else
		  {
		    for (int i = 0; i < unackPkts.size (); ++i)
		      {

			packet p = unackPkts.get (i);
			sendPacket (p, EmulPort, EmulAddr, senderSocket);
		      }
		    timer.cancel ();
		    timer = new Timer ();
		    timer.schedule (new Timeout (), 100);

		    continue;
		  }
		//update nackpackets
		if (gap < 0)
		  {
		    gap = gap + packet.SeqNumModulo;
		  }
		lock.acquire ();
		for (int i = 0; i < gap + 1; ++i)
		  {
		    packet temp = unackPkts.removeFirst ();
		  }
		lock.release ();
		if (base == nextSeq)
		  {
		    timer.cancel ();
		  }
		else
		  {
		    timer.cancel ();
		    timer = new Timer ();
		    timer.schedule (new Timeout (), 100);
		  }

	      }

	  }
	receiveSocket.close ();
	ackLog.close ();
      }
      catch (Exception ioe)
      {
	receiveSocket.close ();
	System.out.println ("receive error");

	//Will enter here if the thread is listening for a packet
	//when we quit, and that's ok.
      }

    }
  }

  static class Sender extends Thread
  {
    public void run ()
    {
      try
      {
	int count = 0;

	while (!snd_done)
	  {
	    while (IsWindowFull (base, nextSeq))
	      {
		Thread.yield ();
	      }
	    if (base == nextSeq)
	      {
		timer.schedule (new Timeout (), 100);

	      }
	    lock.acquire ();
	    packet uackpkt = packets.get (count);
	    unackPkts.add (uackpkt);


	    sendPacket (uackpkt, EmulPort, EmulAddr, senderSocket);


	    seqnumLog.write (String.valueOf (nextSeq));
	    seqnumLog.newLine ();



	    ++count;
	    nextSeq = (nextSeq + 1) % packet.SeqNumModulo;
	    lock.release ();
	    //all regular packets sent(except eot)
	    if (count == packet_sum - 1)
	      {
		snd_done = true;
	      }

	  }
	//send eof packet
	while (true)
	  {
	    Thread.sleep (10);

	    if (base == nextSeq)
	      {


		packet eot = packets.get (packet_sum - 1);
		sendPacket (eot, EmulPort, EmulAddr, senderSocket);
		break;
	      }
	  }
	senderSocket.close ();
	seqnumLog.close ();

      }
      catch (Exception e)
      {
	senderSocket.close ();
	try
	{
	  seqnumLog.close ();
	}
	catch (IOException e1)
	{
	  // TODO Auto-generated catch block
	  e1.printStackTrace ();
	}
	System.out.println ("sender error");

      }

    }

  }

  public static void main (String[]args) throws Exception
  {
    init ();
    base = 0;
    nextSeq = 0;
    boolean done = false;
      EmulAddr = args[0];
      EmulPort = Integer.parseInt (args[1]);
      portNum = Integer.parseInt (args[2]);
      targetFile = args[3];
      senderSocket = new DatagramSocket (EmulPort);
      receiveSocket = new DatagramSocket (portNum);
      lock = new Semaphore (1);
      unackPkts = new LinkedList < packet > ();



    //All packets to be sent.
      packets = new LinkedList < packet > ();
    BufferedReader fileReader =
      new BufferedReader (new FileReader (targetFile));
    //chunk target file to packets.
    int Seq_count = 0;
      packet_sum = 0;
      try
    {
      while (true)
	{
	  if (Seq_count == 32)
	    {
	      Seq_count = 0;
	    }
	  char[] chunk = new char[packet.maxDataLength];

	  int readfile = fileReader.read (chunk, 0, packet.maxDataLength);
	  if (readfile == -1)
	    {
	      packet Packet_EOT = packet.createEOT (Seq_count);
	      packets.add (Packet_EOT);
	      ++packet_sum;
	      fileReader.close ();
	      break;
	    }
	  else
	    {
	      packet p =
		packet.createPacket (Seq_count,
				     new String (chunk, 0, readfile));
	      packets.add (p);
	      ++Seq_count;
	      ++packet_sum;
	    }
	}
    }
    catch (Exception e)
    {
      System.out.println ("readfile error");
    }
    //send packets to emulator/

    snd.start ();

    rcv.start ();






  }


}
