param([string]$Sdk="$env:LOCALAPPDATA\Android\Sdk",[string]$Jdk='C:\Program Files\Android\Android Studio\jbr')
$ErrorActionPreference='Stop'
Set-Location $PSScriptRoot
$bt=Join-Path $Sdk 'build-tools\35.0.0'
$jar=Join-Path $Sdk 'platforms\android-35\android.jar'
$env:JAVA_HOME=$Jdk
New-Item -ItemType Directory -Force build,build\classes | Out-Null
function Check { if($LASTEXITCODE -ne 0){throw "Build failed: $LASTEXITCODE"} }
& "$bt\aapt.exe" package -f -M AndroidManifest.xml -S res -I $jar -F build\base.apk; Check
& "$Jdk\bin\javac.exe" -encoding UTF-8 --release 8 -classpath $jar -d build\classes ((Get-ChildItem src -Recurse -Filter *.java).FullName); Check
$classes=(Get-ChildItem build\classes -Recurse -Filter *.class).FullName
& "$bt\d8.bat" --lib $jar --min-api 26 --output build $classes; Check
Copy-Item build\base.apk build\unsigned.apk -Force
Push-Location build
& "$Jdk\bin\jar.exe" uf unsigned.apk classes.dex; Check
Pop-Location
& "$bt\zipalign.exe" -f -p 4 build\unsigned.apk build\aligned.apk; Check
if(!(Test-Path build\local.jks)){
 & "$Jdk\bin\keytool.exe" -genkeypair -keystore build\local.jks -storepass android -keypass android -alias local -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=MTProxy Local Build'; Check
}
& "$bt\apksigner.bat" sign --ks build\local.jks --ks-pass pass:android --key-pass pass:android --out build\MTProxy-Picker.apk build\aligned.apk; Check
& "$bt\apksigner.bat" verify --verbose build\MTProxy-Picker.apk; Check

