package net.ferretize.libnatpunch;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

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
public class BasePacketReceiver implements PacketReceiver {
    protected NatPunch natPunch;
    private int nextMessageId = 0;

    protected final PacketHandler sentPacketHandler, acknowledgedPacketHandler;

    public BasePacketReceiver(NatPunch natPunch) {
        this.natPunch = natPunch;
        sentPacketHandler = new PacketHandler();
        acknowledgedPacketHandler = new PacketHandler();
    }

    @Override
    public int getAndIncrementNextMessageId() {
        return nextMessageId++;
    }

    @Override
    public boolean handlePacket(EndPointHandler endPointHandler, EndPoint endPoint, PacketData packet) {
    //    natPunch.getLogHandler().info(String.format("Received %s from %s", packet.toString(), endPoint.toString()));

        if (packet.hasFlag(PacketEncoder.FLAGS_GUARANTEED)) {
            PacketHandler.HandledPacket handledPacket = acknowledgedPacketHandler.findPacket(endPoint, packet.messageId);
            PacketData ackPacket = PacketData.acknowledge(getAndIncrementNextMessageId(), packet.messageId, packet.retryCount);
            boolean shouldHandlePacket = false;
            if(handledPacket == null) {
              //  natPunch.getLogHandler().info(String.format("%s not found", packet.toString()));
                shouldHandlePacket = true;
                handledPacket = new PacketHandler.HandledPacket(endPoint, packet);
                acknowledgedPacketHandler.addPacket(handledPacket);
            }
            else if(packet.retryCount == 0) {
                //  The packet has been sent again. Could be because the endpoint has restarted.
                shouldHandlePacket = true;
                handledPacket.packet = packet;
            }
            sendPacket(endPoint, ackPacket);

            if(!shouldHandlePacket) {
      //          natPunch.getLogHandler().info(String.format("Already handled %s", packet.toString()));
                return true;
            }
        }
        else if(packet.hasFlag(PacketEncoder.FLAGS_MULTIPART) || packet.hasFlag(PacketEncoder.FLAGS_MULTIPART_PART)) {
            acknowledgedPacketHandler.addPacket(new PacketHandler.HandledPacket(endPoint, packet));
        }

    /*    if(packet.hasFlag(PacketEncoder.FLAGS_MULTIPART)) {

            PacketData []packetParts = acknowledgedPacketHandler.findPacketParts(endPoint, packet.messageId);
            if(packetParts.length == packet.partCount) {
                packetParts = PacketData.sortParts(packetParts);

                int bufferLength = 0;
                for(PacketData part : packetParts) {
                    bufferLength += part.getArgument(0).length;
                }

                ByteBuffer buf = ByteBuffer.allocate(bufferLength);
                int bufferPosition = 0;
                for(PacketData part : packetParts) {
                    buf.position(bufferPosition);
                    buf.put(part.getArgument(0));
                    bufferPosition += part.getArgument(0).length;
                    natPunch.getLogHandler().info(String.format("\t\t%d %d bytes, MD5 %s", part.partIndex, part.getArgument(0).length, MD5.hash(part.getArgument(0))));
                }
                natPunch.getLogHandler().info(String.format("\t\t%d bytes, MD5 %s", bufferLength, MD5.hash(buf.array())));
            }
            //natPunch.getLogHandler().info(String.format("Received %d/%d parts", packetParts.length, packet.partCount));
        }*/

        switch(packet.command) {
            case PacketEncoder.COMMAND_REGISTER:
            {
                if(packet.getArgumentCount() < 3)
                    break;

                byte []id = packet.getArgument(0);

                EndPoint otherEp = endPointHandler.findEndPoint(id);
                if(otherEp != null && !otherEp.equals(endPoint))
                    endPointHandler.removeEndPoint(otherEp);
                endPoint.id = id;
                try {
                    endPoint.localAddress = InetAddress.getByAddress(packet.getArgument(1));
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
                endPoint.localPort = ByteBuffer.wrap(packet.getArgument(2)).getInt();
                endPoint.keepAlive = true;

                sendPacket(endPoint, PacketData.response(PacketEncoder.FLAGS_GUARANTEED, getAndIncrementNextMessageId(),
                            PacketEncoder.COMMAND_REGISTER,
                            new byte[][] {
                                new byte[]{1},
                                endPoint.externalAddress.getAddress(),
                                ByteBuffer.allocate(4).putInt(endPoint.externalPort).array()
                            }
                        ));
            }
            break;
            case PacketEncoder.COMMAND_REREGISTER:
            {
                if(natPunch.getId() != null)
                    natPunch.getLogHandler().info(String.format("Registering again"));
                    sendPacket(
                            endPointHandler.getIntroducerEndPoint(),
                            PacketData.register(getAndIncrementNextMessageId(),
                                    natPunch.getId(),
                                    natPunch.getNetworkLayer().getLocalAddress().getAddress(),
                                    natPunch.getNetworkLayer().getLocalPort())
                    );
            }
            break;
            case PacketEncoder.COMMAND_PUNCH:
            {
                if(endPoint.id == null) {
                    natPunch.getLogHandler().info(String.format("Sending re-register"));
                    sendPacket(endPoint, PacketData.reRegister(getAndIncrementNextMessageId()));
                    return true;
                }

                if(packet.getArgumentCount() == 0)
                    break;

                EndPoint otherEndPoint = endPointHandler.findEndPoint(packet.getArgument(0));
                if(otherEndPoint != null) {
                    natPunch.getLogHandler().info(String.format("Punching %s for %s", otherEndPoint.toString(), endPoint.toString()));
                    //  Found endpoint for punch target
                    //  Send greet message to target and return target information
                    sendPacket(otherEndPoint, PacketData.greet(getAndIncrementNextMessageId(), endPoint));
                    sendPacket(endPoint, PacketData.response(PacketEncoder.FLAGS_GUARANTEED, getAndIncrementNextMessageId(), PacketEncoder.COMMAND_PUNCH, new byte[][] {
                            new byte[] {1},
                            otherEndPoint.id,
                            otherEndPoint.externalAddress.getAddress(),
                            ByteBuffer.allocate(4).putInt(otherEndPoint.externalPort).array(),
                            otherEndPoint.localAddress.getAddress(),
                            ByteBuffer.allocate(4).putInt(otherEndPoint.localPort).array()
                    }));
                }
                else {
                    natPunch.getLogHandler().info(String.format("Punch target %s not found", MD5.hex(packet.getArgument(0))));
                    //  Punch target not found
                    sendPacket(endPoint, PacketData.response(PacketEncoder.FLAGS_GUARANTEED, getAndIncrementNextMessageId(), PacketEncoder.COMMAND_PUNCH, new byte[][] {
                            new byte[] {0},
                            packet.getArgument(0)
                    }));
                }
            }
            break;
            case PacketEncoder.COMMAND_GREET:
            {
                if(packet.getArgumentCount() < 5)
                    break;

                byte []id = packet.getArgument(0);
                InetAddress externalAddress = null;
                try {
                    externalAddress = InetAddress.getByAddress(packet.getArgument(1));
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
                int externalPort = ByteBuffer.wrap(packet.getArgument(2)).getInt();

                InetAddress localAddress = null;
                try {
                    localAddress = InetAddress.getByAddress(packet.getArgument(3));
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
                int localPort = ByteBuffer.wrap(packet.getArgument(4)).getInt();

                EndPoint newEndPoint = endPointHandler.findEndPoint(id);
                if(newEndPoint == null) {
                    newEndPoint = new EndPoint();
                    newEndPoint.id = id;
                    endPointHandler.addEndPoint(newEndPoint);
                }
                newEndPoint.externalAddress = externalAddress;
                newEndPoint.externalPort = externalPort;
                newEndPoint.localAddress = localAddress;
                newEndPoint.localPort = localPort;

                natPunch.getLogHandler().info(String.format("Sending hello to %s", newEndPoint.toString()));

                sendPacket(newEndPoint, PacketData.hello(getAndIncrementNextMessageId(), natPunch.getLocalEndPoint()));
            }
            break;
            case PacketEncoder.COMMAND_HELLO:
            {
                natPunch.getLogHandler().info(String.format("Hello from %s", endPoint.toString()));
                if(packet.getArgumentCount() < 5)
                    break;

                byte []id = packet.getArgument(0);
                InetAddress externalAddress = null;
                try {
                    externalAddress = InetAddress.getByAddress(packet.getArgument(1));
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
                int externalPort = ByteBuffer.wrap(packet.getArgument(2)).getInt();

                InetAddress localAddress = null;
                try {
                    localAddress = InetAddress.getByAddress(packet.getArgument(3));
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
                int localPort = ByteBuffer.wrap(packet.getArgument(4)).getInt();

                EndPoint newEndPoint = endPointHandler.findEndPoint(id);
                if(newEndPoint == null) {
                    newEndPoint = new EndPoint();
                    newEndPoint.id = id;
                    endPointHandler.addEndPoint(newEndPoint);
                }
                newEndPoint.externalAddress = externalAddress;
                newEndPoint.externalPort = externalPort;
                newEndPoint.localAddress = localAddress;
                newEndPoint.localPort = localPort;
                newEndPoint.keepAlive = true;
            }
            break;
            case PacketEncoder.COMMAND_ACKNOWLEDGE:
            {
                if(packet.getArgumentCount() < 2)
                    break;

                int acknowledgedMessageId = ByteBuffer.wrap(packet.getArgument(0)).getInt();
                byte retryCount = ByteBuffer.wrap(packet.getArgument(1)).get();

                synchronized (sentPacketHandler) {
                    PacketHandler.HandledPacket ackHandledPacket = sentPacketHandler.findPacket(endPoint, acknowledgedMessageId);
                    if (ackHandledPacket != null) {
               //         natPunch.getLogHandler().info(String.format("%s acknowledged #%d after %d tries (we sent %d)", endPoint.toString(), ackHandledPacket.packet.messageId, retryCount + 1, ackHandledPacket.retryCount + 1));
                        if (!ackHandledPacket.packet.hasFlag(PacketEncoder.FLAGS_MULTIPART))
                            sentPacketHandler.removePacket(endPoint, acknowledgedMessageId);
                    }
                //    else
                    //      natPunch.getLogHandler().info(String.format("#%d not found in sent", acknowledgedMessageId));
                }
            }
            break;
            case PacketEncoder.COMMAND_SHOW:
            {
                if(packet.getArgumentCount() == 0)
                    break;

                byte subCommand = ByteBuffer.wrap(packet.getArgument(0)).get();

                switch(subCommand) {
                    case PacketEncoder.COMMAND_SHOW_ENDPOINTS:
                    {
                        ArrayList<byte[]> args = new ArrayList<>();
                        args.add(new byte[]{PacketEncoder.COMMAND_SHOW_ENDPOINTS});
                        for(EndPoint ep : endPointHandler.getEndPoints()) {
                            args.add(ep.id);
                            args.add(ep.externalAddress.getAddress());
                            args.add(ByteBuffer.allocate(4).putInt(ep.externalPort).array());
                            args.add(ep.localAddress.getAddress());
                            args.add(ByteBuffer.allocate(4).putInt(ep.localPort).array());
                        }
                        sendPacket(endPoint, PacketData.response(PacketEncoder.FLAGS_GUARANTEED, getAndIncrementNextMessageId(), PacketEncoder.COMMAND_SHOW, args.toArray(new byte[0][])));
                    }
                    break;
                }
            }
            break;
            case PacketEncoder.COMMAND_RESPONSE:
            {
                if(packet.getArgumentCount() < 1)
                    break;

                byte responseCommand = ByteBuffer.wrap(packet.getArgument(0)).get();

                switch(responseCommand) {
                    case PacketEncoder.COMMAND_REGISTER:
                    {
                        byte responseStatus = ByteBuffer.wrap(packet.getArgument(1)).get();
                        if(responseStatus == 1) {
                            InetAddress externalAddress = null;
                            try {
                                externalAddress = InetAddress.getByAddress(ByteBuffer.wrap(packet.getArgument(2)).array());
                            } catch (UnknownHostException e) {
                                e.printStackTrace();
                            }
                            int externalPort = ByteBuffer.wrap(packet.getArgument(3)).getInt();

                            natPunch.setExternalAddress(externalAddress, externalPort);
                            natPunch.getLogHandler().info(String.format("Registered successfully. External address %s:%d", externalAddress != null ? externalAddress.getHostAddress() : "null", externalPort));
                        }
                    }
                    break;
                    case PacketEncoder.COMMAND_PUNCH:
                    {
                        if(packet.getArgumentCount() < 2)
                            break;

                        byte responseStatus = ByteBuffer.wrap(packet.getArgument(1)).get();
                        if(responseStatus == 1) {
                            if(packet.getArgumentCount() >= 6) {
                                byte []id = packet.getArgument(2);
                                InetAddress externalAddress = null;
                                try {
                                    externalAddress = InetAddress.getByAddress(packet.getArgument(3));
                                } catch (UnknownHostException e) {
                                    e.printStackTrace();
                                }
                                int externalPort = ByteBuffer.wrap(packet.getArgument(4)).getInt();

                                InetAddress localAddress = null;
                                try {
                                    localAddress = InetAddress.getByAddress(packet.getArgument(5));
                                } catch (UnknownHostException e) {
                                    e.printStackTrace();
                                }
                                int localPort = ByteBuffer.wrap(packet.getArgument(6)).getInt();

                                EndPoint newEndPoint = endPointHandler.findEndPoint(id);
                                if(newEndPoint == null) {
                                    newEndPoint = new EndPoint();
                                    newEndPoint.id = id;
                                    endPointHandler.addEndPoint(newEndPoint);
                                }
                                newEndPoint.externalAddress = externalAddress;
                                newEndPoint.externalPort = externalPort;
                                newEndPoint.localAddress = localAddress;
                                newEndPoint.localPort = localPort;

                                //natPunch.getLogHandler().info(String.format("Punch ok %s", newEndPoint.toString()));

                                sendPacket(newEndPoint,
                                        PacketData.hello(getAndIncrementNextMessageId(), natPunch.getLocalEndPoint()));
                            }
                        }
                        else {
                        //    if(packet.getArgumentCount() > 2)
                          //      natPunch.getLogHandler().info(String.format("Punch failed for '%s'", MD5.hex(packet.getArgument(2))));
                        }
                    }
                    break;
                    case PacketEncoder.COMMAND_SHOW:
                    {
                        byte showCommand = ByteBuffer.wrap(packet.getArgument(1)).get();
                        switch(showCommand) {
                            case PacketEncoder.COMMAND_SHOW_ENDPOINTS:
                            {
                              //  int endPointCount = (int) Math.floor((packet.getArgumentCount() - 2) / 5);
                                for(int i = 2; i <= packet.getArgumentCount() - 5; i += 5) {
                                    byte []id = packet.getArgument(i);
                                    InetAddress externalAddress = null;
                                    try {
                                        externalAddress = InetAddress.getByAddress(packet.getArgument(i + 1));
                                    } catch (UnknownHostException e) {
                                        e.printStackTrace();
                                    }
                                    int externalPort = ByteBuffer.wrap(packet.getArgument(i + 2)).getInt();

                                    InetAddress localAddress = null;
                                    try {
                                        localAddress = InetAddress.getByAddress(packet.getArgument(i + 3));
                                    } catch (UnknownHostException e) {
                                        e.printStackTrace();
                                    }
                                    int localPort = ByteBuffer.wrap(packet.getArgument(i + 4)).getInt();

                                    natPunch.getLogHandler().info(String.format("Endpoint %s %s:%d %s:%d",
                                            new String(id, StandardCharsets.ISO_8859_1),
                                            externalAddress != null ? externalAddress.getHostAddress() : "null",
                                            externalPort,
                                            localAddress != null ? localAddress.getHostAddress() : "null",
                                            localPort));
                                }
                            }
                            break;
                        }
                    }
                    break;
                }
            }
            break;
        }
        return false;
    }

    @Override
    public void sendPacket(EndPoint endPoint, PacketData packet) {
        byte []data = natPunch.getPacketEncoder().encode(packet);
        InetAddress address = endPoint.externalAddress;
        int port = endPoint.externalPort;
        if(endPoint.localAddress != null && endPoint.isLocal(natPunch.getNetworkLayer().getLocalAddress())) {
            address = endPoint.localAddress;
            port = endPoint.localPort;
        }

        if(packet.retryCount == 0 && packet.hasFlag(PacketEncoder.FLAGS_GUARANTEED)) {
            synchronized (sentPacketHandler) {
                sentPacketHandler.addPacket(new PacketHandler.HandledPacket(endPoint, packet));
            }
        }

    //    natPunch.getLogHandler().info(String.format("Sending %s to %s:%d", packet.toString(), address.getHostAddress(), port));
        natPunch.getNetworkLayer().send(address, port, data);
    }

    @Override
    public void sendPacketDelayed(EndPoint endPoint, PacketData packet) {
        if(endPoint == null)
            throw new IllegalArgumentException("endPoint cannot be null");

        synchronized (sentPacketHandler) {
            PacketHandler.HandledPacket handledPacket = new PacketHandler.HandledPacket(endPoint, packet);
            handledPacket.timeLastSent = 0;
            handledPacket.retryCount = -1;
            sentPacketHandler.addPacket(handledPacket);
        }
    }

    @Override
    public void update(long timeNow) {
        synchronized (sentPacketHandler) {
            for (PacketHandler.HandledPacket packet : sentPacketHandler.getPacketsToResend(timeNow)) {
//                natPunch.getLogHandler().info(String.format("Retrying (%d) %s", packet.retryCount, packet.packet.toString()));

                packet.timeLastSent = timeNow;
                ++packet.retryCount;
                ++packet.packet.retryCount;
                sendPacket(packet.endPoint, packet.packet);
            }

            sentPacketHandler.removeOldPackets(timeNow);
            acknowledgedPacketHandler.removeOldPackets(timeNow);
        }
    }

    public String toString() {
        return String.format("ACK\t%s\nSENT\t%s", acknowledgedPacketHandler.toString(), sentPacketHandler.toString());
    }
}
