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

import java.util.ArrayList;

/**
 *
 * @author Erik
 */
public class PacketHandler {
    public static class HandledPacket {
        public EndPoint endPoint;
        public PacketData packet;
        public long timeLastSent;
        public short retryCount = 0;
        private boolean markedForRemoval = false;

        public HandledPacket(EndPoint endPoint, PacketData packet) {
            this.endPoint = endPoint;
            this.packet = packet;
        }

        public boolean is(EndPoint endPoint, int messageId) {
            return !markedForRemoval && this.packet.messageId == messageId && this.endPoint.is(endPoint);
        }

        public boolean isPart(EndPoint endPoint, int parentMessageId) {
            return !markedForRemoval && this.packet.parentMessageId == parentMessageId && this.endPoint.is(endPoint);
        }

        public boolean shouldRetry(long timeNow) {
            return !markedForRemoval && (timeNow - timeLastSent) > 50;
        }
    }

    final private ArrayList<HandledPacket> packets;
    
    final public static short RESEND_LIMIT = 3;
    final public static long MAX_PACKET_AGE = 10000;
    
    public PacketHandler() {
        this.packets = new ArrayList<>();
    }
    
    public HandledPacket findPacket(EndPoint ep, int messageId) {
        synchronized(packets) {
            for(HandledPacket packet : packets)
                if(packet.is(ep, messageId))
                    return packet;
        }
        return null;
    }

    public PacketData[] findPacketParts(EndPoint ep, int parentMessageId) {
        HandledPacket parentPacket = findPacket(ep, parentMessageId);
        final PacketData []parts = new PacketData[parentPacket.packet.partCount];
        synchronized (packets) {
            int foundCount = 0;
            for(HandledPacket packet : packets) {
                if(packet.isPart(ep, parentMessageId)) {
                    parts[packet.packet.partIndex] = packet.packet;
                    ++foundCount;
                }
            }
            //System.out.println(String.format("%d/%d parts", foundCount, parts.length));
            if(foundCount == parts.length)
                return parts;
        }
        return null;
    }
    
    public void removePacket(EndPoint ep, int messageId) {
        synchronized(packets) {
            for(HandledPacket packet : packets) {
                if(packet.is(ep, messageId)) {
        //            System.out.println(String.format("Packet %d removed from handler", packet.packet.messageId));
                    packets.remove(packet);
                    return;
                }
            }
        }
    }

    public void removePacketParts(EndPoint endPoint, int parentMessageId) {
        synchronized(packets) {
            for(HandledPacket packet : packets) {
                if(packet.isPart(endPoint, parentMessageId)) {
                    packet.markedForRemoval = true;
                }
            }
        }
    }
    
    public void addPacket(HandledPacket packet) {
     //   System.out.println(String.format("Packet %d added to handler", packet.packet.messageId));
        synchronized(packets) {
            packets.add(packet);
        }
    }

    public void removePacket(HandledPacket packet) {
        packet.markedForRemoval = true;
    }
    
    public void removeOldPackets(long now) {
        synchronized(packets) {
            ArrayList<HandledPacket> toRemove = new ArrayList<>();
            for(HandledPacket packet : packets) {
                if(packet.markedForRemoval)
                    toRemove.add(packet);
                else if(packet.shouldRetry(now)) {
                    if(!packet.packet.hasFlag(PacketEncoder.FLAGS_GUARANTEED) || (now - packet.timeLastSent) > MAX_PACKET_AGE)
                        toRemove.add(packet);
                }
            }
            packets.removeAll(toRemove);
        }
    }
    
    public HandledPacket[] getPacketsToResend(long now) {
        final ArrayList<HandledPacket> ret = new ArrayList<>();
        synchronized(packets) {
            ArrayList<HandledPacket> toRemove = new ArrayList<>();
            for(HandledPacket packet : packets) {
                if(packet.shouldRetry(now)) {
                    if(packet.retryCount >= RESEND_LIMIT || (!packet.packet.hasFlag(PacketEncoder.FLAGS_GUARANTEED) && packet.retryCount >= 0))
                        toRemove.add(packet);
                    else {
                        ret.add(packet);
                    }
                }
            }
            packets.removeAll(toRemove);
        }
        return ret.toArray(new HandledPacket[0]);
    }

    public String toString() {
        StringBuilder ret = new StringBuilder();
        synchronized (packets) {
            int totalPackets = packets.size();
            ret.append(String.format("%d packets in handler\n", totalPackets));
            for(HandledPacket packet : packets) {
                ret.append("\t");
                ret.append(packet.packet.toString());
                ret.append("\n");
            }
        }
        return ret.toString();
    }
}
