package com.arakelian

class MultitoolPluginExtension {
    // target Java version
    String javaVersion = "1.8"

    // true if we should configure "repositories" to use mavenLocal(), mavenCentral() and then jcenter()
    boolean configureRepos = true

    // true if we should log unit tests as they are executed (useful when project has a lot of tests)
    boolean configureTestLogging = true

    // true to remove -SNAPSHOT from version we store in README file
    boolean removeSnapshotFromReadmeVersion = false

    // dependency groups that we exclude from all configurations
    List<String> excludeGroups = [
        // we route everything through SLF4J so no JCL
        'commons-logging'
    ]

    // useful macros, you can add your own
    Map<String,List<String>> macros = [
        'all' : ['clean', 'classpath', 'build', 'install'],
        'deploy' : ['upload', 'closeAndReleaseRepository'],
        'classpath' : ['cleanEclipseClasspath', 'eclipseClasspath', 'eclipseFactoryPath', 'cleanIdeaModule', 'ideaModule'],
    ]

    // relocates; requires "com.github.johnrengelman.shadow" plugin
    Map<String,String> relocates = [:]

   // ProGuard options for minification
    Map<String,Object> proguardOptions = [
        // this work for us, but other clients will want to change this
        'keep' : 'class com.arakelian.** { *; }',
        
        // see: https://sourceforge.net/p/proguard/bugs/459/
        'optimizations' : '!code/allocation/variable,!class/unboxing/*,!method/marking/*',
        
        'optimizationpasses' : 5,
        'dontskipnonpubliclibraryclassmembers' : null,
        'dontobfuscate' : null,
        'dontwarn' : null,
    ]
}
