param(
  [string]$TomcatHome='C:/Users/ASUS/Downloads/apache-tomcat-10.1.60-windows-x64/apache-tomcat-10.1.60',
  [string]$JavaHome='C:/Program Files/Java/jdk-17',
  [string]$AppUrl='http://localhost:8080/QuanLyBanHangKho/'
)
$ErrorActionPreference='Stop'
if(!(Test-Path "$TomcatHome/bin/catalina.bat")){throw 'Không tìm thấy Tomcat'}
$gmailAddress=Read-Host 'Gmail gửi thư (ví dụ tenban@gmail.com)'
if($gmailAddress -notmatch '^[^\s@]+@[^\s@]+\.[^\s@]+$'){throw 'Email không hợp lệ'}
$appSecret=Read-Host 'App Password của Google (không phải mật khẩu đăng nhập Gmail)' -AsSecureString
$secretPointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($appSecret)
$savedEnvironment=@{}
foreach($key in @('MAIL_MODE','SMTP_USERNAME','SMTP_APP_PASSWORD','APP_URL','JAVA_HOME','CATALINA_HOME','CATALINA_BASE')) {
  $savedEnvironment[$key]=[Environment]::GetEnvironmentVariable($key,'Process')
}
try {
  $env:MAIL_MODE='smtp'
  $env:SMTP_USERNAME=$gmailAddress
  $env:SMTP_APP_PASSWORD=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($secretPointer).Replace(' ','')
  if([string]::IsNullOrWhiteSpace($env:SMTP_APP_PASSWORD)){throw 'Chưa nhập App Password'}
  $env:APP_URL=$AppUrl
  $env:JAVA_HOME=$JavaHome
  $env:CATALINA_HOME=$TomcatHome
  $env:CATALINA_BASE=$TomcatHome
  Start-Process -FilePath "$TomcatHome/bin/catalina.bat" -ArgumentList 'run' -WorkingDirectory $TomcatHome -WindowStyle Hidden
  Write-Output 'Đã khởi chạy Tomcat với Gmail SMTP. Kiểm tra log Tomcat và trạng thái mail_outbox để xác nhận gửi thư.'
} finally {
  [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($secretPointer)
  $appSecret.Dispose()
  foreach($key in $savedEnvironment.Keys){[Environment]::SetEnvironmentVariable($key,$savedEnvironment[$key],'Process')}
}
