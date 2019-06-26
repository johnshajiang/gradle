/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.project;

import groovy.lang.Closure;
import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.LoggingManager;
import org.gradle.groovy.scripts.DefaultScript;
import org.gradle.internal.logging.StandardOutputCapture;
import org.gradle.plugin.use.PluginDependenciesSpec;
import org.gradle.plugin.use.internal.PluginRequestCollector;
import org.gradle.util.ConfigureUtil;

import java.util.Map;

public abstract class ProjectScript extends DefaultScript {
    public PluginRequestCollector pluginRequestCollector;

    @Override
    public void apply(Closure closure) {
        getScriptTarget().apply(closure);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void apply(Map options) {
        getScriptTarget().apply(options);
    }

    @Override
    public ScriptHandler getBuildscript() {
        return getScriptTarget().getBuildscript();
    }

    @Override
    public void buildscript(Closure configureClosure) {
        getScriptTarget().buildscript(configureClosure);
    }

    public void plugins(Closure configureClosure) {
        if (pluginRequestCollector == null) {
            pluginRequestCollector = new PluginRequestCollector(getScriptSource());
        }
        // TODO:DAZ This should be provided the line number of the plugins block
        PluginDependenciesSpec spec = pluginRequestCollector.createSpec(0);
        ConfigureUtil.configure(configureClosure, spec);
    }

    @Override
    public StandardOutputCapture getStandardOutputCapture() {
        return getScriptTarget().getStandardOutputCapture();
    }

    @Override
    public LoggingManager getLogging() {
        return getScriptTarget().getLogging();
    }

    @Override
    public Logger getLogger() {
        return getScriptTarget().getLogger();
    }

    public String toString() {
        return getScriptTarget().toString();
    }

    @Override
    public ProjectInternal getScriptTarget() {
        return (ProjectInternal) super.getScriptTarget();
    }
}
