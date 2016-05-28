/*
 * The MIT License
 *
 * Copyright 2016 Erik.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package net.ferretize.libnatpunch;

import javax.print.DocFlavor;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

/**
 *
 * @author Erik
 */
public class EndPoint {
    public byte []id;
    public InetAddress localAddress, externalAddress;
    public int localPort, externalPort;
    public long lastReceive;
    public boolean keepAlive;
    public ByteBuffer data;

    private Object []attributes;
    
    public EndPoint() {
        this.id = null;
        this.localAddress = null;
        this.externalAddress = null;
        this.localPort = 0;
        this.externalPort = 0;
        this.lastReceive = 0;
        this.keepAlive = false;
        this.data = ByteBuffer.allocate(10000);
        this.attributes = new Object[20];
        Arrays.fill(attributes, null);
    }

    public void setAttribute(int index, Object value) {
        if(index < 0 || index >= attributes.length)
            throw new IndexOutOfBoundsException();
        attributes[index] = value;
    }

    public Object getAttribute(int index, Object defaultValue) {
        if(index < 0 || index >= attributes.length)
            throw new IndexOutOfBoundsException();
        return attributes[index] != null ? attributes[index] : defaultValue;
    }

    public Object getAttribute(int index) {
        return getAttribute(index, null);
    }
    
    public boolean isLocal(InetAddress otherExternalAddress) {
        if(this.localAddress == null || this.localAddress.isLoopbackAddress())
            return false;
        return otherExternalAddress.equals(this.externalAddress);
    }

    public boolean is(byte []id) {
        return this.id != null && Arrays.equals(this.id, id);
    }

    public boolean is(InetAddress address, int port) {
        return (externalAddress != null && externalAddress.equals(address) && externalPort == port) ||
                (localAddress != null && localAddress.equals(address) && localPort == port);
    }

    public boolean is(InetSocketAddress address) {
        return is(address.getAddress(), address.getPort());
    }

    public boolean is(EndPoint other) {
        return is(other.id) || is(other.externalAddress, other.externalPort) || is(other.localAddress, other.localPort);
    }
    
    public String toString() {
        return String.format("%s %s %d %s %d", id != null ? new String(id, StandardCharsets.ISO_8859_1) : id, externalAddress != null ? externalAddress.getHostAddress() : "", externalPort,
            localAddress != null ? localAddress.getHostAddress() : "", localPort);
    }
}
