# Java console demos (app/)

This module contains the Java agents and console demos.

Contents:

- `SystemDemo.java` – end-to-end demonstration
- `TouchAgent.java`, `TypingAgent.java`, `FusionEngine.java`, `Logger.java`, etc.

Build and run (Windows cmd):

```bat
javac -d ..\app -cp ..\app *.java
java -cp ..\app app.SystemDemo
```

Or use VS Code tasks from the repo root:

- "javac: build app"
- "java: run SystemDemo"

Configuration: see `../config/app.properties` (NN scoring, Python bridge, fusion weights).
