param(
  [string]$TomcatHome = 'C:/Users/ASUS/Downloads/apache-tomcat-10.1.60-windows-x64/apache-tomcat-10.1.60',
  [string]$JavaHome = 'C:/Program Files/Java/jdk-17',
  [switch]$Test
)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
& node (Join-Path $PSScriptRoot 'version-frontend.cjs')
if($LASTEXITCODE -ne 0){throw 'Frontend asset versioning failed'}
$classes=Join-Path $projectRoot 'build/web/WEB-INF/classes'
$testClasses=Join-Path $projectRoot 'build/test-classes'
New-Item -ItemType Directory -Force $classes,$testClasses,(Join-Path $projectRoot 'dist') | Out-Null
$sources=Get-ChildItem (Join-Path $projectRoot 'src/java') -Recurse -Filter '*.java' | ForEach-Object FullName
& "$JavaHome/bin/javac.exe" -encoding UTF-8 -cp "$TomcatHome/lib/servlet-api.jar;$(Join-Path $projectRoot 'web/WEB-INF/lib/*')" -d $classes $sources
if($LASTEXITCODE -ne 0){throw 'Java compilation failed'}
if($Test){
  $tests=Get-ChildItem (Join-Path $projectRoot 'test/security'),(Join-Path $projectRoot 'test/service') -Recurse -Filter '*.java' | ForEach-Object FullName
  & "$JavaHome/bin/javac.exe" -encoding UTF-8 -cp "$classes;$TomcatHome/lib/servlet-api.jar;$(Join-Path $projectRoot 'web/WEB-INF/lib/*')" -d $testClasses $tests
  if($LASTEXITCODE -ne 0){throw 'Test compilation failed'}
  & "$JavaHome/bin/java.exe" -cp "$classes;$testClasses;$(Join-Path $projectRoot 'web/WEB-INF/lib/*')" service.Sprint2SelfTest
  if($LASTEXITCODE -ne 0){throw 'Sprint 2 tests failed'}
  & "$JavaHome/bin/java.exe" -cp "$classes;$testClasses;$(Join-Path $projectRoot 'web/WEB-INF/lib/*')" service.TechnologyCatalogSelfTest
  if($LASTEXITCODE -ne 0){throw 'Technology catalog tests failed; apply database/technology-catalog.sql first'}
  & "$JavaHome/bin/java.exe" -cp "$classes;$testClasses;$(Join-Path $projectRoot 'web/WEB-INF/lib/*')" service.SmtpSelfTest
  if($LASTEXITCODE -ne 0){throw 'SMTP tests failed; run database/smtp.sql first'}
  & "$JavaHome/bin/java.exe" -cp "$classes;$testClasses;$(Join-Path $projectRoot 'web/WEB-INF/lib/*')" security.PhanQuyenSelfTest
  if($LASTEXITCODE -ne 0){throw 'Permission tests failed'}
  & "$JavaHome/bin/java.exe" -cp "$classes;$testClasses;$(Join-Path $projectRoot 'web/WEB-INF/lib/*')" service.PriceHistorySelfTest
  if($LASTEXITCODE -ne 0){throw 'S3-02 price history tests failed; apply database/sprint3-price-history.sql first'}
  $frontendTests=Get-ChildItem (Join-Path $projectRoot 'test/frontend') -Filter '*.test.cjs' | ForEach-Object FullName
  & node --test $frontendTests
  if($LASTEXITCODE -ne 0){throw 'Frontend tests failed'}
}
Copy-Item -Path (Join-Path $projectRoot 'web/*') -Destination (Join-Path $projectRoot 'build/web') -Recurse -Force
& "$JavaHome/bin/jar.exe" cf (Join-Path $projectRoot 'dist/QuanLyBanHangKho.war') -C (Join-Path $projectRoot 'build/web') .
if($LASTEXITCODE -ne 0){throw 'WAR packaging failed'}
(Get-Item (Join-Path $projectRoot 'build/web/WEB-INF/web.xml')).LastWriteTime=Get-Date
Write-Output 'Build complete. Tomcat will reload the application when watching build/web.'
