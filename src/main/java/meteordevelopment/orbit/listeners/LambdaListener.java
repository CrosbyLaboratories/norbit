package meteordevelopment.orbit.listeners;

import meteordevelopment.orbit.EventHandler;

import java.lang.invoke.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.Consumer;

/**
 * Default implementation of a {@link IListener} that creates a lambda at runtime to call the target method.
 */
public class LambdaListener implements IListener {
    public interface Factory {
        CallSite create(MethodHandles.Lookup caller, String invokedName, MethodType invokedType, MethodType samMethodType, MethodHandle implMethod, MethodType instantiatedMethodType) throws LambdaConversionException;
    }

    private static boolean isJava1dot8;
    private static Constructor<MethodHandles.Lookup> lookupConstructor;
    private static Method privateLookupInMethod;

    private final Class<?> target;
    private final boolean isStatic;
    private final int priority;
    private Consumer<Object> executor;

    /**
     * Creates a new lambda listener, can be used for both static and non-static methods.
     * @param klass Class of the object
     * @param object Object, null if static
     * @param method Method to create lambda for
     */
    @SuppressWarnings("unchecked")
    public LambdaListener(Factory factory, Class<?> klass, Object object, Method method) {
        this.target = method.getParameters()[0].getType();
        this.isStatic = Modifier.isStatic(method.getModifiers());
        this.priority = method.getAnnotation(EventHandler.class).priority();

        try {
            String name = method.getName();
            MethodHandles.Lookup lookup;

            if (isJava1dot8) {
                boolean a = lookupConstructor.isAccessible();
                lookupConstructor.setAccessible(true);
                lookup = lookupConstructor.newInstance(klass);
                lookupConstructor.setAccessible(a);
            }
            else {
                lookup = (MethodHandles.Lookup) privateLookupInMethod.invoke(null, klass, MethodHandles.lookup());
            }

            MethodType methodType = MethodType.methodType(void.class, method.getParameters()[0].getType());

            MethodHandle methodHandle;
            MethodType invokedType;

            if (isStatic) {
                methodHandle = lookup.findStatic(klass, name, methodType);
                invokedType = MethodType.methodType(Consumer.class);
            }
            else {
                methodHandle = lookup.findVirtual(klass, name, methodType);
                invokedType = MethodType.methodType(Consumer.class, klass);
            }

            MethodHandle lambdaFactory = factory.create(lookup, "accept", invokedType, MethodType.methodType(void.class, Object.class), methodHandle, methodType).getTarget();

            if (isStatic) this.executor = (Consumer<Object>) lambdaFactory.invoke();
            else this.executor = (Consumer<Object>) lambdaFactory.invoke(object);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    @Override
    public void call(Object event) {
        executor.accept(event);
    }

    @Override
    public Class<?> getTarget() {
        return target;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public boolean isStatic() {
        return isStatic;
    }

    static {
        try {
            isJava1dot8 = System.getProperty("java.version").startsWith("1.8");

            if (isJava1dot8) {
                lookupConstructor = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class);
            }
            else {
                privateLookupInMethod = MethodHandles.class.getDeclaredMethod("privateLookupIn", Class.class, MethodHandles.Lookup.class);
            }
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }
}
