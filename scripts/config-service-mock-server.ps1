Add-Type -AssemblyName System.Web

$listener = [System.Net.HttpListener]::new()
$listener.Prefixes.Add('http://localhost:8085/')
$listener.Start()

Write-Host 'config-service mock server listening on http://localhost:8085'

$permissions = @{
    'ROLE_ADMIN' = @{
        dashboard = @{ view = $true; edit = $true }
        orders    = @{ view = $true; edit = $true; create = $true; delete = $true }
        users     = @{ view = $true; edit = $true; create = $true; delete = $true }
    }
    'ROLE_USER' = @{
        dashboard = @{ view = $true; edit = $false }
        orders    = @{ view = $true; edit = $false; create = $true; delete = $false }
        users     = @{ view = $false; edit = $false; create = $false; delete = $false }
    }
    'ROLE_VIEWER' = @{
        dashboard = @{ view = $true; edit = $false }
        orders    = @{ view = $true; edit = $false; create = $false; delete = $false }
        users     = @{ view = $true; edit = $false; create = $false; delete = $false }
    }
}

try {
    while ($listener.IsListening) {
        $context = $listener.GetContext()
        $request = $context.Request
        $response = $context.Response

        $path = $request.Url.AbsolutePath
        $correlationId = $request.Headers['X-Correlation-Id']

        if ($path -match '^/api/config/permissions/(.+)$') {
            $role = [System.Web.HttpUtility]::UrlDecode($matches[1])
            $rolePerms = $permissions[$role]

            if ($rolePerms) {
                $payload = @{
                    role     = $role
                    features = $rolePerms
                } | ConvertTo-Json -Depth 5

                $buffer = [System.Text.Encoding]::UTF8.GetBytes($payload)
                $response.StatusCode = 200
                $response.ContentType = 'application/json'
                $response.ContentEncoding = [System.Text.Encoding]::UTF8
                $response.OutputStream.Write($buffer, 0, $buffer.Length)
            }
            else {
                $payload = @{ error = 'Role not found'; role = $role } | ConvertTo-Json
                $buffer = [System.Text.Encoding]::UTF8.GetBytes($payload)
                $response.StatusCode = 404
                $response.ContentType = 'application/json'
                $response.ContentEncoding = [System.Text.Encoding]::UTF8
                $response.OutputStream.Write($buffer, 0, $buffer.Length)
            }
        }
        else {
            $payload = @{ error = 'Not Found'; path = $path } | ConvertTo-Json
            $buffer = [System.Text.Encoding]::UTF8.GetBytes($payload)
            $response.StatusCode = 404
            $response.ContentType = 'application/json'
            $response.ContentEncoding = [System.Text.Encoding]::UTF8
            $response.OutputStream.Write($buffer, 0, $buffer.Length)
        }

        if ($correlationId) {
            $response.Headers['X-Correlation-Id'] = $correlationId
        }

        $response.Close()
    }
}
finally {
    $listener.Stop()
    $listener.Close()
}
