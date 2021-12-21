package com.github.binarybeing.idea.markdown.linker.service

import com.intellij.ide.DataManager
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import org.apache.commons.lang.StringUtils
import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel
import org.intellij.plugins.markdown.ui.preview.ResourceProvider

/**
 * @author binarybeing
 * @date 2021/12/21
 * @note
 */
internal class ProcessLinkProvider (panel: MarkdownHtmlPanel): MarkdownBrowserPreviewExtension, ResourceProvider {
    init {
        panel.browserPipe?.subscribe(openLinkEventName, ::openLink)
        Disposer.register(this) {
            panel.browserPipe?.removeSubscription(openLinkEventName, ::openLink)
        }
    }

    private fun openLink(link: String) {
        if (link.startsWith("se-")){
            var run = Runnable {

                var contributorId = ""
                var searchContent = ""
                var action = ""
                if(link.startsWith("se-all://")){
                    contributorId = "SearchEverywhereContributor.All"
                    searchContent = link.replace("se-all://","")
                    action = "SearchEverywhere"
                }
                if(link.startsWith("se-classes://")){
                    contributorId = "ClassSearchEverywhereContributor"
                    searchContent = link.replace("se-classes://","")
                    action = "GotoClass"
                }
                if(link.startsWith("se-files://")){
                    contributorId = "FileSearchEverywhereContributor"
                    searchContent = link.replace("se-files://","")
                    action = "GotoFile"
                }
                if(link.startsWith("se-symbols://")){
                    contributorId = "SymbolSearchEverywhereContributor"
                    searchContent = link.replace("se-symbols://","")
                    action = "GotoSymbol"
                }
                if(link.startsWith("se-actions://")){
                    contributorId = "ActionSearchEverywhereContributor"
                    searchContent = link.replace("se-actions://","")
                    action = "GotoAction"
                }
                if (StringUtils.isNotEmpty(action)) {
                    val am = ActionManager.getInstance()
                    val ac = am.getAction(action)
                    var ace = AnActionEvent.createFromAnAction(ac,null,"", DataManager.getInstance().getDataContext())
                    var project = ace.project

                    val seManager = SearchEverywhereManager.getInstance(project)
                    seManager.show(contributorId,searchContent ,ace)
                }
            }
            ApplicationManager.getApplication().invokeLater(run);

        }
    }
    override fun dispose() = Unit

    override fun canProvide(resourceName: String):Boolean {
        return false
    }

    override fun loadResource(resourceName: String): ResourceProvider.Resource? {
        return null
    }

    class Provider: MarkdownBrowserPreviewExtension.Provider {
        private val LOG = logger<ProcessLinkProvider.Provider>()
        override fun createBrowserExtension(panel: MarkdownHtmlPanel): MarkdownBrowserPreviewExtension? {
            LOG.info("createBrowserExtension start success")
            return ProcessLinkProvider(panel)
        }
    }
    companion object {
        private const val openLinkEventName = "openLink"
    }
}