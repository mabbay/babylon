import jdk.incubator.code.CodeTransformer;
import jdk.incubator.code.Op;
import jdk.incubator.code.Reflect;
import jdk.incubator.code.bytecode.BytecodeGenerator;
import jdk.incubator.code.dialect.core.CoreOp;
import jdk.incubator.code.dialect.java.JavaType;
import jdk.incubator.code.interpreter.Interpreter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Arrays;

import static jdk.incubator.code.dialect.core.CoreOp.constant;

/*
 * @test
 * @modules jdk.incubator.code
 * @run junit TestSelfInvokeOp
 */
public class TestSelfInvokeOp {
    @Reflect
    static String f(int i) {
        if (i < 0)
            return "";
        String s = f(i - 1);
        return i + (s.isEmpty() ? "" : " " + s);
    }

    @Test
    void testSelfInvokeStatic() throws Throwable {
        Method m = this.getClass().getDeclaredMethod("f", int.class);
        CoreOp.FuncOp funcOp = Op.ofMethod(m).get();
        // replace delimiter: space -> comma
        CoreOp.FuncOp transformed = funcOp.transform((b, op) -> {
            if (op instanceof CoreOp.ConstantOp cop && cop.resultType().equals(JavaType.J_L_STRING) && " ".equals(cop.value())) {
                Op.Result r = b.op(constant(JavaType.J_L_STRING, ","));
                b.context().mapValue(op.result(), r);
            } else {
                b.op(op);
            }
            return b;
        });
        CoreOp.FuncOp lowered = transformed.transform(CodeTransformer.LOWERING_TRANSFORMER);

        Assertions.assertEquals("2,1,0", Interpreter.invoke(MethodHandles.lookup(), lowered, 2));

        MethodHandle mh = BytecodeGenerator.generate(MethodHandles.lookup(), lowered);
        Assertions.assertEquals("2,1,0", mh.invoke(2));
    }

    @Reflect
    String g(int i) {
        if (i < 0)
            return "";
        String s = g(i - 1);
        return i + (s.isEmpty() ? "" : " " + s);
    }

    @Test
    void testSelfInvokeInstance() throws Throwable {
        Method m = this.getClass().getDeclaredMethod("g", int.class);
        CoreOp.FuncOp funcOp = Op.ofMethod(m).get();
        // replace delimiter: space -> comma
        CoreOp.FuncOp transformed = funcOp.transform((b, op) -> {
            if (op instanceof CoreOp.ConstantOp cop && cop.resultType().equals(JavaType.J_L_STRING) && " ".equals(cop.value())) {
                Op.Result r = b.op(constant(JavaType.J_L_STRING, ","));
                b.context().mapValue(op.result(), r);
            } else {
                b.op(op);
            }
            return b;
        });
        CoreOp.FuncOp lowered = transformed.transform(CodeTransformer.LOWERING_TRANSFORMER);

        Assertions.assertEquals("2,1,0", Interpreter.invoke(MethodHandles.lookup(), lowered, this, 2));

        MethodHandle mh = BytecodeGenerator.generate(MethodHandles.lookup(), lowered);
        Assertions.assertEquals("2,1,0", mh.invoke(this, 2));
    }

    @Reflect
    static String w(int v, int... a) {
        if (v < 0) {
            return "$" + a.length;
        }
        // vararg invocation
        String r = "";
        r += w(-1);
        r += w(-1, 1);
        r += w(-1, 2, 3);
        // not vararg invocation
        r += w(-1, new int[] {4, 5, 6});
        return v + r;
    }

    @Test
    void testSelfInvokeWithVarArg() throws Throwable {
        Method m = this.getClass().getDeclaredMethod("w", int.class, int[].class);
        CoreOp.FuncOp funcOp = Op.ofMethod(m).get();
        CoreOp.FuncOp transformed = funcOp.transform((b, op) -> {
            if (op instanceof CoreOp.ConstantOp cop && cop.resultType().equals(JavaType.J_L_STRING) && "$".equals(cop.value())) {
                Op.Result r = b.op(constant(JavaType.J_L_STRING, "*"));
                b.context().mapValue(op.result(), r);
            } else {
                b.op(op);
            }
            return b;
        });
        CoreOp.FuncOp lowered = transformed.transform(CodeTransformer.LOWERING_TRANSFORMER);
        System.out.println(lowered.toText());

        Assertions.assertEquals("2*0*1*2*3", Interpreter.invoke(MethodHandles.lookup(), lowered, 2, new int[] {}));

        MethodHandle mh = BytecodeGenerator.generate(MethodHandles.lookup(), lowered);
        Assertions.assertEquals("2*0*1*2*3", mh.invoke(2, new int[] {}));
    }
}
