package com.arakelian

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class GitProperties extends DefaultTask {
    def propertiesFile

    File getPropertiesFile() {
        project.file(propertiesFile)
    }
    
    @TaskAction
    def action() {
        def branch = 'git rev-parse --abbrev-ref HEAD'.execute().text.trim()
        def revision = 'git rev-list --max-count 1 --timestamp --abbrev-commit HEAD'.execute().text.trim()
        def commitHash = revision.split(' ').last()
        def timestamp = revision ? new java.util.Date(java.util.concurrent.TimeUnit.SECONDS.toMillis(revision.split(' ').first() as long)).format("yyyy-MM-dd'T'HH:mm:ssZ") : null

        File propertiesFile = getPropertiesFile()
        if(!propertiesFile.exists()) {
            propertiesFile.parentFile.mkdirs()
            propertiesFile.createNewFile()
        }

        def newline = System.getProperty('line.separator')
        propertiesFile.text = "git.branch=${branch}${newline}" +
            "git.commit.id=${commitHash}${newline}" +
            "git.commit.time=${timestamp}${newline}" +
            "api.version=${project.version}${newline}"
    }
}
