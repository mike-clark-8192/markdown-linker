package com.github.binarybeing.markdownlinker.services

import com.intellij.openapi.project.Project
import com.github.binarybeing.markdownlinker.MyBundle

class MyProjectService(project: Project) {

    init {
        println(MyBundle.message("projectService", project.name))
    }
}
