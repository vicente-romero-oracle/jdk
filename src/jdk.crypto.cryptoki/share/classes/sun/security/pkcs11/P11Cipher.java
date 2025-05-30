/*
 * Copyright (c) 2003, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package sun.security.pkcs11;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Locale;

import java.security.*;
import java.security.spec.*;

import javax.crypto.*;
import javax.crypto.spec.*;

import jdk.internal.access.JavaNioAccess;
import jdk.internal.access.SharedSecrets;
import sun.nio.ch.DirectBuffer;
import sun.security.jca.JCAUtil;
import sun.security.pkcs11.wrapper.*;
import static sun.security.pkcs11.wrapper.PKCS11Constants.*;
import static sun.security.pkcs11.wrapper.PKCS11Exception.RV.*;

/**
 * Cipher implementation class. This class currently supports
 * DES, DESede, AES, ARCFOUR, and Blowfish.
 *
 * This class is designed to support ECB, CBC, CTR, CTS with NoPadding
 * and ECB, CBC with PKCS5Padding. It will use its own padding impl
 * if the native mechanism does not support padding.
 *
 * Note that PKCS#11 currently only supports ECB, CBC, and CTR.
 * There are no provisions for other modes such as CFB, OFB, and PCBC.
 *
 * @author  Andreas Sterbenz
 * @since   1.5
 */
final class P11Cipher extends CipherSpi {

    private static final JavaNioAccess NIO_ACCESS = SharedSecrets.getJavaNioAccess();

    // mode and padding constants
    private enum Mode {ECB /* or stream ciphers */, CBC, CTR, CTS}
    private enum Pad {NONE, PKCS5}

    private static interface Padding {
        // ENC: format the specified buffer with padding bytes and return the
        // actual padding length
        int setPaddingBytes(byte[] paddingBuffer, int startOff, int padLen);

        // DEC: return the length of trailing padding bytes given the specified
        // padded data
        int unpad(byte[] paddedData, int len)
                throws BadPaddingException, IllegalBlockSizeException;
    }

    private static class PKCS5Padding implements Padding {

        private final int blockSize;

        PKCS5Padding(int blockSize)
                throws NoSuchPaddingException {
            if (blockSize == 0) {
                throw new NoSuchPaddingException
                        ("PKCS#5 padding not supported with stream ciphers");
            }
            this.blockSize = blockSize;
        }

        public int setPaddingBytes(byte[] paddingBuffer, int startOff, int padLen) {
            Arrays.fill(paddingBuffer, startOff, startOff + padLen, (byte) (padLen & 0x007f));
            return padLen;
        }

        public int unpad(byte[] paddedData, int len)
                throws BadPaddingException, IllegalBlockSizeException {
            if ((len < 1) || (len % blockSize != 0)) {
                throw new IllegalBlockSizeException
                    ("Input length must be multiples of " + blockSize);
            }
            byte padValue = paddedData[len - 1];
            if (padValue < 1 || padValue > blockSize) {
                throw new BadPaddingException("Invalid pad value!");
            }
            // sanity check padding bytes
            int padStartIndex = len - padValue;
            for (int i = padStartIndex; i < len; i++) {
                if (paddedData[i] != padValue) {
                    throw new BadPaddingException("Invalid pad bytes!");
                }
            }
            return padValue;
        }
    }

    // token instance
    private final Token token;

    // algorithm name
    private final String algorithm;

    // name of the key algorithm, e.g. DES instead of algorithm DES/CBC/...
    private final String keyAlgorithm;

    // mechanism id
    private final long mechanism;

    // associated session, if any
    private Session session;

    // key, if init() was called
    private P11Key p11Key;

    // flag indicating whether an operation is initialized
    private boolean initialized;

    // flag indicating encrypt or decrypt mode
    private boolean encrypt;

    // mode, Mode.ECB for stream ciphers
    private final Mode blockMode;

    // block size, 0 for stream ciphers
    private final int blockSize;

    // padding type, Pad.NONE for stream ciphers
    private Pad paddingType;

    // when the padding is requested but unsupported by the native mechanism,
    // we use the following to do padding and necessary data buffering.
    // padding object which generate padding and unpad the decrypted data
    private Padding paddingObj;
    // buffer for holding back the block which contains padding bytes
    private byte[] padBuffer;
    private int padBufferLen;

    // original IV, if in Mode.CBC, Mode.CTR or Mode.CTS
    private byte[] iv;

    // number of bytes buffered internally by the native mechanism and padBuffer
    // if we do the padding
    private int bytesBuffered;

    // length of key size in bytes; currently only used by AES given its oid
    // specification mandates a fixed size of the key
    private int fixedKeySize = -1;

    // Indicates whether the underlying PKCS#11 library requires block-sized
    // updates during multi-part operations. In such case, we buffer data in
    // padBuffer up to a block-size. This may be needed only if padding is
    // applied on the Java side. An example of the previous is when the
    // CKM_AES_ECB mechanism is used and the PKCS#11 library is NSS. See more
    // on JDK-8261355.
    private boolean reqBlockUpdates = false;

    P11Cipher(Token token, String algorithm, long mechanism)
            throws PKCS11Exception, NoSuchAlgorithmException {
        super();
        this.token = token;
        this.algorithm = algorithm;
        this.mechanism = mechanism;

        String[] algoParts = algorithm.split("/");

        if (algoParts[0].startsWith("AES")) {
            blockSize = 16;
            int index = algoParts[0].indexOf('_');
            if (index != -1) {
                // should be well-formed since we specify what we support
                fixedKeySize = Integer.parseInt(algoParts[0].substring(index+1))/8;
            }
            keyAlgorithm = "AES";
        } else {
            keyAlgorithm = algoParts[0];
            if (keyAlgorithm.equals("RC4") ||
                    keyAlgorithm.equals("ARCFOUR")) {
                blockSize = 0;
            } else { // DES, DESede, Blowfish
                blockSize = 8;
            }
        }
        blockMode = algoParts.length > 1 ? parseMode(algoParts[1]) : Mode.ECB;
        String defPadding = (blockSize == 0 ? "NoPadding" : "PKCS5Padding");
        String paddingStr =
                (algoParts.length > 2 ? algoParts[2] : defPadding);
        try {
            engineSetPadding(paddingStr);
        } catch (NoSuchPaddingException nspe) {
            // should not happen
            throw new ProviderException(nspe);
        }
    }

    protected void engineSetMode(String mode) throws NoSuchAlgorithmException {
        // Disallow change of mode for now since currently it's explicitly
        // defined in transformation strings
        throw new NoSuchAlgorithmException("Unsupported mode " + mode);
    }

    private Mode parseMode(String mode) throws NoSuchAlgorithmException {
        mode = mode.toUpperCase(Locale.ENGLISH);
        Mode result;
        try {
            result = Mode.valueOf(mode);
        } catch (IllegalArgumentException ignored) {
            throw new NoSuchAlgorithmException("Unsupported mode " + mode);
        }
        if (blockSize == 0 && result != Mode.ECB) {
            throw new NoSuchAlgorithmException(
                    result + " mode not supported with stream ciphers");
        }
        return result;
    }

    // see JCE spec
    protected void engineSetPadding(String padding)
            throws NoSuchPaddingException {
        paddingObj = null;
        padBuffer = null;
        padding = padding.toUpperCase(Locale.ENGLISH);
        if (padding.equals("NOPADDING")) {
            paddingType = Pad.NONE;
            if (blockMode == Mode.CTS) {
                // Buffer at least two blocks (where the last one may be
                // partial). When using NSS, buffer one more block to avoid
                // NSS Bug 1823875: "AES CTS decryption does not update
                // its own context's IV on full blocks input"
                // https://bugzilla.mozilla.org/show_bug.cgi?id=1823875#c2
                int bufferedBlocks = P11Util.isNSS(token) ? 3 : 2;
                padBuffer = new byte[bufferedBlocks * blockSize];
            }
        } else if (padding.equals("PKCS5PADDING")) {
            if (blockMode == Mode.CTR || blockMode == Mode.CTS) {
                throw new NoSuchPaddingException("PKCS#5 padding not " +
                        "supported with " + blockMode + " mode");
            }
            paddingType = Pad.PKCS5;
            if (mechanism != CKM_DES_CBC_PAD && mechanism != CKM_DES3_CBC_PAD &&
                    mechanism != CKM_AES_CBC_PAD) {
                // no native padding support; use our own padding impl
                paddingObj = new PKCS5Padding(blockSize);
                padBuffer = new byte[blockSize];
                // NSS requires block-sized updates in multi-part operations.
                reqBlockUpdates = P11Util.isNSS(token);
            }
        } else {
            throw new NoSuchPaddingException("Unsupported padding " + padding);
        }
    }

    // see JCE spec
    protected int engineGetBlockSize() {
        return blockSize;
    }

    // see JCE spec
    protected int engineGetOutputSize(int inputLen) {
        return doFinalLength(inputLen);
    }

    // see JCE spec
    protected byte[] engineGetIV() {
        return (iv == null) ? null : iv.clone();
    }

    // see JCE spec
    protected AlgorithmParameters engineGetParameters() {
        if (iv == null) {
            return null;
        }
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        try {
            AlgorithmParameters params =
                    AlgorithmParameters.getInstance(keyAlgorithm,
                    P11Util.getSunJceProvider());
            params.init(ivSpec);
            return params;
        } catch (GeneralSecurityException e) {
            // NoSuchAlgorithmException, NoSuchProviderException
            // InvalidParameterSpecException
            throw new ProviderException("Could not encode parameters", e);
        }
    }

    // see JCE spec
    protected void engineInit(int opmode, Key key, SecureRandom random)
            throws InvalidKeyException {
        try {
            implInit(opmode, key, null, random);
        } catch (InvalidAlgorithmParameterException e) {
            throw new InvalidKeyException("init() failed", e);
        }
    }

    // see JCE spec
    protected void engineInit(int opmode, Key key,
            AlgorithmParameterSpec params, SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        byte[] ivValue;
        if (params != null) {
            if (!(params instanceof IvParameterSpec)) {
                throw new InvalidAlgorithmParameterException
                        ("Only IvParameterSpec supported");
            }
            IvParameterSpec ivSpec = (IvParameterSpec) params;
            ivValue = ivSpec.getIV();
        } else {
            ivValue = null;
        }
        implInit(opmode, key, ivValue, random);
    }

    // see JCE spec
    protected void engineInit(int opmode, Key key, AlgorithmParameters params,
            SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        byte[] ivValue;
        if (params != null) {
            try {
                IvParameterSpec ivSpec =
                        params.getParameterSpec(IvParameterSpec.class);
                ivValue = ivSpec.getIV();
            } catch (InvalidParameterSpecException e) {
                throw new InvalidAlgorithmParameterException
                        ("Could not decode IV", e);
            }
        } else {
            ivValue = null;
        }
        implInit(opmode, key, ivValue, random);
    }

    // actual init() implementation
    private void implInit(int opmode, Key key, byte[] iv,
            SecureRandom random)
            throws InvalidKeyException, InvalidAlgorithmParameterException {
        reset(true);
        if (fixedKeySize != -1 &&
                ((key instanceof P11Key) ? ((P11Key) key).length() >> 3 :
                            key.getEncoded().length) != fixedKeySize) {
            throw new InvalidKeyException("Key size is invalid");
        }
        encrypt = switch (opmode) {
            case Cipher.ENCRYPT_MODE -> true;
            case Cipher.DECRYPT_MODE -> false;
            case Cipher.WRAP_MODE, Cipher.UNWRAP_MODE -> throw new UnsupportedOperationException
                    ("Unsupported mode: " + opmode);
            default ->
                // should never happen; checked by Cipher.init()
                    throw new AssertionError("Unknown mode: " + opmode);
        };
        if (blockMode == Mode.ECB) { // ECB or stream cipher
            if (iv != null) {
                if (blockSize == 0) {
                    throw new InvalidAlgorithmParameterException
                            ("IV not used with stream ciphers");
                } else {
                    throw new InvalidAlgorithmParameterException
                            ("IV not used in ECB mode");
                }
            }
        } else { // Mode.CBC, Mode.CTR or Mode.CTS
            if (iv == null) {
                if (!encrypt) {
                    throw new InvalidAlgorithmParameterException(
                            "IV must be specified for decryption in " +
                            blockMode + " mode");
                }
                // generate random IV
                if (random == null) {
                    random = JCAUtil.getSecureRandom();
                }
                iv = new byte[blockSize];
                random.nextBytes(iv);
            } else {
                if (iv.length != blockSize) {
                    throw new InvalidAlgorithmParameterException
                            ("IV length must match block size");
                }
            }
        }
        this.iv = iv;
        p11Key = P11SecretKeyFactory.convertKey(token, key, keyAlgorithm);
        try {
            initialize();
        } catch (PKCS11Exception e) {
            throw new InvalidKeyException("Could not initialize cipher", e);
        }
    }

    // reset the states to the pre-initialized values
    // need to be called after doFinal or prior to re-init
    private void reset(boolean doCancel) {
        if (!initialized) {
            return;
        }
        initialized = false;

        try {
            if (session == null) {
                return;
            }

            if (doCancel && token.explicitCancel) {
                cancelOperation();
            }
        } finally {
            p11Key.releaseKeyID();
            session = token.releaseSession(session);
            bytesBuffered = 0;
            padBufferLen = 0;
            if (padBuffer != null) {
                Arrays.fill(padBuffer, (byte) 0);
            }
        }
    }

    private void cancelOperation() {
        token.ensureValid();

        if (P11Util.trySessionCancel(token, session,
                (encrypt ? CKF_ENCRYPT : CKF_DECRYPT))) {
            return;
        }

        // cancel by finishing operations; avoid killSession as
        // some hardware vendors may require re-login
        try {
            int bufLen = doFinalLength(0);
            byte[] buffer = new byte[bufLen];
            if (encrypt) {
                token.p11.C_EncryptFinal(session.id(), 0, buffer, 0, bufLen);
            } else {
                token.p11.C_DecryptFinal(session.id(), 0, buffer, 0, bufLen);
            }
        } catch (PKCS11Exception e) {
            if (e.match(CKR_OPERATION_NOT_INITIALIZED)) {
                // Cancel Operation may be invoked after an error on a PKCS#11
                // call. If the operation inside the token is already cancelled,
                // do not fail here. This is part of a defensive mechanism for
                // PKCS#11 libraries that do not strictly follow the standard.
                return;
            }
            if (encrypt) {
                throw new ProviderException("Cancel failed", e);
            }
            // ignore failure for decryption
        }
    }

    private void ensureInitialized() throws PKCS11Exception {
        if (!initialized) {
            initialize();
        }
    }

    private void initialize() throws PKCS11Exception {
        if (p11Key == null) {
            throw new ProviderException(
                    "Operation cannot be performed without"
                    + " calling engineInit first");
        }
        token.ensureValid();
        long p11KeyID = p11Key.getKeyID();
        try {
            if (session == null) {
                session = token.getOpSession();
            }
            CK_MECHANISM mechParams = (blockMode == Mode.CTR ?
                    new CK_MECHANISM(mechanism, new CK_AES_CTR_PARAMS(iv)) :
                    new CK_MECHANISM(mechanism, iv));
            if (encrypt) {
                token.p11.C_EncryptInit(session.id(), mechParams, p11KeyID);
            } else {
                token.p11.C_DecryptInit(session.id(), mechParams, p11KeyID);
            }
        } catch (PKCS11Exception e) {
            p11Key.releaseKeyID();
            session = token.releaseSession(session);
            throw e;
        }
        initialized = true;
        bytesBuffered = 0;
        padBufferLen = 0;
    }

    // if update(inLen) is called, how big does the output buffer have to be?
    private int updateLength(int inLen) {
        if (inLen <= 0) {
            return 0;
        }

        int result = inLen + bytesBuffered;
        if (blockMode == Mode.CTS) {
            result -= getCTSMustBeBuffered(result);
        } else if (blockSize != 0 && blockMode != Mode.CTR) {
            // minus the number of bytes in the last incomplete block.
            result -= (result & (blockSize - 1));
        }
        return result;
    }

    // if doFinal(inLen) is called, how big does the output buffer have to be?
    private int doFinalLength(int inLen) {
        if (inLen < 0) {
            return 0;
        }

        int result = inLen + bytesBuffered;
        if (blockSize != 0 && encrypt && paddingType != Pad.NONE) {
            // add the number of bytes to make the last block complete.
            result += (blockSize - (result & (blockSize - 1)));
        }
        return result;
    }

    // see JCE spec
    protected byte[] engineUpdate(byte[] in, int inOfs, int inLen) {
        try {
            byte[] out = new byte[updateLength(inLen)];
            int n = engineUpdate(in, inOfs, inLen, out, 0);
            return P11Util.convert(out, 0, n);
        } catch (ShortBufferException e) {
            // convert since the output length is calculated by updateLength()
            throw new ProviderException(e);
        }
    }

    // see JCE spec
    protected int engineUpdate(byte[] in, int inOfs, int inLen, byte[] out,
            int outOfs) throws ShortBufferException {
        int outLen = out.length - outOfs;
        return implUpdate(in, inOfs, inLen, out, outOfs, outLen);
    }

    // see JCE spec
    @Override
    protected int engineUpdate(ByteBuffer inBuffer, ByteBuffer outBuffer)
            throws ShortBufferException {
        return implUpdate(inBuffer, outBuffer);
    }

    // see JCE spec
    protected byte[] engineDoFinal(byte[] in, int inOfs, int inLen)
            throws IllegalBlockSizeException, BadPaddingException {
        try {
            byte[] out = new byte[doFinalLength(inLen)];
            int n = engineDoFinal(in, inOfs, inLen, out, 0);
            return P11Util.convert(out, 0, n);
        } catch (ShortBufferException e) {
            // convert since the output length is calculated by doFinalLength()
            throw new ProviderException(e);
        }
    }

    // see JCE spec
    protected int engineDoFinal(byte[] in, int inOfs, int inLen, byte[] out,
            int outOfs) throws ShortBufferException, IllegalBlockSizeException,
            BadPaddingException {
        int n = 0;
        if ((inLen != 0) && (in != null)) {
            n = engineUpdate(in, inOfs, inLen, out, outOfs);
            outOfs += n;
        }
        n += implDoFinal(out, outOfs, out.length - outOfs);
        return n;
    }

    // see JCE spec
    @Override
    protected int engineDoFinal(ByteBuffer inBuffer, ByteBuffer outBuffer)
            throws ShortBufferException, IllegalBlockSizeException,
            BadPaddingException {
        int n = engineUpdate(inBuffer, outBuffer);
        n += implDoFinal(outBuffer);
        return n;
    }

    private int implUpdate(byte[] in, int inOfs, int inLen,
            byte[] out, int outOfs, int outLen) throws ShortBufferException {
        if (outLen < updateLength(inLen)) {
            throw new ShortBufferException();
        }
        try {
            ensureInitialized();
            int k = 0;
            int newPadBufferLen = 0;
            if (blockMode == Mode.CTS) {
                // decide how to split the total data (totalInLen) between
                // the token (dataForP11Update) and padBuffer
                // (newPadBufferLen)
                int totalInLen = padBufferLen + inLen;
                newPadBufferLen = getCTSMustBeBuffered(totalInLen);
                int dataForP11Update = totalInLen - newPadBufferLen;
                if (dataForP11Update > 0 && padBufferLen > 0) {
                    // there is data for the token and part of it is in
                    // padBuffer
                    int flushFromPadBuffer;
                    int fillLen = getBytesToCompleteBlock(padBufferLen);
                    if (dataForP11Update >= padBufferLen + fillLen) {
                        // flush the whole padBuffer
                        if (fillLen > 0) {
                            // complete the last padBuffer block from the
                            // input
                            bufferInputBytes(in, inOfs, fillLen);
                            inOfs += fillLen;
                            inLen -= fillLen;
                        }
                        flushFromPadBuffer = padBufferLen;
                    } else {
                        // There is not enough input data available to
                        // complete the padBuffer to a multiple of block
                        // size. Flush part of the padBuffer (up to a
                        // multiple of blockSize) now. Shift the remaining
                        // padBuffer data and buffer more up to completing
                        // newPadBufferLen later.
                        flushFromPadBuffer = dataForP11Update;
                    }
                    if (encrypt) {
                        k = token.p11.C_EncryptUpdate(session.id(),
                                0, padBuffer, 0, flushFromPadBuffer,
                                0, out, outOfs, outLen);
                    } else {
                        k = token.p11.C_DecryptUpdate(session.id(),
                                0, padBuffer, 0, flushFromPadBuffer,
                                0, out, outOfs, outLen);
                    }
                    padBufferLen -= flushFromPadBuffer;
                    if (padBufferLen > 0) {
                        // shift remaining data to the padBuffer start
                        System.arraycopy(padBuffer, flushFromPadBuffer,
                                padBuffer, 0, padBufferLen);
                    }
                }
                newPadBufferLen -= padBufferLen;
                inLen -= newPadBufferLen;
            } else if (paddingObj != null && (!encrypt || reqBlockUpdates)) {
                if (padBufferLen != 0) {
                    if (padBufferLen != padBuffer.length) {
                        int bufCapacity = padBuffer.length - padBufferLen;
                        if (inLen > bufCapacity) {
                            bufferInputBytes(in, inOfs, bufCapacity);
                            inOfs += bufCapacity;
                            inLen -= bufCapacity;
                        } else {
                            bufferInputBytes(in, inOfs, inLen);
                            return 0;
                        }
                    }
                    if (encrypt) {
                        k = token.p11.C_EncryptUpdate(session.id(),
                                0, padBuffer, 0, padBufferLen,
                                0, out, outOfs, outLen);
                    } else {
                        k = token.p11.C_DecryptUpdate(session.id(),
                                0, padBuffer, 0, padBufferLen,
                                0, out, outOfs, outLen);
                    }
                    padBufferLen = 0;
                }
                newPadBufferLen = inLen & (blockSize - 1);
                if (!encrypt && newPadBufferLen == 0) {
                    // While decrypting with implUpdate, the last encrypted block
                    // is always held in a buffer. If it's the final one (unknown
                    // at this point), it may contain padding bytes and need further
                    // processing. In implDoFinal (where we know it's the final one)
                    // the buffer is decrypted, unpadded and returned.
                    newPadBufferLen = padBuffer.length;
                }
                inLen -= newPadBufferLen;
            }
            if (inLen > 0) {
                if (encrypt) {
                    k += token.p11.C_EncryptUpdate(session.id(), 0, in, inOfs,
                            inLen, 0, out, (outOfs + k), (outLen - k));
                } else {
                    k += token.p11.C_DecryptUpdate(session.id(), 0, in, inOfs,
                            inLen, 0, out, (outOfs + k), (outLen - k));
                }
            }
            // update 'padBuffer' if using our own padding impl.
            if ((blockMode == Mode.CTS || paddingObj != null) &&
                    newPadBufferLen > 0) {
                bufferInputBytes(in, inOfs + inLen, newPadBufferLen);
            }
            bytesBuffered += (inLen - k);
            return k;
        } catch (PKCS11Exception e) {
            if (e.match(CKR_BUFFER_TOO_SMALL)) {
                throw (ShortBufferException)
                        (new ShortBufferException().initCause(e));
            }
            // Some implementations such as the NSS Software Token do not
            // cancel the operation upon a C_EncryptUpdate/C_DecryptUpdate
            // failure (as required by the PKCS#11 standard). See JDK-8258833
            // for further information.
            reset(true);
            throw new ProviderException("update() failed", e);
        }
    }

    private int implUpdate(ByteBuffer inBuffer, ByteBuffer outBuffer)
            throws ShortBufferException {
        int inLen = inBuffer.remaining();
        if (inLen <= 0) {
            return 0;
        }

        int outLen = outBuffer.remaining();
        if (outLen < updateLength(inLen)) {
            throw new ShortBufferException();
        }
        int origPos = inBuffer.position();
        NIO_ACCESS.acquireSession(inBuffer);
        try {
            NIO_ACCESS.acquireSession(outBuffer);
            try {
                ensureInitialized();

                long inAddr = 0;
                int inOfs = 0;
                byte[] inArray = null;

                if (inBuffer instanceof DirectBuffer) {
                    inAddr = NIO_ACCESS.getBufferAddress(inBuffer);
                    inOfs = origPos;
                } else if (inBuffer.hasArray()) {
                    inArray = inBuffer.array();
                    inOfs = (origPos + inBuffer.arrayOffset());
                }

                long outAddr = 0;
                int outOfs = 0;
                byte[] outArray = null;
                if (outBuffer instanceof DirectBuffer) {
                    outAddr = NIO_ACCESS.getBufferAddress(outBuffer);
                    outOfs = outBuffer.position();
                } else {
                    if (outBuffer.hasArray()) {
                        outArray = outBuffer.array();
                        outOfs = (outBuffer.position() + outBuffer.arrayOffset());
                    } else {
                        outArray = new byte[outLen];
                    }
                }

                int k = 0;
                int newPadBufferLen = 0;
                if (blockMode == Mode.CTS) {
                    // decide how to split the total data (totalInLen) between
                    // the token (dataForP11Update) and padBuffer
                    // (newPadBufferLen)
                    int totalInLen = padBufferLen + inLen;
                    newPadBufferLen = getCTSMustBeBuffered(totalInLen);
                    int dataForP11Update = totalInLen - newPadBufferLen;
                    if (dataForP11Update > 0 && padBufferLen > 0) {
                        // there is data for the token and part of it is in
                        // padBuffer
                        int flushFromPadBuffer;
                        int fillLen = getBytesToCompleteBlock(padBufferLen);
                        if (dataForP11Update >= padBufferLen + fillLen) {
                            // flush the whole padBuffer
                            if (fillLen > 0) {
                                // complete the last padBuffer block from the
                                // input
                                bufferInputBytes(inBuffer, fillLen);
                                inOfs += fillLen;
                                inLen -= fillLen;
                            }
                            flushFromPadBuffer = padBufferLen;
                        } else {
                            // There is not enough input data available to
                            // complete the padBuffer to a multiple of block
                            // size. Flush part of the padBuffer (up to a
                            // multiple of blockSize) now. Shift the remaining
                            // padBuffer data and buffer more up to completing
                            // newPadBufferLen later.
                            flushFromPadBuffer = dataForP11Update;
                        }
                        if (encrypt) {
                            k = token.p11.C_EncryptUpdate(session.id(),
                                    0, padBuffer, 0, flushFromPadBuffer,
                                    outAddr, outArray, outOfs, outLen);
                        } else {
                            k = token.p11.C_DecryptUpdate(session.id(),
                                    0, padBuffer, 0, flushFromPadBuffer,
                                    outAddr, outArray, outOfs, outLen);
                        }
                        padBufferLen -= flushFromPadBuffer;
                        if (padBufferLen > 0) {
                            // shift remaining data to the padBuffer start
                            System.arraycopy(padBuffer, flushFromPadBuffer,
                                    padBuffer, 0, padBufferLen);
                        }
                    }
                    newPadBufferLen -= padBufferLen;
                    inLen -= newPadBufferLen;
                } else if (paddingObj != null && (!encrypt || reqBlockUpdates)) {
                    if (padBufferLen != 0) {
                        if (padBufferLen != padBuffer.length) {
                            int bufCapacity = padBuffer.length - padBufferLen;
                            if (inLen > bufCapacity) {
                                bufferInputBytes(inBuffer, bufCapacity);
                                inOfs += bufCapacity;
                                inLen -= bufCapacity;
                            } else {
                                bufferInputBytes(inBuffer, inLen);
                                return 0;
                            }
                        }
                        if (encrypt) {
                            k = token.p11.C_EncryptUpdate(session.id(), 0,
                                    padBuffer, 0, padBufferLen, outAddr, outArray,
                                    outOfs, outLen);
                        } else {
                            k = token.p11.C_DecryptUpdate(session.id(), 0,
                                    padBuffer, 0, padBufferLen, outAddr, outArray,
                                    outOfs, outLen);
                        }
                        padBufferLen = 0;
                    }
                    newPadBufferLen = inLen & (blockSize - 1);
                    if (!encrypt && newPadBufferLen == 0) {
                        // While decrypting with implUpdate, the last encrypted block
                        // is always held in a buffer. If it's the final one (unknown
                        // at this point), it may contain padding bytes and need further
                        // processing. In implDoFinal (where we know it's the final one)
                        // the buffer is decrypted, unpadded and returned.
                        newPadBufferLen = padBuffer.length;
                    }
                    inLen -= newPadBufferLen;
                }
                if (inLen > 0) {
                    if (inAddr == 0 && inArray == null) {
                        inArray = new byte[inLen];
                        inBuffer.get(inArray);
                    } else {
                        inBuffer.position(inBuffer.position() + inLen);
                    }
                    if (encrypt) {
                        k += token.p11.C_EncryptUpdate(session.id(), inAddr,
                                inArray, inOfs, inLen, outAddr, outArray,
                                (outOfs + k), (outLen - k));
                    } else {
                        k += token.p11.C_DecryptUpdate(session.id(), inAddr,
                                inArray, inOfs, inLen, outAddr, outArray,
                                (outOfs + k), (outLen - k));
                    }
                }
                // update 'padBuffer' if using our own padding impl.
                if ((blockMode == Mode.CTS || paddingObj != null) &&
                        newPadBufferLen > 0) {
                    bufferInputBytes(inBuffer, newPadBufferLen);
                }
                bytesBuffered += (inLen - k);
                if (!(outBuffer instanceof DirectBuffer) &&
                        !outBuffer.hasArray()) {
                    outBuffer.put(outArray, outOfs, k);
                } else {
                    outBuffer.position(outBuffer.position() + k);
                }
                return k;
            } catch (PKCS11Exception e) {
                // Reset input buffer to its original position for
                inBuffer.position(origPos);
                if (e.match(CKR_BUFFER_TOO_SMALL)) {
                    throw (ShortBufferException)
                            (new ShortBufferException().initCause(e));
                }
                // Some implementations such as the NSS Software Token do not
                // cancel the operation upon a C_EncryptUpdate/C_DecryptUpdate
                // failure (as required by the PKCS#11 standard). See JDK-8258833
                // for further information.
                reset(true);
                throw new ProviderException("update() failed", e);
            } finally {
                NIO_ACCESS.releaseSession(outBuffer);
            }
        } finally {
            NIO_ACCESS.releaseSession(inBuffer);
        }
    }

    private int implDoFinal(byte[] out, int outOfs, int outLen)
            throws ShortBufferException, IllegalBlockSizeException,
            BadPaddingException {
        int requiredOutLen = doFinalLength(0);
        if (outLen < requiredOutLen) {
            throw new ShortBufferException();
        }
        boolean doCancel = true;
        try {
            ensureInitialized();
            int k = 0;
            if (encrypt) {
                if (paddingObj != null) {
                    int startOff = 0;
                    if (reqBlockUpdates) {
                        // call C_EncryptUpdate first if the padBuffer is full
                        // to make room for padding bytes
                        if (padBufferLen == padBuffer.length) {
                            k = token.p11.C_EncryptUpdate(session.id(),
                                0, padBuffer, 0, padBufferLen,
                                0, out, outOfs, outLen);
                        } else {
                            startOff = padBufferLen;
                        }
                    }
                    int actualPadLen = paddingObj.setPaddingBytes(padBuffer,
                            startOff, requiredOutLen - bytesBuffered);
                    k += token.p11.C_EncryptUpdate(session.id(),
                            0, padBuffer, 0, startOff + actualPadLen,
                            0, out, outOfs + k, outLen - k);
                } else if (blockMode == Mode.CTS) {
                    k = token.p11.C_EncryptUpdate(session.id(),
                            0, padBuffer, 0, padBufferLen,
                            0, out, outOfs, outLen);
                }
                // Some implementations such as the NSS Software Token do not
                // cancel the operation upon a C_EncryptUpdate failure (as
                // required by the PKCS#11 standard). Cancel is not needed
                // only after this point. See JDK-8258833 for further
                // information.
                doCancel = false;
                k += token.p11.C_EncryptFinal(session.id(),
                        0, out, (outOfs + k), (outLen - k));
                if (blockMode == Mode.CTS) {
                    convertCTSVariant(null, out, outOfs + k);
                }
            } else {
                // Special handling to match SunJCE provider behavior
                if (bytesBuffered == 0 && padBufferLen == 0) {
                    return 0;
                }
                if (paddingObj != null) {
                    if (padBufferLen != 0) {
                        k = token.p11.C_DecryptUpdate(session.id(), 0,
                                padBuffer, 0, padBufferLen, 0, padBuffer, 0,
                                padBuffer.length);
                    }
                    // Some implementations such as the NSS Software Token do not
                    // cancel the operation upon a C_DecryptUpdate failure (as
                    // required by the PKCS#11 standard). Cancel is not needed
                    // only after this point. See JDK-8258833 for further
                    // information.
                    doCancel = false;
                    k += token.p11.C_DecryptFinal(session.id(), 0, padBuffer, k,
                            padBuffer.length - k);

                    int actualPadLen = paddingObj.unpad(padBuffer, k);
                    k -= actualPadLen;
                    System.arraycopy(padBuffer, 0, out, outOfs, k);
                } else {
                    if (blockMode == Mode.CTS) {
                        convertCTSVariant(null, padBuffer, padBufferLen);
                        k = token.p11.C_DecryptUpdate(session.id(),
                                0, padBuffer, 0, padBufferLen,
                                0, out, outOfs, outLen);
                        outOfs += k;
                        outLen -= k;
                    }
                    doCancel = false;
                    k += token.p11.C_DecryptFinal(session.id(), 0, out, outOfs,
                            outLen);
                }
            }
            return k;
        } catch (PKCS11Exception e) {
            handleException(e);
            throw new ProviderException("doFinal() failed", e);
        } finally {
            reset(doCancel);
        }
    }

    private int implDoFinal(ByteBuffer outBuffer)
            throws ShortBufferException, IllegalBlockSizeException,
            BadPaddingException {
        int outLen = outBuffer.remaining();
        int requiredOutLen = doFinalLength(0);
        if (outLen < requiredOutLen) {
            throw new ShortBufferException();
        }

        boolean doCancel = true;
        NIO_ACCESS.acquireSession(outBuffer);
        try {
            try {
                ensureInitialized();

                long outAddr = 0;
                byte[] outArray = null;
                int outOfs = 0;
                if (outBuffer instanceof DirectBuffer) {
                    outAddr = NIO_ACCESS.getBufferAddress(outBuffer);
                    outOfs = outBuffer.position();
                } else {
                    if (outBuffer.hasArray()) {
                        outArray = outBuffer.array();
                        outOfs = outBuffer.position() + outBuffer.arrayOffset();
                    } else {
                        outArray = new byte[outLen];
                    }
                }

                int k = 0;

                if (encrypt) {
                    if (paddingObj != null) {
                        int startOff = 0;
                        if (reqBlockUpdates) {
                            // call C_EncryptUpdate first if the padBuffer is full
                            // to make room for padding bytes
                            if (padBufferLen == padBuffer.length) {
                                k = token.p11.C_EncryptUpdate(session.id(),
                                        0, padBuffer, 0, padBufferLen,
                                        outAddr, outArray, outOfs, outLen);
                            } else {
                                startOff = padBufferLen;
                            }
                        }
                        int actualPadLen = paddingObj.setPaddingBytes(padBuffer,
                                startOff, requiredOutLen - bytesBuffered);
                        k += token.p11.C_EncryptUpdate(session.id(),
                                0, padBuffer, 0, startOff + actualPadLen,
                                outAddr, outArray, outOfs + k, outLen - k);
                    } else if (blockMode == Mode.CTS) {
                       k = token.p11.C_EncryptUpdate(session.id(),
                                0, padBuffer, 0, padBufferLen,
                                outAddr, outArray, outOfs, outLen);
                    }
                    // Some implementations such as the NSS Software Token do not
                    // cancel the operation upon a C_EncryptUpdate failure (as
                    // required by the PKCS#11 standard). Cancel is not needed
                    // only after this point. See JDK-8258833 for further
                    // information.
                    doCancel = false;
                    k += token.p11.C_EncryptFinal(session.id(),
                            outAddr, outArray, (outOfs + k), (outLen - k));
                    if (blockMode == Mode.CTS) {
                        convertCTSVariant(outBuffer, outArray, outOfs + k);
                    }
                } else {
                    // Special handling to match SunJCE provider behavior
                    if (bytesBuffered == 0 && padBufferLen == 0) {
                        return 0;
                    }
                    if (paddingObj != null) {
                        if (padBufferLen != 0) {
                            k = token.p11.C_DecryptUpdate(session.id(),
                                    0, padBuffer, 0, padBufferLen,
                                    0, padBuffer, 0, padBuffer.length);
                            padBufferLen = 0;
                        }
                        // Some implementations such as the NSS Software Token do not
                        // cancel the operation upon a C_DecryptUpdate failure (as
                        // required by the PKCS#11 standard). Cancel is not needed
                        // only after this point. See JDK-8258833 for further
                        // information.
                        doCancel = false;
                        k += token.p11.C_DecryptFinal(session.id(),
                                0, padBuffer, k, padBuffer.length - k);

                        int actualPadLen = paddingObj.unpad(padBuffer, k);
                        k -= actualPadLen;
                        outArray = padBuffer;
                        outOfs = 0;
                    } else {
                        if (blockMode == Mode.CTS) {
                            convertCTSVariant(null, padBuffer, padBufferLen);
                            k = token.p11.C_DecryptUpdate(session.id(),
                                    0, padBuffer, 0, padBufferLen,
                                    outAddr, outArray, outOfs, outLen);
                            outOfs += k;
                            outLen -= k;
                        }
                        doCancel = false;
                        k += token.p11.C_DecryptFinal(session.id(),
                                outAddr, outArray, outOfs, outLen);
                    }
                }
                if ((!encrypt && paddingObj != null) ||
                        (!(outBuffer instanceof DirectBuffer) &&
                                !outBuffer.hasArray())) {
                    outBuffer.put(outArray, outOfs, k);
                } else {
                    outBuffer.position(outBuffer.position() + k);
                }
                return k;
            } catch (PKCS11Exception e) {
                handleException(e);
                throw new ProviderException("doFinal() failed", e);
            } finally {
                reset(doCancel);
            }
        } finally {
            NIO_ACCESS.releaseSession(outBuffer);
        }
    }

    private int getBytesToCompleteBlock(int availableBytes) {
        int partBlock = availableBytes & (blockSize - 1);
        return partBlock == 0 ? 0 : blockSize - partBlock;
    }

    private int getCTSMustBeBuffered(int availableBytes) {
        return Math.min(availableBytes,
                padBuffer.length - getBytesToCompleteBlock(availableBytes));
    }

    /**
     * The ciphertext ordering for the three variants can be depicted as
     * follows, where 'p' is the penultimate block (which may be partial
     * or full), and 'f' the full final block:
     *
     *                    'p' is a partial block   'p' is a full block
     *                   ------------------------ ---------------------
     *   CS1 (NIST)     |     .... pp ffff       |    .... pppp ffff
     *   CS2 (Schneier) |     .... ffff pp       |    .... pppp ffff
     *   CS3 (Kerberos) |     .... ffff pp       |    .... ffff pppp
     *
     * After encryption, we get the ciphertext from the token formatted as
     * specified in the SunPKCS11 'cipherTextStealingVariant' configuration
     * property. Conversely, before decryption, the ciphertext has to be passed
     * to the token according to the previous formatting. This method converts
     * the ciphertext between the format used by the token and the one used by
     * SunJCE's "AES/CTS/NoPadding" implementation (CS3 as described by RFC
     * 2040, section 8).
     */
    private void convertCTSVariant(ByteBuffer ciphertextBuf,
            byte[] ciphertextArr, int ciphertextEnd) {
        if (padBufferLen == blockSize) {
            // No reordering needed for a single block
            return;
        }
        assert token.ctsVariant != null : "CTS algorithms should not be " +
                "registered if the CTS variant of the token is unknown";
        if (token.ctsVariant == Token.CTSVariant.CS3) {
            // Already CS3
            return;
        }
        int pad = padBufferLen % blockSize;
        if (token.ctsVariant == Token.CTSVariant.CS2 && pad != 0) {
            // CS2 and 'p' is a partial block, equal to CS3
            return;
        }
        if (ciphertextArr != null) {
            ciphertextBuf = ByteBuffer.wrap(ciphertextArr);
        }
        if (ciphertextBuf != null) {
            // No assumptions should be made about the current ciphertextBuf
            // position. Additionally, if ciphertextBuf was not created here,
            // the position should not be altered. To ensure this, use offsets
            // to read and write bytes from the last two blocks (i.e. absolute
            // ByteBuffer operations). Other blocks should not be modified.
            pad = pad == 0 ? blockSize : pad;
            if (encrypt) {
                // .... pp[pp] ffff -> .... ffff pp[pp]
                swapLastTwoBlocks(ciphertextBuf, ciphertextEnd, pad, blockSize);
            } else {
                // .... ffff pp[pp] -> .... pp[pp] ffff
                swapLastTwoBlocks(ciphertextBuf, ciphertextEnd, blockSize, pad);
            }
        }
    }

    private static void swapLastTwoBlocks(ByteBuffer ciphertextBuf,
            int ciphertextEnd, int prevBlockLen, int lastBlockLen) {
        // .... prevBlock lastBlock -> .... lastBlock prevBlock
        int prevBlockStart = ciphertextEnd - prevBlockLen - lastBlockLen;
        byte[] prevBlockBackup = new byte[prevBlockLen];
        ciphertextBuf.get(prevBlockStart, prevBlockBackup);
        ciphertextBuf.put(prevBlockStart, ciphertextBuf,
                ciphertextEnd - lastBlockLen, lastBlockLen);
        ciphertextBuf.put(ciphertextEnd - prevBlockLen, prevBlockBackup);
    }

    private void handleException(PKCS11Exception e)
            throws ShortBufferException, IllegalBlockSizeException {
        if (e.match(CKR_BUFFER_TOO_SMALL)) {
            throw (ShortBufferException)
                    (new ShortBufferException().initCause(e));
        } else if (e.match(CKR_DATA_LEN_RANGE) ||
                e.match(CKR_ENCRYPTED_DATA_LEN_RANGE)) {
            throw (IllegalBlockSizeException)
                    (new IllegalBlockSizeException(e.toString()).initCause(e));
        }
    }

    // see JCE spec
    protected byte[] engineWrap(Key key) throws IllegalBlockSizeException,
            InvalidKeyException {
        // XXX key wrapping
        throw new UnsupportedOperationException("engineWrap()");
    }

    // see JCE spec
    protected Key engineUnwrap(byte[] wrappedKey, String wrappedKeyAlgorithm,
            int wrappedKeyType)
            throws InvalidKeyException, NoSuchAlgorithmException {
        // XXX key unwrapping
        throw new UnsupportedOperationException("engineUnwrap()");
    }

    // see JCE spec
    @Override
    protected int engineGetKeySize(Key key) throws InvalidKeyException {
        int n = P11SecretKeyFactory.convertKey
                (token, key, keyAlgorithm).length();
        return n;
    }

    private final void bufferInputBytes(byte[] in, int inOfs, int len) {
        System.arraycopy(in, inOfs, padBuffer, padBufferLen, len);
        padBufferLen += len;
        bytesBuffered += len;
    }

    private final void bufferInputBytes(ByteBuffer inBuffer, int len) {
        inBuffer.get(padBuffer, padBufferLen, len);
        padBufferLen += len;
        bytesBuffered += len;
    }
}
