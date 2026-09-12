param(
    [string]$Jar = $(if (Test-Path -LiteralPath "$PSScriptRoot\..\java-code-intelligence-mcp.jar") { "$PSScriptRoot\..\java-code-intelligence-mcp.jar" } else { "$PSScriptRoot\..\target\java-code-intelligence-mcp-0.1.0-SNAPSHOT.jar" })
)

if (-not $env:JAVA_HOME) {
    throw "JAVA_HOME must point to JDK 21"
}
& "$env:JAVA_HOME\bin\java.exe" -jar (Resolve-Path -LiteralPath $Jar) @args
