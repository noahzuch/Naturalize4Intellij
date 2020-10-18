package com.github.noahzuch.naturalize4intellij.services

import com.intellij.openapi.project.Project
import com.github.noahzuch.naturalize4intellij.MyBundle

class MyProjectService(project: Project) {

    init {
        println(MyBundle.message("projectService", project.name))
    }
}
