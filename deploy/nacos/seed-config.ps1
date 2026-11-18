# 把 config/ 目录下的 yaml 发布到 Nacos。
# 文件名即 dataId，统一发布到 JOTANG 组、public 命名空间。
#
# 用法（在仓库根目录执行）：
#   powershell -ExecutionPolicy Bypass -File deploy/nacos/seed-config.ps1
#
# 注意：
# - Nacos 3 的管理 API 需要控制台账号，本地默认 nacos/nacos。
# - Windows PowerShell 5.1 默认按 ANSI 读取文件，必须显式用 UTF-8，
#   否则中文注释会乱码并破坏 YAML 结构。

param(
    [string]$Server = "127.0.0.1:8848",
    [string]$Group = "JOTANG",
    [string]$User = "nacos",
    [string]$Password = "nacos"
)

$ErrorActionPreference = "Stop"
$configDir = Join-Path $PSScriptRoot "config"
$base = "http://$Server/nacos/v3/admin/cs/config?username=$User&password=$Password"

Get-ChildItem $configDir -Filter *.yaml | ForEach-Object {
    $content = [System.IO.File]::ReadAllText($_.FullName, [System.Text.Encoding]::UTF8)
    $form = "dataId=$([uri]::EscapeDataString($_.Name))" +
            "&groupName=$([uri]::EscapeDataString($Group))" +
            "&namespaceId=&type=yaml" +
            "&content=$([uri]::EscapeDataString($content))"
    $resp = Invoke-RestMethod -Method Post -Uri $base `
        -ContentType "application/x-www-form-urlencoded; charset=UTF-8" `
        -Body ([System.Text.Encoding]::UTF8.GetBytes($form))
    if ($resp.code -ne 0) {
        throw "发布失败 $($_.Name): $($resp.message)"
    }
    Write-Host "published $($_.Name)"
}
