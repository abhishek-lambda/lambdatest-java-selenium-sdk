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
 * ASM-based ClassFileTransformer that intercepts local driver constructors
 * and redirects them to LambdaTest's RemoteWebDriver.
 * 
 * Supported drivers:
 * - ChromeDriver
 * - FirefoxDriver
 * - EdgeDriver
 * - SafariDriver
 * - OperaDriver
 * - InternetExplorerDriver
 * 
 * This allows users to write: new ChromeDriver() and have it automatically
 * connect to LambdaTest cloud platform.
 */
public class LocalDriverBytecodeTransformer implements ClassFileTransformer {
    
    private static final java.util.Set<String> transformedClasses = 
        new java.util.concurrent.ConcurrentHashMap<String, Boolean>().keySet(true);
    
    // Map of driver class names to browser names for LambdaTest
    private static final java.util.Map<String, String> DRIVER_TO_BROWSER = new java.util.HashMap<>();
    static {
        DRIVER_TO_BROWSER.put("org/openqa/selenium/chrome/ChromeDriver", "Chrome");
        DRIVER_TO_BROWSER.put("org/openqa/selenium/firefox/FirefoxDriver", "Firefox");
        DRIVER_TO_BROWSER.put("org/openqa/selenium/edge/EdgeDriver", "MS Edge");
        DRIVER_TO_BROWSER.put("org/openqa/selenium/safari/SafariDriver", "Safari");
        DRIVER_TO_BROWSER.put("org/openqa/selenium/opera/OperaDriver", "Opera");
        DRIVER_TO_BROWSER.put("org/openqa/selenium/ie/InternetExplorerDriver", "IE");
    }
    
    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                          ProtectionDomain protectionDomain, byte[] classfileBuffer)
            throws IllegalClassFormatException {
        
        // Only transform supported local driver classes
        if (!DRIVER_TO_BROWSER.containsKey(className)) {
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
            
            String browserName = DRIVER_TO_BROWSER.get(className);
            ClassVisitor classVisitor = new LocalDriverClassVisitor(classWriter, className, browserName);
            classReader.accept(classVisitor, 0);
            
            byte[] transformedBytes = classWriter.toByteArray();
            
            return transformedBytes;
            
        } catch (Exception e) {
            // Log error but don't fail - fall back to original driver
            java.util.logging.Logger.getLogger(getClass().getName())
                .warning("Failed to transform " + className + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * ASM ClassVisitor that modifies local driver constructors to redirect to RemoteWebDriver.
     */
    private static class LocalDriverClassVisitor extends ClassVisitor {
        
        private final String driverClassName;
        private final String browserName;
        
        public LocalDriverClassVisitor(ClassVisitor cv, String driverClassName, String browserName) {
            super(Opcodes.ASM9, cv);
            this.driverClassName = driverClassName;
            this.browserName = browserName;
        }
        
        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            
            // Modify no-arg constructors (most common case)
            if ("<init>".equals(name) && "()V".equals(descriptor)) {
                return new LocalDriverConstructorVisitor(mv, access, name, descriptor, driverClassName, browserName);
            }
            
            // Intercept key WebDriver methods to delegate to RemoteWebDriver
            // Methods from WebDriver interface that need delegation
            if (!"<init>".equals(name) && !"<clinit>".equals(name)) {
                // Intercept common WebDriver methods: get, quit, getCurrentUrl, getTitle, etc.
                if (isWebDriverMethod(name, descriptor)) {
                    return new LocalDriverMethodVisitor(mv, access, name, descriptor, driverClassName);
                }
            }
            
            return mv;
        }
        
        /**
         * Check if a method is a WebDriver interface method that needs delegation.
         */
        private boolean isWebDriverMethod(String name, String descriptor) {
            // Common WebDriver interface methods
            return name.equals("get") || 
                   name.equals("getCurrentUrl") ||
                   name.equals("getTitle") ||
                   name.equals("getPageSource") ||
                   name.equals("findElement") ||
                   name.equals("findElements") ||
                   name.equals("getWindowHandle") ||
                   name.equals("getWindowHandles") ||
                   name.equals("switchTo") ||
                   name.equals("navigate") ||
                   name.equals("manage") ||
                   name.equals("quit") ||
                   name.equals("close");
        }
    }
    
    /**
     * ASM MethodVisitor that replaces local driver constructor with RemoteWebDriver creation.
     */
    private static class LocalDriverConstructorVisitor extends MethodVisitor {
        
        private final String driverClassName;
        private final String browserName;
        private boolean replaced = false;
        
        public LocalDriverConstructorVisitor(MethodVisitor mv, int access, String name, 
                                            String descriptor, String driverClassName, String browserName) {
            super(Opcodes.ASM9, mv);
            this.driverClassName = driverClassName;
            this.browserName = browserName;
        }
        
        @Override
        public void visitCode() {
            super.visitCode();
            
            if (!replaced) {
                // Intercept constructor: create RemoteWebDriver and register it
                // Load browser name string
                super.visitLdcInsn(browserName);
                
                // Load 'this' reference  
                super.visitVarInsn(Opcodes.ALOAD, 0);
                
                // Call helper to create RemoteWebDriver and store mapping
                super.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "com/lambdatest/selenium/agent/LocalDriverRedirector",
                    "redirectToLambdaTest",
                    "(Ljava/lang/String;Ljava/lang/Object;)V",
                    false
                );
                
                replaced = true;
            }
        }
        
        @Override
        public void visitInsn(int opcode) {
            super.visitInsn(opcode);
        }
    }
    
    /**
     * ASM MethodVisitor that intercepts WebDriver method calls and delegates to RemoteWebDriver.
     */
    private static class LocalDriverMethodVisitor extends MethodVisitor {
        
        private final String driverClassName;
        private final String methodName;
        private final String methodDescriptor;
        
        public LocalDriverMethodVisitor(MethodVisitor mv, int access, String name, 
                                      String descriptor, String driverClassName) {
            super(Opcodes.ASM9, mv);
            this.driverClassName = driverClassName;
            this.methodName = name;
            this.methodDescriptor = descriptor;
        }
        
        @Override
        public void visitCode() {
            super.visitCode();
            
            // Get RemoteWebDriver for this local driver instance
            // Load 'this'
            super.visitVarInsn(Opcodes.ALOAD, 0);
            
            // Call helper to get RemoteWebDriver
            super.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "com/lambdatest/selenium/agent/LocalDriverRedirector",
                "getRemoteDriver",
                "(Ljava/lang/Object;)Lorg/openqa/selenium/remote/RemoteWebDriver;",
                false
            );
            
            // Check if RemoteWebDriver is null (fallback to original method)
            org.objectweb.asm.Label useOriginal = new org.objectweb.asm.Label();
            super.visitInsn(Opcodes.DUP); // Duplicate RemoteWebDriver reference
            super.visitJumpInsn(Opcodes.IFNULL, useOriginal);
            
            // RemoteWebDriver exists - delegate the method call
            // The RemoteWebDriver is on stack, now we need to:
            // 1. Load method arguments (they're already on stack from original call)
            // 2. Call the method on RemoteWebDriver
            // 3. Return the result
            
            // But we need to handle different method signatures...
            // For now, let's use a simpler approach: call a helper method
            
            // Load method name
            super.visitLdcInsn(methodName);
            
            // Load method descriptor  
            super.visitLdcInsn(methodDescriptor);
            
            // Load 'this' (local driver)
            super.visitVarInsn(Opcodes.ALOAD, 0);
            
            // Call helper to invoke method on RemoteWebDriver
            // This is complex, so for now we'll just delegate simple cases
            
            // For methods like get(String url), we can handle directly
            if (methodName.equals("get") && methodDescriptor.startsWith("(Ljava/lang/String;)V")) {
                // get(String url) - load url argument (it's at position 1)
                super.visitVarInsn(Opcodes.ALOAD, 1);
                // Call get on RemoteWebDriver (which is on stack)
                super.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    "org/openqa/selenium/remote/RemoteWebDriver",
                    "get",
                    "(Ljava/lang/String;)V",
                    false
                );
                // Return void
                super.visitInsn(Opcodes.RETURN);
            } else if (methodName.equals("quit") && "()V".equals(methodDescriptor)) {
                // quit() - call on RemoteWebDriver
                super.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    "org/openqa/selenium/remote/RemoteWebDriver",
                    "quit",
                    "()V",
                    false
                );
                super.visitInsn(Opcodes.RETURN);
            } else if (methodName.equals("getCurrentUrl") && "()Ljava/lang/String;".equals(methodDescriptor)) {
                // getCurrentUrl() - call on RemoteWebDriver and return
                super.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    "org/openqa/selenium/remote/RemoteWebDriver",
                    "getCurrentUrl",
                    "()Ljava/lang/String;",
                    false
                );
                super.visitInsn(Opcodes.ARETURN);
            } else if (methodName.equals("getTitle") && "()Ljava/lang/String;".equals(methodDescriptor)) {
                // getTitle() - call on RemoteWebDriver and return
                super.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    "org/openqa/selenium/remote/RemoteWebDriver",
                    "getTitle",
                    "()Ljava/lang/String;",
                    false
                );
                super.visitInsn(Opcodes.ARETURN);
            } else {
                // For other methods, use original implementation
                super.visitLabel(useOriginal);
                super.visitInsn(Opcodes.POP); // Remove RemoteWebDriver from stack
                // Original method will be called normally
            }
            
            super.visitLabel(useOriginal);
        }
    }
}

