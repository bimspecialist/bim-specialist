# Run locally in Windows PowerShell 5.1 or 7.
# Never commit or share this private keystore.
$ErrorActionPreference = "Stop"
$folder = Join-Path $HOME "BIMMeasure3D-keys"
$keystore = Join-Path $folder "BIMMeasure3D-signing.p12"
New-Item -ItemType Directory -Path $folder -Force | Out-Null
if (Test-Path $keystore) {
    throw "Signing keystore already exists at $keystore. Reuse it; do not replace it or installed app updates will fail."
}
if (-not (Get-Command keytool -ErrorAction SilentlyContinue)) {
    throw "keytool not found. Install JDK 21 and open a new PowerShell window."
}
Write-Host "Creating a long-lived PKCS12 Android signing key. Enter a strong password when prompted."
& keytool -genkeypair -keystore "$keystore" -storetype PKCS12 -alias bim-measure-3d -keyalg RSA -keysize 3072 -validity 10950 -dname "CN=BIM Measure 3D,O=BIM Specialist,C=SA"
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $keystore)) {
    throw "Keystore generation failed."
}
Write-Host "Your private keystore: $keystore"
Write-Host "Back it up in an encrypted offline location. The app requires the same signing key for every future update."
Write-Host "SHA-256 public certificate fingerprint:"
& keytool -list -v -keystore "$keystore" -alias bim-measure-3d
Write-Host "To copy the Base64 keystore to clipboard for the GitHub secret:"
Write-Host '[Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\BIMMeasure3D-keys\BIMMeasure3D-signing.p12")) | Set-Clipboard'
Write-Host "After pasting in GitHub Actions secret, run: Set-Clipboard -Value ''"
