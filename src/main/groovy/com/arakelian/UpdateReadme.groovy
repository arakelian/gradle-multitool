package com.arakelian

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class UpdateReadme extends DefaultTask {
    // properties go here

    @TaskAction
    def action() {
        def nextVersion = project.version
        if(project.extensions.multitool.removeSnapshotFromReadmeVersion && project.version.endsWith('-SNAPSHOT')) {
            // after committing the README file the project version will not have SNAPSHOT on it
            nextVersion = project.version.substring(0, project.version.length() -'-SNAPSHOT'.length())
        }

        def file = new File('README.md')
        def text = file.text
        println "Updating README to reference version " + nextVersion
        file.withWriter { w ->
            w << text.replaceAll("[0-9]+\\.[0-9]+\\.[0-9]+(-SNAPSHOT)?", nextVersion)
        }

        def uncommitted = 'git status README.md --porcelain'.execute().text.trim()
        if(uncommitted) {
            // commit change to README
            def cmd = ['git','commit','-m','Update README to version ' + nextVersion,'README.md']
            def process = new ProcessBuilder(cmd).redirectErrorStream(true).start()
            process.waitFor()
            println process.text
        } else {
            println "No changes made to README"
        }
    }
}
