param(
  [string]$Psql = 'C:/Program Files/PostgreSQL/18/bin/psql.exe',
  [string]$JavaHome = 'C:/Program Files/Java/jdk-17',
  [string]$Database = 'quanlybanhangkho',
  [string]$DatabaseUser = 'postgres',
  [string]$DatabaseHost = 'localhost'
)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$previousPrefix=$env:QA_PREFIX
$env:QA_PREFIX='qa'+[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$seedFile=Join-Path $PSScriptRoot 'seed.sql'
$classes=Join-Path $projectRoot 'build/web/WEB-INF/classes'
$testClasses=Join-Path $projectRoot 'build/test-classes'
$hash=[Convert]::ToBase64String([System.Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes('Demo@123')))
$prefix=$env:QA_PREFIX
@"
BEGIN;
INSERT INTO users(username,email,full_name,password_hash) SELECT '$prefix'||code,'$prefix'||code||'@example.test','QA retest '||code,'$hash' FROM roles;
INSERT INTO user_roles(user_id,role_id) SELECT u.id,r.id FROM users u JOIN roles r ON u.username='$prefix'||r.code;
INSERT INTO user_warehouses(user_id,warehouse_id) SELECT u.id,w.id FROM users u CROSS JOIN warehouses w WHERE u.username IN ('${prefix}WAREHOUSE','${prefix}WH_MANAGER') AND w.code='HCM';
COMMIT;
"@ | Set-Content $seedFile
try {
  & $Psql -h $DatabaseHost -U $DatabaseUser -d $Database -v ON_ERROR_STOP=1 -f $seedFile
  if($LASTEXITCODE -ne 0){throw 'Could not seed isolated QA accounts'}
  & "$JavaHome/bin/javac.exe" -encoding UTF-8 -cp $classes -d $testClasses (Join-Path $PSScriptRoot 'GenerateFixtures.java')
  if($LASTEXITCODE -ne 0){throw 'Fixture compilation failed'}
  & "$JavaHome/bin/java.exe" -cp "$classes;$testClasses" GenerateFixtures $PSScriptRoot $prefix
  if($LASTEXITCODE -ne 0){throw 'Fixture generation failed'}
  & node --test (Join-Path $PSScriptRoot 'regression-*.test.cjs')
  if($LASTEXITCODE -ne 0){throw 'Regression tests failed'}
  foreach($suite in @('api-retest','import-retest')) {
    & node (Join-Path $PSScriptRoot "$suite.cjs")
    if($LASTEXITCODE -ne 0){throw "$suite failed"}
    $result=Get-Content -Raw (Join-Path $projectRoot "reports/$suite-results.json") | ConvertFrom-Json
    if(@($result.results | Where-Object status -eq 'FAIL').Count){throw "$suite contains failed checks"}
  }
} finally {
  & $Psql -h $DatabaseHost -U $DatabaseUser -d $Database -v "prefix=$prefix" -f (Join-Path $PSScriptRoot 'cleanup.sql')
  $env:QA_PREFIX=$previousPrefix
  if($LASTEXITCODE -ne 0){throw 'QA cleanup failed; inspect fixture prefix printed above'}
}
