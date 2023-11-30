/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test
 * @bug 8320971
 * @summary Verify BufferedInputStream.buf is used directly by
 *          BufferedInputStream.implTransferTo() only when its OutputStream
 *          parameter is trusted
 * @key randomness
 */

import java.io.*;
import java.util.Arrays;
import java.util.Objects;
import java.util.Random;
public class TransferToTrusted {
    private static final Random RND = new Random(System.nanoTime());

    private static class UntrustedOutputStream extends OutputStream {
        UntrustedOutputStream() {
            super();
        }

        @Override
        public void write(byte[] b, int off, int len) {
            Objects.checkFromIndexSize(off, len, b.length);
            byte[] tmp = new byte[len];
            RND.nextBytes(tmp);
            System.arraycopy(tmp, 0, b, off, len);
        }

        @Override
        public void write(int b) throws IOException {
            write(new byte[] {(byte)b});
        }
    }

    public static void main(String[] args) throws IOException {
        byte[] buf = new byte[128];
        RND.nextBytes(buf);
        byte[] dup = Arrays.copyOf(buf, buf.length);

        var bis = new BufferedInputStream(new ByteArrayInputStream(dup));
        bis.mark(dup.length);

        var baos = new ByteArrayOutputStream();
        bis.transferTo(baos);
        bis.reset();
        if (!Arrays.equals(buf, bis.readAllBytes()))
            throw new RuntimeException("Internal buffer has been modified");

        bis.reset();
        var out = new UntrustedOutputStream();
        bis.transferTo(out);
        bis.reset();
        if (!Arrays.equals(buf, bis.readAllBytes()))
            throw new RuntimeException("Internal buffer has been modified");
    }
}