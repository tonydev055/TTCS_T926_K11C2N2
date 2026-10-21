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
if(-not $JavaHome){throw 'Truyền -JavaHome tới JDK 17 hoặc cấu hình JAVA_HOME.'}
foreach($taskFile in @("$JavaHome/bin/java.exe","$JavaHome/bin/javac.exe","$TomcatHome/lib/servlet-api.jar")){
    if(-not (Test-Path -LiteralPath $taskFile)){throw "Không tìm thấy: $taskFile"}
}
$taskNames=@('DB_URL','DB_USER','DB_PASSWORD','LECTURER_DATABASE','LECTURER_JDBC_BASE','MAIL_MODE')
$taskSaved=@{}
foreach($taskName in $taskNames){$taskSaved[$taskName]=[Environment]::GetEnvironmentVariable($taskName,'Process')}
Push-Location $taskProject
try {
    if(-not $env:DB_PASSWORD){
        $taskSecret=Read-Host 'Mật khẩu PostgreSQL (không ghi vào file)' -AsSecureString
        $env:DB_PASSWORD=[Net.NetworkCredential]::new('', $taskSecret).Password
    }
    $env:DB_USER=$DatabaseUser
    $env:LECTURER_DATABASE=$DatabaseName
    $env:LECTURER_JDBC_BASE="jdbc:postgresql://${DatabaseHost}:${DatabasePort}"
    $env:DB_URL="$env:LECTURER_JDBC_BASE/$DatabaseName"
    $env:MAIL_MODE='dev'
    & "$PSScriptRoot/build-sprint2.ps1" -JavaHome $JavaHome -TomcatHome $TomcatHome
    if($LASTEXITCODE -ne 0){throw 'Build thất bại'}
    $taskClasses=Join-Path $taskProject 'build/demo-classes'
    New-Item -ItemType Directory -Force $taskClasses | Out-Null
    $taskCp="build/web/WEB-INF/classes;web/WEB-INF/lib/*;$TomcatHome/lib/servlet-api.jar"
    & "$JavaHome/bin/javac.exe" -encoding UTF-8 -cp $taskCp -d $taskClasses scripts/SetupLecturerDemo.java
    if($LASTEXITCODE -ne 0){throw 'Biên dịch bộ demo thất bại'}
    $taskProofs=Join-Path $taskProject "build/lecturer-demo/$DatabaseName/delivery-proofs"
    & "$JavaHome/bin/java.exe" '-Dfile.encoding=UTF-8' "-Dkhoflow.deliveryProofDir=$taskProofs" -cp "$taskClasses;$taskCp" SetupLecturerDemo
    if($LASTEXITCODE -ne 0){throw "Khởi tạo chưa hoàn tất. Database $DatabaseName được giữ để chẩn đoán; chọn tên demo mới khi chạy lại."}
    Write-Host "Đã tạo và xác minh database $DatabaseName. Xem LECTURER_DEMO.md để chạy Tomcat với database này."
} finally {
    foreach($taskName in $taskNames){[Environment]::SetEnvironmentVariable($taskName,$taskSaved[$taskName],'Process')}
    Pop-Location
}
