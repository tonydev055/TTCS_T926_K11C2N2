param(
  [Parameter(Mandatory=$true)][string]$Recipient,
  [switch]$PrepareOnly,
  [string]$JavaHome='C:/Program Files/Java/jdk-17',
  [string]$TomcatHome='C:/Users/ASUS/Downloads/apache-tomcat-10.1.60-windows-x64/apache-tomcat-10.1.60'
)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$Recipient=$Recipient.Trim()
if($Recipient -notmatch '^[^\s@]+@[^\s@]+\.[^\s@]+$'){throw 'Email nhận không hợp lệ'}
$output=Join-Path $projectRoot 'build/s7-live-mail'
New-Item -ItemType Directory -Force $output | Out-Null
$stateFile=Join-Path $output 'state.json'
$manifest=Join-Path $output 'migrations.txt'
$libraries="$TomcatHome/lib/servlet-api.jar;$(Join-Path $projectRoot 'web/WEB-INF/lib/*')"
$sources=@(Get-ChildItem (Join-Path $projectRoot 'src/java') -Recurse -Filter '*.java' | ForEach-Object FullName)
& "$JavaHome/bin/javac.exe" -encoding UTF-8 -cp $libraries -d $output $sources (Join-Path $projectRoot 'test/service/ReminderLiveCheck.java')
if($LASTEXITCODE -ne 0){throw 'Không biên dịch được S7-08; hãy dùng đúng nhánh task S7-08'}
$classPath="$output;$libraries"
function Invoke-S7MailCheck([string]$Action,[string]$Database){
  & "$JavaHome/bin/java.exe" '-Dfile.encoding=UTF-8' -cp $classPath service.ReminderLiveCheck $Action $Database $Recipient $manifest
  if($LASTEXITCODE -ne 0){throw "Kiểm tra S7-08 dừng ở bước $Action"}
}
$saved=@{}
foreach($key in @('DB_URL','MAIL_MODE','SMTP_USERNAME','SMTP_APP_PASSWORD')){$saved[$key]=[Environment]::GetEnvironmentVariable($key,'Process')}
$appSecret=$null;$secretPointer=[IntPtr]::Zero
Push-Location $projectRoot
try{
  if(Test-Path -LiteralPath $stateFile){
    $state=Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
    if($state.recipient -ne $Recipient){throw 'Database kiểm thử này dành cho email khác; không tự đổi người nhận'}
  }else{
    $database='khoflow_s7_test_'+(Get-Date -Format yyyyMMddHHmmss)
    $source=Get-Content (Join-Path $projectRoot 'test/service/S7TestDatabase.java') -Raw
    $migrations=[regex]::Matches($source,'"([a-z0-9-]+\.sql)"') | ForEach-Object {$_.Groups[1].Value}
    if($migrations -notcontains 'sprint7-payment-reminders.sql'){throw 'Nhánh hiện tại chưa có migration S7-08'}
    [IO.File]::WriteAllLines($manifest,[string[]]$migrations)
    Invoke-S7MailCheck 'create' $database
    $baseUrl=if($env:DB_URL){$env:DB_URL}else{'jdbc:postgresql://localhost:5432/quanlybanhangkho'}
    if($baseUrl -notmatch '^jdbc:postgresql://[^/]+/[^?]+$'){throw 'Cần DB_URL PostgreSQL chuẩn, không kèm tham số'}
    $env:DB_URL=$baseUrl.Substring(0,$baseUrl.LastIndexOf('/')+1)+$database
    Invoke-S7MailCheck 'seed' $database
    $state=@{database=$database;url=$env:DB_URL;recipient=$Recipient}
    $state | ConvertTo-Json | Set-Content -LiteralPath $stateFile -Encoding utf8
  }
  if($state.database -notmatch '^khoflow_s7_test_[0-9]{14}$'){throw 'Tên database không an toàn'}
  $env:DB_URL=$state.url
  Invoke-S7MailCheck 'preview' $state.database
  Write-Output "Dữ liệu nằm riêng tại $($state.database); không đổi database Tomcat đang chạy."
  if($PrepareOnly){Write-Output 'Đã chuẩn bị dữ liệu. Chưa gửi email.';return}
  Write-Output "Lượt chạy này gửi tối đa 2 thư KIỂM THỬ S7-08 đến $Recipient qua SMTP."
  $env:SMTP_USERNAME=(Read-Host 'Gmail gửi thư (tài khoản SMTP đã dùng)').Trim()
  if($env:SMTP_USERNAME -notmatch '^[^\s@]+@[^\s@]+\.[^\s@]+$'){throw 'Gmail gửi không hợp lệ'}
  $appSecret=Read-Host 'App Password (nhập ẩn; không lưu ra file)' -AsSecureString
  $secretPointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($appSecret)
  $env:SMTP_APP_PASSWORD=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($secretPointer).Replace(' ','')
  $env:MAIL_MODE='smtp'
  Invoke-S7MailCheck 'send' $state.database
}finally{
  if($secretPointer -ne [IntPtr]::Zero){[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($secretPointer)}
  if($null -ne $appSecret){$appSecret.Dispose()}
  foreach($key in $saved.Keys){[Environment]::SetEnvironmentVariable($key,$saved[$key],'Process')}
  Pop-Location
}
