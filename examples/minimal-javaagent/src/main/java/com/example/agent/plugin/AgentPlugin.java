package com.example.agent.plugin;

import com.example.agent.config.AgentConfig;

import java.lang.instrument.Instrumentation;

public interface AgentPlugin {
    String id();

    void apply(Instrumentation inst, AgentConfig config);
}

