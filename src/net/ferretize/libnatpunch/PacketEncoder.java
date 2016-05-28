package net.ferretize.libnatpunch;

import java.nio.ByteBuffer;

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
public interface PacketEncoder {
    byte FLAGS_GUARANTEED = 0x1;
    byte FLAGS_MULTIPART = 0x2;
    byte FLAGS_MULTIPART_PART = 0x4;

    byte COMMAND_NOP = 0;
    byte COMMAND_ACKNOWLEDGE = 1;
    byte COMMAND_REGISTER = 2;
    byte COMMAND_PUNCH = 3;
    byte COMMAND_RESPONSE = 4;
    byte COMMAND_GREET = 5;
    byte COMMAND_HELLO = 6;
    byte COMMAND_SHOW = 7;
    byte COMMAND_REREGISTER = 8;
    byte COMMAND_CUSTOM = 50;

    byte COMMAND_SHOW_ENDPOINTS = 0;

    int getPacketSize(PacketData packet);

    byte[] encode(PacketData packet);
    PacketData decode(byte []data);

    PacketData hasPacket(ByteBuffer data);
}
