/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.invoke;

import jdk.internal.foreign.AbstractMemorySegmentImpl;
import jdk.internal.foreign.Utils;
import jdk.internal.misc.ScopedMemoryAccess;
import jdk.internal.vm.annotation.ForceInline;

import java.util.Objects;

import static java.lang.invoke.MethodHandleStatics.UNSAFE;

/**
 * Base class for memory segment var handle view implementations.
 */
abstract sealed class VarHandleSegmentViewBase extends VarHandle permits
        VarHandleSegmentAsBytes,
        VarHandleSegmentAsChars,
        VarHandleSegmentAsDoubles,
        VarHandleSegmentAsFloats,
        VarHandleSegmentAsInts,
        VarHandleSegmentAsLongs,
        VarHandleSegmentAsShorts {

    static final boolean BE = UNSAFE.isBigEndian();

    static final ScopedMemoryAccess SCOPED_MEMORY_ACCESS = ScopedMemoryAccess.getScopedMemoryAccess();

    /** endianness **/
    final boolean be;

    /** access size (in bytes, computed from var handle carrier type) **/
    final long length;

    /** alignment constraint (in bytes, expressed as a bit mask) **/
    final long alignmentMask;

    VarHandleSegmentViewBase(VarForm form, boolean be, long length, long alignmentMask, boolean exact) {
        super(form, exact);
        this.be = be;
        this.length = length;
        this.alignmentMask = alignmentMask;
    }

    @ForceInline
    static AbstractMemorySegmentImpl checkAddress(Object obb, long offset, long length, boolean ro) {
        AbstractMemorySegmentImpl oo = (AbstractMemorySegmentImpl) Objects.requireNonNull(obb);
        oo.checkAccess(offset, length, ro);
        return oo;
    }

    @ForceInline
    static long offsetPlain(AbstractMemorySegmentImpl bb, long offset, long alignmentMask) {
        long base = bb.unsafeGetOffset();
        long address = base + offset;
        long maxAlignMask = bb.maxAlignMask();
        if (((address | maxAlignMask) & alignmentMask) != 0) {
            throw newIllegalArgumentExceptionForMisalignedAccess(address);
        }
        return address;
    }

    static IllegalArgumentException newIllegalArgumentExceptionForMisalignedAccess(long address) {
        return new IllegalArgumentException("Misaligned access at address: " + Utils.toHexString(address));
    }

    static UnsupportedOperationException newUnsupportedAccessModeForAlignment(long alignment) {
        return new UnsupportedOperationException("Unsupported access mode for alignment: " + alignment);
    }
}
