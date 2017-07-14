package com.emberjs.resolver

import com.emberjs.Ember
import com.emberjs.cli.EmberCliProjectConfigurator.Companion.inRepoAddons
import com.emberjs.utils.emberRoot
import com.emberjs.utils.parentEmberApp
import com.emberjs.utils.parentEmberModule
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Url
import com.intellij.util.Urls
import com.jetbrains.javascript.debugger.FileUrlMapper

class EmberFileUrlMapper : FileUrlMapper() {
    /**
     * Find the source file associated with the URL
     *
     * For example,
     *
     *   http://localhost:4200/assets/my-app/components/foo.js
     *
     * could resolve to either of these paths:
     *
     *   file://path/to/my-app/app/components/foo.js
     *   file://path/to/my-app/app/lib/my-in-repo-addon/app/components/foo.js
     *
     */
    override fun getFile(url: Url, project: Project, requestor: Url?): VirtualFile? {
        return ModuleManager.getInstance(project).modules.asSequence()
            .mapNotNull { it.emberRoot }
            .flatMap { app ->
                val appName = Ember.getAppName(app) ?: return@flatMap emptySequence<VirtualFile>()

                // search the app and in-repo addons
                val roots = sequenceOf(app).plus(inRepoAddons(app))
                roots.mapNotNull { root ->
                    val path = url.path.removePrefix("/assets/$appName/")
                    root.findFileByRelativePath("app/$path")
                }
            }
            .firstOrNull()
    }

    /**
     * Calculate the URL for a source file path
     *
     * For example:
     *
     *   file://path/to/my-app/app/components/foo.js                    => http://localhost:4200/assets/my-app/components/foo.js
     *   file://path/to/my-app/lib/in-repo-addon/app/components/foo.js  => http://localhost:4200/assets/my-app/components/foo.js
     *   file://path/to/my-app/tests/integration/components/foo-test.js => http://localhost:4200/assets/my-app/tests/integration/components/foo-test.js
     */
    override fun getUrls(file: VirtualFile, project: Project, currentAuthority: String?): MutableList<Url> {
        val authority = currentAuthority ?: return mutableListOf()
        val app = file.parentEmberApp ?: return mutableListOf()
        val appName = Ember.getAppName(app) ?: return mutableListOf()
        val module = file.parentEmberModule ?: return mutableListOf()

        val path = file.path
                .removePrefix(module.path)
                .removePrefix("/")
                .removePrefix("app")
                .removePrefix("/")

        return mutableListOf(Urls.newHttpUrl(authority, "/assets/$appName/$path"))
    }
}