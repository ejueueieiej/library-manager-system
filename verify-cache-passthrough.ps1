<#
.SYNOPSIS
    缓存穿透联调验证脚本（图书详情 Cache:Book:{id}）

.DESCRIPTION
    前提：Redis、MySQL 已启动，应用已在 8081 端口跑起来。

    用法：
        powershell -ExecutionPolicy Bypass -File .\verify-cache-passthrough.ps1

    可选参数：
        -ExistId 34         指定一个"确实存在"的图书 id；默认 0 = 自动取列表第一本
        -MissingId 999999   指定一个"确定不存在"的 id

    脚本做的三件事：登录拿 token -> 每类各请求两次 -> 查 Redis 的 key/TTL/长度。
    真正的"证据"在应用控制台：看第 2 次请求还有没有 book 表的 SQL。
#>
param(
    [string]$BaseUrl   = "http://localhost:8081",
    [string]$Username  = "admin",
    [string]$Password  = "123456",
    [int]$ExistId      = 0,
    [int]$MissingId    = 999999,
    [string]$RedisCli  = "D:\redis\redis-cli.exe",
    [string]$RedisPass = "123456",
    [int]$RedisDb      = 0
)

function Write-Step($Text) {
    Write-Host ""
    Write-Host ("=" * 72) -ForegroundColor DarkGray
    Write-Host $Text -ForegroundColor Cyan
    Write-Host ("=" * 72) -ForegroundColor DarkGray
}

# 统一按 UTF-8 解码响应体：Spring 返回的 Content-Type 不带 charset，
# PowerShell 5.1 会按 ISO-8859-1 解，中文全变乱码 —— 所以这里自己解码。
function Read-Utf8Json($Response) {
    $bytes = $Response.RawContentStream.ToArray()
    $text = [System.Text.Encoding]::UTF8.GetString($bytes)
    return ($text | ConvertFrom-Json)
}

# ---------------- 1. 登录拿 token ----------------
Write-Step "1. 登录：POST $BaseUrl/auth/login"

$loginJson = @{ username = $Username; password = $Password } | ConvertTo-Json -Compress
try {
    $loginResp = Invoke-WebRequest -Method Post -Uri "$BaseUrl/auth/login" `
        -ContentType "application/json; charset=utf-8" `
        -Body ([System.Text.Encoding]::UTF8.GetBytes($loginJson)) -UseBasicParsing
} catch {
    Write-Host "登录请求失败：$($_.Exception.Message)" -ForegroundColor Red
    Write-Host "检查：应用是否已启动？账号密码是否正确？" -ForegroundColor Yellow
    exit 1
}

$login = Read-Utf8Json $loginResp
if ($login.code -ne 1) {
    Write-Host "登录失败：$($login.msg)" -ForegroundColor Red
    exit 1
}

$token = $login.data.token
Write-Host "登录成功（$($login.msg)），token 前 24 位：$($token.Substring(0, [Math]::Min(24, $token.Length)))..." -ForegroundColor Green
$headers = @{ Authorization = "Bearer $token" }

# ---------------- 2. 请求工具 ----------------
function Invoke-BookDetail($Id) {
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $code = "-"
    $msg  = "-"
    try {
        $resp = Invoke-WebRequest -Method Get -Uri "$BaseUrl/books/$Id" -Headers $headers -UseBasicParsing
        $obj = Read-Utf8Json $resp
        $code = $obj.code
        $msg  = $obj.msg
    } catch {
        $code = "HTTP异常"
        $msg  = $_.Exception.Message
    }
    $sw.Stop()
    [pscustomobject]@{
        Id      = $Id
        Elapsed = $sw.ElapsedMilliseconds
        Code    = $code
        Msg     = $msg
    }
}

# ---------------- 3. 存在的 id：看命中 ----------------
if ($ExistId -le 0) {
    $listResp = Invoke-WebRequest -Uri "$BaseUrl/books?pageNum=1&pageSize=1" -Headers $headers -UseBasicParsing
    $list = Read-Utf8Json $listResp
    if ($list.data.list.Count -eq 0) {
        Write-Host "图书表是空的，先在系统里新增一本再测" -ForegroundColor Red
        exit 1
    }
    $ExistId = [int]$list.data.list[0].id
    Write-Host "自动选中存在的图书 id=$ExistId" -ForegroundColor Green
}

Write-Step "2. 存在的图书 id=$ExistId —— 连打两次，看第 2 次是否还有 book 表 SQL"
if (Test-Path $RedisCli) { & $RedisCli -a $RedisPass -n $RedisDb DEL "Cache:Book:$ExistId" 2>$null | Out-Null }
Invoke-BookDetail $ExistId | Format-Table -AutoSize | Out-String | Write-Host
Invoke-BookDetail $ExistId | Format-Table -AutoSize | Out-String | Write-Host
Write-Host "期望：第 1 次控制台有 'FROM book b' 的 SQL，第 2 次没有（只剩拦截器查 sys_user 那条）" -ForegroundColor Yellow

# ---------------- 4. 不存在的 id：看穿透防护 ----------------
Write-Step "3. 不存在的图书 id=$MissingId —— 连打两次，第 2 次绝不能碰数据库"
if (Test-Path $RedisCli) { & $RedisCli -a $RedisPass -n $RedisDb DEL "Cache:Book:$MissingId" 2>$null | Out-Null }
Invoke-BookDetail $MissingId | Format-Table -AutoSize | Out-String | Write-Host
Invoke-BookDetail $MissingId | Format-Table -AutoSize | Out-String | Write-Host
Write-Host "期望：两次都返回 code=0 图书不存在；第 1 次有 book 表 SQL，第 2 次没有" -ForegroundColor Yellow

# ---------------- 5. 验证 Redis 里到底存了什么 ----------------
Write-Step "4. 检查 Redis（空串用 GET 看是空行，必须用 EXISTS/STRLEN 判断）"

if (-not (Test-Path $RedisCli)) {
    Write-Host "没找到 redis-cli：$RedisCli，手动在 Another Redis Desktop Manager 里看吧" -ForegroundColor Yellow
} else {
    $rows = @()
    $missingTtl = -1
    foreach ($id in @($ExistId, $MissingId)) {
        $key    = "Cache:Book:$id"
        $exists = (& $RedisCli -a $RedisPass -n $RedisDb EXISTS $key 2>$null) -join ""
        $ttl    = (& $RedisCli -a $RedisPass -n $RedisDb TTL $key 2>$null) -join ""
        $strlen = (& $RedisCli -a $RedisPass -n $RedisDb STRLEN $key 2>$null) -join ""
        if ($id -eq $MissingId) { $missingTtl = [int]$ttl.Trim() }
        $rows += [pscustomobject]@{
            Key      = $key
            Exists   = $exists.Trim()
            TTL秒    = $ttl.Trim()
            Value字节 = $strlen.Trim()
        }
    }
    $rows | Format-Table -AutoSize | Out-String | Write-Host

    Write-Host "判定标准：" -ForegroundColor Yellow
    Write-Host "  存在的书     Exists=1，Value 字节数 > 0，TTL 在 0~600 之间（10 分钟）"
    Write-Host "  不存在的书   Exists=1，Value 字节数 = 0（空串），TTL 在 0~120 之间（2 分钟）"

    if ($missingTtl -gt 150) {
        Write-Host ""
        Write-Host "⚠ 空值缓存的 TTL 是 $missingTtl 秒（约 10 分钟）—— 说明跑的还是旧代码，重启应用后再测。" -ForegroundColor Red
    }
}

Write-Step "完成。对照应用控制台里两次请求的 SQL 条数，就是穿透防护的实证。"
