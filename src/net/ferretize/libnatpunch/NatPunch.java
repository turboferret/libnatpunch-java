package net.ferretize.libnatpunch;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

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
public class NatPunch implements Runnable {
    private LogHandler logHandler;
    public void setLogHandler(LogHandler logHandler) {
        this.logHandler = logHandler;
    }
    public LogHandler getLogHandler() {
        return logHandler;
    }

    private NetworkLayer networkLayer;
    public void setNetworkLayer(NetworkLayer networkLayer) {
        this.networkLayer = networkLayer;
    }
    public NetworkLayer getNetworkLayer() {
        return networkLayer;
    }

    private PacketEncoder packetEncoder;
    public void setPacketEncoder(PacketEncoder packetEncoder) {
        this.packetEncoder = packetEncoder;
    }
    public PacketEncoder getPacketEncoder() {
        return packetEncoder;
    }

    private PacketReceiver packetReceiver;
    public void setPacketReceiver(PacketReceiver packetReceiver) {
        this.packetReceiver = packetReceiver;
    }
    public PacketReceiver getPacketReceiver() {
        return packetReceiver;
    }

    private InetAddress introducerAddress = null;
    private int introducerPort = 0;

    private InetAddress externalAddress = null;
    private int externalPort = 0;

    private boolean workerThreadRunning = false;
    private final Object workerThreadLock = new Object();

    private EndPointHandler endPointHandler;
    public EndPointHandler getEndPointHandler() {
        return endPointHandler;
    }

    private byte []id;
    public byte[] getId() {
        return id;
    }

    public NatPunch() {
        setLogHandler(new StdoutLogHandler());

        endPointHandler = new EndPointHandler();

    }

    public void setIntroducer(InetAddress address, int port, byte []id) {
        this.introducerAddress = address;
        this.introducerPort = port;
        this.id = id;

        EndPoint endPoint = new EndPoint();
        endPoint.externalAddress = introducerAddress;
        endPoint.externalPort = introducerPort;
        endPointHandler.setIntroducerEndPoint(endPoint);
    }

    public EndPoint getIntroducerEndPoint() {
        if(introducerAddress == null)
            return null;
        return endPointHandler.findEndPoint(introducerAddress, introducerPort);
    }

    public void setExternalAddress(InetAddress address, int port) {
        this.externalAddress = address;
        this.externalPort = port;
    }

    public EndPoint getLocalEndPoint() {
        EndPoint endPoint = new EndPoint();
        endPoint.id = id;
        endPoint.externalAddress = externalAddress;
        endPoint.externalPort = externalPort;
        endPoint.localAddress = networkLayer.getLocalAddress();
        endPoint.localPort = networkLayer.getLocalPort();
        return endPoint;
    }

    public void startWorkerThread() {
        synchronized (workerThreadLock) {
            if(!workerThreadRunning) {
                workerThreadRunning = true;
                Thread thread = new Thread(this);
                thread.start();
            }
        }
    }

    public void stopWorkerThread() {
        synchronized (workerThreadLock) {
            workerThreadRunning = false;
        }
    }

    @Override
    public void run() {
        long timeNow = new Date().getTime(), timeLastPing = timeNow, lastCheckWorkerThreadRunning = timeNow;
        int receiveLength;
        AtomicReference<InetSocketAddress> receiveAddress = new AtomicReference<>();
        byte []receiveBuffer = new byte[2048];
        EndPoint receiveEndPoint;
        boolean shouldSleep;

        if(introducerAddress != null) {
            packetReceiver.sendPacket(
                    endPointHandler.getIntroducerEndPoint(),
                    PacketData.register(packetReceiver.getAndIncrementNextMessageId(),
                            id,
                            networkLayer.getLocalAddress().getAddress(),
                            networkLayer.getLocalPort())
            );
        }

        while(true) {
            timeNow = new Date().getTime();
            if((timeNow - lastCheckWorkerThreadRunning) > 5000) {
                lastCheckWorkerThreadRunning = timeNow;
                synchronized (workerThreadLock) {
                    if(!workerThreadRunning)
                        break;
                }
            }

            shouldSleep = true;

            while((receiveLength = networkLayer.receive(receiveAddress, receiveBuffer, 2048)) > 0) {
                receiveEndPoint = endPointHandler.findEndPoint(receiveAddress.get());
                if(receiveEndPoint == null) {
                    receiveEndPoint = new EndPoint();
                    receiveEndPoint.externalAddress = receiveAddress.get().getAddress();
                    receiveEndPoint.externalPort = receiveAddress.get().getPort();
                    endPointHandler.addEndPoint(receiveEndPoint);
                }
                receiveEndPoint.lastReceive = new Date().getTime();
                receiveEndPoint.data.put(receiveBuffer, 0, receiveLength);

                PacketData packet;
                while((packet = packetEncoder.hasPacket(receiveEndPoint.data)) != null) {
                    packetReceiver.handlePacket(endPointHandler, receiveEndPoint, packet);
                }

                //receiveEndPoint.data.append(new String(, 0, recvLen, StandardCharsets.UTF_8));
                //checkEndPointData(recvEndPoint);

                shouldSleep = false;
            }

            packetReceiver.update(timeNow);

            if((timeNow - timeLastPing) > 30000) {
                timeLastPing = timeNow;

                for(EndPoint ep : endPointHandler.getEndPoints()) {
                    if(ep.keepAlive) {
                        packetReceiver.sendPacket(ep, new PacketData(0, packetReceiver.getAndIncrementNextMessageId(),
                                PacketEncoder.COMMAND_NOP, null));
                    }
                }
            }

            if(shouldSleep) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

        }
    }
}
