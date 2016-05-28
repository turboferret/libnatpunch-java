package net.ferretize.libnatpunch;

import java.nio.ByteBuffer;
import java.util.ArrayList;

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
public class BinaryPacketEncoder implements PacketEncoder {
    static boolean hasFlag(byte flags, byte flag) {
        return (flags & flag) == flag;
    }

    @Override
    public int getPacketSize(PacketData packet) {
        int packetSize = 4 + 1 * 2;
        if(hasFlag(packet.flags, FLAGS_MULTIPART)) {
            packetSize += 1 * 2;
        }
        if(hasFlag(packet.flags, FLAGS_GUARANTEED)) {
            //  Add size of retryCount
            packetSize += 1;
        }
        if(packet.arguments != null && packet.arguments.length > 0) {
            // Size of argument count
            packetSize += 1;
            for(byte []arg : packet.arguments) {
                //  Size of argument length + argument data
                packetSize += 2 + arg.length * 1;
            }
        }
        return packetSize;
    }

    @Override
    public byte[] encode(PacketData packet) {
        //  Size of flags + messageId + command
        short bufferSize = 4 + 1 * 2;

        if(hasFlag(packet.flags, FLAGS_MULTIPART)) {
            //  Add size of partCount
            bufferSize += 1;
        }
        if(hasFlag(packet.flags, FLAGS_GUARANTEED)) {
            //  Add size of retryCount
            bufferSize += 1;
        }

        if(hasFlag(packet.flags, FLAGS_MULTIPART_PART))
            //  Add size of parentMessageId + partIndex
            bufferSize += 1 + 4;

        byte argumentCount = 0;
        if(packet.arguments != null && packet.arguments.length > 0) {
            argumentCount = (byte)packet.arguments.length;
            // Size of argument count
            bufferSize += 1;
            for(byte []arg : packet.arguments) {
                //  Size of argument length + argument data
                bufferSize += 2 + arg.length * 1;
            }
        }

        //  Allocate a buffer with an extra SHORT, to store the packet length
        ByteBuffer buffer = ByteBuffer.allocate(bufferSize + 2);
        buffer.putShort(bufferSize);
        buffer.put(packet.flags).putInt(packet.messageId);

        if(hasFlag(packet.flags, FLAGS_MULTIPART_PART))
            buffer.putInt(packet.parentMessageId);
        if(hasFlag(packet.flags, FLAGS_GUARANTEED))
            buffer.put(packet.retryCount);
        if(hasFlag(packet.flags, FLAGS_MULTIPART))
            buffer.put(packet.partCount);
        if(hasFlag(packet.flags, FLAGS_MULTIPART_PART))
            buffer.put(packet.partIndex);

        buffer.put(packet.command);
        if(argumentCount > 0) {
            buffer.put(argumentCount);
            for(byte []arg : packet.arguments) {
                buffer.putShort((short)arg.length);
                buffer.put(arg);
            }
        }
        return buffer.array();
    }

    @Override
    public PacketData decode(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        short packetSize = buffer.getShort();
        if(data.length - 2 != packetSize)
            throw new IllegalArgumentException("Data length differs from expected packet length ".concat(String.valueOf(packetSize)).concat("/").concat(String.valueOf(data.length)));

        byte flags = buffer.get();
        int messageId = buffer.getInt();
        int parentMessageId = 0;

        if(hasFlag(flags, FLAGS_MULTIPART_PART))
            parentMessageId = buffer.getInt();

        byte retryCount = 0, partIndex = 0, partCount = 0;

        if(hasFlag(flags, FLAGS_GUARANTEED)) {
            retryCount = buffer.get();
        }
        if(hasFlag(flags, FLAGS_MULTIPART)) {
            partCount = buffer.get();
        }
        if(hasFlag(flags, FLAGS_MULTIPART_PART))
            partIndex = buffer.get();

        byte command = buffer.get();
        ArrayList<byte[]> arguments = null;
        if(buffer.hasRemaining()) {
            arguments = new ArrayList<>();
            byte argumentCount = buffer.get();
            while(argumentCount-- > 0) {
                short argumentLength = buffer.getShort();
                byte []dst = new byte[argumentLength];
                buffer.get(dst, 0, argumentLength);
                arguments.add(dst);
            }
        }

        PacketData packet = new PacketData(flags, messageId, retryCount, partIndex, partCount, command, arguments != null ? arguments.toArray(new byte[0][]) : null);
        packet.parentMessageId = parentMessageId;
        return packet;
    }

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    @Override
    public PacketData hasPacket(ByteBuffer data) {
        if(data.position() < 2)
            return null;

        short packetLength = data.getShort(0);
        if(data.position() - 2 != packetLength) {
            return null;
        }

        byte []buffer = new byte[packetLength + 2];
        int positionBefore = data.position();
        data.position(0);
        data.get(buffer, 0, packetLength + 2);
        PacketData packet = decode(buffer);
        if(packet != null) {
            data.position(packetLength + 2);
            data.compact();
            data.position(positionBefore - packetLength - 2);
            return packet;
        }
        return null;
    }
}
