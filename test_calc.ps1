# test_calc.ps1
# Automação de testes para a API /calc/op e /calc/expr usando curl.exe
# Uso: powershell -ExecutionPolicy Bypass -File .\test_calc.ps1
param(
  [string]$BaseUrl = "http://localhost:8000"
)

# Verifica se curl.exe está disponível
if ($null -eq (Get-Command curl.exe -ErrorAction SilentlyContinue)) {
  Write-Host "ERRO: curl.exe não encontrado no PATH. Instale o curl ou use Git Bash / WSL." -ForegroundColor Red
  exit 2
}

# Test cases: cada entrada tem name, path, body e string esperada (expect)
$tests = @(
  @{ name='op-add';        path='/calc/op';   body='{"id":"t1","op":"add","a":3,"b":4}';            expect='"result":7' },
  @{ name='op-sub';        path='/calc/op';   body='{"id":"t2","op":"sub","a":10,"b":5}';           expect='"result":5' },
  @{ name='op-mul';        path='/calc/op';   body='{"id":"t3","op":"mul","a":6,"b":7}';            expect='"result":42' },
  @{ name='op-div';        path='/calc/op';   body='{"id":"t4","op":"div","a":20,"b":4}';          expect='"result":5' },
  @{ name='op-divzero';    path='/calc/op';   body='{"id":"t5","op":"div","a":1,"b":0}';           expect='"error":' },
  @{ name='expr-1';        path='/calc/expr'; body='{"id":"t6","expr":"(2+3)*4/2 - 1"}';           expect='"result":9' },
  @{ name='expr-2';        path='/calc/expr'; body='{"id":"t7","expr":"(10 + 5) * 5"}';            expect='"result":75' },
  @{ name='expr-invalid';  path='/calc/expr'; body='{"id":"t8","expr":"2 + %% 3"}';                 expect='"error":' },
  @{ name='malformed-json';path='/calc/op';   body='{"id":"t9","op": "add", "a": 1, "b": }';       expect='"error":' }
)

$results = @()
$anyFail = $false

Write-Host "Running tests against $BaseUrl`n"

foreach ($t in $tests) {
  $name = $t.name
  $path = $t.path
  $body = $t.body
  $expect = $t.expect

  Write-Host "=> Testing $name ..."

  # Arquivos temporários
  $bodyFile = ".\body_$name.json"
  $respFile = ".\resp_$name.txt"

  # Cria arquivo de corpo (UTF8)
  Set-Content -Path $bodyFile -Value $body -Encoding UTF8

  # Executa curl: grava resposta em $respFile e status HTTP na variável $status
  $fullUrl = "$BaseUrl$path"
  $status = ""
  $curlCmd = @(
    "-s", "-S",               # silencioso (mas mostra erros)
    "-o", $respFile,          # escreve corpo na resposta
    "-w", "%{http_code}",     # retorna o http code no stdout
    "-X", "POST",
    "-H", "Content-Type: application/json",
    "--data-binary", "@$bodyFile",
    $fullUrl
  )

  try {
    $status = & curl.exe @curlCmd
    $lastExit = $LASTEXITCODE
  } catch {
    $status = ""
    $lastExit = $LASTEXITCODE
  }

  # Lê o corpo (se existir)
  $respBody = ""
  if (Test-Path $respFile) {
    $respBody = Get-Content $respFile -Raw -ErrorAction SilentlyContinue
  }

  # Se curl falhou por conexão, marque falha com status 0
  if ($status -eq "" -or -not ($status -match '^\d{3}$')) {
    $httpStatus = 0
  } else {
    $httpStatus = [int]$status
  }

  # Avalia se passou: verifica se o body contém a string esperada
  $ok = $false
  if ($null -ne $respBody -and $respBody -ne "") {
    if ($expect) {
      if ($respBody -like "*$expect*") {
        $ok = $true
      } else {
        $ok = $false
      }
    } else {
      # se não tiver expect, considera sucesso se status é 2xx
      if ($httpStatus -ge 200 -and $httpStatus -lt 300) { $ok = $true } else { $ok = $false }
    }
  } else {
    # Se corpo vazio: considerar sucesso somente se status 2xx
    if ($httpStatus -ge 200 -and $httpStatus -lt 300) { $ok = $true } else { $ok = $false }
  }

  if (-not $ok) { $anyFail = $true }

  if ($ok) {
    Write-Host "  [PASS] $name status=$httpStatus"
  } else {
    Write-Host "  [FAIL] $name status=$httpStatus"
    Write-Host "    Body: $respBody"
  }

  $results += [PSCustomObject]@{
    Test = $name
    Path = $path
    RequestBody = $body
    HttpStatus = $httpStatus
    ResponseBody = $respBody
    Passed = $ok
    CurlExit = $lastExit
  }

  # Remove arquivos temporários
  Remove-Item -Path $bodyFile -ErrorAction SilentlyContinue
  Remove-Item -Path $respFile -ErrorAction SilentlyContinue
}

# Salva CSV com resultados
$results | Export-Csv -Path "test-results.csv" -NoTypeInformation -Encoding UTF8

Write-Host "`nSummary:"
$passed = ($results | Where-Object { $_.Passed }).Count
$total  = $results.Count
Write-Host "  Passed: $passed / $total"
Write-Host "  CSV: test-results.csv"

if ($anyFail) {
  Write-Host "`nAlguns testes falharam." -ForegroundColor Yellow
  exit 1
} else {
  Write-Host "`nTodos os testes passaram." -ForegroundColor Green
  exit 0
}