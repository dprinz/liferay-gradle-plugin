package nl.orange11.liferay;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact;
import org.gradle.api.internal.file.UnionFileCollection;
import org.gradle.api.internal.plugins.DefaultArtifactPublicationSet;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.plugins.*;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.Upload;
import org.gradle.api.tasks.bundling.Jar;

import java.io.File;
import java.util.concurrent.Callable;

import static java.lang.String.format;

/**
 * @author Jelmer Kuperus
 */
public class ServiceBuilderPlugin implements Plugin<Project> {

    public static final String BUILD_SERVICE = "build-service";
    public static final String INSTALL_SERVICE = "install-service";
    public static final String JAR_SERVICE = "jar-service";

    public static final String SERVICE_BUILDER_SOURCE_SET_NAME = "service";

    @Override
    public void apply(Project project) {
        project.getPlugins().apply(LiferayBasePlugin.class);

        createConfiguration(project);
        createServiceBuilderExtension(project);
        configureSourceSets(project);
        configureArchives(project);


        configureTaskRule(project);

        configureBuildServiceTask(project);
    }

    private void createConfiguration(Project project) {

        Configuration configuration = project.getConfigurations().add("servicebuilder");

        configuration.setVisible(false);
        configuration.setTransitive(true);
        configuration.setDescription("The servicebuilder configuration");
    }

    private void createServiceBuilderExtension(Project project) {
        project.getExtensions().create("servicebuilder", ServiceBuilderPluginExtension.class, project);
    }

    private void configureSourceSets(Project project) {

        JavaPluginConvention pluginConvention = project.getConvention().getPlugin(JavaPluginConvention.class);

        SourceSetContainer sourceSets = pluginConvention.getSourceSets();

        SourceSet main = pluginConvention.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);

        SourceSet test = pluginConvention.getSourceSets().getByName(SourceSet.TEST_SOURCE_SET_NAME);

        SourceSet serviceBuilder = sourceSets.add(SERVICE_BUILDER_SOURCE_SET_NAME);

        // TODO: do i want to give it the same classpath as the impl.. probably not since this will be added as
        //       transitive dependencies to the service jar that should not depend on too much crap

        serviceBuilder.setCompileClasspath(project.getConfigurations().getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME));

        // TODO: do we really need it to both ? it seems to be what happens in the java plugin for tests

        UnionFileCollection compileClasspath = new UnionFileCollection();
        compileClasspath.add(main.getCompileClasspath());
        compileClasspath.add(project.files(serviceBuilder.getOutput()));

        main.setCompileClasspath(compileClasspath);




        main.getCompileClasspath().add(project.files(serviceBuilder.getOutput()));
        main.getRuntimeClasspath().add(project.files(serviceBuilder.getOutput()));

        test.getCompileClasspath().add(project.files(serviceBuilder.getOutput()));
        test.getRuntimeClasspath().add(project.files(serviceBuilder.getOutput()));
    }

    private void configureArchives(Project project) {

        JavaPluginConvention pluginConvention = (JavaPluginConvention) project
                .getConvention().getPlugins().get("java");

        Jar jar = project.getTasks().add(JAR_SERVICE, Jar.class);

        jar.setDescription("Assembles a jar archive containing the servicebuilder classes.");
        jar.setGroup(BasePlugin.BUILD_GROUP);
        jar.from(pluginConvention.getSourceSets().getByName(SERVICE_BUILDER_SOURCE_SET_NAME).getOutput());
        jar.setArchiveName(format("%s-service.jar", project.getName()));

        project.getExtensions().getByType(DefaultArtifactPublicationSet.class)
                .addCandidate(new ArchivePublishArtifact(jar));

        project.getConfigurations().getByName(JavaPlugin.RUNTIME_CONFIGURATION_NAME).getArtifacts()
                .add(new ArchivePublishArtifact(jar));

        project.getArtifacts().add("servicebuilder", project.getTasks().getByName(JAR_SERVICE));
    }



    private void configureBuildServiceTask(final Project project) {

        BuildService buildService = project.getTasks().add(BUILD_SERVICE, BuildService.class);
        buildService.setDescription("Builds a liferay service");
        buildService.setGroup(LiferayBasePlugin.LIFERAY_GROUP);

    }


//    private void configureInstallServiceTask(final Project project) {
//
//        Upload installServiceTask = project.getTasks().add(INSTALL_SERVICE, Upload.class);
//
//        Configuration configuration = project.getConfigurations().getByName(Dependency.ARCHIVES_CONFIGURATION);
//        installServiceTask.setConfiguration(configuration);
//
//        MavenRepositoryHandlerConvention repositories = new DslObject(installServiceTask.getRepositories())
//                .getConvention().getPlugin(MavenRepositoryHandlerConvention.class);
//
//        repositories.mavenInstaller();
//        installServiceTask.setDescription("Does a maven install of the archives artifacts into the local .m2 cache.");
//
//
//        Task installTask = project.getTasks().getByName(MavenPlugin.INSTALL_TASK_NAME);
//        install.dependsOn(installTask);
//
//    }

    private void configureTaskRule(final Project project) {
        project.getTasks().withType(BuildService.class, new Action<BuildService>() {
            @Override
            public void execute(BuildService buildService) {
                configureTask(project, buildService);
            }
        });

//        TaskCollection<BuildService> tasks = project.getTasks().withType(BuildService.class);
//        project.tasks.withType(taskType) { T task ->
//            def prunedName = (task.name - taskBaseName ?: task.name)
//            prunedName = prunedName[0].toLowerCase() + prunedName.substring(1)
//            configureTaskDefaults(task, prunedName)
//        }
    }

    protected void configureTask(final Project project, final BuildService buildService) {


        final LiferayPluginExtension liferayExtension = project.getExtensions().getByType(LiferayPluginExtension.class);

        final WarPluginConvention warConvention = project.getConvention().getPlugin(WarPluginConvention.class);

        final JavaPluginConvention javaConvention = project.getConvention().getPlugin(JavaPluginConvention.class);

        SourceSetContainer sourceSets = javaConvention.getSourceSets();
        final SourceSet mainSourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        SourceSet servicebuilderSourceSet = sourceSets.getByName(ServiceBuilderPlugin.SERVICE_BUILDER_SOURCE_SET_NAME);

        final SourceDirectorySet allMainJava = mainSourceSet.getAllJava();
        final SourceDirectorySet allServiceBuilderJava = servicebuilderSourceSet.getAllJava();
        final SourceDirectorySet allResources = mainSourceSet.getResources();

        buildService.getConventionMapping().map("portalClasspath", new Callable<FileCollection>() {
            @Override
            public FileCollection call() throws Exception {
                return liferayExtension.getPortalClasspath();
            }
        });

        buildService.getConventionMapping().map("serviceInputFile", new Callable<File>() {
            @Override
            public File call() throws Exception {
                return new File(warConvention.getWebAppDir(), "WEB-INF/service.xml");
            }
        });

        buildService.getConventionMapping().map("implSrcDir", new Callable<File>() {
            @Override
            public File call() throws Exception {
                return allMainJava.getSrcDirs().iterator().next();
            }
        });

        buildService.getConventionMapping().map("apiSrcDir", new Callable<File>() {
            @Override
            public File call() throws Exception {
                return allServiceBuilderJava.getSrcDirs().iterator().next();
            }
        });

        buildService.getConventionMapping().map("resourceDir", new Callable<File>() {
            @Override
            public File call() throws Exception {
                return allResources.getSrcDirs().iterator().next();
            }
        });

        buildService.getConventionMapping().map("webappSrcDir", new Callable<File>() {
            @Override
            public File call() throws Exception {
                return warConvention.getWebAppDir();
            }
        });

        buildService.getConventionMapping().map("appServerGlobalLibDirName", new Callable<File>() {
            @Override
            public File call() throws Exception {
                return liferayExtension.getAppServerGlobalLibDir();
            }
        });

        buildService.getConventionMapping().map("projectClasspath", new Callable<FileCollection>() {
            @Override
            public FileCollection call() throws Exception {
                return mainSourceSet.getCompileClasspath();
            }
        });

        buildService.getConventionMapping().map("sdkClasspath", new Callable<FileCollection>() {
            @Override
            public FileCollection call() throws Exception {

                Configuration servicebuilderConfiguration = project.getConfigurations().getByName("servicebuilder");

                if (servicebuilderConfiguration.getDependencies().isEmpty()) {
                    project.getDependencies().add("servicebuilder", "com.thoughtworks.qdox:qdox:1.12");
                    project.getDependencies().add("servicebuilder", "jalopy:jalopy:1.5rc3");
                    project.getDependencies().add("servicebuilder", "javax.servlet:servlet-api:2.5");
                    project.getDependencies().add("servicebuilder", "javax.servlet.jsp:jsp-api:2.1");
                    project.getDependencies().add("servicebuilder", "javax.activation:activation:1.1");
                }

                return servicebuilderConfiguration;
            }
        });


//        task.conventionMapping.with {
//            jdependClasspath = {
//                def config = project.configurations['jdepend']
//                if (config.dependencies.empty) {
//                    project.dependencies {
//                        jdepend "jdepend:jdepend:$extension.toolVersion"
//                        jdepend("org.apache.ant:ant-jdepend:1.8.2")
//                    }
//                }
//                config
//            }
//        }



    }
}
