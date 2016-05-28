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

import java.util.Date;

/**
 *
 * @author Erik
 */
public class Packet {
    public String data;
    final public long messageId;
    public short resendCount;
    public EndPoint destination;
    public long lastSent;
    
    final private static short RESEND_INTERVAL_MS = 100;
    
    public Packet(long messageId, String data, EndPoint destination) {
        this.messageId = messageId;
        this.data = data;
        this.destination = destination;
        this.resendCount = 0;
        this.lastSent = new Date().getTime();
    }
    
    public static Packet ack(long messageId, short resendCount, EndPoint destination) {
        Packet p = new Packet(messageId, String.format("ACK %d %d\r\n", messageId, resendCount), destination);
        return p;
    }
    
    public static Packet delayed(long messageId, EndPoint destination, String data) {
        Packet p = new Packet(messageId, data, destination);
        p.resendCount = -1;
        p.lastSent = 0;
        return p;
    }
    
    public String getData() {
        return String.format("GTD %d %d %s", messageId, resendCount, data);
    }
    
    public void hasResent() {
        lastSent = new Date().getTime();
        ++resendCount;
    }
    
    public boolean shouldResend(long now) {
        return now - lastSent > RESEND_INTERVAL_MS;
    }
}
