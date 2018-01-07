package com.arakelian

import org.gradle.api.Plugin
import org.gradle.api.Project

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

        if(project.plugins.hasPlugin("java")) {
	        if(project.extensions.multitool.configureProvided) {
                configureJavaProvided(project)
            }
            
            if(project.plugins.hasPlugin("eclipse")) {
                configureEclipseClasspath(project)
            }
            
            if(project.plugins.hasPlugin("signing") && project.plugins.hasPlugin("maven")) {
				if (project.hasProperty('nexusUsername')) {
	                configureNexusUpload(project)
				}
            }
        }
        
        if(project.extensions.multitool.configureRepos) {
            configureRepositories(project)
        }

        project.afterEvaluate {
	        if(project.plugins.hasPlugin("java")) {
	            configureJava8(project)
	            
		        if(project.extensions.multitool.configureJavaArtifacts) {
		            configureJavaArtifacts(project)
		        }
	        }
            executeCommandShorcuts(project)
        }
    }

    void configureRepositories(Project project) {
		project.repositories {
		    // prefer locally built artifacts
		    mavenLocal()
		
		    // use external repos as fallback
		    mavenCentral()
		    jcenter()
		}
	}
    
    void configureJava8(Project project) {
        project.tasks.withType(org.gradle.api.tasks.compile.JavaCompile) {
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
    
    void configureJavaArtifacts(Project project) {
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
        project.task("testJar", type:org.gradle.jvm.tasks.Jar, dependsOn:project.testClasses) {
            classifier = 'tests'
            from project.sourceSets.test.output
        }

        project.artifacts {
            archives project.tasks.javadocJar
            archives project.tasks.sourcesJar
            archives project.tasks.testJar
        }
    }

    void configureJavaProvided(Project project) {
        // mimic Maven 'provided' configuration, as suggested in GRADLE-784
        def provided = project.configurations.provided
        provided.extendsFrom(project.configurations.compile)
        
        project.configurations {
            // we route everything through SLF4J so no JCL
            all*.exclude group: 'commons-logging'
        }

        project.sourceSets {
            main.compileClasspath += project.configurations.provided
            test.compileClasspath += project.configurations.provided
            test.runtimeClasspath += project.configurations.provided
        }

        if(project.plugins.hasPlugin("idea")) {
            project.idea {
                module {
                    //if you need to put 'provided' dependencies on the classpath
                    scopes.PROVIDED.plus += [ configurations.provided ]
                }
            }
        }
    }

    void configureJavaTests(Project project) {
        project.test {
            afterTest { desc, result ->
                // nice to see test results as they are executed
                println "${desc.className} > ${desc.name}  ${result.resultType}"
            }
        }
    }

    void configureEclipseClasspath(Project project) {
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
	    project.signing {
	        sign configurations.archives
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
	                    authentication(userName: nexusUsername, password: nexusPassword)
	                }
	
	                snapshotRepository(url: "https://oss.sonatype.org/content/repositories/snapshots/") {
	                    authentication(userName: nexusUsername, password: nexusPassword)
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
