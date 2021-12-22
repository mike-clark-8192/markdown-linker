# markdown-linker

<!-- Plugin description -->
Markdown plugin that helps you to click on a md link to search and jump to any file, symbol, class, action in the IDEA

Example:
```
[xx.java](se-classes://xx.java:20)
```

This example means that you can click "xx.java" to open the search, and "xx.java:20" will be the search content. After confirming by pressing Enter, you can jump to the 20th line of the xx.java file

The "se-classes" in the parentheses of the example can be replaced by the following, each of which represents a different search behavior
- se-actions: search for action
- se-all: search everywhere
- se-files: search for file
- se-classes: search for class
- se-symbols: search for symbols
<!-- Plugin description end -->
