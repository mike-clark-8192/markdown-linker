package com.github.binarybeing.idea.markdown.linker.service

import com.intellij.ide.DataManager
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManager
import com.intellij.ide.util.gotoByName.GotoClassModel2
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel
import org.intellij.plugins.markdown.ui.preview.ResourceProvider
import java.net.URI

internal class ProcessLinkProvider(panel: MarkdownHtmlPanel) : MarkdownBrowserPreviewExtension, ResourceProvider {
    companion object {
        val LOG = logger<ProcessLinkProvider>()
    }

    private val targets = mapOf(
        "all" to Triple("SearchEverywhereContributor.All", "SearchEverywhere", ::handleLuckyAll),
        "class" to Triple("ClassSearchEverywhereContributor", "GotoClass", ::handleLuckyClazz),
        "file" to Triple("FileSearchEverywhereContributor", "GotoFile", ::handleLuckyFiles),
        "symbol" to Triple("SymbolSearchEverywhereContributor", "GotoSymbol", ::handleLuckySymbols),
        "action" to Triple("ActionSearchEverywhereContributor", "GotoAction", ::handleLuckyActions)
    )

    // Regular expression to extract line number from URI fragment
    private val lineNumberRegex = Regex("(?:L|line-)?(\\d+)")

    init {
        LOG.info("Initializing ProcessLinkProvider for panel: $panel (class: ${panel::class.qualifiedName})")
        @Suppress("UnstableApiUsage")
        panel.browserPipe?.let { pipe ->
            val handler = object : org.intellij.plugins.markdown.ui.preview.BrowserPipe.Handler {
                override fun processMessageReceived(data: String): Boolean {
                    ApplicationManager.getApplication().invokeLater {
                        openLink(data)
                    }
                    return true
                }
            }
            pipe.subscribe("mdlink", handler)
            Disposer.register(this) { pipe.removeSubscription("mdlink", handler) }
        }
//        if (panel is MarkdownHtmlPanelEx) {
//            panel.putUserData(MarkdownHtmlPanelEx.DO_NOT_USE_LINK_OPENER, false)
//        }
    }

    private fun openLink(link: String) {
        val uri = URI(link)
        var action = uri.host.orEmpty()
        val path = uri.path.orEmpty().removePrefix("/")

        // Extract line number from fragment if present
        val lineNumber = uri.fragment?.let { fragment ->
            lineNumberRegex.find(fragment)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }
        LOG.info("Parsed URI: action=$action, path=$path, lineNumber=$lineNumber")

        val lucky = action.startsWith("jump-")
        action = if (lucky) action.removePrefix("jump-") else action
        val target = targets[action] ?: run {
            LOG.warn("Unrecognized action: $action")
            showUnrecognizedAction(action)
            return
        }
        target.let { (contributorId, actionId, luckyHandler) ->
            if (lucky) {
                luckyHandler(path, lineNumber)?.let { result -> LOG.info("lucky result: $result") } ?: run {
                    LOG.warn("No result found for lucky action: $action with path: $path")
                }
            } else {
                val searchText = lineNumber?.let { "$path:$lineNumber" } ?: path
                showSearchDialog(searchText, contributorId, actionId)
            }
        }
    }

    private fun showUnrecognizedAction(action: String) {
        val available = targets.keys.joinToString("\n")
        val msg = """
            '$action' is not a recognized link target. 
            Available targets are:
            $available
        """.trimIndent()
        // log warning
        LOG.warn("Unrecognized link target: $action")
        Messages.showErrorDialog(msg, "Unrecognized Link Target")
    }

    @Suppress("DEPRECATION")
    private fun getDataContext(): com.intellij.openapi.actionSystem.DataContext {
        return DataManager.getInstance().dataContext
    }

    private fun showSearchDialog(path: String, contributorId: String, actionId: String) {
        val dataContext = getDataContext()
        val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return
        runCatching {
            val ac = ActionManager.getInstance().getAction(actionId)
            val ace = AnActionEvent.createFromAnAction(ac, null, "", dataContext)
            SearchEverywhereManager.getInstance(project)
                .show(contributorId, path, ace)
        }.onFailure { LOG.error("Error showing search dialog", it) }
    }

    private fun handleLuckyClazz(path: String, lineNumber: Int? = null): String? {
        val dataContext = getDataContext()
        val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return null

        val psiClass = GotoClassModel2(project)
            .getElementsByName(path, false, path)
            .firstOrNull() as? PsiClass ?: return null

        val psiFile = psiClass.containingFile
        navigateToFileWithLineNumber(psiFile, lineNumber)
        return psiClass.qualifiedName
    }

    private fun handleLuckyFiles(path: String, lineNumber: Int? = null): String? {
        val dataContext = getDataContext()
        val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return null

        // Try resolving relative to project root
        val projectBasePath = project.basePath
        val vFile = projectBasePath?.let {
            LocalFileSystem.getInstance().findFileByPath("$it/$path")
        } ?: LocalFileSystem.getInstance().findFileByPath(path) // fallback: absolute path

        @Suppress("DEPRECATION")
        val psiFile = vFile?.let { com.intellij.psi.PsiManager.getInstance(project).findFile(it) }
            ?: FilenameIndex.getFilesByName(
                project,
                path.substringAfterLast('/'),
                GlobalSearchScope.projectScope(project)
            ).firstOrNull()

        if (psiFile != null) {
            navigateToFileWithLineNumber(psiFile, lineNumber)
            return psiFile.presentation?.presentableText
        }
        return null
    }

    private fun navigateToFileWithLineNumber(file: PsiFile, lineNumber: Int?) {
        val path = file.virtualFile.path
        if (lineNumber != null && lineNumber > 0) {
            LOG.debug("Navigating to file at path: '$path', to line number: $lineNumber (user‑1 => ${lineNumber - 1})")
            val descriptor = OpenFileDescriptor(
                file.project,
                file.virtualFile,
                lineNumber - 1, // editor is zero-based
                0
            )
            descriptor.navigate(true)
            LOG.info("Navigated to $path at line $lineNumber")
        } else {
            LOG.debug("Navigating to file at path: '$path' without a specific line number")
            file.navigate(true)
            LOG.info("Navigated to $path (default location)")
        }
    }

    private fun handleLuckySymbols(path: String, @Suppress("unused") lineNumber: Int? = null): String? {
        val dataContext = getDataContext()
        val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return null

        // Use the same model behind Navigate | Symbol...
        @Suppress("DEPRECATION") 
        val model = com.intellij.ide.util.gotoByName.GotoSymbolModel2(project)
        // false = only project symbols, change to true to include libraries
        val items = model.getElementsByName(path, false, path)
            .filterIsInstance<NavigationItem>()
        val symbol = items.firstOrNull() ?: return null

        symbol.navigate(true)
        // You can get the display name from the item's presentation
        return symbol.presentation?.presentableText
    }

    private fun handleLuckyActions(path: String, @Suppress("unused") lineNumber: Int? = null): String? {
        val dataContext = getDataContext()
        val action = ActionManager.getInstance().getAction(path)
        action?.let {
            val ace = AnActionEvent.createFromAnAction(it, null, "", dataContext)
            it.actionPerformed(ace)
        }
        return action?.templatePresentation?.text
    }

    private fun handleLuckyAll(path: String, lineNumber: Int? = null): String? {
        return handleLuckyClazz(path, lineNumber)
            ?: handleLuckyFiles(path, lineNumber)
            ?: handleLuckySymbols(path, lineNumber)
            ?: handleLuckyActions(path, lineNumber)
    }

    override fun dispose() {
        LOG.info("dispose() called on ProcessLinkProvider")
    }

    private val testScripts = listOf("mdlink/mdlink.js")

    override val scripts: List<String>
        get() = testScripts

    override val resourceProvider: ResourceProvider
        get() = this

    override val priority: MarkdownBrowserPreviewExtension.Priority
        get() = MarkdownBrowserPreviewExtension.Priority.AFTER_ALL

    override fun canProvide(resourceName: String): Boolean = testScripts.contains(resourceName)

    override fun loadResource(resourceName: String): ResourceProvider.Resource? {
        if (resourceName == "mdlink/mdlink.js") {
            val js = """
(function() {
  const oldClick = window.document.onclick;
  window.document.onclick = function(e) {
    let target = e.target;
    while (target && target.tagName.toLowerCase() !== 'a') {
      target = target.parentNode;
    }
    if (!target || !target.hasAttribute('href')) {
      return oldClick ? oldClick.call(this, e) : true;
    }

    const href = target.getAttribute('href');
    const [protocol] = href.split(':');
    if (protocol === 'mdlink') {
      e.stopPropagation();
      window.__IntelliJTools.messagePipe.post("mdlink", href);
      return false;
    }

    return oldClick ? oldClick.call(this, e) : true;
  };
})();
""".trimIndent()
            return ResourceProvider.Resource(js.toByteArray(), "application/javascript")
        }
        return null
    }

    protected fun finalize() {
        LOG.info("finalize() called on ProcessLinkProvider")
    }

    class Provider : MarkdownBrowserPreviewExtension.Provider {
        companion object {
            private val LOG = logger<Provider>()
        }
        override fun createBrowserExtension(panel: MarkdownHtmlPanel): MarkdownBrowserPreviewExtension? {
            LOG.info("Creating ProcessLinkProvider for panel: $panel (class: ${panel::class.qualifiedName})")
            return ProcessLinkProvider(panel)
        }

    }


}
