$ErrorActionPreference = 'Stop'
$versions = @('1.21.1', '1.21.2', '26.1.1', '26.1.2', '26.2', '26.3')
foreach ($version in $versions) {
    Push-Location (Join-Path $PSScriptRoot "versions/$version")
    try {
        & .\gradlew.bat build --console=plain --max-workers=2
        if ($LASTEXITCODE -ne 0) { throw "Build failed: Minecraft $version" }
    } finally {
        Pop-Location
    }
}
