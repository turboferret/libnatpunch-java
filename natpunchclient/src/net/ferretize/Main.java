/**
 * The MIT License (MIT)
 * Copyright (c) <year> <copyright holders>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package net.ferretize;

import net.ferretize.libnatpunch.*;

import java.io.Console;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;

public class Main {
    public static void main(String[] args) throws UnknownHostException, SocketException, InterruptedException {
        String remoteHost = "localhost";
        int remotePort = 0;
        int localPort = 30000;
        String id = "ERIKTEST";

        if(args.length > 0)
            localPort = Integer.valueOf(args[0]);
        if(args.length > 1)
            remotePort = Integer.valueOf(args[1]);
        if(args.length > 2)
            id = args[2];
        if(args.length > 3)
            remoteHost = args[3];

        NatPunch natPunch = new NatPunch();
        if(remotePort > 0)
            natPunch.setIntroducer(InetAddress.getByName(remoteHost), remotePort, id.getBytes(StandardCharsets.ISO_8859_1));
        natPunch.setNetworkLayer(new UDPNetworkLayer(localPort));
        natPunch.setPacketEncoder(new BinaryPacketEncoder());
        natPunch.setPacketReceiver(new TestPacketReceiver(natPunch));
        natPunch.startWorkerThread();

        Console console = System.console();
        EndPoint selectedEndPoint = null;
        while(true) {
            if(console != null) {
                String lineOriginal = console.readLine();
                String line = lineOriginal.toLowerCase();
                if(line.equals("exit"))
                    break;
                else if(line.startsWith("select ")) {
                    selectedEndPoint = natPunch.getEndPointHandler().findEndPoint(lineOriginal.substring(7).getBytes(StandardCharsets.ISO_8859_1));
                }
                else if(line.equals("show endpoints")) {
                    natPunch.getPacketReceiver().sendPacketDelayed(natPunch.getIntroducerEndPoint(),
                            PacketData.show(natPunch.getPacketReceiver().getAndIncrementNextMessageId(), PacketEncoder.COMMAND_SHOW_ENDPOINTS));
                }
                else if(line.equals("test small")) {
                    Random rand = new Random();
                    for(int i = 0; i < 5; ++i) {
                        byte []data = new byte[500];
                        rand.nextBytes(data);
                        natPunch.getLogHandler().info(String.format("Sending %d bytes, MD5 %s", data.length, MD5.hash(data)));
                        natPunch.getPacketReceiver().sendPacketDelayed(natPunch.getIntroducerEndPoint(),
                                new PacketData(PacketEncoder.FLAGS_GUARANTEED, natPunch.getPacketReceiver().getAndIncrementNextMessageId(),
                                        20,
                                        new byte[][]{data}));
                    }
                }
                else if(line.equals("test big")) {
                    Random rand = new Random();
                    byte []data = new byte[50000];
                    rand.nextBytes(data);
                    int messageId = natPunch.getPacketReceiver().getAndIncrementNextMessageId();
                    natPunch.getLogHandler().info(String.format("\t\tSending %d bytes / 50 parts, MD5 %s", data.length, MD5.hash(data)));
                    for(int i = 0; i < 50; ++i) {
                        byte []dataPart = Arrays.copyOfRange(data, i * 1000, i * 1000 + 1000);
               //         natPunch.getLogHandler().info(String.format("\t\tPart %d, MD5 %s", i, MD5.hash(dataPart)));
                        natPunch.getPacketReceiver().sendPacketDelayed(natPunch.getIntroducerEndPoint(),
                                new PacketData(
                                        PacketEncoder.FLAGS_GUARANTEED | PacketEncoder.FLAGS_MULTIPART,
                                        messageId, 0, i, 50, 30,
                                        new byte[][] {
                                                dataPart
                                        }
                                ));
                    }
                }
                else if(line.startsWith("punch ")) {
                    natPunch.getPacketReceiver().sendPacketDelayed(natPunch.getIntroducerEndPoint(),
                            new PacketData(
                                    PacketEncoder.FLAGS_GUARANTEED,
                                    natPunch.getPacketReceiver().getAndIncrementNextMessageId(),
                                    PacketEncoder.COMMAND_PUNCH,
                                    new byte[][] {
                                            lineOriginal.substring(6).getBytes(StandardCharsets.ISO_8859_1)
                                    }
                            ));
                }
                else if(line.equals("image")) {
                    natPunch.getPacketReceiver().sendPacketDelayed(
                            selectedEndPoint != null ? selectedEndPoint : natPunch.getIntroducerEndPoint(),
                            new PacketData(
                                    PacketEncoder.FLAGS_GUARANTEED,
                                    natPunch.getPacketReceiver().getAndIncrementNextMessageId(),
                                    TestPacketReceiver.COMMAND_IMAGE,
                                    null
                            ));
                }
                else if(line.equals("debug")) {
                    System.out.println(natPunch.getPacketReceiver().toString());
                }
            }
            Thread.sleep(1000);
        }

        natPunch.stopWorkerThread();

        /*BinaryPacketEncoder encoder = new BinaryPacketEncoder();

        byte[] data = encoder.encode(new PacketData(PacketEncoder.FLAGS_GUARANTEED | PacketEncoder.FLAGS_MULTIPART,
                1, 0, 2, 4, 25,
                new String[]{"test", "abc"}, StandardCharsets.ISO_8859_1));

        PacketData packet = encoder.decode(data);

        log(packet.toString());*/
    }
}
