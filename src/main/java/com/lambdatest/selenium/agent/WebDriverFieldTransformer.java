package com.lambdatest.selenium.agent;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * ASM-based transformer that intercepts WebDriver field access and redirects to ThreadLocal.
 * 
 * This makes parallel="methods" execution thread-safe WITHOUT any code changes.
 * 
 * Transformation:
 * - All GETFIELD on WebDriver fields → call to ThreadLocalDriverStorage.get()
 * - All PUTFIELD on WebDriver fields → call to ThreadLocalDriverStorage.set()
 */
public class WebDriverFieldTransformer implements ClassFileTransformer {
    
    private static final Logger LOGGER = Logger.getLogger(WebDriverFieldTransformer.class.getName());
    
    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                          ProtectionDomain protectionDomain, byte[] classfileBuffer)
            throws IllegalClassFormatException {
        
        // Only transform test classes (heuristic: classes with test annotations or in test packages)
        // Exclude JDK, frameworks, and library classes
        if (className == null || 
            className.startsWith("java/") ||
            className.startsWith("javax/") ||
            className.startsWith("sun/") ||
            className.startsWith("com/sun/") ||
            className.startsWith("jdk/") ||           // JDK internal classes
            className.startsWith("org/xml/") ||       // XML parsers
            className.startsWith("org/jcp/") ||       // JCP (Java Community Process) internal classes
            className.startsWith("apple/") ||         // Apple-specific classes (macOS JDK)
            className.startsWith("org/testng/") ||
            className.startsWith("org/junit/") ||
            className.startsWith("net/bytebuddy/") ||
            className.startsWith("org/openqa/selenium/") ||
            className.startsWith("com/lambdatest/selenium/")) {  // Don't transform SDK itself
            return null; // Don't transform JDK, TestNG, JUnit, ByteBuddy, or Selenium classes
        }
        
        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
            
            WebDriverFieldVisitor visitor = new WebDriverFieldVisitor(Opcodes.ASM9, cw, className, loader);
            cr.accept(visitor, ClassReader.EXPAND_FRAMES);
            
            if (visitor.wasTransformed()) {
                LOGGER.info("Transformed WebDriver fields in class: " + className.replace('/', '.'));
                return cw.toByteArray();
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to transform class " + className + ": " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * ASM ClassVisitor that finds and transforms WebDriver field access.
     */
    private static class WebDriverFieldVisitor extends ClassVisitor {
        
        private final String className;
        private final ClassLoader loader;
        private boolean transformed = false;
        private final Map<String, String> webDriverFields = new ConcurrentHashMap<>();
        private String superClassName;
        
        public WebDriverFieldVisitor(int api, ClassVisitor cv, String className, ClassLoader loader) {
            super(api, cv);
            this.className = className;
            this.loader = loader;
        }
        
        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.superClassName = superName;
            super.visit(version, access, name, signature, superName, interfaces);
        }
        
        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            // Check if field is WebDriver type
            if (descriptor.equals("Lorg/openqa/selenium/WebDriver;") ||
                descriptor.equals("Lorg/openqa/selenium/remote/RemoteWebDriver;")) {
                webDriverFields.put(name, descriptor);
                LOGGER.fine("Found WebDriver field: " + className + "." + name);
            }
            return super.visitField(access, name, descriptor, signature, value);
        }
        
        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new WebDriverFieldMethodVisitor(api, mv, className, this);
        }
        
        public boolean wasTransformed() {
            return transformed || !webDriverFields.isEmpty();
        }
        
        public void markTransformed() {
            transformed = true;
        }
        
        public Map<String, String> getWebDriverFields() {
            return webDriverFields;
        }
        
        public String getSuperClassName() {
            return superClassName;
        }
    }
    
    /**
     * ASM MethodVisitor that intercepts GETFIELD and PUTFIELD instructions on WebDriver fields.
     */
    private static class WebDriverFieldMethodVisitor extends MethodVisitor {
        
        private final String className;
        private final WebDriverFieldVisitor parentVisitor;
        
        public WebDriverFieldMethodVisitor(int api, MethodVisitor mv, String className,
                                          WebDriverFieldVisitor parentVisitor) {
            super(api, mv);
            this.className = className;
            this.parentVisitor = parentVisitor;
        }
        
        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            // Check if this is a WebDriver field access
            // Note: owner might be a parent class (inheritance), so we check descriptor only
            boolean isWebDriverField = descriptor.equals("Lorg/openqa/selenium/WebDriver;") || 
                                       descriptor.equals("Lorg/openqa/selenium/remote/RemoteWebDriver;");
            
            if (isWebDriverField) {
                String key = resolveCanonicalFieldKey(owner, name);
                if (opcode == Opcodes.PUTFIELD) {
                    // Intercept field write: driver = value
                    // Stack before: [obj, value]
                    // We need to call: ThreadLocalDriverStorage.setDriver(className, fieldName, value)
                    parentVisitor.markTransformed();
                    
                    // Swap value and object so we can remove object while keeping value
                    mv.visitInsn(Opcodes.SWAP);         // Stack: [value, obj]
                    mv.visitInsn(Opcodes.POP);          // Remove obj -> [value]
                    
                    // Prepare arguments for setDriver(String, WebDriver)
                    mv.visitLdcInsn(key); // Push field key -> [value, key]
                    mv.visitInsn(Opcodes.SWAP);         // -> [key, value]
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                        "com/lambdatest/selenium/agent/ThreadLocalDriverStorage",
                        "setDriver",
                        "(Ljava/lang/String;Lorg/openqa/selenium/WebDriver;)V",
                        false);
                    
                    return; // Don't call original PUTFIELD
                    
                } else if (opcode == Opcodes.GETFIELD) {
                    // Intercept field read: value = driver
                    // Stack before: [obj]
                    // We need to call: ThreadLocalDriverStorage.getDriver(className, fieldName)
                    parentVisitor.markTransformed();
                    
                    mv.visitInsn(Opcodes.POP); // Remove obj from stack
                    
                    // Call ThreadLocalDriverStorage.getDriver
                    mv.visitLdcInsn(key); // Push field key
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                        "com/lambdatest/selenium/agent/ThreadLocalDriverStorage",
                        "getDriver",
                        "(Ljava/lang/String;)Lorg/openqa/selenium/WebDriver;",
                        false);
                    
                    return; // Don't call original GETFIELD
                }
            }
            
            // Not a WebDriver field, use original instruction
            super.visitFieldInsn(opcode, owner, name, descriptor);
        }

        private String resolveCanonicalFieldKey(String ownerInternalName, String fieldName) {
            // Use ASM to walk the class hierarchy without loading classes (avoids LinkageError)
            String declaringClass = findDeclaringClassViaASM(ownerInternalName, fieldName);
            if (declaringClass != null) {
                return declaringClass.replace('/', '.') + "." + fieldName;
            }
            return ownerInternalName.replace('/', '.') + "." + fieldName;
        }

        private String findDeclaringClassViaASM(String startClass, String fieldName) {
            String current = startClass;
            ClassLoader cl = parentVisitor.loader;
            
            // Walk up the hierarchy using ASM to avoid class loading
            while (current != null && !current.equals("java/lang/Object")) {
                try {
                    // Read the class bytecode
                    String resourceName = current + ".class";
                    java.io.InputStream is = cl.getResourceAsStream(resourceName);
                    if (is == null) {
                        break;
                    }
                    
                    ClassReader cr = new ClassReader(is);
                    FieldFinder finder = new FieldFinder(fieldName);
                    cr.accept(finder, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    
                    if (finder.fieldFound) {
                        return current;
                    }
                    
                    current = finder.superName;
                } catch (Exception e) {
                    break;
                }
            }
            return null;
        }
        
        private static class FieldFinder extends ClassVisitor {
            private final String targetField;
            boolean fieldFound = false;
            String superName;
            
            FieldFinder(String targetField) {
                super(Opcodes.ASM9);
                this.targetField = targetField;
            }
            
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                this.superName = superName;
            }
            
            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                if (name.equals(targetField)) {
                    fieldFound = true;
                }
                return null;
            }
        }
    }
}

