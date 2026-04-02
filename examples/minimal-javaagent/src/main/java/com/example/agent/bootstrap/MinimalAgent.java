package com.example.agent.bootstrap;

import com.example.agent.config.AgentConfig;
import com.example.agent.plugin.AgentPlugin;
import com.example.agent.plugin.LoggingPlugin;

import java.lang.instrument.Instrumentation;
import java.util.Arrays;
import java.util.List;

public final class MinimalAgent {
    private MinimalAgent() {
    }

    public static void premain(String agentArgs, Instrumentation inst) {
        AgentConfig config = AgentConfig.parse(agentArgs);
        List<AgentPlugin> plugins = Arrays.asList(new LoggingPlugin());

        System.out.println("[agent] start, args=" + (agentArgs == null ? "" : agentArgs));
        for (AgentPlugin plugin : plugins) {
            if (!config.enabled(plugin.id())) {
                System.out.println("[agent] skip plugin: " + plugin.id());
                continue;
            }
            plugin.apply(inst, config);
            System.out.println("[agent] installed plugin: " + plugin.id());
        }
    }
}

