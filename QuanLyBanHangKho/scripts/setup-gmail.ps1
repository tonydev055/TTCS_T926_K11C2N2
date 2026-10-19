param(
  [string]$TomcatHome = 'C:/Users/ASUS/Downloads/apache-tomcat-10.1.60-windows-x64/apache-tomcat-10.1.60',
  [ValidateSet('smtp','dev')]
  [string]$Mode = 'smtp',
  [string]$AppUrl = 'http://localhost:8080/QuanLyBanHangKho/'
)
# Cài đặt một lần cấu hình gửi email cho Tomcat bằng file bin/setenv.bat.
# Tomcat đọc file này mỗi lần khởi động (kể cả khi chạy từ NetBeans), nên không cần chạy start-gmail.ps1 nữa.
# App Password chỉ được ghi vào thư mục Tomcat trên máy này, không ghi vào repository.
$ErrorActionPreference = 'Stop'

while (-not (Test-Path (Join-Path $TomcatHome 'bin/catalina.bat'))) {
  Write-Host "Không tìm thấy Tomcat tại: $TomcatHome" -ForegroundColor Yellow
  $TomcatHome = Read-Host 'Nhập thư mục Tomcat (thư mục chứa bin\catalina.bat), để trống để thoát'
  if ([string]::IsNullOrWhiteSpace($TomcatHome)) { throw 'Đã huỷ: chưa có thư mục Tomcat' }
  $TomcatHome = $TomcatHome.Trim('"').Trim()
}
$setenv = Join-Path $TomcatHome 'bin/setenv.bat'
$managed = '^\s*set\s+"?(MAIL_MODE|SMTP_USERNAME|SMTP_APP_PASSWORD|APP_URL)='

# Giữ nguyên các dòng cấu hình khác người dùng đã có trong setenv.bat.
$kept = @()
if (Test-Path $setenv) {
  Copy-Item $setenv "$setenv.bak" -Force
  $kept = @(Get-Content $setenv | Where-Object { $_ -notmatch $managed })
}

if ($AppUrl -notmatch '^https?://[^\s"%^&|<>]+$') { throw 'APP_URL phải bắt đầu bằng http:// hoặc https:// và không chứa khoảng trắng hay ký tự " % ^ & | < >' }
if (-not $AppUrl.EndsWith('/')) { $AppUrl = $AppUrl + '/' }

$lines = @()
if ($Mode -eq 'dev') {
  $lines += 'set "MAIL_MODE=dev"'
} else {
  $gmail = (Read-Host 'Gmail dùng để gửi thư (ví dụ tenban@gmail.com)').Trim()
  if ($gmail -notmatch '^[^\s@"%^&|<>]+@[^\s@"%^&|<>]+\.[^\s@"%^&|<>]+$') { throw 'Địa chỉ Gmail không hợp lệ' }
  $secret = Read-Host 'App Password của Google cho Gmail trên (không phải mật khẩu đăng nhập)' -AsSecureString
  $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
  try {
    $password = ([Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)) -replace '\s', ''
  } finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    $secret.Dispose()
  }
  if ($password -notmatch '^[A-Za-z]{16}$') { throw 'App Password của Google gồm 16 chữ cái (có thể dán kèm khoảng trắng). Kiểm tra lại tại https://myaccount.google.com/apppasswords' }
  $lines += 'set "MAIL_MODE=smtp"'
  $lines += 'set "SMTP_USERNAME=' + $gmail + '"'
  $lines += 'set "SMTP_APP_PASSWORD=' + $password + '"'
}
$lines += 'set "APP_URL=' + $AppUrl + '"'

Set-Content -Path $setenv -Value ($kept + $lines) -Encoding ASCII
$password = $null

Write-Host ''
Write-Host "Đã ghi cấu hình email vào: $setenv" -ForegroundColor Green
if ($Mode -eq 'smtp') {
  Write-Host "Chế độ: gửi Gmail thật từ $gmail"
} else {
  Write-Host 'Chế độ: dev (thư chỉ lưu trong hộp thư thử nghiệm KhoMail)'
}

# Nhắc migration hàng đợi email nếu kiểm tra được database.
$psql = Get-Command psql -ErrorAction SilentlyContinue
if ($Mode -eq 'smtp' -and $psql) {
  $dbUser = $env:DB_USER; if (-not $dbUser) { $dbUser = 'postgres' }
  $savedPg = $env:PGPASSWORD
  try {
    if ($env:DB_PASSWORD) { $env:PGPASSWORD = $env:DB_PASSWORD } elseif (-not $env:PGPASSWORD) { $env:PGPASSWORD = '123456' }
    $table = & $psql.Source -h localhost -U $dbUser -d quanlybanhangkho -tAc "SELECT to_regclass('public.mail_outbox') IS NOT NULL" 2>$null
    if ($LASTEXITCODE -eq 0 -and "$table".Trim() -eq 'f') {
      Write-Host 'Database chưa có bảng mail_outbox: hãy chạy database/smtp.sql trước khi gửi thư.' -ForegroundColor Yellow
    }
  } catch { } finally { $env:PGPASSWORD = $savedPg }
}

Write-Host 'Bước tiếp theo: tắt Tomcat (NetBeans: Services > Servers > Apache Tomcat > Stop) rồi chạy lại để áp dụng.' -ForegroundColor Cyan
if ($Mode -eq 'smtp') { Write-Host 'Kiểm tra: trang Quên mật khẩu không còn nút "Mở hộp thư thử nghiệm" và bảng mail_outbox có thư trạng thái SENT.' }
