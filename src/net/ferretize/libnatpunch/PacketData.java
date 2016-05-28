package net.ferretize.libnatpunch;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;

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
public class PacketData {
    public byte flags, retryCount, partIndex, partCount, command;
    public int messageId, parentMessageId;
    public byte [][]arguments;

    public PacketData(byte flags, int messageId, byte retryCount, byte partIndex, byte partCount, byte command, byte [][]arguments) {
        this.flags = flags;
        this.messageId = messageId;
        this.retryCount = retryCount;
        this.partIndex = partIndex;
        this.partCount = partCount;
        this.command = command;
        this.arguments = arguments;
    }

    public PacketData(int flags, int messageId, int retryCount, int partIndex, int partCount, int command, byte [][]arguments) {
        this.flags = (byte)flags;
        this.messageId = messageId;
        this.retryCount = (byte)retryCount;
        this.partIndex = (byte)partIndex;
        this.partCount = (byte)partCount;
        this.command = (byte)command;
        this.arguments = arguments;
    }

    public PacketData(int flags, int messageId, int command, byte [][]arguments) {
        this.flags = (byte)flags;
        this.messageId = messageId;
        this.retryCount = (byte)0;
        this.partIndex = (byte)0;
        this.partCount = (byte)0;
        this.command = (byte)command;
        this.arguments = arguments;
    }

    public static PacketData reRegister(int messageId) {
        return new PacketData(PacketEncoder.FLAGS_GUARANTEED,
                messageId, PacketEncoder.COMMAND_REREGISTER, null);
    }

    public static PacketData greet(int messageId, EndPoint otherEndPoint) {
        if(otherEndPoint == null)
            throw new IllegalArgumentException("otherEndPoint cannot be null");
        return new PacketData(PacketEncoder.FLAGS_GUARANTEED,
                messageId,
                PacketEncoder.COMMAND_GREET,
                new byte[][] {
                        otherEndPoint.id,
                        otherEndPoint.externalAddress != null ? otherEndPoint.externalAddress.getAddress() : null,
                        ByteBuffer.allocate(4).putInt(otherEndPoint.externalPort).array(),
                        otherEndPoint.localAddress != null ? otherEndPoint.localAddress.getAddress() : null,
                        ByteBuffer.allocate(4).putInt(otherEndPoint.localPort).array()
                }
                );
    }

    public static PacketData hello(int messageId, EndPoint endPoint) {
        PacketData packet = greet(messageId, endPoint);
        packet.command = PacketEncoder.COMMAND_HELLO;
        return packet;
    }

    public static PacketData response(int flags, int messageId, byte responseCommand, byte [][]arguments) {
        byte[][] args = Arrays.copyOf(new byte[][]{new byte[]{responseCommand}}, 1 + (arguments != null ? arguments.length : 0));
        if(arguments != null && arguments.length > 0)
            System.arraycopy(arguments, 0, args, 1, arguments.length);
        return new PacketData(flags, messageId, PacketEncoder.COMMAND_RESPONSE, args);
    }

    public static PacketData show(int messageId, byte showCommand) {
        return new PacketData(PacketEncoder.FLAGS_GUARANTEED, messageId, PacketEncoder.COMMAND_SHOW,
                new byte[][] {
                   new byte[] {showCommand}
                });
    }

    public static PacketData acknowledge(int messageId, int acknowledgedMessageId, int retryCount) {
        return new PacketData(0, messageId, PacketEncoder.COMMAND_ACKNOWLEDGE,
                new byte[][] {
                        ByteBuffer.allocate(4).putInt(acknowledgedMessageId).array(),
                        ByteBuffer.allocate(4).putInt(retryCount).array()
                }
                );
    }

    public static PacketData register(int messageId, byte []id, byte []localAddress, int localPort) {
        return new PacketData(PacketEncoder.FLAGS_GUARANTEED, messageId, PacketEncoder.COMMAND_REGISTER,
                new byte[][] {
                        id,
                        localAddress,
                        ByteBuffer.allocate(4).putInt(localPort).array()
                }
                );
    }

    public static PacketData multipart(int flags, int messageId, int partCount, int command, byte [][]arguments) {
        return new PacketData(PacketEncoder.FLAGS_MULTIPART | flags, messageId,
                0, 0, partCount, command, arguments);
    }

    public static PacketData part(int flags, int messageId, int parentMessageId, int partIndex, byte [][]arguments) {
        PacketData packet = new PacketData(PacketEncoder.FLAGS_MULTIPART_PART | flags, messageId, 0, partIndex, 0, 0, arguments);
        packet.parentMessageId = parentMessageId;
        return packet;
    }

    public PacketData(int flags, int messageId, int retryCount, int partIndex, int partCount, int command, String []arguments, Charset charset) {
        this.flags = (byte)flags;
        this.messageId = messageId;
        this.retryCount = (byte)retryCount;
        this.partIndex = (byte)partIndex;
        this.partCount = (byte)partCount;
        this.command = (byte)command;
        this.arguments = null;

        if(arguments != null) {
            ArrayList<byte[]> argList = new ArrayList<>();
            for(String arg : arguments) {
                argList.add(charset.encode(arg).array());
            }
            this.arguments = argList.toArray(new byte[0][]);
        }
    }

    public int getArgumentCount() {
        return arguments != null ? arguments.length : 0;
    }

    public byte[] getArgument(int index) {
        if(arguments == null || index >= arguments.length)
            return null;
        return arguments[index];
    }

    public boolean hasFlag(byte flag) {
        return (flags & flag) == flag;
    }

    public static String commandToString(byte command) {
        switch(command) {
            case PacketEncoder.COMMAND_ACKNOWLEDGE:
                return "ACKNOWLEDGE";
            case PacketEncoder.COMMAND_GREET:
                return "GREET";
            case PacketEncoder.COMMAND_HELLO:
                return "HELLO";
            case PacketEncoder.COMMAND_PUNCH:
                return "PUNCH";
            case PacketEncoder.COMMAND_REGISTER:
                return "REGISTER";
            case PacketEncoder.COMMAND_RESPONSE:
                return "RESPONSE";
            case PacketEncoder.COMMAND_SHOW:
                return "SHOW";
            case PacketEncoder.COMMAND_NOP:
                return "NOP";
        }
        return String.valueOf((int)command);
    }

    public static PacketData[] sortParts(PacketData []parts) {
        PacketData []ret = new PacketData[parts[0].partCount];
        Arrays.fill(ret, null);
        for(PacketData part : parts) {
            if(part.partIndex >= ret.length)
                throw new IllegalArgumentException();
            if(ret[part.partIndex] != null)
                continue;
//                throw  new IllegalArgumentException();
            ret[part.partIndex] = part;
        }
        return ret;
    }

    public String toString() {
        StringBuilder ret = new StringBuilder(String.format("FLAGS=%d MESSAGEID=%d", flags, messageId));
        if(BinaryPacketEncoder.hasFlag(flags, PacketEncoder.FLAGS_GUARANTEED)) {
            ret.append(String.format(" RETRYCOUNT=%d", retryCount));
        }
        if(BinaryPacketEncoder.hasFlag(flags, PacketEncoder.FLAGS_MULTIPART)) {
            ret.append(String.format(" PARTINDEX=%d PARTCOUNT=%d", partIndex, partCount));
        }
        ret.append(String.format(" COMMAND=%s ARGUMENTS=%d", commandToString(command), arguments != null ? arguments.length : 0));
        return ret.toString();
    }
}
