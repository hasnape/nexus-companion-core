# Nexus Companion — signature v0.4.29 sur un poste Windows avec Android Studio.
# Ne fonctionne QUE si les fichiers de signature privés sont présents SUR CE POSTE.
# Usage (PowerShell) :
#   .\scripts\sign-nexus-companion-windows.ps1 -PrivateSigningDirectory "$HOME\nexus-signing-private"
# La clé et les mots de passe ne sont jamais envoyés au réseau ou enregistrés sur GitHub.
[CmdletBinding()]
param(
  [Parameter(Mandatory=$true)][string]$PrivateSigningDirectory,
  [string]$OutputDirectory = (Join-Path $HOME "Downloads")
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$source = 'https://emcvqfexhel0ijwf.public.blob.vercel-storage.com/nexus-companion-v0.4.29-unsigned-qugAyGydUZDVEvWPrjzmziOT0Ry034.apk'
$sourceHash = 'bcb349b63f03f6310382832ce699f6c9ee51d35439fcd68597050f454c1f7131'
$expectedCert = '01445cd0f0f56688fba46bad32840ff85fbdee119cddbd2d3a19314d94c472d1'
$keystore = Join-Path $PrivateSigningDirectory "nexus-companion-production.jks"
$credentialFile = Join-Path $PrivateSigningDirectory "nexus-companion-signature-credentials.txt"
$sdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$buildTools = Join-Path $sdk 'build-tools\35.0.0'
$zipalign = Join-Path $buildTools 'zipalign.exe'
$apksigner = Join-Path $buildTools 'apksigner.bat'
$aapt = Join-Path $buildTools 'aapt.exe'
foreach ($item in @($keystore,$credentialFile,$zipalign,$apksigner,$aapt)) {
  if (-not (Test-Path -LiteralPath $item -PathType Leaf)) { throw "Fichier nécessaire introuvable : $item" }
}

$secretText = Get-Content -LiteralPath $credentialFile -Raw -Encoding UTF8
$aliasMatch = [regex]::Match($secretText, '(?m)^Alias\s*:\s*([^\r\n]+)')
$passwordMatch = [regex]::Match($secretText, '(?m)^Mot de passe du keystore et de la clé\s*:\s*([^\r\n]+)')
if (-not ($aliasMatch.Success -and $passwordMatch.Success)) { throw 'Format du fichier privé de signature non reconnu' }
$keyAlias = $aliasMatch.Groups[1].Value.Trim()
$password = $passwordMatch.Groups[1].Value.Trim()
if (-not $keyAlias -or -not $password) { throw 'Informations privées de signature incomplètes' }
$secretText = $null

if (-not (Test-Path -LiteralPath $OutputDirectory -PathType Container)) { throw "Dossier de sortie introuvable : $OutputDirectory" }
$unsigned = Join-Path $OutputDirectory 'nexus-companion-v0.4.29-unsigned.apk'
$aligned = Join-Path $OutputDirectory 'nexus-companion-v0.4.29-aligned.apk'
$signed = Join-Path $OutputDirectory 'nexus-companion-v0.4.29-native-alpha-release.apk'
if (Test-Path -LiteralPath $signed) { throw "Le fichier de sortie existe déjà, opération interrompue : $signed" }
if (Test-Path -LiteralPath $aligned) { throw "Le fichier intermédiaire existe déjà : $aligned" }

if (-not (Test-Path -LiteralPath $unsigned)) {
  Write-Host "Téléchargement de l'APK NON signée depuis Vercel Blob..."
  Invoke-WebRequest -Uri $source -OutFile $unsigned
}
$hash = (Get-FileHash -LiteralPath $unsigned -Algorithm SHA256).Hash.ToLowerInvariant()
if ($hash -ne $sourceHash) { throw "L'empreinte SHA-256 de l'APK source ne correspond pas à la version vérifiée" }

try {
  & $zipalign -f -p 4 $unsigned $aligned
  if ($LASTEXITCODE -ne 0) { throw 'zipalign a échoué' }
  $env:NEXUS_ANDROID_STORE_PASSWORD = $password
  $env:NEXUS_ANDROID_KEY_PASSWORD = $password
  & $apksigner sign --ks $keystore --ks-key-alias $keyAlias --ks-pass env:NEXUS_ANDROID_STORE_PASSWORD --key-pass env:NEXUS_ANDROID_KEY_PASSWORD --out $signed $aligned
  if ($LASTEXITCODE -ne 0) { throw "Signature impossible, vérifier les paramètres privés" }
  $verification = & $apksigner verify --verbose --print-certs $signed
  if ($LASTEXITCODE -ne 0) { throw "La signature de l'APK est invalide" }
  $certificateLine = $verification | Where-Object { $_ -match 'Signer #1 certificate SHA-256 digest:\s*([a-fA-F0-9]+)' } | Select-Object -First 1
  if (-not $certificateLine) { throw "Empreinte du certificat introuvable" }
  $actualCert = ([regex]::Match($certificateLine, 'digest:\s*([a-fA-F0-9]+)')).Groups[1].Value.ToLowerInvariant()
  if ($actualCert -ne $expectedCert) { throw "Certificat différent de la version déjà installée" }
  $badging = (& $aapt dump badging $signed | Select-Object -First 1)
  if (-not ($badging -match "name='fr\.nexuscompanion\.android'" -and $badging -match "versionCode='35'")) {
    throw 'Identifiant Android ou versionCode incorrect'
  }
  & $zipalign -c -p 4 $signed
  if ($LASTEXITCODE -ne 0) { throw 'APK mal alignée' }
  Write-Host "APK signée et vérifiée : $signed"
  Write-Host "Certificat de production identique à la version précédente."
  Write-Host "SHA-256 APK : $((Get-FileHash -LiteralPath $signed -Algorithm SHA256).Hash)"
  Write-Host 'Installer par-dessus Alif existant, SANS désinstaller. Tester ensuite la mémoire et la voix.'
}
catch {
  if (Test-Path -LiteralPath $signed) { Remove-Item -LiteralPath $signed -Force -ErrorAction SilentlyContinue }
  throw
}
finally {
  Remove-Item Env:NEXUS_ANDROID_STORE_PASSWORD -ErrorAction SilentlyContinue
  Remove-Item Env:NEXUS_ANDROID_KEY_PASSWORD -ErrorAction SilentlyContinue
  $password = $null
  $keyAlias = $null
  if (Test-Path -LiteralPath $aligned) { Remove-Item -LiteralPath $aligned -Force -ErrorAction SilentlyContinue }
}
