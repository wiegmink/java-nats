// Copyright 2015-2018 The NATS Authors
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at:
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package io.nats.client;

import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Random;

/**
 * A highly performant unique identifier generator.
 */
public final class NUID {


    /*
     * NUID needs to be very fast to generate and truly unique, all while being entropy pool
     * friendly. We will use 12 bytes of crypto generated data (entropy draining), and 10 bytes of
     * sequential data that is started at a pseudo random number and increments with a pseudo-random
     * increment. Total is 22 bytes of base 62 ascii text :)
     */

    // Constants
    static final char[] digits = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C',
            'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T',
            'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k',
            'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'};
    static final int base = 62;
    static final int preLen = 12;
    static final int seqLen = 10;
    static final long maxSeq = 839299365868340224L; // base^seqLen == 62^10
    static final long minInc = 33L;
    static final long maxInc = 333L;
    static final int totalLen = preLen + seqLen;
    private SecureRandom srand;
    private final Random prand;

    // Instance fields
    char[] pre;
    private long seq;
    private long inc;


    // Global NUID
    public static final NUID globalNUID = new NUID();

    static NUID getInstance() {
        return globalNUID;
    }

    /**
     * The default NUID constructor.
     */
    public NUID() {
        // Generate a cryto random int, 0 <= val < max to seed pseudorandom
        long seed = 0L;
        try {
            srand = SecureRandom.getInstance("SHA1PRNG");
            seed = bytesToLong(srand.generateSeed(8)); // seed with 8 bytes (64 bits)
        } catch (NoSuchAlgorithmException e) {
            // IGNORE - FIXME:  This should error up somehow.
        }

        if (seed != 0L) {
            prand = new Random(seed);
        } else {
            prand = new Random();
        }

        seq = nextLong(prand, maxSeq);
        inc = minInc + nextLong(prand, maxInc - minInc);
        pre = new char[preLen];
        for (int i = 0; i < preLen; i++) {
            pre[i] = '0';
        }
        randomizePrefix();
    }

    /**
     * Generate the next NUID string from the global locked NUID instance.
     *
     * @return the next NUID string from the global locked NUID instance.
     */
    public static synchronized String nextGlobal() {
        return getInstance().next();
    }

    /**
     * Generate the next NUID string from this instance.
     *
     * @return the next NUID string from this instance.
     */
    public final String next() {
        // Increment and capture.
        seq += inc;
        if (seq >= maxSeq) {
            randomizePrefix();
            resetSequential();
        }

        // Copy prefix
        char[] b = new char[totalLen];
        System.arraycopy(pre, 0, b, 0, preLen);

        // copy in the seq in base36.
        int i = b.length;
        for (long l = seq; i > preLen; l /= base) {
            i--;
            b[i] = digits[(int) (l % base)];
        }
        return new String(b);
    }

    // Resets the sequntial portion of the NUID
    void resetSequential() {
        seq = nextLong(prand, maxSeq);
        inc = minInc + nextLong(prand, maxInc - minInc);
    }

    /*
     * Generate a new prefix from random. This *can* drain entropy and will be called automatically
     * when we exhaust the sequential range.
     */

    final void randomizePrefix() {
        byte[] cb = new byte[preLen];

        // Use SecureRandom for prefix only
        srand.nextBytes(cb);

        for (int i = 0; i < preLen; i++) {
            pre[i] = digits[(cb[i] & 0xFF) % base];
        }
    }

    static long nextLong(Random rng, long maxValue) {
        // error checking and 2^x checking removed for simplicity.
        long bits;
        long val;
        do {
            bits = (rng.nextLong() << 1) >>> 1;
            val = bits % maxValue;
        } while (bits - val + (maxValue - 1) < 0L);
        return val;
    }

    static byte[] longToBytes(long x) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.SIZE);
        buffer.putLong(x);
        return buffer.array();
    }

    static long bytesToLong(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.SIZE);
        buffer.put(bytes);
        buffer.flip();// need flip
        return buffer.getLong();
    }

    /**
     * @return the pre
     */
    char[] getPre() {
        return pre;
    }

    /**
     * Sets the prefix.
     *
     * @param pre the pre to set
     */
    void setPre(char[] pre) {
        this.pre = Arrays.copyOf(pre, pre.length);
    }

    /**
     * Return the current sequence value.
     *
     * @return the seq
     */
    long getSeq() {
        return seq;
    }

    /**
     * Set the sequence to the supplied value.
     *
     * @param seq the seq to set
     */
    void setSeq(long seq) {
        this.seq = seq;
    }

    /**
     * Return the current increment.
     *
     * @return the inc
     */
    long getInc() {
        return inc;
    }

    /**
     * Set the increment to the supplied value.
     *
     * @param inc the inc to set
     */
    void setInc(long inc) {
        this.inc = inc;
    }
}
