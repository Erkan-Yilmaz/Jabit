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

package ch.dissem.bitmessage.ports;

import ch.dissem.bitmessage.utils.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

import static ch.dissem.bitmessage.utils.Bytes.inc;

/**
 * A POW engine using all available CPU cores.
 */
public class MultiThreadedPOWEngine implements ProofOfWorkEngine {
    private static final Logger LOG = LoggerFactory.getLogger(MultiThreadedPOWEngine.class);
    private static final Semaphore semaphore = new Semaphore(1, true);

    /**
     * This method will block until all pending nonce calculations are done, but not wait for its own calculation
     * to finish.
     * (This implementation becomes very inefficient if multiple nonce are calculated at the same time.)
     *
     * @param initialHash the SHA-512 hash of the object to send, sans nonce
     * @param target      the target, representing an unsigned long
     * @param callback    called with the calculated nonce as argument. The ProofOfWorkEngine implementation must make
     */
    @Override
    public void calculateNonce(byte[] initialHash, byte[] target, Callback callback) {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        callback = new CallbackWrapper(callback);
        int cores = Runtime.getRuntime().availableProcessors();
        if (cores > 255) cores = 255;
        LOG.info("Doing POW using " + cores + " cores");
        List<Worker> workers = new ArrayList<>(cores);
        for (int i = 0; i < cores; i++) {
            Worker w = new Worker(workers, (byte) cores, i, initialHash, target, callback);
            workers.add(w);
        }
        for (Worker w : workers) {
            // Doing this in the previous loop might cause a ConcurrentModificationException in the worker
            // if a worker finds a nonce while new ones are still being added.
            w.start();
        }
    }

    private static class Worker extends Thread {
        private final Callback callback;
        private final byte numberOfCores;
        private final List<Worker> workers;
        private final byte[] initialHash;
        private final byte[] target;
        private final MessageDigest mda;
        private final byte[] nonce = new byte[8];

        public Worker(List<Worker> workers, byte numberOfCores, int core, byte[] initialHash, byte[] target,
                      Callback callback) {
            this.callback = callback;
            this.numberOfCores = numberOfCores;
            this.workers = workers;
            this.initialHash = initialHash;
            this.target = target;
            this.nonce[7] = (byte) core;
            try {
                mda = MessageDigest.getInstance("SHA-512");
            } catch (NoSuchAlgorithmException e) {
                LOG.error(e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }

        @Override
        public void run() {
            do {
                inc(nonce, numberOfCores);
                mda.update(nonce);
                mda.update(initialHash);
                if (!Bytes.lt(target, mda.digest(mda.digest()), 8)) {
                    synchronized (callback) {
                        if (!Thread.interrupted()) {
                            for (Worker w : workers) {
                                w.interrupt();
                            }
                            // Clear interrupted flag for callback
                            Thread.interrupted();
                            callback.onNonceCalculated(initialHash, nonce);
                        }
                    }
                    return;
                }
            } while (!Thread.interrupted());
        }
    }

    public static class CallbackWrapper implements Callback {
        private final Callback callback;
        private final long startTime;
        private boolean waiting = true;

        public CallbackWrapper(Callback callback) {
            this.startTime = System.currentTimeMillis();
            this.callback = callback;
        }

        @Override
        public void onNonceCalculated(byte[] initialHash, byte[] nonce) {
            // Prevents the callback from being called twice if two nonces are found simultaneously
            synchronized (this) {
                if (waiting) {
                    semaphore.release();
                    LOG.info("Nonce calculated in " + ((System.currentTimeMillis() - startTime) / 1000) + " seconds");
                    waiting = false;
                    callback.onNonceCalculated(initialHash, nonce);
                }
            }
        }
    }
}
