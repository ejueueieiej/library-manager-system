
$base='http://localhost:8082'
$rc='D:\redis\redis-cli.exe'
$pass=0; $fail=0

function ReadJson($resp) { return ([Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray()) | ConvertFrom-Json) }
function Req($m,$u,$b) {
    if ($null -eq $b) { return Invoke-WebRequest -Method $m -Uri $u -Headers $script:h -UseBasicParsing }
    return Invoke-WebRequest -Method $m -Uri $u -Headers $script:h -UseBasicParsing -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes(($b | ConvertTo-Json -Compress -Depth 5)))
}
function Exists($k) { return ((& $rc -a 123456 -n 0 EXISTS $k 2>$null) -join '').Trim() }
function Check($name, $ok, $detail) {
    if ($ok) { $script:pass++; Write-Output ("[PASS] " + $name + "   " + $detail) }
    else     { $script:fail++; Write-Output ("[FAIL] " + $name + "   " + $detail) }
}

$login = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' -Body ([Text.Encoding]::UTF8.GetBytes('{"username":"admin","password":"123456"}'))
$script:h = @{ Authorization = "Bearer $($login.data.token)" }

$isbn = "CACHE" + [int]([DateTimeOffset]::Now.ToUnixTimeSeconds() % 100000000)
$dto = @{ isbn=$isbn; name='缓存失效测试书'; author='测试'; publisher='测试社'; publishDate='2020-01-01'; categoryId=1; price=10.00; totalNum=2; intro='自动化验证用'; status=1 }
$null = ReadJson (Req 'Post' "$base/books" $dto)
$list = ReadJson (Req 'Get' "$base/books?isbn=$isbn&pageNum=1&pageSize=1" $null)
$id = $list.data.list[0].id
$key = "Cache:Book:$id"
Write-Output ("测试图书 id=$id  isbn=$isbn  key=$key")

# 0) 预热缓存
$null = Req 'Get' "$base/books/$id" $null
Check '预热缓存' ((Exists $key) -eq '1') ("EXISTS=" + (Exists $key))

# 1) UpdateBook -> 删缓存
$dto2 = @{ isbn=$isbn; name='缓存失效测试书-改'; author='测试'; publisher='测试社'; publishDate='2020-01-01'; categoryId=1; price=10.00; totalNum=2; intro='自动化验证用'; status=1 }
$null = ReadJson (Req 'Put' "$base/books/$id" $dto2)
Check 'UpdateBook 删缓存' ((Exists $key) -eq '0') ("EXISTS=" + (Exists $key))
$d = ReadJson (Req 'Get' "$base/books/$id" $null)
Check '改后详情是新值' ($d.data.name -eq '缓存失效测试书-改') ("name=" + $d.data.name)

# 2) UpdateBookStatus -> 删缓存
$null = ReadJson (Req 'Patch' "$base/books/$id/status?status=0" $null)
Check 'UpdateBookStatus 删缓存' ((Exists $key) -eq '0') ("EXISTS=" + (Exists $key))
$d = ReadJson (Req 'Get' "$base/books/$id" $null)
Check '下架后详情 status=0' ($d.data.status -eq 0) ("status=" + $d.data.status)

# 上架回来
$null = ReadJson (Req 'Patch' "$base/books/$id/status?status=1" $null)
$null = Req 'Get' "$base/books/$id" $null
$before = (ReadJson (Req 'Get' "$base/books/$id" $null)).data.availableNum
Write-Output ("借书前 availableNum=" + $before)

# 3) BorrowBook -> 删缓存
$null = ReadJson (Req 'Post' "$base/borrows" @{ bookId=$id })
Check 'BorrowBook 删缓存' ((Exists $key) -eq '0') ("EXISTS=" + (Exists $key))
$d = ReadJson (Req 'Get' "$base/books/$id" $null)
$afterBorrow = $d.data.availableNum
Check '借书后详情可借数 -1' ([double]$afterBorrow -eq [double]$before - 1) ("availableNum=" + $afterBorrow)

# 找到借阅记录 id
$my = ReadJson (Req 'Get' "$base/borrows/my?pageNum=1&pageSize=20" $null)
$rec = $my.data.list | Where-Object { $_.bookId -eq $id -and $_.status -eq 1 } | Select-Object -First 1
$borrowId = $rec.id

# 4) ReturnBook -> 删缓存
$null = ReadJson (Req 'Patch' "$base/borrows/$borrowId/return" $null)
Check 'ReturnBook 删缓存' ((Exists $key) -eq '0') ("EXISTS=" + (Exists $key))
$d = ReadJson (Req 'Get' "$base/books/$id" $null)
Check '还书后详情可借数恢复' ([double]$d.data.availableNum -eq [double]$before) ("availableNum=" + $d.data.availableNum)

# 5) DeleteBook -> 删缓存
$null = ReadJson (Req 'Delete' "$base/books/$id" $null)
Check 'DeleteBook 删缓存' ((Exists $key) -eq '0') ("EXISTS=" + (Exists $key))
$d = ReadJson (Req 'Get' "$base/books/$id" $null)
Check '删书后详情提示不存在' ($d.msg -eq '图书不存在') ("msg=" + $d.msg)

# 清理借阅记录（已归还，可删）
try { $null = ReadJson (Req 'Delete' "$base/borrows/$borrowId" $null); Write-Output '借阅记录已清理' } catch { Write-Output ('借阅记录清理失败: ' + $_.Exception.Message) }
& $rc -a 123456 -n 0 DEL $key 2>$null | Out-Null

Write-Output ("结果: PASS=" + $pass + "  FAIL=" + $fail)
