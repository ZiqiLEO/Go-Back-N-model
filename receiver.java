


import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Timer;
import java.util.TimerTask;
import java.util.LinkedList;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.util.concurrent.Semaphore;
public class receiver
{
  public static String EmulAddr;
  public static int EmulPort;
  public static int portNum;
  public static String targetFile;




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

    }

  }

  public static packet receivePacket (DatagramSocket receiveSocket) throws
    Exception
  {

    byte[] Data = new byte[500];
    DatagramPacket ack = new DatagramPacket (Data, Data.length);
      receiveSocket.receive (ack);
    packet ackPkt = packet.parseUDPdata (Data);

      return ackPkt;

  }
  public static void main (String[]args) throws Exception
  {
    EmulAddr = args[0];
    EmulPort = Integer.parseInt (args[1]);
    portNum = Integer.parseInt (args[2]);
    targetFile = args[3];
    System.out.println (portNum);
    DatagramSocket senderSocket = new DatagramSocket (EmulPort);
    DatagramSocket receiveSocket = new DatagramSocket (portNum);
    int exp_seq = 0;
    boolean done = false;
    BufferedWriter file = new BufferedWriter (new FileWriter (targetFile));
    BufferedWriter arrivalLog =
      new BufferedWriter (new FileWriter ("arrival.log"));
    int eot_seq = 0;
    boolean first = false;
      try
    {
      while (true)
	{
	  byte[]Data = new byte[512];
	  DatagramPacket ackk = new DatagramPacket (Data, Data.length);
	    receiveSocket.receive (ackk);
	  packet p = packet.parseUDPdata (Data);
	  int seqnum = p.getSeqNum ();


	  if (p.getType () != 2)
	    {
	      arrivalLog.write (String.valueOf (seqnum));
	      arrivalLog.newLine ();
	    }

	  if (seqnum == exp_seq)
	    {
	      if (p.getType () == 2)
		{

		  eot_seq = p.getSeqNum ();
		  break;
		}
	      else
		{

		  packet ack = packet.createACK (seqnum);
		  sendPacket (ack, EmulPort, EmulAddr, senderSocket);
		  String data = new String (p.getData ());
		  file.write (data);
		  ++exp_seq;
		  if (exp_seq == 32)
		    {
		      exp_seq = 0;
		    }
		}
	    }
	  else
	    {
	      int ack_seq = 0;
	      if (exp_seq == 0)
		{
		  ack_seq = 31;
		}
	      else
		{
		  ack_seq = exp_seq - 1;
		}
	      packet ack = packet.createACK (ack_seq);
	      sendPacket (ack, EmulPort, EmulAddr, senderSocket);

	    }

	}



    }
    catch (Exception e)
    {
      System.out.println ("receive error");
    }

    packet eot = packet.createEOT (eot_seq);
    sendPacket (eot, EmulPort, EmulAddr, senderSocket);

    receiveSocket.close ();
    arrivalLog.close ();
    file.close ();
  }
}
