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

package ch.dissem.bitmessage;

import ch.dissem.bitmessage.entity.BitmessageAddress;
import ch.dissem.bitmessage.entity.Encrypted;
import ch.dissem.bitmessage.entity.ObjectMessage;
import ch.dissem.bitmessage.entity.payload.Broadcast;
import ch.dissem.bitmessage.entity.payload.GetPubkey;
import ch.dissem.bitmessage.entity.payload.ObjectPayload;
import ch.dissem.bitmessage.ports.*;
import ch.dissem.bitmessage.utils.Singleton;
import ch.dissem.bitmessage.utils.UnixTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.TreeSet;

/**
 * The internal context should normally only be used for port implementations. If you need it in your client
 * implementation, you're either doing something wrong, something very weird, or the BitmessageContext should
 * get extended.
 * <p>
 * On the other hand, if you need the BitmessageContext in a port implementation, the same thing might apply.
 * </p>
 */
public class InternalContext {
    private final static Logger LOG = LoggerFactory.getLogger(InternalContext.class);

    private final Cryptography cryptography;
    private final Inventory inventory;
    private final NodeRegistry nodeRegistry;
    private final NetworkHandler networkHandler;
    private final AddressRepository addressRepository;
    private final MessageRepository messageRepository;
    private final ProofOfWorkRepository proofOfWorkRepository;
    private final ProofOfWorkEngine proofOfWorkEngine;
    private final MessageCallback messageCallback;
    private final CustomCommandHandler customCommandHandler;
    private final ProofOfWorkService proofOfWorkService;

    private final TreeSet<Long> streams = new TreeSet<>();
    private final int port;
    private final long clientNonce;
    private final long networkNonceTrialsPerByte = 1000;
    private final long networkExtraBytes = 1000;
    private final long pubkeyTTL;
    private long connectionTTL;
    private int connectionLimit;

    public InternalContext(BitmessageContext.Builder builder) {
        this.cryptography = builder.cryptography;
        this.inventory = builder.inventory;
        this.nodeRegistry = builder.nodeRegistry;
        this.networkHandler = builder.networkHandler;
        this.addressRepository = builder.addressRepo;
        this.messageRepository = builder.messageRepo;
        this.proofOfWorkRepository = builder.proofOfWorkRepository;
        this.proofOfWorkService = new ProofOfWorkService();
        this.proofOfWorkEngine = builder.proofOfWorkEngine;
        this.clientNonce = cryptography.randomNonce();
        this.messageCallback = builder.messageCallback;
        this.customCommandHandler = builder.customCommandHandler;
        this.port = builder.port;
        this.connectionLimit = builder.connectionLimit;
        this.connectionTTL = builder.connectionTTL;
        this.pubkeyTTL = builder.pubkeyTTL;

        Singleton.initialize(cryptography);

        // TODO: streams of new identities and subscriptions should also be added. This works only after a restart.
        for (BitmessageAddress address : addressRepository.getIdentities()) {
            streams.add(address.getStream());
        }
        for (BitmessageAddress address : addressRepository.getSubscriptions()) {
            streams.add(address.getStream());
        }
        if (streams.isEmpty()) {
            streams.add(1L);
        }

        init(cryptography, inventory, nodeRegistry, networkHandler, addressRepository, messageRepository,
                proofOfWorkRepository, proofOfWorkService, proofOfWorkEngine,
                messageCallback, customCommandHandler);
        for (BitmessageAddress identity : addressRepository.getIdentities()) {
            streams.add(identity.getStream());
        }
    }

    private void init(Object... objects) {
        for (Object o : objects) {
            if (o instanceof ContextHolder) {
                ((ContextHolder) o).setContext(this);
            }
        }
    }

    public Cryptography getCryptography() {
        return cryptography;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public NodeRegistry getNodeRegistry() {
        return nodeRegistry;
    }

    public NetworkHandler getNetworkHandler() {
        return networkHandler;
    }

    public AddressRepository getAddressRepository() {
        return addressRepository;
    }

    public MessageRepository getMessageRepository() {
        return messageRepository;
    }

    public ProofOfWorkRepository getProofOfWorkRepository() {
        return proofOfWorkRepository;
    }

    public ProofOfWorkEngine getProofOfWorkEngine() {
        return proofOfWorkEngine;
    }

    public ProofOfWorkService getProofOfWorkService() {
        return proofOfWorkService;
    }

    public long[] getStreams() {
        long[] result = new long[streams.size()];
        int i = 0;
        for (long stream : streams) {
            result[i++] = stream;
        }
        return result;
    }

    public int getPort() {
        return port;
    }

    public long getNetworkNonceTrialsPerByte() {
        return networkNonceTrialsPerByte;
    }

    public long getNetworkExtraBytes() {
        return networkExtraBytes;
    }

    public void send(final BitmessageAddress from, BitmessageAddress to, final ObjectPayload payload,
                     final long timeToLive) {
        try {
            if (to == null) to = from;
            long expires = UnixTime.now(+timeToLive);
            LOG.info("Expires at " + expires);
            final ObjectMessage object = new ObjectMessage.Builder()
                    .stream(to.getStream())
                    .expiresTime(expires)
                    .payload(payload)
                    .build();
            if (object.isSigned()) {
                object.sign(from.getPrivateKey());
            }
            if (payload instanceof Broadcast) {
                ((Broadcast) payload).encrypt();
            } else if (payload instanceof Encrypted) {
                object.encrypt(to.getPubkey());
            }
            messageCallback.proofOfWorkStarted(payload);
            proofOfWorkService.doProofOfWork(to, object);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void sendPubkey(final BitmessageAddress identity, final long targetStream) {
        try {
            long expires = UnixTime.now(pubkeyTTL);
            LOG.info("Expires at " + expires);
            final ObjectMessage response = new ObjectMessage.Builder()
                    .stream(targetStream)
                    .expiresTime(expires)
                    .payload(identity.getPubkey())
                    .build();
            response.sign(identity.getPrivateKey());
            response.encrypt(cryptography.createPublicKey(identity.getPublicDecryptionKey()));
            messageCallback.proofOfWorkStarted(identity.getPubkey());
            // TODO: remember that the pubkey is just about to be sent, and on which stream!
            proofOfWorkService.doProofOfWork(response);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void requestPubkey(final BitmessageAddress contact) {
        long expires = UnixTime.now(+pubkeyTTL);
        LOG.info("Expires at " + expires);
        final ObjectMessage response = new ObjectMessage.Builder()
                .stream(contact.getStream())
                .expiresTime(expires)
                .payload(new GetPubkey(contact))
                .build();
        messageCallback.proofOfWorkStarted(response.getPayload());
        proofOfWorkService.doProofOfWork(response);
    }

    public long getClientNonce() {
        return clientNonce;
    }

    public long getConnectionTTL() {
        return connectionTTL;
    }

    public int getConnectionLimit() {
        return connectionLimit;
    }

    public CustomCommandHandler getCustomCommandHandler() {
        return customCommandHandler;
    }

    public interface ContextHolder {
        void setContext(InternalContext context);
    }
}