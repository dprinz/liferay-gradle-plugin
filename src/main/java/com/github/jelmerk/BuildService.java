/*
 * Copyright 2002-2010 the original author or authors.
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

package com.github.jelmerk;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Copy;
import org.apache.tools.ant.taskdefs.Echo;
import org.apache.tools.ant.taskdefs.Java;
import org.apache.tools.ant.taskdefs.Mkdir;
import org.apache.tools.ant.types.Path;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskExecutionException;

import java.io.File;

/**
 * Generates a liferay service (java source files) from a xml service definition file.
 *
 * @author Jelmer Kuperus
 */
public class BuildService extends DefaultTask {

    private FileCollection classpath;

    private String pluginName;

    private File implSrcDir;
    private File apiSrcDir;
    private File resourceDir;
    private File webappSrcDir;

    private File jalopyInputFile;
    private File serviceInputFile;

    /**
     * Performs the build service task.
     */
    @TaskAction
    public void buildService() {
        File workingDir = prepareWorkingDir();
        createOutputDirectories();
        String processOutput = buildService(workingDir);
        echoOutput(processOutput);

        if (didNotExecuteSuccessfully(processOutput)) {
            throw new TaskExecutionException(this, null);
        }
    }

    private boolean didNotExecuteSuccessfully(String processOutput) {
        return processOutput != null && processOutput.contains("Error");
    }

    private void createOutputDirectories() {
        Mkdir mkServicebuilderMainSourceSetDir = new Mkdir();
        mkServicebuilderMainSourceSetDir.setDir(getImplSrcDir());
        mkServicebuilderMainSourceSetDir.execute();

        Mkdir mkSqlDir = new Mkdir();
        mkSqlDir.setDir(new File(getWebappSrcDir(), "sql"));
        mkSqlDir.execute();
    }

    private File prepareWorkingDir() {

        // the Jalopy file to use is not a parameter you can pass to service builder it just looks at a number
        // of predefined locations on the filesystem. So we set up a working dir where we mimic the layout
        // servicebuilder expects as a workaround

        File workingDir = getProject().mkdir(new File(getProject().getBuildDir(), "servicebuilder"));
        File miscDir = getProject().mkdir(new File(workingDir, "misc"));

        File jalopyFile = new File(miscDir, "jalopy.xml");

        if (getJalopyInputFile() != null) {
            Copy copy = new Copy();
            copy.setProject(getAnt().getProject());
            copy.setFile(getJalopyInputFile());
            copy.setTofile(jalopyFile);
            copy.setOverwrite(true);
            copy.execute();
        }

        return workingDir;
    }

    private String buildService(File workingDir) {
        Java javaTask = new Java();
        javaTask.setTaskName("service builder");
        javaTask.setClassname("com.liferay.portal.tools.servicebuilder.ServiceBuilder");

        javaTask.setFork(true); // must fork or the working dir we set below is not picked up
        javaTask.setDir(workingDir);
        javaTask.setOutputproperty("service.test.output");

        Project antProject = getAnt().getAntProject();

        Path antClassPath = new Path(antProject);

        for (File dep : getClasspath()) {
            antClassPath.createPathElement()
                    .setLocation(dep);
        }

        javaTask.setProject(antProject);
        javaTask.setClasspath(antClassPath);

        javaTask.createArg()
                .setLine("-Dexternal-properties=com/liferay/portal/tools/dependencies/portal-tools.properties");

        javaTask.createArg()
                .setLine("-Dorg.apache.commons.logging.Log=org.apache.commons.logging.impl.Log4JLogger");

        javaTask.createArg()
                .setLine("service.input.file=" + getServiceInputFile().getPath());

        javaTask.createArg()
                .setLine("service.hbm.file="
                        + new File(getResourceDir(), "META-INF/portlet-hbm.xml").getPath());

        javaTask.createArg()
                .setLine("service.orm.file="
                        + new File(getResourceDir(), "META-INF/portlet-orm.xml").getPath());

        javaTask.createArg()
                .setLine("service.model.hints.file="
                        + new File(getResourceDir(), "META-INF/portlet-model-hints.xml").getPath());

        javaTask.createArg()
                .setLine("service.spring.file="
                        + new File(getResourceDir(), "META-INF/portlet-spring.xml").getPath());

        javaTask.createArg()
                .setLine("service.spring.base.file="
                        + new File(getResourceDir(), "META-INF/base-spring.xml").getPath());

        javaTask.createArg()
                .setLine("service.spring.cluster.file="
                        + new File(getResourceDir(), "META-INF/cluster-spring.xml").getPath());

        javaTask.createArg()
                .setLine("service.spring.dynamic.data.source.file="
                        + new File(getResourceDir(), "META-INF/dynamic-data-source-spring.xml").getPath());

        javaTask.createArg()
                .setLine("service.spring.hibernate.file="
                        + new File(getResourceDir(), "META-INF/hibernate-spring.xml").getPath());

        javaTask.createArg()
                .setLine("service.spring.infrastructure.file="
                        + new File(getResourceDir(), "META-INF/infrastructure-spring.xml").getPath());

        javaTask.createArg()
                .setLine("service.spring.shard.data.source.file="
                        + new File(getResourceDir(), "META-INF/shard-data-source-spring.xml").getPath());

        javaTask.createArg()
                .setLine("service.api.dir=" + getApiSrcDir().getPath());

        javaTask.createArg()
                .setLine("service.impl.dir=" + getImplSrcDir().getPath());

        javaTask.createArg()
                .setLine("service.json.file=" + new File(getWebappSrcDir(), "js/service.js").getPath());

        javaTask.createArg()
                .setLine("service.sql.dir=" + new File(getWebappSrcDir(), "WEB-INF/sql").getPath());

        javaTask.createArg()
                .setLine("service.sql.file=tables.sql");

        javaTask.createArg()
                .setLine("service.sql.indexes.file=indexes.sql");

        javaTask.createArg()
                .setLine("service.sql.indexes.properties.file=indexes.properties");

        javaTask.createArg()
                .setLine("service.sql.sequences.file=sequences.sql");

        javaTask.createArg()
                .setLine("service.auto.namespace.tables=true");

        javaTask.createArg()
                .setLine("service.bean.locator.util=com.liferay.util.bean.PortletBeanLocatorUtil");

        javaTask.createArg()
                .setLine("service.props.util=com.liferay.util.service.ServiceProps");

        javaTask.createArg()
                .setLine("service.plugin.name=" + getPluginName());

        //javaTask.createJvmarg().setLine("-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5006");

        javaTask.execute();

        return antProject.getProperty("service.test.output");
    }

    private void echoOutput(String processOutput) {
        Echo echo = new Echo();
        //echo.setProject(getAnt().getAntProject());
        echo.setMessage(processOutput);
        echo.execute();
    }

    @Input
    public String getPluginName() {
        return pluginName;
    }

    public void setPluginName(String pluginName) {
        this.pluginName = pluginName;
    }

    @InputFile
    public File getServiceInputFile() {
        return serviceInputFile;
    }

    public void setServiceInputFile(File serviceInputFile) {
        this.serviceInputFile = serviceInputFile;
    }

    @Optional
    @InputFile
    public File getJalopyInputFile() {
        return jalopyInputFile;
    }

    public void setJalopyInputFile(File jalopyInputFile) {
        this.jalopyInputFile = jalopyInputFile;
    }

    @OutputDirectory
    public File getImplSrcDir() {
        return implSrcDir;
    }

    public void setImplSrcDir(File implSrcDir) {
        this.implSrcDir = implSrcDir;
    }

    @OutputDirectory
    public File getApiSrcDir() {
        return apiSrcDir;
    }

    public void setApiSrcDir(File apiSrcDir) {
        this.apiSrcDir = apiSrcDir;
    }

    @OutputDirectory
    public File getResourceDir() {
        return resourceDir;
    }

    public void setResourceDir(File resourceDir) {
        this.resourceDir = resourceDir;
    }

    @OutputDirectory
    public File getWebappSrcDir() {
        return webappSrcDir;
    }

    public void setWebappDir(File webappSrcDir) {
        this.webappSrcDir = webappSrcDir;
    }

    @InputFiles
    public FileCollection getClasspath() {
        return classpath;
    }

    public void setClasspath(FileCollection classpath) {
        this.classpath = classpath;
    }
}
