package com.github.jelmerk;

import org.gradle.BuildAdapter;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.plugins.WarPlugin;
import org.gradle.api.plugins.WarPluginConvention;
import org.gradle.api.specs.Spec;

import java.io.File;

/**
 * @author Jelmer Kuperus
 */
public class ThemePlugin implements Plugin<Project> {

    private static final String BUILD_THUMBNAIL = "buildThumbnail";
    private static final String MERGE_THEME = "mergeTheme";

    @Override
    public void apply(final Project project) {
        project.getPlugins().apply(LiferayBasePlugin.class);

        createThemeExtension(project);

        configureWar(project);

        configureMergeTemplateTaskDefaults(project);
        configureMergeTemplateTask(project);

        configureBuildThumbnailTaskDefaults(project);
        configureBuildThumbnailTask(project);
    }


    private void configureWar(Project project) {
        WarPluginConvention warConvention = project.getConvention().getPlugin(WarPluginConvention.class);
        warConvention.setWebAppDirName(new File(project.getBuildDir(), "webapp").getAbsolutePath());
    }

    private void createThemeExtension(Project project) {
        project.getExtensions().create("theme", ThemePluginExtension.class, project);
    }


    private void configureMergeTemplateTaskDefaults(final Project project) {
        project.getGradle().addBuildListener(new BuildAdapter() {
            @Override
            public void projectsEvaluated(Gradle gradle) {

                final LiferayPluginExtension liferayExtension = project.getExtensions()
                        .getByType(LiferayPluginExtension.class);

                project.getTasks().withType(MergeTheme.class, new Action<MergeTheme>() {
                    @Override
                    public void execute(MergeTheme mergeTheme) {
                        if (mergeTheme.getAppServerPortalDir() == null) {
                            mergeTheme.setAppServerPortalDir(liferayExtension.getAppServerPortalDir());
                        }
                    }
                });
            }
        });
    }

    private void configureMergeTemplateTask(Project project) {

        final WarPluginConvention warConvention = project.getConvention().getPlugin(WarPluginConvention.class);

        final ThemePluginExtension themeExtension = project.getExtensions().getByType(ThemePluginExtension.class);

        final MergeTheme task = project.getTasks().add(MERGE_THEME, MergeTheme.class);
        task.setThemeType(themeExtension.getThemeType());

        project.getGradle().addBuildListener(new BuildAdapter() {
            @Override
            public void projectsEvaluated(Gradle gradle) {

                if (task.getThemeType() == null) {
                    task.setThemeType(themeExtension.getThemeType());
                }

                if (task.getParentThemeName() == null) {
                    task.setParentThemeName(themeExtension.getParentThemeName());
                }

                if (task.getDiffsDir() == null) {
                    task.setDiffsDir(themeExtension.getDiffsDir());
                }

                if (task.getOutputDir() == null) {
                    task.setOutputDir(warConvention.getWebAppDir());
                }
            }
        });
    }

    private void configureBuildThumbnailTaskDefaults(final Project project) {
        project.getGradle().addBuildListener(new BuildAdapter() {
            @Override
            public void projectsEvaluated(Gradle gradle) {
                final LiferayPluginExtension liferayExtension = project.getExtensions()
                        .getByType(LiferayPluginExtension.class);

                project.getTasks().withType(BuildThumbnail.class, new Action<BuildThumbnail>() {
                    @Override
                    public void execute(BuildThumbnail task) {
                        if (task.getClasspath() == null) {
                            task.setClasspath(liferayExtension.getPortalClasspath());
                        }
                    }
                });
            }
        });
    }

    private void configureBuildThumbnailTask(final Project project) {
        final WarPluginConvention warConvention = project.getConvention().getPlugin(WarPluginConvention.class);
        final ThemePluginExtension themeExtension = project.getExtensions().getByType(ThemePluginExtension.class);

        Task mergeTask = project.getTasks().getByName(MERGE_THEME);

        final BuildThumbnail task = project.getTasks().add(BUILD_THUMBNAIL, BuildThumbnail.class);

        project.getGradle().addBuildListener(new BuildAdapter() {
            @Override
            public void projectsEvaluated(Gradle gradle) {

                // only set the default if nothing was changed from say a configuration closure

                if (task.getOriginalFile() == null) {
                    task.setOriginalFile(new File(themeExtension.getDiffsDir(), "images/screenshot.png"));
                }

                if (task.getThumbnailFile() == null) {
                    task.setThumbnailFile(new File(warConvention.getWebAppDir(), "images/thumbnail.png"));
                }
            }
        });
        task.dependsOn(mergeTask);

        task.onlyIf(new Spec<Task>() {
            @Override
            public boolean isSatisfiedBy(Task element) {
                BuildThumbnail castTask = (BuildThumbnail) element; //NOSONAR

                return castTask.getOriginalFile().exists() &&
                        !new File(themeExtension.getDiffsDir(), "images/thumbnail.png").exists();
            }


        });

        Task warTask = project.getTasks().getByName(WarPlugin.WAR_TASK_NAME);
        warTask.dependsOn(task);
    }

}
