package net.ferretize.libnatpunch;

import java.io.*;

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
public class IO {
    public static void writeAllBytes(String path, byte []data) throws IOException {
        File file = new File(path);
        FileOutputStream stream = new FileOutputStream(file);
        stream.write(data);
        stream.close();
    }

    public static void appendAllBytes(String path, byte []data) throws IOException {
        File file = new File(path);
        FileOutputStream stream = new FileOutputStream(file, true);
        stream.write(data);
        stream.close();
    }

    public static byte[] readAllBytes(String path) throws IOException {
        File file = new File(path);
        FileInputStream stream = new FileInputStream(file);
        byte []buffer = new byte[(int) file.length()];
        stream.read(buffer);
        return buffer;
    }
}
