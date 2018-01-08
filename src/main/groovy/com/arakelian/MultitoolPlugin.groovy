package com.arakelian

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

class MultitoolPlugin implements Plugin<Project> {
    void apply(Project project) {
        // users can configure this plugin using 'multitool' of type MultitoolPluginExtension
        project.extensions.add("multitool", MultitoolPluginExtension)

        // we expose this configuration
        project.configurations.create('provided')

        project.task("shadowSources", type: ShadowSources) {
            group = "Multitool"
            description = "Shadow source files"
        }

        project.task("gitProperties", type: GitProperties) {
            group = "Multitool"
            description = "Generate Git properties"
            propertiesFile = new File(project.getBuildDir(), 'resources/main/git.properties')
        }

        project.task("updateReadme", type: UpdateReadme) {
            group = "Multitool"
            description = "Update version number in README file"
        }

        project.afterEvaluate {
            doAfterEvaluate(project)
        }
    }

    void doAfterEvaluate(Project project) {
        configureRepositories(project)

        if(project.plugins.hasPlugin("java")) {
            configureJava8(project)
            configureJavaProvided(project)
            configureJarArtifacts(project)
            configureTestLogging(project)
            configureEclipseClasspath(project)
            configureNexusUpload(project)
            configureShadow(project)
        }

        executeCommandShorcuts(project)
    }

    void configureTestLogging(Project project) {
        if(!project.extensions.multitool.configureTestLogging) {
            return;
        }

        project.test {
            afterTest { description, result ->
                // nice to see test results as they are executed
                println "${description.className} > ${description.name}  ${result.resultType}"
            }
        }
    }

    void configureRepositories(Project project) {
        if(!project.extensions.multitool.configureRepos) {
            return;
        }

        project.repositories {
            // prefer locally built artifacts
            mavenLocal()

            // use external repos as fallback
            mavenCentral()
            jcenter()
        }
    }

    void configureJava8(Project project) {
        project.tasks.withType(JavaCompile) { task ->
            sourceCompatibility = project.extensions.multitool.javaVersion
            targetCompatibility = project.extensions.multitool.javaVersion

            // java 8 option which export names of constructor and method parameter names; no longer
            // have to declare parameter names with @JsonCreator
            options.compilerArgs << "-parameters"

            // Eclipse code formatting removes extraneous parenthesis which errorprone complains about
            if (project.plugins.hasPlugin('net.ltgt.errorprone')) {
                options.compilerArgs << '-Xep:OperatorPrecedence:OFF'
            }
        }
    }

    void configureJarArtifacts(Project project) {
        // Maven Central requires Javadocs
        project.task("javadocJar", type: org.gradle.jvm.tasks.Jar, dependsOn:project.classes) {
            classifier = 'javadoc'
            from project.javadoc
        }

        // always nice to have source code available in other projects
        project.task("sourcesJar", type:org.gradle.jvm.tasks.Jar, dependsOn:project.classes) {
            classifier = 'sources'
            from project.sourceSets.main.allSource
        }

        // other projects may want to extend our unit tests
        if(!project.plugins.hasPlugin("com.github.johnrengelman.shadow")) {
            project.task("testJar", type:org.gradle.jvm.tasks.Jar, dependsOn:project.testClasses) {
                classifier = 'tests'
                from project.sourceSets.test.output
            }
        } else {
            project.task("testJar", type:ShadowJar, dependsOn:project.testClasses) {
                classifier = 'tests'
                from project.sourceSets.test.output
                configurations = [project.configurations.testRuntime]
            }
        }

        project.artifacts {
            archives project.tasks.testJar
            archives project.tasks.javadocJar
            archives project.tasks.sourcesJar
        }
    }

    void configureJavaProvided(Project project) {
        // mimic Maven 'provided' configuration, as suggested in GRADLE-784
        def provided = project.configurations.provided
        provided.extendsFrom(project.configurations.compile)

        project.configurations {
            project.extensions.multitool.excludeGroups.each{ group ->
                all*.exclude group: group
            }
        }

        project.sourceSets {
            main.compileClasspath += project.configurations.provided
            test.compileClasspath += project.configurations.provided
            test.runtimeClasspath += project.configurations.provided
        }

        project.plugins.withType(org.gradle.plugins.ide.idea.IdeaPlugin, { plugin ->
            project.plugins.withType(org.gradle.api.plugins.JavaPlugin, { javaPlugin ->
                project.idea {
                    module {
                        //if you need to put 'provided' dependencies on the classpath
                        scopes.PROVIDED.plus += [ project.configurations.provided ]
                    }
                }
            })
        })
    }

    void configureShadow(Project project) {
        if(!project.plugins.hasPlugin("com.github.johnrengelman.shadow")) {
            if(project.extensions.multitool.relocates.size()!=0) {
                throw new org.gradle.api.ProjectConfigurationException("Cannot specific multitool.relocates without shadow plugin",null);
            }
            return;
        }

        project.sourceSets {
            // shadow configuration is added by Shadow plugin, but it's only configured for the main sourceset
            test.compileClasspath += project.configurations.shadow
            test.runtimeClasspath += project.configurations.shadow
        }

        project.tasks.withType(ShadowJar).each { task ->
            task.mergeServiceFiles()

            // we don't want poms for third-party stuff
            task.exclude 'META-INF/maven/**/*'

            // we are only shadowing 'shadow' dependencies
            task.configurations = [project.configurations.shadow]

            // define relocations
            project.extensions.multitool.relocates.each{ from, to ->
                task.relocate from, to
            }
        }

        // disable original jar
        def jar = project.tasks.jar
        def jarArchivePath = jar.archivePath
        jar.enabled = false

        // shadow artifact is temporary resource that is processed by ProGuard to
        // remove unused classes
        def shadowJar = project.tasks.shadowJar
        shadowJar.classifier = 'shadow'

        // ProGuard needs reference to JDK rt.jar
        def javaHome = System.getProperty('java.home')
        def libJars = project.fileTree(dir: javaHome + "/lib/", include: "*.jar")

        // need to give it dependencies that were not compiled into jar
        def compileJars = project.configurations.compile.getFiles()
        libJars += compileJars

        def minify = project.task("minify", type:proguard.gradle.ProGuardTask, dependsOn:shadowJar) {
            injars shadowJar.archivePath
            outjars jarArchivePath
            libraryjars libJars
        }

        project.extensions.multitool.proguardOptions.each { key, value ->
             if(value!=null) {
                 minify."$key"(value)
             } else {
                 minify."$key"()
             }
        }

        // make sure we minify first
        def assemble = project.tasks.assemble
        assemble.dependsOn(minify)
    }

    void configureEclipseClasspath(Project project) {
        if(!project.plugins.hasPlugin("eclipse")) {
            return;
        }

        // Custom Eclipse .classpath generation to use project references instead of .jar references;
        // this allows us to do refactoring in Eclipse across projects
        // see: https://docs.gradle.org/current/dsl/org.gradle.plugins.ide.eclipse.model.EclipseClasspath.html

        project.eclipse {
            classpath {
                // override default 'bin'
                defaultOutputDir = project.file('target/classes')

                // ensure that 'provided' configuration jars available in Eclipose
                plusConfigurations += [ project.configurations.provided ]

                // we want source files
                downloadSources = true
                downloadJavadoc = false

                // customize generated .classpath file
                file {
                    def project_refs = []
                    def matched_libs = []

                    // closure executed after .classpath content is loaded from existing file
                    // and after gradle build information is merged
                    whenMerged { classpath ->

                        // build list of dependencies that we want to replace with Eclipse project refs
                        println 'Finding local projects'
                        def use_eclipse_project_refs = []
                        new File(project.projectDir, "..").eachDir {
                            if(new File("${it}/build.gradle").exists()) {
                                use_eclipse_project_refs += it.name
                             }
                        }

                        println 'Generating Eclipse .classpath file'
                        def kindOrder = [ 'src':1, 'con':2, 'lib':3, 'output':0 ];
                        classpath.entries.sort(true, { a,b ->
                            def order = kindOrder[a.kind] <=> kindOrder[b.kind]
                            order != 0 ? order : a.path <=> b.path
                        } as Comparator).each { entry ->
                            if(entry.kind.equals('lib')) {
                                use_eclipse_project_refs.each { name ->
                                    def regex = '/(' + ( name.endsWith('-') ?
                                        java.util.regex.Pattern.quote(name.substring(0,name.length()-1)) + '(?:-[A-Za-z]+)*'
                                            : java.util.regex.Pattern.quote(name) ) + ')-([\\w\\.]+?)(-[A-Za-z]+)?\\.jar$'
                                    def pattern = java.util.regex.Pattern.compile(regex)
                                    def matcher = pattern.matcher(entry.path)
                                    if(matcher.find()) {
                                        def match = matcher.group(1)
                                        println match + ' (' + matcher.group(2) + ') matched ' + entry.path
                                        matched_libs += [ entry ]
                                        project_refs += [ match ]
                                    }
                                }
                            }
                        }
                        classpath.entries.removeAll(matched_libs)
                    }

                    // final adjustments to .classpath file before it is saved
                    withXml { xml ->
                        def node = xml.asNode()
                        project_refs.unique(false).each { name ->
                            println "Creating Eclipse project dependency: " + name
                            node.appendNode('classpathentry', [ combineaccessrules: false, exported: true, kind: 'src', path: '/' + name ])
                        }
                    }
                }
            }
        }
    }

    void configureNexusUpload(Project project) {
        if(!project.plugins.hasPlugin("signing") || !project.plugins.hasPlugin("maven") ||
                !project.hasProperty('nexusUsername')) {
            return;
        }

        project.signing {
            sign project.configurations.archives
        }

        // note: nexus credentials are typically kept in ~/.gradle/gradle.properties
        project.uploadArchives {
            repositories {
                // deploy locally
                mavenLocal()

                // see: http://central.sonatype.org/pages/gradle.html
                mavenDeployer {
                    beforeDeployment {
                        org.gradle.api.artifacts.maven.MavenDeployment deployment -> project.signing.signPom(deployment)
                    }

                    repository(url: "https://oss.sonatype.org/service/local/staging/deploy/maven2/") {
                        authentication(userName: project.nexusUsername, password: project.nexusPassword)
                    }

                    snapshotRepository(url: "https://oss.sonatype.org/content/repositories/snapshots/") {
                        authentication(userName: project.nexusUsername, password: project.nexusPassword)
                    }

                    pom.project {
                        name project.name
                        packaging 'jar'
                        description project.description

                        url 'https://github.com/arakelian/' + project.name
                        scm {
                            connection 'scm:git:https://github.com/arakelian/' + project.name + '.git'
                            developerConnection 'scm:git:git@github.com:arakelian/' + project.name + '.git'
                            url 'https://github.com/arakelian/' + project.name + '.git'
                        }

                        licenses {
                            license {
                                name 'Apache License 2.0'
                                url 'https://www.apache.org/licenses/LICENSE-2.0'
                                distribution 'repo'
                            }
                        }

                        developers {
                            developer {
                                id = 'arakelian'
                                name = 'Greg Arakelian'
                                email = 'greg@arakelian.com'
                            }
                        }
                    }
                }
            }
        }
    }

    void executeCommandShorcuts(Project project) {
        // This code allows us to define aliases, such as "all", so that when we do "gradle all",
        // we can substitute in a series of other gradle tasks
        // see: https://caffeineinduced.wordpress.com/2015/01/25/run-a-list-of-gradle-tasks-in-specific-order/
        def newTasks = []

        // gradle respects ordering of tasks specified on command line, so we replace shortcuts
        // with equivalent commands as though they were specified by user

        project.gradle.startParameter.taskNames.each { param ->
           def macro = project.extensions.multitool.macros[param]
           if( macro ) {
               macro.each { task ->
                   if(project.tasks.names.contains(task)) {
                      newTasks << task
                   }
               }
           } else {
               newTasks << param
           }
        }

        // replace command line arguments
        project.gradle.startParameter.taskNames = newTasks.flatten()
    }
}
