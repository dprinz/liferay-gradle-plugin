package com.github.jelmerk;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.plugins.WarPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.external.javadoc.StandardJavadocDocletOptions;

import java.io.File;
import java.util.concurrent.Callable;

import static java.lang.String.format;

/**
 * @author Jelmer Kuperus
 */
public class ServiceBuilderPlugin implements Plugin<Project> {

    public static final String BUILD_SERVICE = "generateService";

    public static final String JAVADOC_SERVICE = "javadocService";

    public static final String JAR_SERVICE = "jarService";

    public static final String SERVICE_BUILDER_CONFIGURATION_NAME = "serviceBuilder";

    public static final String SERVICE_SOURCE_SET_NAME = "service";

    public static final String SERVICE_CONFIGURATION_NAME = "service";

    @Override
    public void apply(Project project) {
        project.getPlugins().apply(LiferayBasePlugin.class);

        configureConfigurations(project);
        createServiceBuilderExtension(project);
        configureSourceSets(project);
        configureArchives(project);

        configureTaskRule(project);

        configureJavadocRule(project);

        configureServiceJavaDoc(project);
        configureBuildServiceTask(project);
    }

    private void configureConfigurations(Project project) {

        Configuration serviceBuilderConfiguration = project.getConfigurations().add(SERVICE_BUILDER_CONFIGURATION_NAME);
        serviceBuilderConfiguration.setVisible(false);
        serviceBuilderConfiguration.setDescription("The servicebuilder configuration");

        Configuration serviceConfiguration = project.getConfigurations().add(SERVICE_CONFIGURATION_NAME);
        serviceConfiguration.setDescription("The service configuration");
    }

    private void createServiceBuilderExtension(Project project) {
        project.getExtensions().create("servicebuilder", ServiceBuilderPluginExtension.class, project);
    }

    private void configureSourceSets(Project project) {
        JavaPluginConvention pluginConvention = project.getConvention().getPlugin(JavaPluginConvention.class);
        pluginConvention.getSourceSets().add(SERVICE_SOURCE_SET_NAME);
    }

    private void configureArchives(Project project) {

        JavaPluginConvention pluginConvention = project.getConvention().getPlugin(JavaPluginConvention.class);

        Jar jar = project.getTasks().add(JAR_SERVICE, Jar.class);

        jar.setDescription("Assembles a jar archive containing the servicebuilder classes.");
        jar.setGroup(BasePlugin.BUILD_GROUP);
        jar.from(pluginConvention.getSourceSets().getByName(SERVICE_SOURCE_SET_NAME).getOutput());
        jar.setClassifier("service");

        project.getArtifacts().add(SERVICE_CONFIGURATION_NAME, project.getTasks().getByName(JAR_SERVICE));
    }

    private void configureBuildServiceTask(Project project) {
        BuildService buildService = project.getTasks().add(BUILD_SERVICE, BuildService.class);
        buildService.setDescription("Builds a liferay service");
        buildService.setGroup(LiferayBasePlugin.LIFERAY_GROUP);
    }

    private void configureJavadocRule(Project project) {
        project.getTasks().withType(Javadoc.class, new Action<Javadoc>() {
            @Override
            public void execute(Javadoc task) {
                if (!(task.getOptions() instanceof StandardJavadocDocletOptions)) {
                    return;
                }

                StandardJavadocDocletOptions castOptions = (StandardJavadocDocletOptions) task.getOptions();
                castOptions.getTags().add("generated:a:\"ServiceBuilder generated this class. " +
                    "Modifications in this class will be overwritten the next time it is generated");
            }
        });
    }


    private void configureServiceJavaDoc(Project project) {
        JavaPluginConvention javaConvention = project.getConvention().getPlugin(JavaPluginConvention.class);

        SourceSet serviceSourceSet = javaConvention.getSourceSets().getByName(SERVICE_SOURCE_SET_NAME);
        Javadoc serviceJavadoc = project.getTasks().add(JAVADOC_SERVICE, Javadoc.class);
        serviceJavadoc.setDescription("Generates Javadoc API documentation for the servicebuilder service api.");
        serviceJavadoc.setGroup(JavaBasePlugin.DOCUMENTATION_GROUP);
        serviceJavadoc.setClasspath(serviceSourceSet.getOutput().plus(serviceSourceSet.getCompileClasspath()));

        StandardJavadocDocletOptions options = new StandardJavadocDocletOptions();
        options.getTags().add("generated:a:\"ServiceBuilder generated this class. " +
            "Modifications in this class will be overwritten the next time it is generated");
        serviceJavadoc.setOptions(options);

        serviceJavadoc.setDestinationDir(new File(javaConvention.getDocsDir(), "serviceJavadoc"));
        serviceJavadoc.setSource(serviceSourceSet.getAllJava());

        Javadoc mainJavadoc =  (Javadoc) project.getTasks().getByName(JavaPlugin.JAVADOC_TASK_NAME);
        mainJavadoc.dependsOn(serviceJavadoc);
    }


    private void configureTaskRule(final Project project) {
        project.getTasks().withType(BuildService.class, new Action<BuildService>() {
            @Override
            public void execute(BuildService buildService) {
                configureBuildServiceTaskDefaults(project, buildService);
            }
        });
    }

    protected void configureBuildServiceTaskDefaults(final Project project, final BuildService buildService) {

        final LiferayPluginExtension liferayExtension = project.getExtensions().getByType(LiferayPluginExtension.class);

        final ServiceBuilderPluginExtension serviceBuilderExtension = project.getExtensions()
                .getByType(ServiceBuilderPluginExtension.class);

        final WarPluginConvention warConvention = project.getConvention().getPlugin(WarPluginConvention.class);

        final JavaPluginConvention javaConvention = project.getConvention().getPlugin(JavaPluginConvention.class);

        SourceSetContainer sourceSets = javaConvention.getSourceSets();
        SourceSet mainSourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        SourceSet servicebuilderSourceSet = sourceSets.getByName(SERVICE_SOURCE_SET_NAME);

        final SourceDirectorySet allMainJava = mainSourceSet.getAllJava();
        final SourceDirectorySet allServiceBuilderJava = servicebuilderSourceSet.getAllJava();
        final SourceDirectorySet allResources = mainSourceSet.getResources();

        buildService.getConventionMapping().map("pluginName", new Callable<String>() {
            @Override
            public String call() throws Exception {
                return project.getName();
            }
        });


        buildService.getConventionMapping().map("serviceInputFile", new Callable<File>() {
            @Override
            public File call() throws Exception {
                if (serviceBuilderExtension.getServiceInputFile() != null) {
                    return serviceBuilderExtension.getServiceInputFile();
                }
                return new File(warConvention.getWebAppDir(), "WEB-INF/service.xml");
            }
        });

        buildService.getConventionMapping().map("jalopyInputFile", new Callable<File>() {
            @Override
            public File call() throws Exception {
                if (serviceBuilderExtension.getJalopyInputFile() != null) {
                    return serviceBuilderExtension.getJalopyInputFile();
                }
                return null;
            }
        });

        buildService.getConventionMapping().map("implSrcDir", new Callable<File>() {
            @Override
            public File call() throws Exception {
                if (serviceBuilderExtension.getImplSrcDir() != null) {
                    return serviceBuilderExtension.getImplSrcDir();
                }
                return allMainJava.getSrcDirs().iterator().next();
            }
        });

        buildService.getConventionMapping().map("apiSrcDir", new Callable<File>() {
            @Override
            public File call() throws Exception {
                if (serviceBuilderExtension.getApiSrcDir() != null) {
                    return serviceBuilderExtension.getApiSrcDir();
                }
                return allServiceBuilderJava.getSrcDirs().iterator().next();
            }
        });

        buildService.getConventionMapping().map("resourceDir", new Callable<File>() {
            @Override
            public File call() throws Exception {
                if (serviceBuilderExtension.getResourceDir() != null) {
                    return serviceBuilderExtension.getResourceDir();
                }
                return allResources.getSrcDirs().iterator().next();
            }
        });

        buildService.getConventionMapping().map("webappSrcDir", new Callable<File>() {
            @Override
            public File call() throws Exception {
                return warConvention.getWebAppDir();
            }
        });

        buildService.getConventionMapping().map("classpath", new Callable<FileCollection>() {
            @Override
            public FileCollection call() throws Exception {

                Configuration servicebuilderConfiguration = project.getConfigurations()
                        .getByName(SERVICE_BUILDER_CONFIGURATION_NAME);

                if (servicebuilderConfiguration.getDependencies().isEmpty()) {

                    // the sdk dependencies : we will need to download those

                    DependencyHandler projectDependencies = project.getDependencies();

                    projectDependencies.add(SERVICE_BUILDER_CONFIGURATION_NAME, "com.thoughtworks.qdox:qdox:1.12");
                    projectDependencies.add(SERVICE_BUILDER_CONFIGURATION_NAME, "jalopy:jalopy:1.5rc3");
                    projectDependencies.add(SERVICE_BUILDER_CONFIGURATION_NAME, "javax.servlet:servlet-api:2.5");
                    projectDependencies.add(SERVICE_BUILDER_CONFIGURATION_NAME, "javax.servlet.jsp:jsp-api:2.1");
                    projectDependencies.add(SERVICE_BUILDER_CONFIGURATION_NAME, "javax.activation:activation:1.1");

                    //  the portal classpath dependencies : we have those locally

                    projectDependencies.add(SERVICE_BUILDER_CONFIGURATION_NAME, liferayExtension.getPortalClasspath());

                    // the common classpath dependencies : we can get from the portal

                    File appServerGlobalLibDirName = liferayExtension.getAppServerGlobalLibDir();

                    FileCollection appserverClasspath = project.files(
                            new File(appServerGlobalLibDirName, "commons-digester.jar"),
                            new File(appServerGlobalLibDirName, "commons-lang.jar"),
                            new File(appServerGlobalLibDirName, "easyconf.jar")
                    );

                    projectDependencies.add(SERVICE_BUILDER_CONFIGURATION_NAME, appserverClasspath);

                }

                return servicebuilderConfiguration;
            }
        });


    }
}
