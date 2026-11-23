param(
    [string]$SdkRoot = $env:ANDROID_HOME,
    [string]$SigningDirectory = (Join-Path $env:LOCALAPPDATA 'ShortVideoBlocker\signing'),
    [string]$Gradle = (Join-Path $PSScriptRoot 'gradlew.bat')
)

$ErrorActionPreference = 'Stop'

function Invoke-Tool([string]$Executable, [string[]]$Arguments) {
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$(Split-Path -Leaf $Executable) failed with exit code $LASTEXITCODE."
    }
}

if (-not $SdkRoot -or -not $env:JAVA_HOME) {
    throw 'Set ANDROID_HOME and JAVA_HOME to the Android SDK and JDK 17 directories.'
}

$buildTools = Join-Path $SdkRoot 'build-tools\35.0.0'
$zipalign = Join-Path $buildTools 'zipalign.exe'
$apksigner = Join-Path $buildTools 'apksigner.bat'
$keytool = Join-Path $env:JAVA_HOME 'bin\keytool.exe'
$publish = Join-Path $PSScriptRoot 'dist'
New-Item -ItemType Directory -Path $publish, $SigningDirectory -Force | Out-Null

Push-Location $PSScriptRoot
try {
    Invoke-Tool $Gradle @('--console=plain', ':app:assembleAlwaysRelease', ':app:assembleToggleRelease')
    foreach ($edition in @('always', 'toggle')) {
        $key = Join-Path $SigningDirectory "$edition.p12"
        $passwordFile = Join-Path $SigningDirectory "$edition.dpapi"
        $hasKey = Test-Path -LiteralPath $key
        $hasPassword = Test-Path -LiteralPath $passwordFile
        if ($hasKey -ne $hasPassword) {
            throw "Restore the missing signing file for $edition before building that APK."
        }

        if (-not $hasKey) {
            $random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
            $bytes = New-Object byte[] 32
            try {
                $random.GetBytes($bytes)
                $env:SHORT_VIDEO_SIGN_PASSWORD = [Convert]::ToBase64String($bytes)
            } finally {
                $random.Dispose()
            }
            Invoke-Tool $keytool @('-genkeypair', '-keystore', $key, '-storetype', 'PKCS12',
                '-storepass:env', 'SHORT_VIDEO_SIGN_PASSWORD', '-keypass:env', 'SHORT_VIDEO_SIGN_PASSWORD',
                '-alias', $edition, '-keyalg', 'RSA', '-keysize', '3072', '-validity', '10000',
                '-dname', "CN=CrazyFocus $edition")
            ConvertTo-SecureString $env:SHORT_VIDEO_SIGN_PASSWORD -AsPlainText -Force |
                ConvertFrom-SecureString | Set-Content -LiteralPath $passwordFile -Encoding ASCII
        } else {
            $encrypted = (Get-Content -LiteralPath $passwordFile -Raw).Trim()
            $secure = ConvertTo-SecureString $encrypted
            $env:SHORT_VIDEO_SIGN_PASSWORD = [System.Net.NetworkCredential]::new('', $secure).Password
        }

        $unsigned = "app\build\outputs\apk\$edition\release\app-$edition-release-unsigned.apk"
        $aligned = "app\build\outputs\apk\$edition\release\app-$edition-release-aligned.apk"
        $verified = "app\build\outputs\apk\$edition\release\app-$edition-release-signed.apk"
        $apk = Join-Path $publish "CrazyFocus-$edition.apk"
        Invoke-Tool $zipalign @('-f', '4', $unsigned, $aligned)
        Invoke-Tool $apksigner @('sign', '--ks', $key, '--ks-key-alias', $edition,
            '--ks-pass', 'env:SHORT_VIDEO_SIGN_PASSWORD', '--key-pass', 'env:SHORT_VIDEO_SIGN_PASSWORD',
            '--v4-signing-enabled', 'false', '--out', $verified, $aligned)
        Invoke-Tool $apksigner @('verify', '--verbose', '--print-certs', $verified)
        Copy-Item -LiteralPath $verified -Destination $apk -Force
        Get-FileHash -LiteralPath $apk -Algorithm SHA256
        Remove-Item Env:SHORT_VIDEO_SIGN_PASSWORD
    }
} finally {
    Remove-Item Env:SHORT_VIDEO_SIGN_PASSWORD -ErrorAction SilentlyContinue
    Pop-Location
}
