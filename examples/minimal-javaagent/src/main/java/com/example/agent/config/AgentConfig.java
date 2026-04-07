package com.example.agent.config;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AgentConfig {
    private final Map<String, String> values;

    private AgentConfig(Map<String, String> values) {
        this.values = values;
    }

    public static AgentConfig parse(String agentArgs) {
        Map<String, String> parsed = new HashMap<String, String>();
        if (agentArgs == null || agentArgs.trim().isEmpty()) {
            return new AgentConfig(parsed);
        }
        String[] parts = agentArgs.split(",");
        for (String part : parts) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2) {
                parsed.put(kv[0].trim(), kv[1].trim());
            }
        }
        return new AgentConfig(parsed);
    }

    public boolean enabled(String pluginId) {
        String key = "plugin." + pluginId + ".enabled";
        String v = values.get(key);
        return v == null || Boolean.parseBoolean(v);
    }

    public String targetClass() {
        String v = values.get("targetClass");
        return v == null ? "com.example.app.GreetingService" : v;
    }

    public String targetMethod() {
        String v = values.get("targetMethod");
        return v == null ? "sayHello" : v;
    }

    public Set<String> targetMethods() {
        String v = values.get("targetMethods");
        if (v == null || v.trim().isEmpty()) {
            Set<String> defaults = new LinkedHashSet<String>();
            defaults.add(targetMethod());
            return defaults;
        }
        Set<String> methods = new LinkedHashSet<String>();
        String[] parts = v.split("\\|");
        for (String part : parts) {
            String method = part.trim();
            if (!method.isEmpty()) {
                methods.add(method);
            }
        }
        if (methods.isEmpty()) {
            methods.add(targetMethod());
        }
        return methods;
    }

    public List<String> defaultInterceptors() {
        String v = values.get("defaultInterceptors");
        if (v != null && !v.trim().isEmpty()) {
            return parseInterceptorList(v);
        }

        List<String> defaults = new ArrayList<String>();
        if (interceptorEnabled("logging")) {
            defaults.add("logging");
        }
        if (interceptorEnabled("timing")) {
            defaults.add("timing");
        }
        if (interceptorEnabled("metric")) {
            defaults.add("metric");
        }
        if (defaults.isEmpty()) {
            defaults.add("logging");
        }
        return defaults;
    }

    /**
     * Format: methodA=logging|timing;methodB=logging
     */
    public Map<String, List<String>> methodInterceptors() {
        String v = values.get("methodInterceptors");
        if (v == null || v.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, List<String>> result = new HashMap<String, List<String>>();
        String[] methods = v.split(";");
        for (String methodItem : methods) {
            String[] kv = methodItem.split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            String methodName = kv[0].trim();
            if (methodName.isEmpty()) {
                continue;
            }
            List<String> ids = parseInterceptorList(kv[1]);
            if (!ids.isEmpty()) {
                result.put(methodName, ids);
            }
        }
        return result;
    }

    private List<String> parseInterceptorList(String raw) {
        List<String> ids = new ArrayList<String>();
        String[] parts = raw.split("\\|");
        for (String part : parts) {
            String id = part.trim();
            if (!id.isEmpty()) {
                ids.add(id);
            }
        }
        return ids;
    }

    public boolean interceptorEnabled(String interceptorId) {
        String key = "interceptor." + interceptorId + ".enabled";
        String v = values.get(key);
        return v == null || Boolean.parseBoolean(v);
    }

    /**
     * How often the console metric reporter prints a snapshot (seconds).
     * Configured via agent arg {@code metric.reportIntervalSec=N}.  Default: 10.
     */
    public long metricReportIntervalSec() {
        String v = values.get("metric.reportIntervalSec");
        if (v == null) {
            return 10L;
        }
        try {
            long parsed = Long.parseLong(v);
            return parsed > 0 ? parsed : 10L;
        } catch (NumberFormatException ignored) {
            return 10L;
        }
    }

    public long timingWarnMs() {
        String v = values.get("interceptor.timing.warnMs");
        if (v == null) {
            return 0L;
        }
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}

