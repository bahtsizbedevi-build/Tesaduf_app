@echo off
rem Builds TESADUF with the JDK, Gradle and Android SDK bundled with Unity
rem (no Android Studio needed). Usage:  gradlew-unity.cmd assembleDebug
rem
rem The Windows user folder contains Turkish characters (O-umlaut, c-cedilla, u-umlaut).
rem Several Java/Gradle parts (IPC sockets, test workers, AGP) break on such paths, so the
rem build runs through ASCII-only folders: a junction to this project plus an ASCII
rem Gradle home. Nothing is moved or copied.
setlocal
set "UNITY_ANDROID=C:\Program Files\Unity\Hub\Editor\6000.6.4f1\Editor\Data\PlaybackEngines\AndroidPlayer"
set "JAVA_HOME=%UNITY_ANDROID%\OpenJDK"
set "BUILD_LINK=C:\Users\Public\TesadufBuild"
if not exist "C:\Users\Public\tesaduf-tmp" mkdir "C:\Users\Public\tesaduf-tmp"
if not exist "%BUILD_LINK%" mklink /J "%BUILD_LINK%" "%~dp0." >nul
set "GRADLE_USER_HOME=C:\Users\Public\gradle-home"
set "JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:/Users/Public/tesaduf-tmp"
"%JAVA_HOME%\bin\java.exe" -Xmx64m -cp "%UNITY_ANDROID%\Tools\gradle\lib\gradle-launcher-9.3.1.jar" org.gradle.launcher.GradleMain -p "%BUILD_LINK%" %*
