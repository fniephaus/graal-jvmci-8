/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle.test;

import org.junit.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;

public class BytecodeInterpreterPartialEvaluationTest extends PartialEvaluationTest {

    public static class Bytecode {
        public static final byte CONST = 0;
        public static final byte RETURN = 1;
        public static final byte ADD = 2;
        public static final byte IFZERO = 3;
        public static final byte POP = 4;
    }

    public static class Program extends RootNode {
        private final String name;
        @CompilationFinal private final byte[] bytecodes;
        @CompilationFinal private final FrameSlot[] locals;
        @CompilationFinal private final FrameSlot[] stack;

        public Program(String name, byte[] bytecodes, int maxLocals, int maxStack) {
            this.name = name;
            this.bytecodes = bytecodes;
            locals = new FrameSlot[maxLocals];
            stack = new FrameSlot[maxStack];
            for (int i = 0; i < maxLocals; ++i) {
                locals[i] = this.getFrameDescriptor().addFrameSlot("local" + i);
                locals[i].setKind(FrameSlotKind.Int);
            }
            for (int i = 0; i < maxStack; ++i) {
                stack[i] = this.getFrameDescriptor().addFrameSlot("stack" + i);
                stack[i].setKind(FrameSlotKind.Int);
            }
        }

        protected void setInt(VirtualFrame frame, int stackIndex, int value) {
            frame.setInt(stack[stackIndex], value);
        }

        protected int getInt(VirtualFrame frame, int stackIndex) {
            try {
                return frame.getInt(stack[stackIndex]);
            } catch (FrameSlotTypeException e) {
                throw new IllegalStateException("Error accessing stack slot " + stackIndex);
            }
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        @ExplodeLoop(merge = true)
        public Object execute(VirtualFrame frame) {
            int topOfStack = -1;
            int bci = 0;
            while (true) {
                CompilerAsserts.partialEvaluationConstant(bci);
                byte bc = bytecodes[bci];
                byte value = 0;
                switch (bc) {
                    case Bytecode.CONST:
                        value = bytecodes[bci + 1];
                        setInt(frame, ++topOfStack, value);
                        bci = bci + 2;
                        continue;
                    case Bytecode.RETURN:
                        return getInt(frame, topOfStack);
                    case Bytecode.ADD:
                        setInt(frame, topOfStack - 1, getInt(frame, topOfStack) + getInt(frame, topOfStack - 1));
                        topOfStack--;
                        bci = bci + 1;
                        continue;
                    case Bytecode.IFZERO:
                        if (getInt(frame, topOfStack--) == 0) {
                            bci = bytecodes[bci + 1];
                            continue;
                        } else {
                            bci = bci + 2;
                            continue;
                        }
                    case Bytecode.POP:
                        topOfStack--;
                        bci++;
                        continue;
                }
            }
        }
    }

    public static Object constant42() {
        return 42;
    }

    @Test
    public void constReturnProgram() {
        byte[] bytecodes = new byte[]{
        /* 0: */Bytecode.CONST,
        /* 1: */42,
        /* 2: */Bytecode.RETURN};
        assertPartialEvalEquals("constant42", new Program("constReturnProgram", bytecodes, 0, 2));
    }

    @Test
    public void constAddProgram() {
        byte[] bytecodes = new byte[]{
        /* 0: */Bytecode.CONST,
        /* 1: */40,
        /* 2: */Bytecode.CONST,
        /* 3: */2,
        /* 4: */Bytecode.ADD,
        /* 5: */Bytecode.RETURN};
        assertPartialEvalEquals("constant42", new Program("constAddProgram", bytecodes, 0, 2));
    }

    @Test
    public void simpleIfProgram() {
        byte[] bytecodes = new byte[]{
        /* 0: */Bytecode.CONST,
        /* 1: */40,
        /* 2: */Bytecode.CONST,
        /* 3: */1,
        /* 4: */Bytecode.IFZERO,
        /* 5: */8,
        /* 6: */Bytecode.CONST,
        /* 7: */42,
        /* 8: */Bytecode.RETURN};
        assertPartialEvalEquals("constant42", new Program("simpleIfProgram", bytecodes, 0, 3));
    }

    @Test
    public void ifAndPopProgram() {
        byte[] bytecodes = new byte[]{
        /* 0: */Bytecode.CONST,
        /* 1: */40,
        /* 2: */Bytecode.CONST,
        /* 3: */1,
        /* 4: */Bytecode.IFZERO,
        /* 5: */9,
        /* 6: */Bytecode.POP,
        /* 7: */Bytecode.CONST,
        /* 8: */42,
        /* 9: */Bytecode.RETURN};
        assertPartialEvalEquals("constant42", new Program("ifAndPopProgram", bytecodes, 0, 3));
    }

    @Test(timeout = 1000)
    public void manyIfsProgram() {
        byte[] bytecodes = new byte[]{
        /* 0: */Bytecode.CONST,
        /* 1: */40,
        /* 2: */Bytecode.CONST,
        /* 3: */1,
        /* 4: */Bytecode.IFZERO,
        /* 5: */8,
        /* 6: */Bytecode.CONST,
        /* 7: */1,
        /* 8: */Bytecode.IFZERO,
        /* 9: */12,
        /* 10: */Bytecode.CONST,
        /* 11: */1,
        /* 12: */Bytecode.IFZERO,
        /* 13: */16,
        /* 14: */Bytecode.CONST,
        /* 15: */1,
        /* 16: */Bytecode.IFZERO,
        /* 17: */20,
        /* 18: */Bytecode.CONST,
        /* 19: */1,
        /* 20: */Bytecode.IFZERO,
        /* 21: */24,
        /* 22: */Bytecode.CONST,
        /* 23: */1,
        /* 24: */Bytecode.IFZERO,
        /* 25: */28,
        /* 26: */Bytecode.CONST,
        /* 27: */1,
        /* 28: */Bytecode.IFZERO,
        /* 29: */32,
        /* 30: */Bytecode.CONST,
        /* 31: */1,
        /* 32: */Bytecode.IFZERO,
        /* 33: */36,
        /* 34: */Bytecode.CONST,
        /* 35: */1,
        /* 36: */Bytecode.IFZERO,
        /* 37: */40,
        /* 38: */Bytecode.CONST,
        /* 39: */1,
        /* 40: */Bytecode.IFZERO,
        /* 41: */44,
        /* 42: */Bytecode.CONST,
        /* 43: */42,
        /* 44: */Bytecode.RETURN};
        assertPartialEvalEquals("constant42", new Program("manyIfsProgram", bytecodes, 0, 3));
    }
}
