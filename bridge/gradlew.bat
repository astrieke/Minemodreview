@rem Ruft das Gradle des Haupt-Repos auf. Weil das Arbeitsverzeichnis dieser Ordner bleibt,
@rem baut Gradle nur diese Mod, z. B. "gradlew build" oder "gradlew 1.21.1:build".
@call "%~dp0..\gradlew.bat" %*
