package com.megaease.easeagent.config;

import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class PluginConfigTest {

    public static Map<String, String> globalSource() {
        Map<String, String> global = new HashMap<>();
        global.put("enabled", "true");
        global.put("tcp.enabled", "true");
        global.put("host", "127.0.0.1");
        global.put("count", "127");
        global.put("double", "127.1");
        global.put("double_1", "127.2");
        global.put("list", "a,b,c");
        return global;
    }

    Map<String, String> coverSource() {
        Map<String, String> cover = new HashMap<>();
        cover.put("tcp.enabled", "false");
        cover.put("http.enabled", "true");
        cover.put("host", "127.0.0.3");
        cover.put("count", "127");
        cover.put("double", "127.3");
        cover.put("list", "a,b,c");
        return cover;
    }

    PluginConfig build() {
        String domain = "testdomain";
        String id = "testid";
        String namespace = "namespace";
        Map<String, String> global = globalSource();
        Map<String, String> cover = coverSource();
        return new PluginConfig(domain, id, global, namespace, cover);
    }

    @Test
    public void domain() {
        assertEquals(build().domain(), "testdomain");
    }

    @Test
    public void namespace() {
        assertEquals(build().namespace(), "namespace");
    }

    @Test
    public void id() {
        assertEquals(build().id(), "testid");
    }

    @Test
    public void hasProperty() {
        PluginConfig config = build();
        assertTrue(config.hasProperty("enabled"));
        assertTrue(config.hasProperty("tcp.enabled"));
        assertTrue(config.hasProperty("http.enabled"));
        assertFalse(config.hasProperty("http.enabled.cccc"));
    }

    @Test
    public void getString() {
        PluginConfig config = build();
        assertEquals(config.getString("enabled"), "true");
        assertEquals(config.getString("tcp.enabled"), "false");
        assertEquals(config.getString("count"), "127");
        assertEquals(config.getString("host"), "127.0.0.3");
        assertEquals(config.getString("http.enabled"), "true");
        assertEquals(config.getString("http.enabled.sss"), null);
    }

    @Test
    public void getInt() {
        PluginConfig config = build();
        assertEquals((int) config.getInt("count"), 127);
        assertNull(config.getInt("enabled"));
        assertNull(config.getInt("cccccccccccccc"));
    }

    @Test
    public void getBoolean() {
        PluginConfig config = build();
        assertTrue(config.getBoolean("enabled"));
        assertFalse(config.getBoolean("tcp.enabled"));
        assertFalse(config.getBoolean("http.enabled"));
        assertFalse(config.getBoolean("http.enabled.ssss"));
    }

    @Test
    public void getDouble() {
        PluginConfig config = build();
        assertTrue(Math.abs(config.getDouble("double") - 127.3) < 0.0001);
        assertTrue(Math.abs(config.getDouble("double_1") - 127.2) < 0.0001);
        assertNull(config.getDouble("enabled"));
    }

    @Test
    public void getLong() {
        PluginConfig config = build();
        assertEquals((long) config.getLong("count"), 127l);
        assertNull(config.getLong("enabled"));
        assertNull(config.getLong("cccccccccccccc"));
    }

    @Test
    public void getStringList() {
        PluginConfig config = build();
        List<String> list = config.getStringList("list");
        assertEquals(list.size(), 3);
        assertEquals(list.get(0), "a");
        assertEquals(list.get(1), "b");
        assertEquals(list.get(2), "c");
    }

    @Test
    public void addChangeListener() {
        PluginConfig config = build();
        config.addChangeListener((oldConfig, newConfig) -> {
        });
        assertNotNull(config.getConfigChangeListener());
    }

    @Test
    public void getConfigChangeListener() {
        addChangeListener();
    }

    @Test
    public void keySet() {
        PluginConfig config = build();
        Set<String> set = config.keySet();
        Map<String, String> source = globalSource();
        source.putAll(coverSource());
        assertEquals(set.size(), source.size());
        for (String s : set) {
            assertTrue(source.containsKey(s));
        }
    }
}
