param(
    [string]$OutputRoot = "$PSScriptRoot\..\dist",
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath "$PSScriptRoot\..").Path
$version = '0.1.0-SNAPSHOT'
$name = "java-code-intelligence-mcp-$version"
$packageDir = Join-Path $OutputRoot $name

if (-not $SkipBuild) {
    Push-Location $projectRoot
    try {
        & mvn -q clean package
        if ($LASTEXITCODE -ne 0) { throw "Maven build failed with exit code $LASTEXITCODE" }
    } finally { Pop-Location }
}

$jarPath = Join-Path $projectRoot "target\$name.jar"
if (-not (Test-Path -LiteralPath $jarPath)) { throw "Build artifact not found: $jarPath" }

if (Test-Path -LiteralPath $packageDir) { Remove-Item -LiteralPath $packageDir -Recurse -Force }
New-Item -ItemType Directory -Path "$packageDir\bin", "$packageDir\config" -Force | Out-Null
Copy-Item -LiteralPath $jarPath -Destination "$packageDir\java-code-intelligence-mcp.jar"
Copy-Item -LiteralPath "$projectRoot\scripts\run.ps1" -Destination "$packageDir\bin\run.ps1"
Copy-Item -LiteralPath "$projectRoot\scripts\run.sh" -Destination "$packageDir\bin\run.sh"
Copy-Item -LiteralPath "$projectRoot\README.md", "$projectRoot\MCP_API.md", "$projectRoot\AI_AGENT_GUIDE.md", "$projectRoot\THIRD_PARTY_LICENSES.md" -Destination $packageDir
Copy-Item -LiteralPath "$projectRoot\config\claude-code.json.example" -Destination "$packageDir\config"

$zip = Join-Path $OutputRoot "$name.zip"
if (Test-Path -LiteralPath $zip) { Remove-Item -LiteralPath $zip -Force }
Compress-Archive -LiteralPath $packageDir -DestinationPath $zip
Write-Host "Created $zip"
