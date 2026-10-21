param(
    [ValidatePattern('^khoflow_demo(?:_[a-z0-9_]+)?$')][string]$DatabaseName='khoflow_demo',
    [ValidatePattern('^[a-zA-Z0-9.-]+$')][string]$DatabaseHost='localhost',
    [ValidateRange(1,65535)][int]$DatabasePort=5432,
    [string]$DatabaseUser='postgres',
    [string]$JavaHome=$env:JAVA_HOME,
    [Parameter(Mandatory=$true)][string]$TomcatHome
)
$ErrorActionPreference='Stop'
$taskProject=Split-Path -Parent $PSScriptRoot
if(-not (Test-Path -LiteralPath "$JavaHome/bin/java.exe")){throw 'Cần -JavaHome tới JDK 17 hoặc JAVA_HOME.'}
if(-not (Test-Path -LiteralPath "$TomcatHome/bin/catalina.bat")){throw 'Cần Tomcat 10.1 cho Windows.'}
if(-not (Test-Path -LiteralPath "$taskProject/dist/QuanLyBanHangKho.war")){throw 'Chạy setup-lecturer-demo.ps1 trước.'}
$taskNames=@('DB_URL','DB_USER','DB_PASSWORD','MAIL_MODE','JAVA_HOME','CATALINA_HOME','CATALINA_BASE','CATALINA_OPTS','LECTURER_DATABASE','LECTURER_JDBC_BASE')
$taskSaved=@{}
foreach($taskName in $taskNames){$taskSaved[$taskName]=[Environment]::GetEnvironmentVariable($taskName,'Process')}
try {
    if(-not $env:DB_PASSWORD){$taskSecret=Read-Host 'Mật khẩu PostgreSQL' -AsSecureString;$env:DB_PASSWORD=[Net.NetworkCredential]::new('', $taskSecret).Password}
    $env:JAVA_HOME=$JavaHome
    $env:CATALINA_HOME=(Resolve-Path -LiteralPath $TomcatHome).Path
    $env:CATALINA_BASE=$env:CATALINA_HOME
    $env:DB_URL="jdbc:postgresql://${DatabaseHost}:${DatabasePort}/$DatabaseName"
    $env:DB_USER=$DatabaseUser
    $env:MAIL_MODE='dev'
    $env:LECTURER_DATABASE=$DatabaseName
    $env:LECTURER_JDBC_BASE="jdbc:postgresql://${DatabaseHost}:${DatabasePort}"
    $taskCp="$taskProject/build/demo-classes;$taskProject/build/web/WEB-INF/classes;$taskProject/web/WEB-INF/lib/*;$TomcatHome/lib/servlet-api.jar"
    & "$JavaHome/bin/java.exe" '-Dfile.encoding=UTF-8' -cp $taskCp SetupLecturerDemo --check-ready
    if($LASTEXITCODE -ne 0){throw 'Database demo chưa sẵn sàng. Chạy setup-lecturer-demo.ps1 trước.'}
    $taskProofs=Join-Path $taskProject "build/lecturer-demo/$DatabaseName/delivery-proofs"
    $env:CATALINA_OPTS=($env:CATALINA_OPTS+' -Dkhoflow.deliveryProofDir="'+$taskProofs+'"')
    # Dùng Tomcat riêng cho demo; không thay setenv của server đang vận hành.
    Copy-Item -LiteralPath "$taskProject/dist/QuanLyBanHangKho.war" -Destination "$TomcatHome/webapps/QuanLyBanHangKho.war"
    Write-Host "Database: $DatabaseName; mở http://localhost:8080/QuanLyBanHangKho/ (cổng mặc định Tomcat). Ctrl+C để dừng."
    & "$TomcatHome/bin/catalina.bat" run
    if($LASTEXITCODE -ne 0){throw 'Tomcat khởi động hoặc chạy thất bại; xem thông báo phía trên.'}
} finally {
    foreach($taskName in $taskNames){[Environment]::SetEnvironmentVariable($taskName,$taskSaved[$taskName],'Process')}
}
