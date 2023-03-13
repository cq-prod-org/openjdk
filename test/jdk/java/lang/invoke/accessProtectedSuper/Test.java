/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8022718
 * @summary Runtime accessibility checking: protected class, if extended, should be accessible from another package
 * @modules java.base/jdk.internal.classfile
 * @modules java.base/jdk.internal.classfile.attribute
 * @modules java.base/jdk.internal.classfile.constantpool
 * @compile -XDignore.symbol.file BogoLoader.java MethodInvoker.java Test.java anotherpkg/MethodSupplierOuter.java
 * @run main/othervm Test
 */

import java.lang.reflect.AccessFlag;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import jdk.internal.classfile.ClassTransform;
import jdk.internal.classfile.attribute.InnerClassInfo;
import jdk.internal.classfile.attribute.InnerClassesAttribute;
import jdk.internal.classfile.constantpool.ClassEntry;
import jdk.internal.classfile.constantpool.Utf8Entry;

interface MyFunctionalInterface {

    void invokeMethodReference();
}

public class Test {

    public static void main(String[] argv) throws Throwable {
        ClassTransform makeProtectedNop = ClassTransform.ACCEPT_ALL;
        ClassTransform makeProtectedMod = (cb, ce) -> {
            if (ce instanceof InnerClassesAttribute ica) {
                cb.accept(InnerClassesAttribute.of(ica.classes().stream().map(ici -> {
                    var flags = EnumSet.copyOf(ici.flags());
                    flags.remove(AccessFlag.PRIVATE);
                    flags.remove(AccessFlag.PUBLIC);
                    flags.add(AccessFlag.PROTECTED);
                    // AccessFlags doesn't support inner class flags yet
                    var updatedFlags = flags.stream().mapToInt(AccessFlag::mask).reduce(0, (a, b) -> a | b);
                    System.out.println("visitInnerClass: name = " + ici.innerClass().asInternalName()
                            + ", outerName = " + ici.outerClass().map(ClassEntry::asInternalName).orElse("null")
                            + ", innerName = " + ici.innerName().map(Utf8Entry::stringValue).orElse("null")
                            + ", access original = 0x" + Integer.toHexString(ici.flagsMask())
                            + ", access modified to 0x" + Integer.toHexString(updatedFlags));
                    return InnerClassInfo.of(ici.innerClass(), ici.outerClass(), ici.innerName(), updatedFlags);
                }).toList()));
            } else {
                cb.accept(ce);
            }
        };

        int errors = 0;
        errors += tryModifiedInvocation(makeProtectedNop);
        errors += tryModifiedInvocation(makeProtectedMod);

        if (errors > 0) {
            throw new Error("FAIL; there were errors");
        }
    }

    private static int tryModifiedInvocation(ClassTransform makeProtected)
            throws Throwable {
        var replace = new HashMap<String, ClassTransform>();
        replace.put("anotherpkg.MethodSupplierOuter$MethodSupplier", makeProtected);
        var in_bogus = new HashSet<String>();
        in_bogus.add("MethodInvoker");
        in_bogus.add("MyFunctionalInterface");
        in_bogus.add("anotherpkg.MethodSupplierOuter"); // seems to be never loaded
        in_bogus.add("anotherpkg.MethodSupplierOuter$MethodSupplier");

        BogoLoader bl = new BogoLoader(in_bogus, replace);
        try {
            Class<?> isw = bl.loadClass("MethodInvoker");
            Method meth = isw.getMethod("invoke");
            Object result = meth.invoke(null);
        } catch (Throwable th) {
            System.out.flush();
            Thread.sleep(250); // Let Netbeans get its I/O sorted out.
            th.printStackTrace();
            System.err.flush();
            Thread.sleep(250); // Let Netbeans get its I/O sorted out.
            return 1;
        }
        return 0;
    }
}
