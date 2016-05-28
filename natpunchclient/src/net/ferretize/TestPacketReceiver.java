package net.ferretize;

import net.ferretize.libnatpunch.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;

/**
 * The MIT License (MIT)
 * Copyright (c) <year> <copyright holders>
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
public class TestPacketReceiver extends BasePacketReceiver {
    public static final byte COMMAND_IMAGE = PacketEncoder.COMMAND_CUSTOM;
    public static final byte COMMAND_IMAGE_CONTINUOUS = COMMAND_IMAGE + 1;

    private Runtime runtime = Runtime.getRuntime();

    public TestPacketReceiver(NatPunch natPunch) {
        super(natPunch);
    }

    @Override
    public boolean handlePacket(EndPointHandler endPointHandler, EndPoint endPoint, PacketData packet) {
        if(super.handlePacket(endPointHandler, endPoint, packet))
            return true;

        if(packet.hasFlag(PacketEncoder.FLAGS_MULTIPART_PART)) {
            PacketHandler.HandledPacket parentPacket = acknowledgedPacketHandler.findPacket(endPoint, packet.parentMessageId);
         //   natPunch.getLogHandler().info(String.format("Looking for parent %d", packet.parentMessageId));
            if (parentPacket != null) {
                switch (parentPacket.packet.command) {
                    case PacketEncoder.COMMAND_RESPONSE: {
                        if (parentPacket.packet.getArgumentCount() == 0)
                            break;

                        byte responseCommand = ByteBuffer.wrap(parentPacket.packet.getArgument(0)).get();

                        switch (responseCommand) {
                            case COMMAND_IMAGE: {
                       //         long t1 = new Date().getTime();
                                PacketData[] packetParts = acknowledgedPacketHandler.findPacketParts(endPoint, parentPacket.packet.messageId);
                     //           long t2 = new Date().getTime();

                                if (packetParts != null) {
                                    //packetParts = PacketData.sortParts(packetParts);

                                    int bufferLength = 0;
                                    for (PacketData part : packetParts) {
                                        bufferLength += part.getArgument(0).length;
                                    }

                                    ByteBuffer buf = ByteBuffer.allocate(bufferLength);
                                    int bufferPosition = 0;
                                    for (PacketData part : packetParts) {
                                        buf.position(bufferPosition);
                                        buf.put(part.getArgument(0));
                                        bufferPosition += part.getArgument(0).length;
                                  //      natPunch.getLogHandler().info(String.format("\t\t%d %d bytes, %s MD5 %s", part.partIndex, part.getArgument(0).length, MD5.hex(part.getArgument(0), 0, 1), MD5.hash(part.getArgument(0))));
                                  //      natPunch.getLogHandler().info(String.format("\t\t%d %s", part.partIndex, MD5.hex(part.getArgument(0), 0, 20)));
                                    }

//                                    long t3 = new Date().getTime();
                               //     natPunch.getLogHandler().info(String.format("Find %d ms\nMemory %d ms", t2 - t1, t3 - t2));
//                                    natPunch.getLogHandler().info(String.format("\t\t%d bytes, MD5 %s", bufferLength, MD5.hash(buf.array())));
                                }
                                //natPunch.getLogHandler().info(String.format("Received %d/%d parts", packetParts.length, packet.partCount));
                            }
                        }
                    }
                    break;
                }
            }
            return true;
        }

        switch (packet.command) {
            case COMMAND_IMAGE_CONTINUOUS:
            {
                endPoint.setAttribute(0, new Date().getTime() + 10000);
                endPoint.setAttribute(1, (long)0);
            }
            break;
            case COMMAND_IMAGE:
            {
                sendImage(endPoint);
            }
            break;
        }
        return false;
    }

    @Override
    public void update(long timeNow) {
        super.update(timeNow);

        for(EndPoint endPoint : natPunch.getEndPointHandler().getEndPoints()) {
            long continuousImagesUntil = (long)endPoint.getAttribute(0, (long)0);
            if(continuousImagesUntil > timeNow) {
                long lastImage = (long)endPoint.getAttribute(1, (long)0);
                if((timeNow - lastImage) > 100) {
                    endPoint.setAttribute(1, timeNow);
                    sendImage(endPoint);
                }
            }
        }
    }

    public void sendImage(EndPoint endPoint) {
        long t1 = new Date().getTime();

        int raspistillPid = 0;
        Process process = null;
        try {
            process = runtime.exec("pgrep raspistill");
            byte []buffer = new byte[64];
            int readLen = process.getInputStream().read(buffer);
            if(readLen > 0) {
                raspistillPid = Integer.valueOf(new String(buffer, 0, readLen, StandardCharsets.ISO_8859_1).replace("\n", ""));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        long t2 = new Date().getTime();
      //  natPunch.getLogHandler().info(String.format("Camera time %d ms", t2 - t1));

        byte []data = null;
        int dataLength = 0;
        if(raspistillPid > 0) {
            try {
                runtime.exec(String.format("kill -USR1 %d", raspistillPid)).waitFor();
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
            }

            int sleepCount = 0;
            File file = new File("o.jpg");
            do {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if(sleepCount++ > 10)
                    break;
            }
            while(!file.exists());

            if(file.exists()) {
                FileInputStream stream = null;
                try {
                    stream = new FileInputStream(file);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }

                if(stream != null) {
                    data = new byte[100000];
                    int readLen;
                    try {
                        while((readLen = stream.read(data, dataLength, 100000 - dataLength)) > 0) {
                            dataLength += readLen;
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        return;
                    }

                    try {
                        stream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        long t3 = new Date().getTime();

        if(data != null) {
            final int partSize = 1000;
            int partCount = (int) Math.ceil(1.0 * dataLength / partSize);
            int partIndex = 0;
            int messageId = getAndIncrementNextMessageId();

            PacketData parentPacket = PacketData.multipart(PacketEncoder.FLAGS_GUARANTEED, messageId, partCount, PacketEncoder.COMMAND_RESPONSE,
                    new byte[][] {
                            new byte[]{COMMAND_IMAGE}
                    });

            natPunch.getPacketReceiver().sendPacketDelayed(endPoint, parentPacket);

            for(int p = 0; partIndex < partCount; ++partIndex) {
                int partLength = Math.min(partSize, dataLength - p);
                byte []partData = Arrays.copyOfRange(data, p, p + partLength);
                p += partLength;
                //natPunch.getLogHandler().info(String.format("\t\t%d %s", partIndex, MD5.hex(partData, 0, 20)));
                //             natPunch.getLogHandler().info(String.format("\t\t%d %d bytes, %s MD5 %s", partIndex, partData.length, MD5.hex(partData, 0, 1), MD5.hash(partData)));

                natPunch.getPacketReceiver().sendPacketDelayed(endPoint,
                        PacketData.part(0, natPunch.getPacketReceiver().getAndIncrementNextMessageId(),
                                messageId, partIndex,
                                new byte[][] {
                                        partData
                                }));
            }

            long t4 = new Date().getTime();
      //      natPunch.getLogHandler().info(String.format("Camera %d ms\tIO %d ms\tMemory %d ms", t2 - t1, t3 - t2, t4 - t3));

            //       natPunch.getLogHandler().info(String.format("\t\t%d bytes, MD5 %s", dataLength, MD5.hash(data)));
        }
    }
}
