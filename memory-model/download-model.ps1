$ErrorActionPreference = 'Stop'

$artifacts = @(
    @{
        Uri = 'https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2/resolve/main/onnx/model_quint8_avx2.onnx'
        Path = Join-Path $PSScriptRoot 'model.onnx'
    },
    @{
        Uri = 'https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2/resolve/main/tokenizer.json'
        Path = Join-Path $PSScriptRoot 'tokenizer.json'
    }
)

foreach ($artifact in $artifacts) {
    if (Test-Path $artifact.Path) {
        Write-Host "Keeping existing file: $($artifact.Path)"
        continue
    }

    $partialPath = "$($artifact.Path).download"
    try {
        Write-Host "Downloading $($artifact.Uri)"
        Invoke-WebRequest -Uri $artifact.Uri -OutFile $partialPath
        Move-Item -Path $partialPath -Destination $artifact.Path
    }
    finally {
        if (Test-Path $partialPath) {
            Remove-Item -Path $partialPath -Force
        }
    }
}