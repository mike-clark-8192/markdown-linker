# markdown-linker

<!-- Plugin description -->
Markdown plugin that helps you to click on a md link to search and jump to any file, symbol, class, action in the IDEA

Example:
```
[xx.java](mdlink://file/Main.java#L20)
```

This example means that you can click "xx.java" to open the search, and "xx.java:20" will be the search content. 
After confirming by pressing Enter, you can jump to the 20th line of the xx.java file

The host portion of the URL ("file" in the example) can be one of the following actions:
- class: search for class
- file: search for file
- symbol: search for symbols
- action: search for action
- all: search everywhere

Prefixing the host with "jump-" (e.g., "jump-file") will directly jump to the closest match without opening the search dialog.
<!-- Plugin description end -->