param([switch]$Release)
$ErrorActionPreference='Stop'
Push-Location $PSScriptRoot
try {
 if(-not $env:JAVA_HOME -and (Test-Path -LiteralPath 'C:/Program Files/Android/Android Studio/jbr')){$env:JAVA_HOME='C:/Program Files/Android/Android Studio/jbr'}
 $assetRoot=Join-Path $PSScriptRoot 'app/src/main/assets'
 New-Item -ItemType Directory -Force -Path $assetRoot | Out-Null
 Copy-Item -Path (Join-Path $PSScriptRoot 'ui/*') -Destination $assetRoot -Recurse -Force
 if($Release){
  if(-not $env:JAMPLAYER_KEYSTORE_PATH -or -not $env:AMPLAYER_STORE_PASSWORD -or -not $env:AMPLAYER_KEY_PASSWORD){throw 'Release signing requires JAMPLAYER_KEYSTORE_PATH, AMPLAYER_STORE_PASSWORD and AMPLAYER_KEY_PASSWORD.'}
  & ./gradlew.bat assembleRelease lintRelease --no-daemon --console plain
 }else{& ./gradlew.bat assembleDebug lintDebug --no-daemon --console plain}
 if($LASTEXITCODE -ne 0){throw 'Android build/check failed.'}
} finally {Pop-Location}
