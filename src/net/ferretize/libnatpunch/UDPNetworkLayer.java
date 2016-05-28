package net.ferretize.libnatpunch;

import java.io.IOException;
import java.net.*;
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
public class UDPNetworkLayer implements NetworkLayer {
    private InetAddress localAddress;
    private int localPort;
    private DatagramSocket socket;

    public UDPNetworkLayer(InetAddress localAddress, int localPort) throws SocketException {
        this.localAddress = localAddress;
        this.localPort = localPort;

        createSocket();
    }

    public UDPNetworkLayer(String localAddress, int localPort) throws UnknownHostException, SocketException {
        this.localAddress = InetAddress.getByName(localAddress);
        this.localPort = localPort;

        createSocket();
    }

    public UDPNetworkLayer(int localPort) throws UnknownHostException, SocketException {
        this.localAddress = InetAddress.getLocalHost();
        this.localPort = localPort;

        createSocket();
    }

    private void createSocket() throws SocketException {
        socket = new DatagramSocket(localPort);
        socket.setSoTimeout(50);
    }

    @Override
    public InetAddress getLocalAddress() {
        return localAddress;
    }

    @Override
    public int getLocalPort() {
        return localPort;
    }

    @Override
    public int send(InetAddress address, int port, byte[] data) {
        DatagramPacket packet = new DatagramPacket(data, 0, data.length, address, port);
        try {
            socket.send(packet);
        } catch (IOException ex) {
            return 0;
        }
        return data.length;
    }

    @Override
    public int receive(AtomicReference<InetSocketAddress> address, byte[] buffer, int bufferLength) {
        DatagramPacket packet = new DatagramPacket(buffer, bufferLength);
        try {
            socket.receive(packet);
        } catch (IOException ex) {
            return 0;
        }
        address.set((InetSocketAddress)packet.getSocketAddress());
        return packet.getLength();
    }
}
