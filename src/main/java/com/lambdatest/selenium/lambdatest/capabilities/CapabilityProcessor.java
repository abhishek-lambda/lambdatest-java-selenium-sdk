package com.lambdatest.selenium.lambdatest.capabilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openqa.selenium.remote.DesiredCapabilities;

/**
 * Processes capabilities from configuration and applies them to DesiredCapabilities and lt:options.
 * Separates Selenium 3 and Selenium 4 capability handling for clarity.
 */
public class CapabilityProcessor {
    
    private final Map<String, Object> config;
    private final DesiredCapabilities capabilities;
    private final Map<String, Object> ltOptions;
    
    public CapabilityProcessor(Map<String, Object> config, DesiredCapabilities capabilities, Map<String, Object> ltOptions) {
        this.config = config;
        this.capabilities = capabilities;
        this.ltOptions = ltOptions;
    }
    
    /**
     * Process a capability definition and apply it to the appropriate target(s).
     */
    public void process(CapabilityDefinition definition) {
        Object value = definition.getValue(config);
        if (value == null) {
            return;
        }
        
        String targetKey = definition.getTargetKey();
        
        switch (definition.getTarget()) {
            case DESIRED_CAPABILITIES:
                capabilities.setCapability(targetKey, value);
                break;
                
            case LT_OPTIONS:
                ltOptions.put(targetKey, value);
                break;
                
            case BOTH:
                capabilities.setCapability(targetKey, value);
                ltOptions.put(targetKey, value);
                break;
        }
    }
    
    /**
     * Process multiple capability definitions.
     */
    public void process(List<CapabilityDefinition> definitions) {
        for (CapabilityDefinition definition : definitions) {
            process(definition);
        }
    }
    
    /**
     * Process a capability with custom logic (for complex cases).
     */
    public void processCustom(String key, CustomCapabilityHandler handler) {
        if (config.containsKey(key)) {
            handler.handle(config.get(key), capabilities, ltOptions);
        }
    }
    
    /**
     * Interface for custom capability handling logic.
     */
    @FunctionalInterface
    public interface CustomCapabilityHandler {
        void handle(Object value, DesiredCapabilities capabilities, Map<String, Object> ltOptions);
    }
}

