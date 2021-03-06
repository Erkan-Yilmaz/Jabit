/*
 * Copyright 2015 Christian Basler
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ch.dissem.bitmessage.utils;

import ch.dissem.bitmessage.entity.Streamable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import static ch.dissem.bitmessage.utils.AccessCounter.inc;

/**
 * This class handles encoding simple types from byte stream, according to
 * https://bitmessage.org/wiki/Protocol_specification#Common_structures
 */
public class Encode {
    public static void varIntList(long[] values, OutputStream stream) throws IOException {
        varInt(values.length, stream);
        for (long value : values) {
            varInt(value, stream);
        }
    }

    public static void varInt(long value, OutputStream stream) throws IOException {
        varInt(value, stream, null);
    }

    public static void varInt(long value, OutputStream stream, AccessCounter counter) throws IOException {
        if (value < 0) {
            // This is due to the fact that Java doesn't really support unsigned values.
            // Please be aware that this might be an error due to a smaller negative value being cast to long.
            // Normally, negative values shouldn't occur within the protocol, and I large enough longs
            // to being recognized as negatives aren't realistic.
            stream.write(0xff);
            inc(counter);
            int64(value, stream, counter);
        } else if (value < 0xfd) {
            int8(value, stream, counter);
        } else if (value <= 0xffffL) {
            stream.write(0xfd);
            inc(counter);
            int16(value, stream, counter);
        } else if (value <= 0xffffffffL) {
            stream.write(0xfe);
            inc(counter);
            int32(value, stream, counter);
        } else {
            stream.write(0xff);
            inc(counter);
            int64(value, stream, counter);
        }
    }

    public static void int8(long value, OutputStream stream) throws IOException {
        int8(value, stream, null);
    }

    public static void int8(long value, OutputStream stream, AccessCounter counter) throws IOException {
        stream.write((int) value);
        inc(counter);
    }

    public static void int16(long value, OutputStream stream) throws IOException {
        int16(value, stream, null);
    }

    public static void int16(long value, OutputStream stream, AccessCounter counter) throws IOException {
        stream.write(ByteBuffer.allocate(4).putInt((int) value).array(), 2, 2);
        inc(counter, 2);
    }

    public static void int32(long value, OutputStream stream) throws IOException {
        int32(value, stream, null);
    }

    public static void int32(long value, OutputStream stream, AccessCounter counter) throws IOException {
        stream.write(ByteBuffer.allocate(4).putInt((int) value).array());
        inc(counter, 4);
    }

    public static void int64(long value, OutputStream stream) throws IOException {
        int64(value, stream, null);
    }

    public static void int64(long value, OutputStream stream, AccessCounter counter) throws IOException {
        stream.write(ByteBuffer.allocate(8).putLong(value).array());
        inc(counter, 8);
    }

    public static void varString(String value, OutputStream out) throws IOException {
        byte[] bytes = value.getBytes("utf-8");
        // Technically, it says the length in characters, but I think this one might be correct.
        // It doesn't really matter, as only ASCII characters are being used.
        // see also Decode#varString()
        varInt(bytes.length, out);
        out.write(bytes);
    }

    public static void varBytes(byte[] data, OutputStream out) throws IOException {
        varInt(data.length, out);
        out.write(data);
    }

    /**
     * Serializes a {@link Streamable} object and returns the byte array.
     *
     * @param streamable the object to be serialized
     * @return an array of bytes representing the given streamable object.
     * @throws IOException if an I/O error occurs.
     */
    public static byte[] bytes(Streamable streamable) throws IOException {
        if (streamable == null) return null;

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        streamable.write(stream);
        return stream.toByteArray();
    }

    /**
     * @param streamable the object to be serialized
     * @param padding    the result will be padded such that its length is a multiple of <em>padding</em>
     * @return the bytes of the given {@link Streamable} object, 0-padded such that the final length is x*padding.
     * @throws IOException if an I/O error occurs.
     */
    public static byte[] bytes(Streamable streamable, int padding) throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        streamable.write(stream);
        int offset = padding - stream.size() % padding;
        int length = stream.size() + offset;
        byte[] result = new byte[length];
        stream.write(result, offset, stream.size());
        return result;
    }
}
