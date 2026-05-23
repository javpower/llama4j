# llama4j 原生库编译脚本 (Windows)
#
# 用法:
#   .\scripts\build-native.ps1 -Classifier windows-x86_64 -Gpu cuda
#   .\scripts\build-native.ps1 -Classifier windows-x86_64 -Gpu cpu

param(
    [Parameter(Mandatory=$true)]
    [ValidateSet("windows-x86_64", "windows-aarch64")]
    [string]$Classifier,

    [Parameter(Mandatory=$true)]
    [ValidateSet("cuda", "cpu")]
    [string]$Gpu,

    [string]$OutputDir = "",

    [string]$LlamaCppDir = "C:\llama.cpp",

    [int]$Jobs = 0
)

$ErrorActionPreference = "Stop"
$ProjectDir = Resolve-Path (Join-Path $PSScriptRoot "..")

if ($Jobs -eq 0) { $Jobs = [Environment]::ProcessorCount }
if ($OutputDir -eq "") {
    $OutputDir = Join-Path $ProjectDir "llama4j-native\src\main\resources\native\$Classifier"
}

Write-Host "=========================================="
Write-Host " llama4j Native Library Build (Windows)"
Write-Host "=========================================="
Write-Host " Platform:    $Classifier"
Write-Host " GPU:         $Gpu"
Write-Host " Output:      $OutputDir"
Write-Host " Jobs:        $Jobs"
Write-Host "=========================================="

# Step 1: Clone/update llama.cpp
if (-not (Test-Path "$LlamaCppDir\.git")) {
    Write-Host "[1/4] Cloning llama.cpp..."
    git clone --depth 1 https://github.com/ggerganov/llama.cpp.git $LlamaCppDir
} else {
    Write-Host "[1/4] Updating llama.cpp..."
    Push-Location $LlamaCppDir
    git fetch --depth 1 origin master
    git reset --hard origin/master
    Pop-Location
}

# Step 2: Build llama.cpp
Write-Host "[2/4] Building llama.cpp..."

$cmakeArgs = @(
    "-B", "$LlamaCppDir\build",
    "-S", $LlamaCppDir,
    "-DCMAKE_BUILD_TYPE=Release",
    "-DBUILD_SHARED_LIBS=ON",
    "-DLLAMA_BUILD_TESTS=OFF",
    "-DLLAMA_BUILD_EXAMPLES=OFF",
    "-DLLAMA_BUILD_TOOLS=ON",
    "-DLLAMA_BUILD_SERVER=OFF",
    "-DLLAMA_BUILD_COMMON=ON"
)

if ($Gpu -eq "cuda") {
    $cmakeArgs += "-DGGML_CUDA=ON"
}

& cmake @cmakeArgs
& cmake --build "$LlamaCppDir\build" --config Release --parallel $Jobs

# Step 3: Compile JNI bridge
Write-Host "[3/4] Compiling JNI bridge..."

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

$binDir = Join-Path $LlamaCppDir "build\bin\Release"
if (-not (Test-Path $binDir)) { $binDir = Join-Path $LlamaCppDir "build\bin" }

# Copy llama.cpp DLLs
Copy-Item "$binDir\ggml*.dll" $OutputDir -ErrorAction SilentlyContinue
Copy-Item "$binDir\llama*.dll" $OutputDir -ErrorAction SilentlyContinue
Copy-Item "$binDir\mtmd*.dll" $OutputDir -ErrorAction SilentlyContinue

$javaHome = $env:JAVA_HOME
if (-not $javaHome) { $javaHome = (Get-Command java).Source | Split-Path | Split-Path }

# Ensure JAVA_HOME env var is set (CMake FindJNI and our CMakeLists.txt both use it)
$env:JAVA_HOME = $javaHome
Write-Host "JAVA_HOME: $javaHome"

# Use CMake to build JNI bridge
$jniBuildDir = Join-Path $ProjectDir "llama4j-native\src\main\c++\build"
New-Item -ItemType Directory -Force -Path $jniBuildDir | Out-Null

& cmake -B $jniBuildDir -S "$ProjectDir\llama4j-native\src\main\c++" `
    "-DLLAMA_CPP_ROOT=$LlamaCppDir" `
    "-DJAVA_HOME=$javaHome"
if ($LASTEXITCODE -ne 0) { throw "CMake configure failed with exit code $LASTEXITCODE" }

& cmake --build $jniBuildDir --config Release
if ($LASTEXITCODE -ne 0) { throw "CMake build failed with exit code $LASTEXITCODE" }

# Copy JNI bridge DLL
Copy-Item "$jniBuildDir\Release\llama4j.dll" $OutputDir -ErrorAction SilentlyContinue
Copy-Item "$jniBuildDir\llama4j.dll" $OutputDir -ErrorAction SilentlyContinue

# Step 4: Verify
Write-Host "[4/4] Verifying output..."
Get-ChildItem $OutputDir | Format-Table Name, Length
Write-Host "Build complete!"
