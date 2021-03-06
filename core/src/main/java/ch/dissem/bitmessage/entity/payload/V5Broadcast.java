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

package ch.dissem.bitmessage.entity.payload;

import ch.dissem.bitmessage.entity.BitmessageAddress;
import ch.dissem.bitmessage.entity.Plaintext;
import ch.dissem.bitmessage.utils.Decode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Users who are subscribed to the sending address will see the message appear in their inbox.
 */
public class V5Broadcast extends V4Broadcast {
    private byte[] tag;

    private V5Broadcast(long stream, byte[] tag, CryptoBox encrypted) {
        super(5, stream, encrypted, null);
        this.tag = tag;
    }

    public V5Broadcast(BitmessageAddress senderAddress, Plaintext plaintext) {
        super(5, senderAddress.getStream(), null, plaintext);
        if (senderAddress.getVersion() < 4)
            throw new IllegalArgumentException("Address version 4 (or newer) expected, but was " + senderAddress.getVersion());
        this.tag = senderAddress.getTag();
    }

    public static V5Broadcast read(InputStream is, long stream, int length) throws IOException {
        return new V5Broadcast(stream, Decode.bytes(is, 32), CryptoBox.read(is, length - 32));
    }

    public byte[] getTag() {
        return tag;
    }

    @Override
    public void writeBytesToSign(OutputStream out) throws IOException {
        out.write(tag);
        super.writeBytesToSign(out);
    }

    @Override
    public void write(OutputStream out) throws IOException {
        out.write(tag);
        super.write(out);
    }
}
