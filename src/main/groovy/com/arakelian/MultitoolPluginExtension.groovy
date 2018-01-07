package com.arakelian

class MultitoolPluginExtension {
    // Java version
    String javaVersion = "1.8"
    
    // flags
    boolean configureRepos = true
    boolean configureJavaArtifacts = true
    boolean configureProvided = true
    
    // useful macros, you can add your own
    Map<String,List<String>> macros = [
        'all' : ['clean', 'classpath', 'build', 'install'],
        'deploy' : ['upload', 'closeAndReleaseRepository'],
        'classpath' : ['cleanEclipseClasspath', 'eclipseClasspath', 'eclipseFactoryPath', 'cleanIdeaModule', 'ideaModule'],
    ]
}
