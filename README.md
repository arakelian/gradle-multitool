# Gradle Multitool

This Gradle plugin does a few things:

1. Configures Java builds for Java 11.  
   * Source and target compatibility is set to version 11. 
   * Java compiler is configured to export names of constructor and method argument names.

2. Adds -sources, -javadoc, and -tests Jars to a Java build.

3. Adds a `provided` configuration for Java projects.

4. Enables minification of Jar files that shadow dependencies using ProGuard.

5. Generates Eclipse classpath that has project dependencies for projects that you have checked out locally.

6. Adds user-defined macros to Gradle command line, e.g. "all" option that is equivalent to 'clean build install'.



