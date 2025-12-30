import jdk.incubator.code.CodeTransformer;
import jdk.incubator.code.Op;
import jdk.incubator.code.Reflect;
import jdk.incubator.code.bytecode.BytecodeGenerator;
import jdk.incubator.code.dialect.java.JavaType;
import jdk.incubator.code.interpreter.Interpreter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

import static jdk.incubator.code.dialect.core.CoreOp.*;

/*
 * @test
 * @modules jdk.incubator.code
 * @run junit TestRecursiveFuncOp
 */
public class TestRecursiveFuncOp {
    @Reflect
    static String f(int i) {
        if (i < 0)
            return "";
        String s = f(i - 1);
        return i + (s.isEmpty() ? "" : " " + s);
    }

    @Test
    void test() throws Throwable {
        Method f = this.getClass().getDeclaredMethod("f", int.class);
        FuncOp funcOp = Op.ofMethod(f).get();
        // replace delimiter: space -> comma
        FuncOp transformed = funcOp.transform((b, op) -> {
            if (op instanceof ConstantOp cop && cop.resultType().equals(JavaType.J_L_STRING) && " ".equals(cop.value())) {
                Op.Result r = b.op(constant(JavaType.J_L_STRING, ","));
                b.context().mapValue(op.result(), r);
            } else {
                b.op(op);
            }
            return b;
        });
        FuncOp lowered = transformed.transform(CodeTransformer.LOWERING_TRANSFORMER);

        Assertions.assertEquals("2,1,0", Interpreter.invoke(MethodHandles.lookup(), lowered, 2));

        MethodHandle mh = BytecodeGenerator.generate(MethodHandles.lookup(), lowered);
        mh.invoke(2);
        Assertions.assertEquals("2,1,0", mh.invoke(2));
    }
}
