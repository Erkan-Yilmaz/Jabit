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

package ch.dissem.bitmessage.entity;

import ch.dissem.bitmessage.entity.payload.Pubkey;
import ch.dissem.bitmessage.entity.payload.V4Pubkey;
import ch.dissem.bitmessage.entity.valueobject.PrivateKey;
import ch.dissem.bitmessage.exception.DecryptionFailedException;
import ch.dissem.bitmessage.utils.*;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;

import static ch.dissem.bitmessage.entity.payload.Pubkey.Feature.DOES_ACK;
import static ch.dissem.bitmessage.utils.Singleton.security;
import static org.junit.Assert.*;

public class BitmessageAddressTest {
    @Test
    public void ensureBase58DecodesCorrectly() {
        assertHexEquals("800C28FCA386C7A227600B2FE50B7CAE11EC86D3BF1FBE471BE89827E19D72AA1D507A5B8D",
                Base58.decode("5HueCGU8rMjxEXxiPuD5BDku4MkFqeZyd4dZ1jvhTVqvbTLvyTJ"));
    }

    @Test
    public void ensureAddressStaysSame() {
        String address = "BM-2D9Vc5rFxxR5vTi53T9gkLfemViHRMVLQZ";
        assertEquals(address, new BitmessageAddress(address).toString());
    }

    @Test
    public void ensureStreamAndVersionAreParsed() {
        BitmessageAddress address = new BitmessageAddress("BM-2D9Vc5rFxxR5vTi53T9gkLfemViHRMVLQZ");
        assertEquals(1, address.getStream());
        assertEquals(3, address.getVersion());

        address = new BitmessageAddress("BM-87hJ99tPAXxtetvnje7Z491YSvbEtBJVc5e");
        assertEquals(1, address.getStream());
        assertEquals(4, address.getVersion());
    }

    @Test
    public void testCreateAddress() {
        BitmessageAddress address = new BitmessageAddress(new PrivateKey(false, 1, 1000, 1000, DOES_ACK));
        assertNotNull(address.getPubkey());
    }

    @Test
    public void testV2PubkeyImport() throws IOException {
        ObjectMessage object = TestUtils.loadObjectMessage(2, "V2Pubkey.payload");
        Pubkey pubkey = (Pubkey) object.getPayload();
        BitmessageAddress address = new BitmessageAddress("BM-opWQhvk9xtMFvQA2Kvetedpk8LkbraWHT");
        address.setPubkey(pubkey);
    }

    @Test
    public void testV3PubkeyImport() throws IOException {
        BitmessageAddress address = new BitmessageAddress("BM-2D9Vc5rFxxR5vTi53T9gkLfemViHRMVLQZ");
        assertArrayEquals(Bytes.fromHex("007402be6e76c3cb87caa946d0c003a3d4d8e1d5"), address.getRipe());

        ObjectMessage object = TestUtils.loadObjectMessage(3, "V3Pubkey.payload");
        Pubkey pubkey = (Pubkey) object.getPayload();
        assertTrue(object.isSignatureValid(pubkey));
        address.setPubkey(pubkey);

        assertArrayEquals(Bytes.fromHex("007402be6e76c3cb87caa946d0c003a3d4d8e1d5"), pubkey.getRipe());
    }

    @Test
    public void testV4PubkeyImport() throws IOException, DecryptionFailedException {
        BitmessageAddress address = new BitmessageAddress("BM-2cXxfcSetKnbHJX2Y85rSkaVpsdNUZ5q9h");
        ObjectMessage object = TestUtils.loadObjectMessage(4, "V4Pubkey.payload");
        object.decrypt(address.getPublicDecryptionKey());
        V4Pubkey pubkey = (V4Pubkey) object.getPayload();
        assertTrue(object.isSignatureValid(pubkey));
        address.setPubkey(pubkey);
    }

    @Test
    public void testV3AddressImport() throws IOException {
        String address_string = "BM-2DAjcCFrqFrp88FUxExhJ9kPqHdunQmiyn";
        assertEquals(3, new BitmessageAddress(address_string).getVersion());
        assertEquals(1, new BitmessageAddress(address_string).getStream());

        byte[] privsigningkey = getSecret("5KU2gbe9u4rKJ8PHYb1rvwMnZnAJj4gtV5GLwoYckeYzygWUzB9");
        byte[] privencryptionkey = getSecret("5KHd4c6cavd8xv4kzo3PwnVaYuBgEfg7voPQ5V97aZKgpYBXGck");

        System.out.println("\n\n" + Strings.hex(privsigningkey) + "\n\n");

        BitmessageAddress address = new BitmessageAddress(new PrivateKey(privsigningkey, privencryptionkey,
                security().createPubkey(3, 1, privsigningkey, privencryptionkey, 320, 14000)));
        assertEquals(address_string, address.getAddress());
    }

    @Test
    public void testGetSecret() throws IOException {
        assertHexEquals("0C28FCA386C7A227600B2FE50B7CAE11EC86D3BF1FBE471BE89827E19D72AA1D",
                getSecret("5HueCGU8rMjxEXxiPuD5BDku4MkFqeZyd4dZ1jvhTVqvbTLvyTJ"));
    }

    private byte[] getSecret(String walletImportFormat) throws IOException {
        byte[] bytes = Base58.decode(walletImportFormat);
        if (bytes[0] != (byte) 0x80)
            throw new IOException("Unknown format: 0x80 expected as first byte, but secret " + walletImportFormat + " was " + bytes[0]);
        if (bytes.length != 37)
            throw new IOException("Unknown format: 37 bytes expected, but secret " + walletImportFormat + " was " + bytes.length + " long");

        byte[] hash = security().doubleSha256(bytes, 33);
        for (int i = 0; i < 4; i++) {
            if (hash[i] != bytes[33 + i]) throw new IOException("Hash check failed for secret " + walletImportFormat);
        }
        return Arrays.copyOfRange(bytes, 1, 33);
    }

    @Test
    public void testV4AddressImport() throws IOException {
        assertEquals(4, new BitmessageAddress("BM-2cV5f9EpzaYARxtoruSpa6pDoucSf9ZNke").getVersion());
        byte[] privsigningkey = getSecret("5KMWqfCyJZGFgW6QrnPJ6L9Gatz25B51y7ErgqNr1nXUVbtZbdU");
        byte[] privencryptionkey = getSecret("5JXXWEuhHQEPk414SzEZk1PHDRi8kCuZd895J7EnKeQSahJPxGz");
        BitmessageAddress address = new BitmessageAddress(new PrivateKey(privsigningkey, privencryptionkey,
                security().createPubkey(4, 1, privsigningkey, privencryptionkey, 320, 14000)));
        assertEquals("BM-2cV5f9EpzaYARxtoruSpa6pDoucSf9ZNke", address.getAddress());
    }

    private void assertHexEquals(String hex, byte[] bytes) {
        assertEquals(hex.toLowerCase(), Strings.hex(bytes).toString().toLowerCase());
    }
}
