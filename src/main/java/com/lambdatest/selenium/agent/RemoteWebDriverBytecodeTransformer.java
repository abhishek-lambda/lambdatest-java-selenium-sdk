package com.lambdatest.selenium.agent;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * ASM-based ClassFileTransformer that injects capability enhancement into RemoteWebDriver constructor.
 * 
 * This is the SINGLE POINT where capability enhancement happens:
 * - Intercepts RemoteWebDriver constructor
 * - Enhances capabilities from lambdatest.yml BEFORE driver creation
 * - Registers driver for automatic cleanup AFTER construction
 * 
 * This prevents duplicate enhancement that would happen if we also intercepted MutableCapabilities.
 */
public class RemoteWebDriverBytecodeTransformer implements ClassFileTransformer {
    
    private static final java.util.Set<String> transformedClasses = new java.util.concurrent.ConcurrentHashMap<String, Boolean>().keySet(true);
    
    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                          ProtectionDomain protectionDomain, byte[] classfileBuffer)
            throws IllegalClassFormatException {
        
        // Only transform RemoteWebDriver class
        if (!"org/openqa/selenium/remote/RemoteWebDriver".equals(className)) {
            return null;
        }
        
        // Prevent multiple transformations of the same class
        if (transformedClasses.contains(className)) {
            return null;
        }
        
        transformedClasses.add(className);
        
        try {
            ClassReader classReader = new ClassReader(classfileBuffer);
            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            
            ClassVisitor classVisitor = new RemoteWebDriverClassVisitor(classWriter);
            classReader.accept(classVisitor, 0);
            
            byte[] transformedBytes = classWriter.toByteArray();
            
            return transformedBytes;
            
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * ASM ClassVisitor that modifies RemoteWebDriver constructors to inject capability enhancement calls.
     */
    private static class RemoteWebDriverClassVisitor extends ClassVisitor {
        
        public RemoteWebDriverClassVisitor(ClassVisitor cv) {
            super(Opcodes.ASM9, cv);
        }
        
        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            
            // Only modify constructors
            if ("<init>".equals(name)) {
                return new RemoteWebDriverConstructorVisitor(mv, access, name, descriptor);
            }
            
            return mv;
        }
    }
    
    /**
     * ASM MethodVisitor that injects static method calls into RemoteWebDriver constructors.
     */
    private static class RemoteWebDriverConstructorVisitor extends MethodVisitor {
        
        private final String descriptor;
        private boolean methodCallInjected = false;
        
        public RemoteWebDriverConstructorVisitor(MethodVisitor mv, int access, String name, String descriptor) {
            super(Opcodes.ASM9, mv);
            this.descriptor = descriptor;
        }
        
        @Override
        public void visitCode() {
            super.visitCode();
            
            // Inject static method call at the beginning of constructor
            if (!methodCallInjected) {
                // Only intercept the specific constructor that users typically call: (URL, Capabilities)
                if ("(Ljava/net/URL;Lorg/openqa/selenium/Capabilities;)V".equals(descriptor)) {
                    // For constructor (URL, Capabilities), the arguments are at positions 1 and 2
                    // (position 0 is 'this')
                    
                    // Check if this is a LambdaTest URL and capabilities are mutable
                    // Load URL argument (at position 1)
                    super.visitVarInsn(Opcodes.ALOAD, 1);
                    super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/net/URL", "toString", "()Ljava/lang/String;", false);
                    super.visitLdcInsn("lambdatest.com");
                    super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z", false);
                    
                    org.objectweb.asm.Label endLabel = new org.objectweb.asm.Label();
                    super.visitJumpInsn(Opcodes.IFEQ, endLabel); // Jump if false (not LambdaTest URL)
                    
                    // If LambdaTest URL, check if capabilities are MutableCapabilities
                    super.visitVarInsn(Opcodes.ALOAD, 2);
                    super.visitTypeInsn(Opcodes.INSTANCEOF, "org/openqa/selenium/MutableCapabilities");
                    super.visitJumpInsn(Opcodes.IFEQ, endLabel); // Jump if false
                    
                    // Cast Capabilities to MutableCapabilities and call enhanceCapabilities
                    super.visitVarInsn(Opcodes.ALOAD, 2);
                    super.visitTypeInsn(Opcodes.CHECKCAST, "org/openqa/selenium/MutableCapabilities");
                    super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "com/lambdatest/selenium/agent/RemoteWebDriverAdvice",
                        "enhanceCapabilities",
                        "(Lorg/openqa/selenium/MutableCapabilities;)V",
                        false
                    );
                    
                    super.visitLabel(endLabel);
                    
                    // Set flag immediately to prevent multiple injections
                    methodCallInjected = true;
                }
            }
        }
        
        @Override
        public void visitInsn(int opcode) {
            // If this is a RETURN instruction at the end of constructor, register the driver
            if (opcode == Opcodes.RETURN && "(Ljava/net/URL;Lorg/openqa/selenium/Capabilities;)V".equals(descriptor)) {
                // Before returning from constructor, register this driver instance for cleanup
                // Load 'this' (the newly created RemoteWebDriver instance)
                super.visitVarInsn(Opcodes.ALOAD, 0);
                
                // Call LambdaTestAgent.registerDriver(this)
                super.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "com/lambdatest/selenium/agent/LambdaTestAgent",
                    "registerDriver",
                    "(Lorg/openqa/selenium/WebDriver;)V",
                    false
                );
            }
            
            super.visitInsn(opcode);
        }
    }
}
