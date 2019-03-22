package com.arakelian

class MultitoolPluginExtension {
	// target Java version
	String javaVersion = "11"

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

	// dependency groups that we exclude from all configurations
	List<String> errorproneCompilerOptions = [
		'-XepDisableWarningsInGeneratedCode',
		'-Xep:OperatorPrecedence:OFF'
	]
	
	
	// Eclipse output folder
	String eclipseOutputFolder = "target"
	
	// package patterns to exclude from Eclipse
	List<String> excludeFromEclipse = []


	// useful macros, you can add your own
	Map<String,List<String>> macros = [
		'all' : [
			'clean',
			'classpath',
			'build',
			'install'
		],
		'deploy' : [
			'uploadArchives',
			'closeAndReleaseRepository'
		],
		'classpath' : [
			'cleanEclipseClasspath',
			'eclipseClasspath',
			'eclipseFactoryPath',
			'cleanIdeaModule',
			'ideaModule'
		],
	]

	// NOTE: requires "com.github.johnrengelman.shadow" plugin
	Map<String,String> relocates = [:]

	// injected into relocated package names
	Map<String,String> relocatePrefixes = [
		"shadowJar" : "",
		"shadowTestJar" : "tests.",
	]

	// package patterns to include in relocation
	List<String> includeInRelocation = []

	// package patterns to exclude from relocation
	List<String> excludeFromRelocation = []

	// true if we should minify after relocation
	boolean configureMinify = true

	// configure ProGaurd
	Closure proguardConfiguration = null

	// ProGuard options for minification
	Closure defaultProguardConfiguration = {
		keep includedescriptorclasses:true, 'class com.arakelian.** { *; }'

        // Preserve the special static methods that are required in all enumeration classes.
        keepclassmembers allowoptimization:true, \
            'enum * { \
                <fields>; \
                public static **[] values(); \
                public static ** valueOf(java.lang.String); \
            }'

        // Explicitly preserve all serialization members
        keepclassmembers \
            'class * implements java.io.Serializable { \
                static final long serialVersionUID; \
                static final java.io.ObjectStreamField[] serialPersistentFields; \
                private void writeObject(java.io.ObjectOutputStream); \
                private void readObject(java.io.ObjectInputStream); \
                java.lang.Object writeReplace(); \
                java.lang.Object readResolve(); \
            }'

		keepclasseswithmembernames includedescriptorclasses:true, 'class * { native <methods>; }'

		keepattributes 'Exceptions'
		keepattributes 'InnerClasses'
		keepattributes 'Signature'
		keepattributes 'Deprecated'
		keepattributes 'SourceFile'
		keepattributes 'LineNumberTable'
		keepattributes '*Annotation*'
		keepattributes 'EnclosingMethod'

		// see: https://sourceforge.net/p/proguard/bugs/459/
		optimizations '!code/allocation/variable'
		optimizations '!class/unboxing/*'
		optimizations '!method/marking/*'

        dontnote '**.com.google.common.base.Throwables'
        dontnote '**.com.google.common.base.internal.Finalizer'
        dontnote '**.com.google.common.cache.Striped64$Cell'
        dontnote '**.com.google.common.cache.Striped64'
        dontnote '**.com.google.common.hash.Striped64$Cell'
        dontnote '**.com.google.common.hash.Striped64'
        dontnote '**.com.google.common.util.concurrent.AbstractFuture$UnsafeAtomicHelper'
        dontnote '**.com.google.common.util.concurrent.AbstractFuture'
        dontnote '**.com.google.common.util.concurrent.MoreExecutors'
		
		optimizationpasses 2

		dontskipnonpubliclibraryclassmembers()
		dontobfuscate()
		dontwarn()
	}

	// filtering of input jar entries
	Map<String,String> injarsFilters = [
		// see: https://sourceforge.net/p/proguard/bugs/665/
		"filter": "!META-INF/versions/9/**.class"
	]

	// filtering of library jar entries
	Map<String,String> libraryjarsFilters = [
		// see: https://sourceforge.net/p/proguard/bugs/665/
		"filter": "!META-INF/versions/9/**.class"
	]

	Map<String,String> outjarsFilters = [:]
}
